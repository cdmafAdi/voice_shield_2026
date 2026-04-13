package com.example.register;

import java.io.Serializable;

public class CallLogModel implements Serializable {
    public String number;
    public int riskScore;
    public String label;
    public String summary;
    public String timestamp;
    public String callType;  // Added
    public String duration;  // Added

    public CallLogModel(String number, int riskScore, String label, String summary, String timestamp, String callType, String duration) {
        this.number = number;
        this.riskScore = riskScore;
        this.label = label;
        this.summary = summary;
        this.timestamp = timestamp;
        this.callType = callType;
        this.duration = duration;
    }

    public String getColorHex() {
        if (riskScore >= 75) return "#FF4B4B";
        if (riskScore >= 40) return "#FFD600";
        return "#00FFC5";
    }
}
