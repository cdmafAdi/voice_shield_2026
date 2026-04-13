package com.example.register;

import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ProgressBar statusGlow;
    private FrameLayout shieldIcon;
    private boolean isActivityActive = false; // Flag to stop animations when activity is destroyed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isActivityActive = true;
        requestBatteryExemption();
        checkOverlayPermissionOnly();
        requestScreeningRole();

        // UI transparency and edge-to-edge layout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setContentView(R.layout.activity_main);

        // 1. Initialize Views
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        statusGlow = findViewById(R.id.statusGlow);
        shieldIcon = findViewById(R.id.shieldStatusIcon);

        View btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction(() -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    if (drawerLayout != null) {
                        drawerLayout.openDrawer(GravityCompat.START);
                        updateHeaderData();
                    }
                }).start();
            });
        }
        requestScreeningRole();


        // 3. Navigation Drawer Logic
        setupNavigation();

        // 4. Start Aesthetic Animations
        startBreathingAnimation();
        startFloatingAnimation();
    }
    private void checkOverlayPermissionOnly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 123);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                Toast.makeText(this, "Shield Activated! App will work when closed.", Toast.LENGTH_SHORT).show();
                // Optional: Start a dummy service just to "warm up" the process
            } else {
                Toast.makeText(this, "Role Denied. Please enable it for background protection.", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void requestScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                startActivityForResult(intent, 123);
            }
        }
    }

    private void setupNavigation() {
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                Intent intent = null;

                // CHANGED: nav_report_spam opens ReportSpamActivity
                if (id == R.id.nav_report_spam) {
                    intent = new Intent(this, ReportSpamActivity.class);
                } else if (id == R.id.nav_premium) {
                    intent = new Intent(this, SettingsActivity.class);
                } else if (id == R.id.nav_about) {
                    intent = new Intent(this, AboutUsActivity.class);
                } else if (id == R.id.nav_help) {
                    intent = new Intent(this, HelpFeedbackActivity.class);
                } else if (id == R.id.nav_logout) {
                    performLogout();
                    return true;
                }

                if (intent != null) {
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }

                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }
    }

    private void performLogout() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        Intent intent = new Intent(this, RegistrationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeaderData();
        displayDynamicLogs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityActive = false; // Prevents animation recursion after activity dies
    }

    private void updateHeaderData() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String userName = prefs.getString("user_name", "AGENT");
        String userPhone = prefs.getString("user_phone", "0000000000");

        if (userPhone != null && !userPhone.startsWith("+91")) {
            userPhone = "+91 " + userPhone;
        }

        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            View headerView = navigationView.getHeaderView(0);
            TextView tvName = headerView.findViewById(R.id.tvHeaderName);
            TextView tvPhone = headerView.findViewById(R.id.tvHeaderPhone);

            if (tvName != null) tvName.setText("WELCOME, " + userName.toUpperCase());
            if (tvPhone != null) tvPhone.setText(userPhone);
        }
    }

    private void displayDynamicLogs() {
        LinearLayout container = findViewById(R.id.callLogContainer);
        if (container == null) return;
        container.removeAllViews();

        ArrayList<CallLogModel> history = CallHistoryManager.getHistory(this);
        if (history.isEmpty()) {
            addEmptyState(container);
            return;
        }

        for (CallLogModel call : history) {
            // Inflate the row
            View card = getLayoutInflater().inflate(R.layout.item_call_log_row, container, false);

            // Find all 4 required UI components
            TextView tvNumber = card.findViewById(R.id.tvLogNumber);
            TextView tvType = card.findViewById(R.id.tvLogType);    // NEW COMPONENT
            TextView tvLabel = card.findViewById(R.id.tvLogLabel);  // SECURITY STATUS
            TextView tvScore = card.findViewById(R.id.tvLogScore);
            View stripe = card.findViewById(R.id.riskStripe);

            int color = android.graphics.Color.parseColor(call.getColorHex());

            // 1. SET THE PHONE NUMBER (Fixes the "Outgoing Call" text issue)
            if (tvNumber != null) {
                tvNumber.setText(call.number);
            }

            // 2. SET THE CALL TYPE (Incoming/Outgoing)
            if (tvType != null) {
                tvType.setText(call.callType);
            }

            // 3. SET THE SECURITY LABEL (Safe/Suspicious/Scam)
            if (tvLabel != null) {
                tvLabel.setText(call.label);
                // Apply color to label for high-risk calls
                if (call.riskScore >= 40) tvLabel.setTextColor(color);
            }

            // 4. SET THE RISK SCORE
            if (tvScore != null) {
                tvScore.setText(call.riskScore + "%");
                tvScore.setTextColor(color);
            }

            if (stripe != null) stripe.setBackgroundColor(color);

            // Click Logic
            card.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, CallAnalysisActivity.class);
                intent.putExtra("EXTRA_CALL_DATA", call);
                startActivity(intent);
            });

            container.addView(card);
        }
    }

    private void addEmptyState(LinearLayout container) {
        TextView tvEmpty = new TextView(this);
        tvEmpty.setText("NO RECENT ACTIVITY\nShield is active and monitoring.");
        tvEmpty.setTextColor(Color.parseColor("#66FFFFFF"));
        tvEmpty.setTextSize(14);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, 100, 0, 100);
        tvEmpty.setLineSpacing(0, 1.3f);
        container.addView(tvEmpty);
    }

    private void showCallDetails(CallLogModel call) {
        new AlertDialog.Builder(this)
                .setTitle(call.label)
                .setMessage("Risk: " + call.riskScore + "%\nTime: " + call.timestamp +
                        "\n\nAnalysis:\n" + call.summary)
                .setPositiveButton("OK", null)
                .show();
    }

    private void startBreathingAnimation() {
        if (statusGlow == null || !isActivityActive) return;
        statusGlow.animate()
                .alpha(0.35f).scaleX(1.1f).scaleY(1.1f)
                .setDuration(3000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (isActivityActive) {
                        statusGlow.animate()
                                .alpha(0.1f).scaleX(0.95f).scaleY(0.95f)
                                .setDuration(3000)
                                .withEndAction(this::startBreathingAnimation)
                                .start();
                    }
                }).start();
    }

    private void startFloatingAnimation() {
        if (shieldIcon == null || !isActivityActive) return;
        shieldIcon.animate()
                .translationY(-15f)
                .setDuration(2200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (isActivityActive) {
                        shieldIcon.animate()
                                .translationY(15f)
                                .setDuration(2200)
                                .withEndAction(this::startFloatingAnimation)
                                .start();
                    }
                }).start();
    }

    private void checkPermissionAndStart(int riskLevel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 123);
        } else {
            startOverlayService(riskLevel);
        }
    }

    private void startOverlayService(int riskLevel) {
        Intent intent = new Intent(this, VoiceShieldService.class);
        intent.putExtra("risk_val", riskLevel);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}