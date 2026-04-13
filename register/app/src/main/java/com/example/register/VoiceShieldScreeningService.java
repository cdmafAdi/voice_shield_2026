package com.example.register;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

public class VoiceShieldScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details callDetails) {
        // We no longer start the service here.
        // CallReceiver.java handles starting the service for both Incoming and Outgoing.

        Log.d("VoiceShield", "ScreeningService: Handing over control to CallReceiver.");

        // Just tell the system to allow the call to proceed normally
        sendCallResponse(callDetails);
    }

    private void sendCallResponse(Call.Details callDetails) {
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
        respondToCall(callDetails, response);
    }
}