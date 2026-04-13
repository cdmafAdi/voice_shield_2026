package com.example.register;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import androidx.appcompat.widget.AppCompatButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SecurityPrivacyActivity extends AppCompatActivity {

    private AppCompatButton btnAgree;
    private LinearLayout checkContainer;
    private View statusIndicator;
    private boolean isAccepted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_privacy);

        // 1. Initialize views
        btnAgree = findViewById(R.id.btnAgree);
        checkContainer = findViewById(R.id.checkContainer);
        statusIndicator = findViewById(R.id.statusIndicator);

        View bottomArea = findViewById(R.id.bottomActionArea);
        if (bottomArea != null) {
            bottomArea.bringToFront();
            bottomArea.setElevation(20f);
        }

        // 2. Start Ambient Background Logic
        startAmbientFloating();

        // 3. Set features
        setupFeature(findViewById(R.id.item1), "No Audio Stored", "Voice data is never stored on our servers.", android.R.drawable.ic_lock_idle_lock);
        setupFeature(findViewById(R.id.item2), "Calls Stay on Device", "Calls are processed locally and stay secure.", android.R.drawable.ic_menu_call);
        setupFeature(findViewById(R.id.item3), "Anonymous Analysis", "Protection without tracking your identity.", android.R.drawable.ic_menu_view);
        setupFeature(findViewById(R.id.item4), "Transparent Permissions", "You always know what data we access.", android.R.drawable.ic_dialog_info);

        // 4. Custom Checkbox Logic
        if (checkContainer != null) {
            checkContainer.setOnClickListener(v -> {
                isAccepted = !isAccepted;
                if (isAccepted) {
                    statusIndicator.setBackgroundResource(R.drawable.status_dot_active);
                    statusIndicator.setBackgroundTintList(null);
                    btnAgree.setEnabled(true);
                    btnAgree.setAlpha(1.0f);
                } else {
                    statusIndicator.setBackgroundResource(R.drawable.premium_round_outline);
                    btnAgree.setEnabled(false);
                    btnAgree.setAlpha(0.3f);
                }
            });
        }

        // 5. Entrance Animations
        animateEntrance();

        // 6. Back Navigation - UPDATED TO GO TO REGISTRATION
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                Intent intent = new Intent(SecurityPrivacyActivity.this, RegistrationActivity.class);
                // Brings existing RegistrationActivity to front or starts new one if needed
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            });
        }

        // 7. FINAL EXIT - GO TO PERMISSIONS (UPDATED DESTINATION)
        if (btnAgree != null) {
            btnAgree.setOnClickListener(v -> {
                // This is the bridge to the Permission page
                Intent intent = new Intent(SecurityPrivacyActivity.this, EnableProtectionActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish(); // Destroys Privacy screen so they can't go back to it
            });
        }
    }

    private void animateEntrance() {
        View title = findViewById(R.id.titleText);
        View subTitle = findViewById(R.id.subTitleText);
        View it1 = findViewById(R.id.item1);
        View it2 = findViewById(R.id.item2);
        View it3 = findViewById(R.id.item3);
        View it4 = findViewById(R.id.item4);
        View[] views = { title, subTitle, it1, it2, it3, it4, checkContainer, btnAgree };
        for (int i = 0; i < views.length; i++) {
            if (views[i] != null) {
                views[i].setAlpha(0f);
                views[i].setTranslationY(80f);
                views[i].animate().alpha(1f).translationY(0f).setDuration(700)
                        .setStartDelay(200 + (i * 120))
                        .setInterpolator(new AccelerateDecelerateInterpolator()).start();
            }
        }
    }

    private void setupFeature(View container, String title, String desc, int iconRes) {
        if (container != null) {
            TextView titleView = container.findViewById(R.id.featureTitle);
            TextView descView = container.findViewById(R.id.featureDesc);
            ImageView iconView = container.findViewById(R.id.featureIcon);
            if (titleView != null) titleView.setText(title);
            if (descView != null) descView.setText(desc);
            if (iconView != null) {
                iconView.setImageResource(iconRes);
                iconView.setScaleX(0f);
                iconView.setScaleY(0f);
                iconView.animate().scaleX(1f).scaleY(1f).setStartDelay(800).setDuration(600)
                        .setInterpolator(new OvershootInterpolator()).start();
                startSmallPulse(iconView);
            }
        }
    }

    private void startAmbientFloating() {
        View ambientOrb = findViewById(R.id.ambientGlowOrb);
        if (ambientOrb == null) return;
        ambientOrb.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D8B4FE")));
        ambientOrb.animate().translationX(100f).translationY(-100f).setDuration(15000)
                .setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(() -> {
                    if (ambientOrb != null) {
                        ambientOrb.animate().translationX(-100f).translationY(100f).setDuration(15000)
                                .withEndAction(this::startAmbientFloating).start();
                    }
                }).start();
        ObjectAnimator pulse = ObjectAnimator.ofFloat(ambientOrb, "alpha", 0.3f, 0.6f);
        pulse.setDuration(8000);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
    }

    private void startSmallPulse(View view) {
        ScaleAnimation pulse = new ScaleAnimation(1.0f, 1.05f, 1.0f, 1.05f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(2500 + (int)(Math.random() * 1000));
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        view.startAnimation(pulse);
    }
}