/**
 * LogFragment.java
 *
 * Allows the user to select a sustainability activity and submit it
 * as either a quick log or a pending verification log.
 * Writes ActivityLog documents to the "activity_logs" Firestore collection.
 *
 * Role in design: Part of the UI/Controller layer. Collects user input,
 * creates ActivityLog model objects, and saves them to Firestore.
 *
 * Outstanding issues: verified proof-upload flow is handled by the teammate
 * responsible for proof submission. This fragment currently stores the
 * pending_verification status correctly.
 *
 * @author Izza
 */
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

import com.example.klimate.model.ActivityLog;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class LogFragment extends Fragment {

    private LinearLayout selectedCard = null;
    private String selectedActivityName = null;

    // Default = quick unless you wire verified tab/button to change it
    private String selectedStatus = "quick";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

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
        mAuth = FirebaseAuth.getInstance();

        LinearLayout[] cards = new LinearLayout[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            final int index = i;
            cards[i] = view.findViewById(cardIds[i]);
            cards[i].setOnClickListener(v -> selectCard(cards, (LinearLayout) v, activityNames[index]));
        }

        View quickTab = view.findViewById(R.id.btn_quick_log);
        View verifiedTab = view.findViewById(R.id.btn_verified_log);

        if (quickTab != null) {
            quickTab.setOnClickListener(v -> {
                selectedStatus = "quick";
                Toast.makeText(getContext(), "Quick Log selected", Toast.LENGTH_SHORT).show();
            });
        }

        if (verifiedTab != null) {
            verifiedTab.setOnClickListener(v -> {
                selectedStatus = "pending_verification";
                Toast.makeText(getContext(), "Verified Log selected", Toast.LENGTH_SHORT).show();
            });
        }

        TextView btnLog = view.findViewById(R.id.btn_log_activity);
        btnLog.setOnClickListener(v -> submitLog(cards));

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

    private void submitLog(LinearLayout[] cards) {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to submit a log", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCard == null || selectedActivityName == null) {
            Toast.makeText(getContext(), "Please select an activity first", Toast.LENGTH_SHORT).show();
            return;
        }

        int basePoints = getPointsForActivity(selectedActivityName);

        ActivityLog log = new ActivityLog(
                currentUser.getUid(),
                selectedActivityName,
                selectedStatus,
                basePoints,
                Timestamp.now()
        );

        DocumentReference docRef = db.collection("activity_logs").document();
        log.setLogId(docRef.getId());

        docRef.set(log)
                .addOnSuccessListener(unused -> {
                    String message;
                    if ("pending_verification".equals(selectedStatus)) {
                        message = "Verified log submitted for proof/validation";
                    } else {
                        message = "Quick activity logged successfully ✅";
                    }

                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    deselectAll(cards);
                    selectedCard = null;
                    selectedActivityName = null;
                    selectedStatus = "quick";
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Failed to save log: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private int getPointsForActivity(String activityType) {
        switch (activityType) {
            case "Cycling":
                return 15;
            case "Public Transit":
                return 12;
            case "Recycling":
                return 8;
            case "Plant-based meal":
                return 10;
            case "Reusable cup":
                return 5;
            case "Composting":
                return 9;
            case "Walked":
                return 10;
            case "Energy saving":
                return 7;
            default:
                return 5;
        }
    }
}