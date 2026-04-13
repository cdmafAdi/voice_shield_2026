package com.example.register;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.*;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceShieldService extends Service {
    private static final String TAG = "VoiceShieldService";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private boolean isIncomingCall = true;
    private View overlayView;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;

    private int lastScore = 0;
    private int lastServerAverage = 0;
    private String lastReason = "No suspicious patterns detected.";
    private long startTimeMillis;
    private String callReceivedTime;
    private String currentCallNumber = "Unknown Number";

    private AudioRecord recorder;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private WebSocketManager socketManager;
    private File tempDir;

    private boolean callHasEnded = false;   // NEW

    @Override
    public void onCreate() {
        setupForegroundService();
        super.onCreate();
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        startTimeMillis = System.currentTimeMillis();
        callReceivedTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupStorage();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupForegroundService();

        if (intent != null) {
            if ("STOP_RECORDING_GRACEFULLY".equals(intent.getAction())) {
                handleCallEndedGracefully();
                return START_STICKY;
            }
            if (intent.hasExtra("contact_number")) {
                currentCallNumber = intent.getStringExtra("contact_number");
            }
        }

        showOverlayBanner();

        if (socketManager == null) {
            socketManager = new WebSocketManager(this);
            socketManager.start("wss://operose-cedrick-unescheatable.ngrok-free.dev/listen");
        }

        mainHandler.postDelayed(this::startRecording, 1500);

        return START_STICKY;
    }

    public void processNewScore(int score, int avgScore, String reason) {
        Log.d(TAG, "📊 Live Score Received → " + score + "% | Reason: " + reason);
        this.lastScore = score;
        this.lastServerAverage = avgScore;
        this.lastReason = reason;
        updateUI(score);
    }

    // NEW METHOD - For late final verdict after call ends
    public void updateFinalVerdict(int finalScore, String finalReason) {
        this.lastServerAverage = finalScore;
        this.lastReason = finalReason;

        Log.d(TAG, "🔄 Late final verdict received - Updating call log");

        long durationSecs = (System.currentTimeMillis() - startTimeMillis) / 1000;
        String durationStr = String.format(Locale.getDefault(), "%02d:%02d",
                Math.max(0, durationSecs / 60), Math.max(0, durationSecs % 60));

        String label = (finalScore >= 75) ? "SCAM" : (finalScore >= 40 ? "SUSPICIOUS" : "SAFE");

        CallLogModel updatedLog = new CallLogModel(
                currentCallNumber != null ? currentCallNumber : "Unknown",
                finalScore,
                label,
                finalReason != null ? finalReason : "No threats detected",
                callReceivedTime != null ? callReceivedTime : "Just now",
                isIncomingCall ? "Incoming" : "Outgoing",
                durationStr
        );

        CallHistoryManager.saveCall(this, updatedLog);
    }

    private void handleCallEndedGracefully() {
        isRecording.set(false);
        callHasEnded = true;

        if (socketManager != null) {
            socketManager.markCallEnded();   // ← This was already there
        }

        // Save initial log
        long durationSecs = (System.currentTimeMillis() - startTimeMillis) / 1000;
        String durationStr = String.format(Locale.getDefault(), "%02d:%02d",
                Math.max(0, durationSecs / 60), Math.max(0, durationSecs % 60));

        String label = (lastServerAverage >= 75) ? "SCAM" : (lastServerAverage >= 40 ? "SUSPICIOUS" : "SAFE");

        CallLogModel finalLog = new CallLogModel(
                currentCallNumber != null ? currentCallNumber : "Unknown",
                (int) lastServerAverage,
                label,
                lastReason != null ? lastReason : "No threats detected",
                callReceivedTime != null ? callReceivedTime : "Just now",
                isIncomingCall ? "Incoming" : "Outgoing",
                durationStr
        );
        CallHistoryManager.saveCall(this, finalLog);

        Log.d(TAG, "✅ Initial log saved. Waiting for possible late verdict...");

        // Final cleanup after grace period
        // Final cleanup after grace period
        mainHandler.postDelayed(() -> {
            removeBanner();
            if (socketManager != null) socketManager.close();
            stopForeground(true);
            stopSelf();
        }, 15000);   // ← Changed from 30000 to 15000
    }

    private void removeBanner() {
        try {
            if (windowManager != null && overlayView != null && overlayView.isAttachedToWindow()) {
                windowManager.removeView(overlayView);
                Log.d("VoiceShieldService", "🚫 Banner removed from screen.");
            }
        } catch (Exception e) {
            Log.e("VoiceShieldService", "Error removing banner: " + e.getMessage());
        } finally {
            overlayView = null;
        }
    }

    private void startRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) return;
        recorder.startRecording();
        isRecording.set(true);
        new Thread(() -> recordLoop(bufferSize)).start();
    }

    private void recordLoop(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream();
        while (isRecording.get()) {
            int read = recorder.read(buffer, 0, buffer.length);
            if (read > 0) {
                applyGain(buffer, 5.0f);
                chunkBuffer.write(buffer, 0, read);
                if (chunkBuffer.size() >= 96000) {
                    byte[] chunk = chunkBuffer.toByteArray();
                    if (socketManager != null) socketManager.sendAudio(chunk);
                    chunkBuffer.reset();
                }
            }
        }
    }

    private void applyGain(byte[] buffer, float gain) {
        for (int i = 0; i < buffer.length; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            int amplified = (int) (sample * gain);
            if (amplified > 32767) amplified = 32767;
            else if (amplified < -32768) amplified = -32768;
            buffer[i] = (byte) (amplified & 0xFF);
            buffer[i + 1] = (byte) ((amplified >> 8) & 0xFF);
        }
    }

    private void showOverlayBanner() {
        try {
            if (overlayView != null) return;

            ContextThemeWrapper themeWrapper = new ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar);
            overlayView = LayoutInflater.from(themeWrapper).inflate(R.layout.layout_call_alert, null);

            int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP;
            params.y = 300;

            overlayView.setVisibility(View.INVISIBLE);
            windowManager.addView(overlayView, params);

            overlayView.findViewById(R.id.btnMinimize).setOnClickListener(v -> {
                overlayView.setVisibility(View.GONE);
            });

            Log.d(TAG, "✅ Banner layout initialized successfully.");
        } catch (Exception e) {
            Log.e(TAG, "❌ WindowManager Inflation Error: " + e.getMessage());
        }
    }

    public void updateUI(int risk) {
        if (overlayView == null) return;

        mainHandler.post(() -> {
            try {
                CardView card = overlayView.findViewById(R.id.alertCard);
                TextView tvTitle = overlayView.findViewById(R.id.tvAlertTitle);
                TextView tvRisk = overlayView.findViewById(R.id.tvRiskLevel);
                ProgressBar progress = overlayView.findViewById(R.id.riskProgress);
                TextView tvReason = overlayView.findViewById(R.id.tvReason);

                tvRisk.setText(risk + "%");
                if (progress != null) {
                    progress.setProgress(risk);
                }

                if (risk >= 75) {
                    card.setCardBackgroundColor(0xFFFF4444);
                    tvTitle.setText("⚠ THREAT DETECTED");
                    tvTitle.setTextColor(0xFFFFFFFF);
                    tvRisk.setTextColor(0xFFFFFFFF);
                    if (tvReason != null) {
                        tvReason.setTextColor(0xFFFFFFFF);
                        tvReason.setText(lastReason != null && !lastReason.isEmpty()
                                ? lastReason
                                : "High risk voice patterns & urgent manipulation detected");
                    }
                    if (toneGenerator != null) {
                        toneGenerator.startTone(ToneGenerator.TONE_SUP_INTERCEPT, 300);
                    }
                    if (vibrator != null) {
                        vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
                    }

                } else if (risk >= 40) {
                    card.setCardBackgroundColor(0xFFFFBB00);
                    tvTitle.setText("⚡ CAUTION ADVISED");
                    tvTitle.setTextColor(0xFF000000);
                    tvRisk.setTextColor(0xFF000000);
                    if (tvReason != null) {
                        tvReason.setTextColor(0xFF000000);
                        tvReason.setText(lastReason != null && !lastReason.isEmpty()
                                ? lastReason
                                : "Suspicious conversation pattern detected");
                    }
                    if (toneGenerator != null) {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
                    }

                } else {// LOW RISK → Hide the banner (as per your requirement)
                    overlayView.setVisibility(View.INVISIBLE);
                    return;
                }

                // Always show the banner now (even for safe calls) so you can see it's alive
                // Show banner only for Caution or Threat
                if (overlayView.getVisibility() != View.VISIBLE) {
                    overlayView.setVisibility(View.VISIBLE);
                    overlayView.setAlpha(0f);
                    overlayView.animate()
                            .alpha(1f)
                            .translationY(0)
                            .setDuration(280)
                            .start();
                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating banner UI: " + e.getMessage());
            }
        });
    }

    private void setupForegroundService() {
        String CHANNEL_ID = "ShieldChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(CHANNEL_ID, "Shield Protection", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(chan);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("VoiceShield is Active")
                .setContentText("Protecting you from scam calls...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
    }

    private void setupStorage() {
        tempDir = new File(getExternalFilesDir(null), "chunks");
        if (!tempDir.exists()) tempDir.mkdirs();
    }

    @Override
    public void onDestroy() {
        isRecording.set(false);
        if (windowManager != null && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        if (socketManager != null) socketManager.close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}