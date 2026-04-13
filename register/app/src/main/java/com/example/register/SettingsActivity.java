package com.example.register;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// FIREBASE IMPORTS
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class SettingsActivity extends AppCompatActivity {

    private EditText etName, etEmail;
    private TextView tvPhone;
    private SwitchCompat switchContactShield;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive UI setup
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        // Link Views
        etName = findViewById(R.id.etSettingsName);
        etEmail = findViewById(R.id.etSettingsEmail);
        tvPhone = findViewById(R.id.tvSettingsPhone);
        switchContactShield = findViewById(R.id.switchContactShield);

        // Load Identity
        etName.setText(prefs.getString("user_name", "Unknown Agent"));
        etEmail.setText(prefs.getString("user_email", "not_set@vault.com"));
        tvPhone.setText(prefs.getString("user_phone", "+91 00000 00000"));
        switchContactShield.setChecked(prefs.getBoolean("contact_shield", false));

        // Permission Components
        AppCompatButton btnNeural = findViewById(R.id.btnGrantNeural);
        View statusNeural = findViewById(R.id.statusNeural);
        AppCompatButton btnCall = findViewById(R.id.btnGrantCall);
        View statusCall = findViewById(R.id.statusCall);

        refreshAllUI(btnNeural, statusNeural, btnCall, statusCall);

        // Permission Handlers
        btnNeural.setOnClickListener(v -> handleToggle("perm_neural", Manifest.permission.RECORD_AUDIO, 101, btnNeural, statusNeural));
        statusNeural.setOnClickListener(v -> handleToggle("perm_neural", Manifest.permission.RECORD_AUDIO, 101, btnNeural, statusNeural));

        btnCall.setOnClickListener(v -> handleToggle("perm_call", Manifest.permission.READ_PHONE_STATE, 102, btnCall, statusCall));
        statusCall.setOnClickListener(v -> handleToggle("perm_call", Manifest.permission.READ_PHONE_STATE, 102, btnCall, statusCall));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // --- UPDATED SAVE PROFILE WITH PHONE-BASED FIREBASE SYNC & VALIDATION ---
        // --- UPDATED SAVE PROFILE WITH SPECIFIC TOGGLE MESSAGES ---
        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> {
            // 1. Get current (old) values
            String oldName = prefs.getString("user_name", "");
            String oldEmail = prefs.getString("user_email", "");
            boolean oldShield = prefs.getBoolean("contact_shield", false);

            // 2. Get the new values
            String newName = etName.getText().toString().trim();
            String newEmail = etEmail.getText().toString().trim();
            boolean newShield = switchContactShield.isChecked();
            String currentPhone = tvPhone.getText().toString().trim();

            if (newName.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "Fields cannot be blank!", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users").child(currentPhone);
            HashMap<String, Object> updates = new HashMap<>();
            updates.put("name", newName);
            updates.put("email", newEmail);
            updates.put("contact_shield", newShield);

            dbRef.updateChildren(updates).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {

                    // --- IMPROVED DYNAMIC TOAST LOGIC ---
                    StringBuilder message = new StringBuilder("Updated: ");
                    boolean changed = false;

                    if (!newName.equals(oldName)) {
                        message.append("Name");
                        changed = true;
                    }
                    if (!newEmail.equals(oldEmail)) {
                        if (changed) message.append(", ");
                        message.append("Email");
                        changed = true;
                    }

                    // Check if shield status actually changed
                    if (newShield != oldShield) {
                        if (changed) message.append("\n"); // Move to new line if name/email also changed

                        if (newShield) {
                            message.append("🛡️ Shield ENABLED for saved contacts");
                        } else {
                            message.append("⚠️ Shield DISABLED for saved contacts");
                        }
                        changed = true;
                    }

                    if (!changed) {
                        message = new StringBuilder("No changes detected.");
                    } else {
                        message.append(" successfully!");
                    }
                    // ---------------------------

                    prefs.edit()
                            .putString("user_name", newName)
                            .putString("user_email", newEmail)
                            .putBoolean("contact_shield", newShield)
                            .apply();

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null && !newName.equals(oldName)) {
                        user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(newName).build());
                    }

                    Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
                    finish();

                } else {
                    Toast.makeText(this, "Database Sync Failed!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Logout
        findViewById(R.id.btnLogoutSettings).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut(); // Real Firebase Sign Out
            prefs.edit().putBoolean("isLoggedIn", false).apply();
            finish();
        });
    }

    // --- PERMISSION LOGIC (UNCHANGED) ---
    private void handleToggle(String prefKey, String perm, int code, AppCompatButton btn, View status) {
        if (!prefs.getBoolean(prefKey, false)) {
            ActivityCompat.requestPermissions(this, new String[]{perm}, code);
        } else {
            Toast.makeText(this, "Redirecting to System to revoke access...", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
            updateState(prefKey, false, btn, status);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            if (code == 101) updateState("perm_neural", true, findViewById(R.id.btnGrantNeural), findViewById(R.id.statusNeural));
            else if (code == 102) updateState("perm_call", true, findViewById(R.id.btnGrantCall), findViewById(R.id.statusCall));
        }
    }

    private void updateState(String key, boolean state, AppCompatButton btn, View status) {
        prefs.edit().putBoolean(key, state).apply();
        updatePermissionUI(state, btn, status);
        if (!state) Toast.makeText(this, "Security Access Revoked", Toast.LENGTH_SHORT).show();
    }

    private void updatePermissionUI(boolean isGranted, AppCompatButton btn, View status) {
        btn.setVisibility(isGranted ? View.GONE : View.VISIBLE);
        if (status != null) status.setVisibility(isGranted ? View.VISIBLE : View.GONE);
    }

    private void refreshAllUI(AppCompatButton b1, View s1, AppCompatButton b2, View s2) {
        checkAndSync("perm_neural", Manifest.permission.RECORD_AUDIO, b1, s1);
        checkAndSync("perm_call", Manifest.permission.READ_PHONE_STATE, b2, s2);
    }

    private void checkAndSync(String key, String perm, AppCompatButton btn, View status) {
        boolean hasPerm = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
        boolean localStatus = prefs.getBoolean(key, false);
        if (!hasPerm && localStatus) {
            prefs.edit().putBoolean(key, false).apply();
            updatePermissionUI(false, btn, status);
        } else {
            updatePermissionUI(localStatus, btn, status);
        }
    }
}