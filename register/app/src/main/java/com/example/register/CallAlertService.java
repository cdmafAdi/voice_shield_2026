package com.example.register;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

public class CallAlertService extends Service {
    private WindowManager windowManager;
    private View alertView;
    private WindowManager.LayoutParams params;
    private boolean isMinimized = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Create Notification Channel IMMEDIATELY
        String CHANNEL_ID = "voice_shield_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Shield Active", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        // 2. Build the Notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("VoiceShield Protection Active")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        // 3. Start Foreground (Android 14 requires the type flag here too)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(2001, notification);
        }

        // 4. Initialize Overlay
        initOverlay();
    }

    private void initOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        alertView = LayoutInflater.from(this).inflate(R.layout.layout_call_alert_banner, null);

        int heightPx = (int) (80 * getResources().getDisplayMetrics().density);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                heightPx,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP;
        windowManager.addView(alertView, params);

        // Slide Down Animation
        alertView.setTranslationY(-heightPx);
        alertView.animate().translationY(0).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();

        alertView.findViewById(R.id.btnMinimize).setOnClickListener(v -> toggleMinimize());
    }

    private void toggleMinimize() {
        if (!isMinimized) {
            alertView.animate().scaleY(0.25f).alpha(0.8f).setDuration(300).start();
            isMinimized = true;
        } else {
            alertView.animate().scaleY(1.0f).alpha(1.0f).setDuration(300).start();
            isMinimized = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (alertView != null) windowManager.removeView(alertView);
    }
}