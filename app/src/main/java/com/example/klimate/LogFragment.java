package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LogFragment extends Fragment {

    private LinearLayout selectedCard = null;
    private String selectedActivityName = null;
    private boolean isSaving = false;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private final int[] cardIds = {
            R.id.card_cycling, R.id.card_transit, R.id.card_recycling,
            R.id.card_plantbased, R.id.card_reusable, R.id.card_composting,
            R.id.card_walked, R.id.card_energy
    };

    private final String[] activityNames = {
            "Cycling", "Public Transit", "Recycling", "Plant-based meal",
            "Reusable cup", "Composting", "Walked", "Energy saving"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        LinearLayout[] cards = new LinearLayout[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            final int index = i;
            cards[i] = view.findViewById(cardIds[i]);
            cards[i].setOnClickListener(v -> selectCard(cards, (LinearLayout) v, activityNames[index]));
        }

        TextView btnLog = view.findViewById(R.id.btn_log_activity);
        btnLog.setOnClickListener(v -> saveQuickLog(cards, btnLog));

        return view;
    }

    private void selectCard(LinearLayout[] cards, LinearLayout card, String name) {
        deselectAll(cards);
        card.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card_selected));
        selectedCard = card;
        selectedActivityName = name;
    }

    private void deselectAll(LinearLayout[] cards) {
        for (LinearLayout c : cards) {
            c.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card));
        }
    }

    private void saveQuickLog(LinearLayout[] cards, TextView btnLog) {
        if (isSaving) {
            return;
        }

        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to save an activity", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCard == null || selectedActivityName == null) {
            Toast.makeText(getContext(), "Please select an activity first", Toast.LENGTH_SHORT).show();
            return;
        }

        isSaving = true;
        btnLog.setEnabled(false);
        btnLog.setAlpha(0.6f);

        String logId = db.collection("activity_logs").document().getId();

        Map<String, Object> activityLog = new HashMap<>();
        activityLog.put("logId", logId);
        activityLog.put("userId", currentUser.getUid());
        activityLog.put("activityType", selectedActivityName);
        activityLog.put("status", "quick");
        activityLog.put("points", getBasePoints(selectedActivityName));
        activityLog.put("bonusPoints", 0);
        activityLog.put("proofUrl", null);
        activityLog.put("voteCount", 0);
        activityLog.put("timestamp", Timestamp.now());

        db.collection("activity_logs")
                .document(logId)
                .set(activityLog)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), selectedActivityName + " logged successfully ✅", Toast.LENGTH_SHORT).show();
                    deselectAll(cards);
                    selectedCard = null;
                    selectedActivityName = null;
                    isSaving = false;
                    btnLog.setEnabled(true);
                    btnLog.setAlpha(1.0f);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to save log: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    isSaving = false;
                    btnLog.setEnabled(true);
                    btnLog.setAlpha(1.0f);
                });
    }

    private int getBasePoints(String activityType) {
        switch (activityType) {
            case "Cycling":
                return 15;
            case "Public Transit":
                return 12;
            case "Recycling":
                return 10;
            case "Plant-based meal":
                return 10;
            case "Reusable cup":
                return 8;
            case "Composting":
                return 10;
            case "Walked":
                return 12;
            case "Energy saving":
                return 9;
            default:
                return 5;
        }
    }
}