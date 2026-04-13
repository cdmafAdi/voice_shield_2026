package com.example.register;

import android.content.Intent;
import android.content.SharedPreferences; // Added
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

public class HomeMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- ADDED SESSION CHECK ---
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("isSetupDone", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        // ---------------------------

        setContentView(R.layout.activity_home_main);

        // 1. Initialize Visual Components
        View header = findViewById(R.id.homeHeader);
        View wave = findViewById(R.id.waveContainerFull);
        View footer = findViewById(R.id.homeFooter);
        View buttonGlow = findViewById(R.id.buttonGlow);
        View btnStart = findViewById(R.id.btnStartRound);

        // 2. Start Background Ambient Logic
        startAmbientFloating();

        // 3. Entrance Animations
        if (header != null) {
            header.setAlpha(0f);
            header.setTranslationY(-50f);
            header.animate().alpha(1f).translationY(0f).setDuration(1000).setInterpolator(new DecelerateInterpolator()).start();
        }

        if (wave != null) {
            wave.setAlpha(0f);
            wave.animate().alpha(1f).setDuration(1500).setStartDelay(300).start();
        }

        if (footer != null) {
            footer.setAlpha(0f);
            footer.setTranslationY(100f);
            footer.animate().alpha(1f).translationY(0f).setDuration(1000).setStartDelay(500).start();
        }

        // 4. Initialize Core Visuals
        createFullScreenWave();
        if (buttonGlow != null) {
            startHighIntensityGlow(buttonGlow);
        }

        // 5. Action Listener
        if (btnStart != null) {
            ScaleAnimation btnPulse = new ScaleAnimation(1.0f, 1.08f, 1.0f, 1.08f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            btnPulse.setDuration(1500);
            btnPulse.setRepeatMode(Animation.REVERSE);
            btnPulse.setRepeatCount(Animation.INFINITE);
            btnPulse.setInterpolator(new AccelerateDecelerateInterpolator());
            btnStart.startAnimation(btnPulse);

            btnStart.setOnClickListener(v -> {
                v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(100).withEndAction(() -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    Intent intent = new Intent(HomeMainActivity.this, RegistrationActivity.class);
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }).start();
            });
        }
    }

    private void createFullScreenWave() {
        LinearLayout container = findViewById(R.id.waveContainerFull);
        if (container == null) return;
        container.removeAllViews();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int barWidth = 8;
        int barSpacing = 4;
        int barCount = metrics.widthPixels / (barWidth + barSpacing);
        int centerIndex = barCount / 2;
        Random random = new Random();
        for (int i = 0; i < barCount; i++) {
            View bar = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(barWidth, 20);
            lp.setMargins(2, 0, 2, 0);
            bar.setLayoutParams(lp);
            bar.setBackgroundColor(0xFFD8B4FE);
            bar.setAlpha(0.6f);
            container.addView(bar);
            float distanceToCenter = Math.abs(i - centerIndex) / (float) centerIndex;
            float intensity = (float) Math.pow(1.0f - distanceToCenter, 1.8f);
            float baseScale = 2.0f + (intensity * 12.0f);
            float randomMaxScale = baseScale + (random.nextFloat() * (intensity * 5.0f));
            int randomDuration = 600 + random.nextInt(800);
            ScaleAnimation sa = new ScaleAnimation(1f, 1f, 1f, randomMaxScale, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            sa.setDuration(randomDuration);
            sa.setStartOffset(i * 15L);
            sa.setRepeatCount(Animation.INFINITE);
            sa.setRepeatMode(Animation.REVERSE);
            sa.setInterpolator(new AccelerateDecelerateInterpolator());
            bar.startAnimation(sa);
        }
    }

    private void startAmbientFloating() {
        View ambientOrb = findViewById(R.id.ambientGlowOrb);
        if (ambientOrb == null) return;
        ambientOrb.animate().translationX(100f).translationY(-100f).setDuration(15000).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(() -> {
            ambientOrb.animate().translationX(-100f).translationY(100f).setDuration(15000).withEndAction(this::startAmbientFloating).start();
        }).start();
        ObjectAnimator pulse = ObjectAnimator.ofFloat(ambientOrb, "alpha", 0.3f, 0.7f);
        pulse.setDuration(8000);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
    }

    private void startHighIntensityGlow(View glowView) {
        AnimationSet glowSet = new AnimationSet(true);
        ScaleAnimation scale = new ScaleAnimation(0.92f, 1.15f, 0.92f, 1.15f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        AlphaAnimation alpha = new AlphaAnimation(0.3f, 0.7f);
        glowSet.addAnimation(scale);
        glowSet.addAnimation(alpha);
        glowSet.setDuration(2500);
        glowSet.setRepeatCount(Animation.INFINITE);
        glowSet.setRepeatMode(Animation.REVERSE);
        glowSet.setInterpolator(new AccelerateDecelerateInterpolator());
        glowView.startAnimation(glowSet);
    }
}