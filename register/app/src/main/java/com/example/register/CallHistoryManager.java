package com.example.register;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

public class CallHistoryManager {
    private static final String PREF_NAME = "VoiceShieldPrefs";
    private static final String KEY_LOGS = "call_logs";

    public static void saveCall(Context context, CallLogModel call) {
        if (call.number == null || call.number.equalsIgnoreCase("Unknown Number")) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_LOGS, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("number", call.number);
            obj.put("score", call.riskScore);
            obj.put("label", call.label);
            obj.put("summary", call.summary);
            obj.put("time", call.timestamp);
            obj.put("type", call.callType);     // New
            obj.put("duration", call.duration); // New
            array.put(obj);
            prefs.edit().putString(KEY_LOGS, array.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static ArrayList<CallLogModel> getHistory(Context context) {
        ArrayList<CallLogModel> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_LOGS, "[]"));
            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject o = array.getJSONObject(i);
                list.add(new CallLogModel(
                        o.getString("number"),
                        o.getInt("score"),
                        o.getString("label"),
                        o.getString("summary"),
                        o.getString("time"),
                        o.optString("type", "Incoming"),
                        o.optString("duration", "00:00")
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }
}