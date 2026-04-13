package com.example.register;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.ResponseBody;
import okio.ByteString;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketManager {
    private WebSocket webSocket;
    private OkHttpClient client;
    private OkHttpClient httpClient;
    private VoiceShieldService service;
    private String currentUrl;
    private String serverBaseUrl;
    private String sessionId = null;

    // Use AtomicBoolean to prevent race conditions on multi-threaded callbacks
    private final AtomicBoolean callEnded          = new AtomicBoolean(false);
    private final AtomicBoolean isSendingAudio     = new AtomicBoolean(true);
    private final AtomicBoolean finalVerdictReceived = new AtomicBoolean(false);
    private final AtomicBoolean pollingStarted     = new AtomicBoolean(false);

    private static final int MAX_POLL_RETRIES  = 8;
    private static final int POLL_INTERVAL_MS  = 2000;
    private volatile int pollRetryCount        = 0;

    private static final String TAG = "VoiceShieldWS";

    public WebSocketManager(VoiceShieldService service) {
        this.service = service;
        this.client = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)     // Increased from 15s to 30s
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .build();
    }

    public void start(String serverUrl) {
        this.currentUrl = serverUrl;

        // Derive base HTTP URL from WebSocket URL
        this.serverBaseUrl = serverUrl
                .replace("ws://", "http://")
                .replace("wss://", "https://")
                .replaceAll("/listen.*", "");

        Log.d(TAG, "🌐 Server base URL: " + serverBaseUrl);

        // Reset all state flags on each new connection
        callEnded.set(false);
        isSendingAudio.set(true);
        finalVerdictReceived.set(false);
        pollingStarted.set(false);
        pollRetryCount = 0;
        sessionId = null;

        Request request = new Request.Builder().url(serverUrl).build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "✅ Connected to AI Server!");
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Log.d(TAG, "📩 WS RAW: " + text);
                try {
                    JSONObject root = new JSONObject(text);
                    String type = root.optString("type", "");

                    if (type.equals("SESSION_ID")) {
                        sessionId = root.optString("session_id", null);
                        Log.d(TAG, "🔑 Session ID saved: " + sessionId);
                        return;
                    }

                    if (type.contains("COMPLETE_CALL_VERDICT") ||
                            type.contains("FINAL") ||
                            type.contains("PROACTIVE") ||
                            type.contains("ULTIMATE")) {
                        if (finalVerdictReceived.compareAndSet(false, true)) {
                            Log.d(TAG, "🎯 WS FINAL VERDICT received!");
                            handleFinalVerdict(root);
                        }
                        return;
                    }

                    // Live chunk verdicts
                    int currentScore = 0;
                    int avgScore     = 0;
                    String reason    = "Analyzing conversation...";

                    if (root.has("entire_call_verdict")) {
                        JSONObject entire = root.getJSONObject("entire_call_verdict");
                        if (entire.has("risk_assessment")) {
                            JSONObject risk = entire.getJSONObject("risk_assessment");
                            currentScore = (int) Math.round(risk.optDouble("risk_score", 0.0));
                            avgScore     = (int) Math.round(risk.optDouble("avg_score", currentScore));
                        }
                        if (entire.has("linguistic_analysis")) {
                            reason = entire.getJSONObject("linguistic_analysis").optString("reason", reason);
                        }
                    } else if (root.has("risk_assessment")) {
                        JSONObject assessment = root.getJSONObject("risk_assessment");
                        currentScore = (int) Math.round(assessment.optDouble("risk_score", 0.0));
                        avgScore     = (int) Math.round(assessment.optDouble("avg_score", currentScore));
                        reason       = assessment.optString("conclusion", "Suspicious activity detected.");
                    } else if (root.has("combined_risk_score")) {
                        currentScore = (int) Math.round(root.getDouble("combined_risk_score"));
                        avgScore     = currentScore;
                        reason       = root.optString("linguistic_reason", "Potential threat...");
                    } else if (root.has("risk_score")) {
                        currentScore = root.getInt("risk_score");
                        avgScore     = root.optInt("avg_score", currentScore);
                        reason       = root.optString("reason", reason);
                    }

                    if (currentScore > 0 || avgScore > 0) {
                        final int finalCurrent = currentScore;
                        final int finalAvg     = avgScore;
                        final String finalReason = reason;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (service != null) {
                                service.processNewScore(finalCurrent, finalAvg, finalReason);
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "❌ WS JSON Error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                Log.e(TAG, "❌ WS Failed: " + t.getMessage());
                if (callEnded.get() && !finalVerdictReceived.get() && sessionId != null) {
                    startPollingForVerdict();
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "📴 WS Closed: " + reason + " (code: " + code + ")");
                if (callEnded.get() && !finalVerdictReceived.get() && sessionId != null) {
                    startPollingForVerdict();
                }
            }
        });
    }

    public void sendAudio(byte[] pcmData) {
        if (webSocket != null && isSendingAudio.get()) {
            try {
                webSocket.send(ByteString.of(pcmData));
            } catch (Exception e) {
                Log.e(TAG, "❌ Audio send failed: " + e.getMessage());
            }
        }
    }

    /**
     * CRITICAL: Call this when the phone call ends — NOT close().
     * This starts HTTP polling for the final verdict.
     * close() can be called 15s later via a Handler to clean up.
     */
    public void markCallEnded() {
        if (!callEnded.compareAndSet(false, true)) {
            Log.w(TAG, "⚠️ markCallEnded() called twice — ignoring duplicate");
            return;
        }
        isSendingAudio.set(false);
        Log.d(TAG, "📴 Call ENDED — 5s WS grace → HTTP poll (session: " + sessionId + ")");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!finalVerdictReceived.get()) {
                Log.d(TAG, "⏳ No WS verdict after 5s → starting HTTP polling");
                if (webSocket != null) {
                    webSocket.close(1000, "Call ended - switching to HTTP polling");
                }
                startPollingForVerdict();
            } else {
                Log.d(TAG, "✅ WS already delivered verdict — skipping HTTP poll");
            }
        }, 5000);
    }

    private void startPollingForVerdict() {
        if (sessionId == null) {
            Log.e(TAG, "❌ No session_id — cannot poll! Was SESSION_ID message received?");
            return;
        }
        if (finalVerdictReceived.get()) {
            Log.d(TAG, "✅ Verdict already received — polling skipped");
            return;
        }
        // Guard: only start polling once even if onClosed + onFailure both fire
        if (!pollingStarted.compareAndSet(false, true)) {
            Log.w(TAG, "⚠️ Polling already started — ignoring duplicate trigger");
            return;
        }
        pollRetryCount = 0;
        Log.d(TAG, "🚀 HTTP verdict polling STARTED for session: " + sessionId);
        pollOnce();
    }

    private void pollOnce() {
        if (finalVerdictReceived.get()) {
            Log.d(TAG, "✅ Polling stopped — verdict already received");
            return;
        }
        if (pollRetryCount >= MAX_POLL_RETRIES) {
            Log.e(TAG, "❌ Max poll retries (" + MAX_POLL_RETRIES + ") reached — giving up");
            return;
        }

        pollRetryCount++;
        String url = serverBaseUrl + "/final-verdict/" + sessionId;
        Log.d(TAG, "📡 Poll #" + pollRetryCount + "/" + MAX_POLL_RETRIES + ": " + url);

        Request request = new Request.Builder().url(url).get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "❌ Poll HTTP FAILED #" + pollRetryCount + ": " + e.getMessage());
                scheduleNextPoll();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "❌ Poll HTTP " + response.code() + " on attempt #" + pollRetryCount);
                        scheduleNextPoll();
                        return;
                    }

                    String bodyString = body != null ? body.string() : "{}";
                    Log.d(TAG, "📥 Poll response #" + pollRetryCount + ": " + bodyString);

                    JSONObject json   = new JSONObject(bodyString);
                    String status     = json.optString("status", "unknown");

                    Log.d(TAG, "📊 Poll status='" + status + "' has(verdict)=" + json.has("verdict"));

                    if ("ready".equals(status)) {
                        if (finalVerdictReceived.compareAndSet(false, true)) {
                            JSONObject verdict = json.getJSONObject("verdict");
                            Log.d(TAG, "🎯 HTTP VERDICT FOUND on poll #" + pollRetryCount);
                            handleFinalVerdict(verdict);
                        }
                    } else {
                        Log.d(TAG, "⏳ Verdict not ready → scheduling poll #" + (pollRetryCount + 1));
                        scheduleNextPoll();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Poll parse error #" + pollRetryCount + ": " + e.getMessage());
                    scheduleNextPoll();
                }
            }
        });
    }

    private void scheduleNextPoll() {
        new Handler(Looper.getMainLooper()).postDelayed(this::pollOnce, POLL_INTERVAL_MS);
    }

    private void handleFinalVerdict(JSONObject root) {
        try {
            Log.d(TAG, "🔍 Parsing verdict. Raw (first 200): "
                    + root.toString().substring(0, Math.min(200, root.toString().length())));

            JSONObject data = root.optJSONObject("data");
            if (data == null) data = root;

            double score = 0.0;
            if (data.has("risk_assessment")) {
                score = data.getJSONObject("risk_assessment").optDouble("risk_score", 0.0);
                Log.d(TAG, "📊 Score from risk_assessment: " + score);
            } else {
                score = data.optDouble("risk_score",
                        data.optDouble("average_risk_score",
                                data.optDouble("combined_risk_score", 0.0)));
                Log.d(TAG, "📊 Score from fallback fields: " + score);
            }

            String reason = "Call analysis completed";
            if (data.has("linguistic_analysis")) {
                reason = data.getJSONObject("linguistic_analysis").optString("reason", reason);
            } else {
                reason = data.optString("linguistic_reason", data.optString("reason", reason));
            }

            final int    finalScore  = (int) Math.round(score);
            final String finalReason = reason;

            Log.d(TAG, "🛡️ FINAL VERDICT → Score: " + finalScore + " | Reason: " + finalReason);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (service != null) {
                    service.processNewScore(finalScore, finalScore, finalReason);
                    service.updateFinalVerdict(finalScore, finalReason);
                    Log.d(TAG, "✅ Service updated with final verdict!");
                } else {
                    Log.w(TAG, "⚠️ Service is null — UI not updated");
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "❌ Verdict parse FAILED: " + e.getMessage());
            Log.e(TAG, "🔍 Full root: " + root.toString());
        }
    }

    /**
     * Only call this for final cleanup (e.g. 15s after markCallEnded).
     * Do NOT call this to end a call — use markCallEnded() instead.
     */
    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Service shutdown");
            Log.d(TAG, "🛑 WS closed (service shutdown)");
        }
    }

    // Debug accessors
    public String  getSessionId()     { return sessionId; }
    public boolean hasFinalVerdict()  { return finalVerdictReceived.get(); }
    public String  getServerBaseUrl() { return serverBaseUrl; }
}