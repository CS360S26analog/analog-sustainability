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
 * @author Haroon
 */
package com.example.klimate;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
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

    // Default = quick unless the verified tab is selected
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

    /**
     * Inflates the logging screen, initializes Firebase,
     * wires activity cards and log submission actions.
     *
     * @param inflater the LayoutInflater used to inflate the fragment layout
     * @param container the parent view group
     * @param savedInstanceState previously saved fragment state
     * @return the root view for the logging screen
     */
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

        View quickTab    = view.findViewById(R.id.btn_quick_log);
        View verifiedTab = view.findViewById(R.id.btn_verified_log);

        // FIX: switch visual active state between the two tabs
        if (quickTab != null && verifiedTab != null) {
            quickTab.setOnClickListener(v -> {
                selectedStatus = "quick";
                setActiveTab(quickTab, verifiedTab);
            });

            verifiedTab.setOnClickListener(v -> {
                selectedStatus = "pending_verification";
                setActiveTab(verifiedTab, quickTab);
            });

            // Start with Quick tab visually active
            setActiveTab(quickTab, verifiedTab);
        }

        TextView btnLog = view.findViewById(R.id.btn_log_activity);
        btnLog.setOnClickListener(v -> submitLog(cards));

        return view;
    }

    /**
     * Marks one tab as visually active and the other as inactive,
     * animating the text colour on both so the switch feels smooth.
     * Background swaps immediately (the pill shape can't cross-fade
     * without a custom drawable, but the colour animation makes the
     * overall transition feel polished).
     *
     * @param active   the TextView tab that should appear selected
     * @param inactive the TextView tab that should appear unselected
     */
    private void setActiveTab(View active, View inactive) {
        // Swap backgrounds immediately
        active.setBackgroundResource(R.drawable.bg_toggle_selected);
        inactive.setBackground(null);

        // Animate text colour: active → white, inactive → color_text_secondary
        int white       = requireContext().getColor(android.R.color.white);
        int secondary   = requireContext().getColor(R.color.color_text_secondary);

        animateTextColor((TextView) active,   secondary, white);
        animateTextColor((TextView) inactive, white,     secondary);
    }

    /**
     * Smoothly animates a TextView's text colour from {@code fromColor}
     * to {@code toColor} over 200 ms.
     */
    private void animateTextColor(TextView tv, int fromColor, int toColor) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        animator.setDuration(200);
        animator.addUpdateListener(anim -> tv.setTextColor((int) anim.getAnimatedValue()));
        animator.start();
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

        int basePoints = getBasePoints(selectedActivityName);

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
                    // Award base points to the user immediately on any log submit
                    new PointsManager().awardBasePoints(currentUser.getUid(), basePoints);

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

                    // Reset tab back to Quick after a successful submit
                    selectedStatus = "quick";
                    View quickTab    = getView() != null ? getView().findViewById(R.id.btn_quick_log)    : null;
                    View verifiedTab = getView() != null ? getView().findViewById(R.id.btn_verified_log) : null;
                    if (quickTab != null && verifiedTab != null) {
                        setActiveTab(quickTab, verifiedTab);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Failed to save log: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }
    private int getBasePoints(String activityType) {
        switch (activityType) {
            case "Cycling":
                return 30;
            case "Public Transit":
                return 20;
            case "Recycling":
                return 15;
            case "Plant-based meal":
                return 25;
            case "Reusable cup":
                return 10;
            case "Composting":
                return 20;
            case "Walked":
                return 20;
            case "Energy saving":
                return 10;
            default:
                return 0;
        }
    }
}