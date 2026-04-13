package com.example.register;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {

    private EditText etName, etPhone;
    private AppCompatButton btnLogin;
    private View tvSignUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. Initialize Views - UNTOUCHED
        etName = findViewById(R.id.etLoginName);
        etPhone = findViewById(R.id.etLoginPhone);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.tvSignUp);

        startAmbientFloating();

        // 3. Setup Logic - UNTOUCHED
        setupInputLogic();

        // 4. Entrance Animations - UNTOUCHED
        slideUp(findViewById(R.id.headerText), 200);
        slideUp(etName, 400);
        slideUp(etPhone, 600);
        slideUp(btnLogin, 800);
        slideUp(tvSignUp, 1000);

        // 5. Login Logic
        btnLogin.setOnClickListener(v -> {
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
                                    .setDuration(100)
                                    .withEndAction(() -> performFirebaseLogin())
                                    .start();
                        }).start();
            }
        });

        // 6. Back to Register
        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegistrationActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void performFirebaseLogin() {
        String phoneInput = etPhone.getText().toString().trim();
        String nameInput = etName.getText().toString().trim();

        btnLogin.setText("AUTHORIZING...");
        btnLogin.setAlpha(0.7f);

        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference("users");
        mDatabase.child(phoneInput).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot snapshot = task.getResult();

                // Get values from Firebase
                String dbName = snapshot.child("name").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                String phone = snapshot.child("phone").getValue(String.class);

                // Check if name matches (Optional, but good for security)
                if (dbName != null && dbName.equalsIgnoreCase(nameInput)) {
                    // --- IDENTITY SYNC ---
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("user_name", dbName);
                    editor.putString("user_email", email);
                    editor.putString("user_phone", phone);
                    editor.putBoolean("isLoggedIn", true);
                    editor.apply();

                    Intent intent = new Intent(LoginActivity.this, SecurityPrivacyActivity.class);
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                } else {
                    // Name doesn't match the phone number in DB
                    btnLogin.setText("INITIALIZE PROTECTION");
                    btnLogin.setAlpha(1.0f);
                    showSimpleError(etName);
                    Toast.makeText(this, "Name does not match registered identity", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Phone number not found in Firebase
                btnLogin.setText("INITIALIZE PROTECTION");
                btnLogin.setAlpha(1.0f);
                showSimpleError(etPhone);
                Toast.makeText(this, "Phone number not found. Please register first.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupInputLogic() {
        setupFieldEffects(etName);
        setupFieldEffects(etPhone);

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

    // UPDATED VALIDATION WITH TOASTS
    private boolean validateAll() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (name.isEmpty()) {
            showSimpleError(etName);
            Toast.makeText(this, "Please enter your registered name", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (phone.length() != 10) {
            showSimpleError(etPhone);
            Toast.makeText(this, "Mobile number must be exactly 10 digits", Toast.LENGTH_SHORT).show();
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
}