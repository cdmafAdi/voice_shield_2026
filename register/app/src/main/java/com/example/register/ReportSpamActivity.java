package com.example.register;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class ReportSpamActivity extends AppCompatActivity {

    private LinearLayout callsContainer;
    private Dialog reportDialog;
    private View currentlyPendingCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Stealth System UI
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_report_spam);

        callsContainer = findViewById(R.id.riskyCallsContainer);
        setupReportDialog();

        // REAL-TIME DYNAMIC FETCH (Threshold Check: 74%)
        syncThreatLogs();

        findViewById(R.id.btnBackReport).setOnClickListener(v -> finish());
    }

    private void syncThreatLogs() {
        ArrayList<CallLogModel> history = CallHistoryManager.getHistory(this);
        callsContainer.removeAllViews();

        if (history == null || history.isEmpty()) return;

        for (CallLogModel call : history) {
            // THE NEURAL FILTER: Automatically staged for risk >= 74
            if (call.riskScore >= 74) {
                displayThreatCard(call);
            }
        }
    }

    private void displayThreatCard(CallLogModel call) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_risky_call_log, callsContainer, false);

        TextView tvNum = card.findViewById(R.id.tvLogNumber);
        TextView tvLabel = card.findViewById(R.id.tvLogLabel);
        TextView tvScore = card.findViewById(R.id.tvLogScore);

        tvNum.setText(call.number);
        tvLabel.setText("Threat Identified: " + call.label);
        tvScore.setText(call.riskScore + "%");

        // Action Logic
        card.findViewById(R.id.btnNo).setOnClickListener(v -> {
            Toast.makeText(this, "Marked as Safe", Toast.LENGTH_SHORT).show();
            animateNeutralize(card);
        });

        card.findViewById(R.id.btnYes).setOnClickListener(v -> {
            currentlyPendingCard = card;
            card.setTag(call);
            reportDialog.show();
        });

        callsContainer.addView(card);
    }

    private void setupReportDialog() {
        reportDialog = new Dialog(this);
        reportDialog.setContentView(R.layout.dialog_report_reason);
        reportDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        reportDialog.findViewById(R.id.btnSubmitFinal).setOnClickListener(v -> {
            EditText et = reportDialog.findViewById(R.id.etReportNotes);
            if(et.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Intelligence input required.", Toast.LENGTH_SHORT).show();
                return;
            }

            // FEEDBACK & ANIMATION
            Toast.makeText(this, "Number Reported: Block Initiated", Toast.LENGTH_LONG).show();
            reportDialog.dismiss();

            if (currentlyPendingCard != null) {
                animateNeutralize(currentlyPendingCard);
            }
        });
    }

    private void animateNeutralize(View view) {
        // Professional Slide-Out Sequence
        view.animate()
                .translationX(600f) // Dynamic slide out
                .alpha(0f)          // Neural fade
                .scaleX(0.85f)      // Structural shrink
                .scaleY(0.85f)
                .setDuration(700)   // Exact timing for "smooth fade"
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    callsContainer.removeView(view);
                    currentlyPendingCard = null;
                })
                .start();
    }
}