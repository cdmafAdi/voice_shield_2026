package com.example.register;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class RegistrationActivity extends AppCompatActivity {

    private EditText etName, etPhone, etEmail;
    private AppCompatButton btnContinue;
    private View tvAlreadyAccount;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // 1. Initialize Views - UNTOUCHED
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        btnContinue = findViewById(R.id.btnContinue);
        tvAlreadyAccount = findViewById(R.id.tvAlreadyAccount);
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // 2. Start Animations - UNTOUCHED
        startAmbientFloating();
        setupInputLogic();

        // 3. Entrance Animations - UNTOUCHED
        slideUp(findViewById(R.id.headerText), 200);
        slideUp(etName, 400);
        slideUp(etPhone, 600);
        slideUp(etEmail, 800);
        slideUp(btnContinue, 1000);
        slideUp(tvAlreadyAccount, 1200);

        // 4. Logic - UNTOUCHED
        btnContinue.setOnClickListener(v -> {
            if (validateAll()) {
                v.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(80)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            v.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(120)
                                    .withEndAction(() -> saveAndProceed())
                                    .start();
                        }).start();
            }
        });

        tvAlreadyAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void slideUp(View view, int delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(100f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void setupInputLogic() {
        setupFieldEffects(etName);
        setupFieldEffects(etPhone);
        setupFieldEffects(etEmail);

        TextWatcher cleaner = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                View current = getCurrentFocus();
                if (current instanceof EditText) {
                    current.setBackgroundResource(R.drawable.bg_input_purple_glow);
                }
            }
        };
        etName.addTextChangedListener(cleaner);
        etPhone.addTextChangedListener(cleaner);
        etEmail.addTextChangedListener(cleaner);
    }

    private void setupFieldEffects(EditText editText) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setBackgroundResource(R.drawable.bg_input_purple_glow);
            } else {
                v.setBackgroundResource(R.drawable.glass_card_stroke);
            }
        });
    }

    private void showSimpleError(EditText editText) {
        editText.setBackgroundResource(R.drawable.bg_input_error_red);
    }

    // UPDATED VALIDATION LOGIC WITH TOASTS
    private boolean validateAll() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        // Check Name
        if (name.isEmpty()) {
            showSimpleError(etName);
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Check Phone
        if (phone.length() != 10) {
            showSimpleError(etPhone);
            Toast.makeText(this, "Mobile number must be exactly 10 digits", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Check Email
        if (email.isEmpty()) {
            showSimpleError(etEmail);
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showSimpleError(etEmail);
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void startAmbientFloating() {
        View ambientOrb = findViewById(R.id.ambientGlowOrb);
        if (ambientOrb == null) return;

        ambientOrb.animate()
                .translationX(100f).translationY(-100f)
                .setDuration(15000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    ambientOrb.animate()
                            .translationX(-100f).translationY(100f)
                            .setDuration(15000)
                            .withEndAction(this::startAmbientFloating)
                            .start();
                }).start();

        ObjectAnimator pulse = ObjectAnimator.ofFloat(ambientOrb, "alpha", 0.3f, 0.7f);
        pulse.setDuration(8000);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
    }

    private void saveAndProceed() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        btnContinue.setText("ENROLLING...");
        btnContinue.setAlpha(0.7f);

        // --- IDENTITY SYNC: Saves all data locally ---
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("user_name", name);
        editor.putString("user_phone", phone);
        editor.putString("user_email", email);
        editor.putBoolean("isLoggedIn", true);
        editor.apply();

        // Firebase logic - Optimized to save profile in one go
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("phone", phone);

        mDatabase.child("users").child(phone).updateChildren(userData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Intent intent = new Intent(RegistrationActivity.this, SecurityPrivacyActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            } else {
                btnContinue.setText("ACTIVATE SHIELD");
                btnContinue.setAlpha(1.0f);
                Toast.makeText(this, "Enrollment Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }
}