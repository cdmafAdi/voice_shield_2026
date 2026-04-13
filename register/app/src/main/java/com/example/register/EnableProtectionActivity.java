package com.example.register;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import androidx.appcompat.widget.AppCompatButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

public class EnableProtectionActivity extends AppCompatActivity {

    private AppCompatButton btnStartProtection;
    private final int TOTAL_PERMISSIONS = 3;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                refreshAllRows();
                checkFinalStatus();
                if (!isGranted) {
                    Toast.makeText(this, "Protocol Authorization Failed", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enable_protection);

        startAmbientFloating();

        btnStartProtection = findViewById(R.id.btnStartProtection);
        if (btnStartProtection != null) {
            btnStartProtection.setEnabled(false);
            btnStartProtection.setAlpha(0.3f);
        }

        // 2. Initialize Permission Cards
        setupPermissionRow(findViewById(R.id.cardMic), android.R.drawable.ic_btn_speak_now,
                "Microphone", "Analyze caller audio for deepfakes.", Manifest.permission.RECORD_AUDIO);

        setupPermissionRow(findViewById(R.id.cardPhone), android.R.drawable.stat_sys_phone_call,
                "Phone State", "Detect when calls are active.", Manifest.permission.READ_PHONE_STATE);

        setupPermissionRow(findViewById(R.id.cardOverlay), android.R.drawable.ic_menu_today,
                "Call Integrity", "Verify incoming numbers.", Manifest.permission.READ_CALL_LOG);

        animateEntrance();

        // --- UPDATED BACK NAVIGATION ---
        View btnBack = findViewById(R.id.btnBackPerm);
        if (btnBack == null) btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> animateClick(v, () -> {
                // Explicitly return to Security & Privacy
                Intent intent = new Intent(EnableProtectionActivity.this, SecurityPrivacyActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }));
        }
        // -------------------------------

        if (btnStartProtection != null) {
            btnStartProtection.setOnClickListener(v -> animateClick(v, () -> {
                getSharedPreferences("AppPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("isSetupDone", true)
                        .apply();

                Intent intent = new Intent(EnableProtectionActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }));
        }

        checkFinalStatus();
    }

    private void setupPermissionRow(View container, int icon, String title, String desc, String androidPermission) {
        if (container == null) return;

        ImageView imgIcon = container.findViewById(R.id.permIcon);
        TextView txtTitle = container.findViewById(R.id.permTitle);
        TextView txtDesc = container.findViewById(R.id.permDesc);
        View btnGrant = container.findViewById(R.id.btnGrant);

        if (imgIcon != null) imgIcon.setImageResource(icon);
        if (txtTitle != null) txtTitle.setText(title);
        if (txtDesc != null) txtDesc.setText(desc);

        if (btnGrant != null) {
            btnGrant.setOnClickListener(v -> requestPermissionLauncher.launch(androidPermission));
        }

        updateRowState(container, androidPermission, false);
    }

    private void updateRowState(View container, String permission, boolean animate) {
        if (container == null) return;

        CardView card = container.findViewById(R.id.mainCard);
        View btnGrant = container.findViewById(R.id.btnGrant);
        View statusLayout = container.findViewById(R.id.txtStatus);
        View borderGlow = container.findViewById(R.id.borderGlow);

        // FIXED: Using ID instead of getChildAt to avoid resolution errors
        View contentLayout = container.findViewById(R.id.cardContentLayout);

        boolean isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;

        if (isGranted) {
            if (btnGrant != null) btnGrant.setVisibility(View.GONE);
            if (statusLayout != null) statusLayout.setVisibility(View.VISIBLE);

            // 1. Light up the border glow with Dark Purple Luxury Tint
            if (borderGlow != null) {
                borderGlow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#D8B4FE")));
                borderGlow.animate().alpha(1.0f).setDuration(500).start();
            }

            if (card != null) {
                // 2. Switch to active Purple Glass Look (Deeper purple glass)
                card.setCardBackgroundColor(Color.parseColor("#26D8B4FE"));
                if (contentLayout != null) {
                    contentLayout.setBackgroundResource(R.drawable.glass_card_purple_tint);
                }
            }
        } else {
            // Default "Not Granted" Frost Glass State
            if (btnGrant != null) btnGrant.setVisibility(View.VISIBLE);
            if (statusLayout != null) statusLayout.setVisibility(View.GONE);
            if (borderGlow != null) borderGlow.setAlpha(0f);

            if (card != null) {
                card.setCardBackgroundColor(Color.parseColor("#0DFFFFFF"));
                if (contentLayout != null) {
                    contentLayout.setBackgroundResource(R.drawable.bg_premium_card_border);
                }
            }
        }
    }

    private void refreshAllRows() {
        updateRowState(findViewById(R.id.cardMic), Manifest.permission.RECORD_AUDIO, true);
        updateRowState(findViewById(R.id.cardPhone), Manifest.permission.READ_PHONE_STATE, true);
        updateRowState(findViewById(R.id.cardOverlay), Manifest.permission.READ_CALL_LOG, true);
    }

    private void checkFinalStatus() {
        int grants = 0;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) grants++;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) grants++;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) grants++;

        if (grants >= TOTAL_PERMISSIONS) {
            if (btnStartProtection != null) {
                btnStartProtection.setEnabled(true);
                btnStartProtection.animate().alpha(1.0f).setDuration(500).start();
            }
        }
    }

    private void animateClick(View view, Runnable action) {
        if (view == null) return;
        view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).withEndAction(() -> {
            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
            if (action != null) view.postDelayed(action, 50);
        }).start();
    }

    private void animateEntrance() {
        View btnBack = findViewById(R.id.btnBackPerm);
        if (btnBack == null) btnBack = findViewById(R.id.btnBack);

        View[] views = {
                btnBack,
                findViewById(R.id.permHeader),
                findViewById(R.id.cardMic),
                findViewById(R.id.cardPhone),
                findViewById(R.id.cardOverlay),
                btnStartProtection
        };

        for (int i = 0; i < views.length; i++) {
            if (views[i] != null) {
                views[i].setAlpha(0f);
                views[i].setTranslationY(60f);
                views[i].animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(700)
                        .setStartDelay(100 + (i * 100))
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        }
    }

    private void startAmbientFloating() {
        View ambientOrb = findViewById(R.id.ambientGlowOrb);
        if (ambientOrb == null) return;
        ambientOrb.animate()
                .translationX(100f).translationY(-100f)
                .setDuration(15000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (ambientOrb != null) {
                        ambientOrb.animate()
                                .translationX(-100f).translationY(100f)
                                .setDuration(15000)
                                .withEndAction(this::startAmbientFloating)
                                .start();
                    }
                }).start();
    }
}