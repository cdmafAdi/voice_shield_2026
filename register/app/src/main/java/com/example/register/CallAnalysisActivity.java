package com.example.register;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.BlockedNumberContract;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class CallAnalysisActivity extends AppCompatActivity {

    private ProgressBar riskProgressBar;
    private TextView tvRiskPercentage, tvCallerNumber, tvSignalStatus, tvAnalysisSummary;
    private View riskGlowCircle, riskGlowCircleOuter;
    private final ArgbEvaluator colorEvaluator = new ArgbEvaluator();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Transparent UI Setup
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_call_analysis);

        // 2. Initialize Views (Cleaned - No Jitter/Stress here)
        riskProgressBar = findViewById(R.id.riskProgressBar);
        tvRiskPercentage = findViewById(R.id.tvRiskPercentage);
        tvCallerNumber = findViewById(R.id.tvCallerNumber);
        tvSignalStatus = findViewById(R.id.tvSignalStatus);
        tvAnalysisSummary = findViewById(R.id.tvAnalysisSummary);
        riskGlowCircle = findViewById(R.id.riskGlowCircle);
        riskGlowCircleOuter = findViewById(R.id.riskGlowCircleOuter);

        View btnBack = findViewById(R.id.btnBack);
        View btnBlock = findViewById(R.id.btnBlock);
        View btnWhitelist = findViewById(R.id.btnWhitelist);

        // 3. Catch Call Data
        CallLogModel data = (CallLogModel) getIntent().getSerializableExtra("EXTRA_CALL_DATA");

        // Inside onCreate, after "if (data != null) {"
        // Inside onCreate after finding all views
        if (data != null) {
            // 1. Set the Big Title
            if (tvCallerNumber != null) {
                tvCallerNumber.setText(data.number);
            }

            // 2. Set the Status (Intelligence Report Title)
            // This was the line causing the crash! Now it is safe.
            if (tvSignalStatus != null) {
                tvSignalStatus.setText(data.label);
            }

            // 3. Set the Summary
            if (tvAnalysisSummary != null) {
                tvAnalysisSummary.setText(data.summary);
            }

            // 4. Set the new metadata fields
            TextView tvType = findViewById(R.id.tvAnalysisType);
            TextView tvStart = findViewById(R.id.tvAnalysisStartTime);
            TextView tvDuration = findViewById(R.id.tvAnalysisDuration);

            if (tvType != null) tvType.setText("Type: " + data.callType);
            if (tvStart != null) tvStart.setText("Started: " + data.timestamp);
            if (tvDuration != null) tvDuration.setText("Duration: " + data.duration);

            animateRiskMeter(data.riskScore);
        }

        // 4. Button Logic
        // 4. Button Logic
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (btnBlock != null) {
            btnBlock.setOnClickListener(v -> {
                if (data == null || data.number == null || data.number.isEmpty()) {
                    Toast.makeText(this, "Cannot block: Invalid number", Toast.LENGTH_SHORT).show();
                    return;
                }

                String numberToBlock = data.number.trim();

                // Optional: Remove +91 if present
                if (numberToBlock.startsWith("+91")) {
                    numberToBlock = numberToBlock.substring(3).trim();
                }

                blockPhoneNumber(numberToBlock);
            });
        }

        if (btnWhitelist != null) {
            btnWhitelist.setOnClickListener(v -> {
                if (data == null || data.number == null || data.number.isEmpty()) {
                    Toast.makeText(this, "Cannot mark as safe: Invalid number", Toast.LENGTH_SHORT).show();
                    return;
                }

                String numberToSave = data.number.trim();
                markNumberAsSafe(numberToSave);
            });
        }

        pulseGlowAnimation();
    }
    private void blockPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Toast.makeText(this, "Invalid number to block", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {

                ContentValues values = new ContentValues();
                values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phoneNumber.trim());

                getContentResolver().insert(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                        values
                );

                Toast.makeText(this, "✅ " + phoneNumber + " has been BLOCKED successfully", Toast.LENGTH_LONG).show();
                finish();

            } else {
                Toast.makeText(this, "Blocking not supported on Android " + android.os.Build.VERSION.SDK_INT, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e("CallAnalysis", "Block failed: " + e.getMessage());
            Toast.makeText(this, "Failed to block number.\nTry blocking from Phone Settings.", Toast.LENGTH_LONG).show();
        }
    }
    private void markNumberAsSafe(String phoneNumber) {
        try {
            // Save to Safe List using SharedPreferences (Simple & Reliable)
            SharedPreferences prefs = getSharedPreferences("SafeNumbers", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Add number to set (prevents duplicates)
            java.util.Set<String> safeSet = prefs.getStringSet("safe_numbers", new java.util.HashSet<>());
            safeSet.add(phoneNumber);

            editor.putStringSet("safe_numbers", safeSet);
            editor.apply();

            Toast.makeText(this, "✅ " + phoneNumber + " marked as SAFE", Toast.LENGTH_LONG).show();
            finish();

        } catch (Exception e) {
            Log.e("CallAnalysis", "Failed to mark as safe: " + e.getMessage());
            Toast.makeText(this, "Failed to mark as safe. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
    private void animateRiskMeter(int targetProgress) {
        ValueAnimator animator = ValueAnimator.ofInt(0, targetProgress);
        animator.setDuration(2200);
        animator.setInterpolator(new DecelerateInterpolator());

        // Hex Colors for your 40/75 logic
        int colorGreen = 0xFF00FFC5;
        int colorYellow = 0xFFFFD700;
        int colorRed = 0xFFFF4B4B;

        animator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();

            if (riskProgressBar != null) {
                riskProgressBar.setProgress(progress);

                int mixedColor;
                // Applying your specific 40/75 logic
                if (progress < 40) {
                    float fraction = progress / 40f;
                    mixedColor = (int) colorEvaluator.evaluate(fraction, colorGreen, colorYellow);
                } else if (progress < 75) {
                    float fraction = (progress - 40) / 35f;
                    mixedColor = (int) colorEvaluator.evaluate(fraction, colorYellow, colorRed);
                } else {
                    mixedColor = colorRed;
                }

                ColorStateList csl = ColorStateList.valueOf(mixedColor);
                riskProgressBar.setProgressTintList(csl);
                riskGlowCircle.setBackgroundTintList(csl);
                riskGlowCircleOuter.setBackgroundTintList(csl);
                tvRiskPercentage.setTextColor(mixedColor);
            }

            if (tvRiskPercentage != null) {
                tvRiskPercentage.setText(progress + "%");
            }
        });
        animator.start();
    }

    private void pulseGlowAnimation() {
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.12f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.12f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0.4f, 0.7f);

        View[] components = {riskProgressBar, riskGlowCircle, riskGlowCircleOuter};

        for (View v : components) {
            if (v != null) {
                ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(v, scaleX, scaleY, alpha);
                pulse.setDuration(1800);
                pulse.setInterpolator(new AccelerateDecelerateInterpolator());
                pulse.setRepeatCount(ObjectAnimator.INFINITE);
                pulse.setRepeatMode(ObjectAnimator.REVERSE);
                pulse.start();
            }
        }
    }
}