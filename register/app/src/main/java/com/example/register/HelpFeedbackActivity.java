package com.example.register;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;

public class HelpFeedbackActivity extends AppCompatActivity {

    private EditText etMessage;
    private DatabaseReference mDatabase;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_feedback);

        // Firebase reference
        mDatabase = FirebaseDatabase.getInstance().getReference("SupportMessages");
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        etMessage = findViewById(R.id.etFeedbackMessage);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnSubmitFeedback).setOnClickListener(v -> {
            String message = etMessage.getText().toString().trim();

            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
                return;
            }

            sendFeedbackToFirebase(message);
        });
    }

    private void sendFeedbackToFirebase(String message) {
        // Get user details to know who sent it
        String name = prefs.getString("user_name", "Anonymous");
        String phone = prefs.getString("user_phone", "No Phone");
        long timestamp = System.currentTimeMillis();

        HashMap<String, Object> feedbackMap = new HashMap<>();
        feedbackMap.put("userName", name);
        feedbackMap.put("userPhone", phone);
        feedbackMap.put("message", message);
        feedbackMap.put("timestamp", timestamp);

        // Push to Firebase with a unique ID
        mDatabase.push().setValue(feedbackMap)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Feedback sent. Thank you!", Toast.LENGTH_LONG).show();
                    etMessage.setText(""); // Clear input
                    finish(); // Optional: Close screen after sending
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}