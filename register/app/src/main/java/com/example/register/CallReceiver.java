package com.example.register;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.os.Build;

public class CallReceiver extends BroadcastReceiver {
    private static String lastNumber = "Unknown Number";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        // 1. CAPTURE ACTUAL DIGITS FOR INCOMING
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            String incoming = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (incoming != null) lastNumber = incoming;
        }

        // 2. CAPTURE ACTUAL DIGITS FOR OUTGOING (This stops the "Outgoing Call" text)
        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            String outgoing = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if (outgoing != null) lastNumber = outgoing;
        }

        if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            boolean shieldSavedContacts = prefs.getBoolean("contact_shield", false);

            // --- THE FIX ---
            // Instead of hardcoding the STRING "Outgoing Call", we use the digits we captured.
            String phoneNumber = lastNumber;

            // Safety check: if it's still unknown, try one last time to get digits from the intent
            if (phoneNumber.equals("Unknown Number")) {
                String extraNum = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                if (extraNum != null) phoneNumber = extraNum;
            }

            boolean isSaved = ContactUtils.isNumberInContacts(context, phoneNumber);

            // Keep your existing Toggle Logic
            if (!shieldSavedContacts && isSaved) {
                Log.d("VoiceShield", "Saved contact & Toggle OFF. Aborting.");
                return;
            }

            Intent i = new Intent(context, VoiceShieldService.class);
            // NOW sending the REAL digits (+91...) to the service
            i.putExtra("contact_number", phoneNumber);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        }
        else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            Log.d("VoiceShield", "🛑 Call ended. Signaling Graceful Shutdown...");
            lastNumber = "Unknown Number"; // Reset for next call

            Intent graceIntent = new Intent(context, VoiceShieldService.class);
            graceIntent.setAction("STOP_RECORDING_GRACEFULLY");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(graceIntent);
            } else {
                context.startService(graceIntent);
            }
        }
    }
}