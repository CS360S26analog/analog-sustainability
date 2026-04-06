/**
 * ProfileFragment.java
 *
 * Displays the logged-in student's profile, including real-time points
 * balance read from Firestore, streak days, CO2 saved, and a hardcoded
 * list of available rewards. Also handles navigation row click feedback.
 *
 * Role in design: Part of the View layer (MVC). Reads from the users/
 * Firestore collection using the current Firebase Auth UID. Points are
 * updated by PointsManager whenever votes change.
 *
 * Outstanding issues: Rewards list is hardcoded for Half checkpoint.
 * Challenges count is hardcoded — requires Challenge model to be live.
 *
 * @author Haroon
 * @author Izza
 * @author Karar
 * @author Maryam W.
 * @author Maryam A.
 */
package com.example.klimate;

import android.content.Intent;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    /**
     * Reward item data class — holds display info for one reward row.
     */
    private static class Reward {
        final String emoji;
        final String name;
        final int pointsCost;

        Reward(String emoji, String name, int pointsCost) {
            this.emoji = emoji;
            this.name = name;
            this.pointsCost = pointsCost;
        }
    }

    /** Hardcoded rewards catalogue for Half checkpoint. */
    private static final Reward[] REWARDS = {
            new Reward("☕", "Free Coffee",          500),
            new Reward("🚲", "Bike Rental Voucher", 1200),
            new Reward("♻️", "Eco Tote Bag",         800),
            new Reward("🌍", "Plant a Tree",        1000),
            new Reward("🍃", "Campus Meal Discount", 1500),
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return view;

        String uid = currentUser.getUid();

        // --- TextViews to update from Firestore ---
        TextView tvName        = view.findViewById(R.id.tv_display_name);
        TextView tvPointsBadge = view.findViewById(R.id.tv_points_badge);
        TextView tvCo2         = view.findViewById(R.id.tv_co2_value);
        TextView tvStreak      = view.findViewById(R.id.tv_streak_value);
        TextView tvAvatar      = view.findViewById(R.id.tv_avatar_initials);

        // --- Read user document from Firestore ---
        db.collection("users").document(uid)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;

                    String displayName = doc.getString("displayName");
                    long totalPoints   = doc.getLong("totalPoints")  != null ? doc.getLong("totalPoints")  : 0;
                    long streakDays    = doc.getLong("streakDays")   != null ? doc.getLong("streakDays")   : 0;
                    double co2Saved    = doc.getDouble("co2SavedKg") != null ? doc.getDouble("co2SavedKg") : 0.0;

                    if (tvName != null && displayName != null) {
                        tvName.setText(displayName);
                    }

                    if (tvAvatar != null && displayName != null && displayName.length() >= 2) {
                        tvAvatar.setText(displayName.substring(0, 2).toUpperCase());
                    }

                    if (tvPointsBadge != null) {
                        tvPointsBadge.setText("• " + totalPoints + " pts");
                    }

                    if (tvCo2 != null) {
                        tvCo2.setText(String.format("%.1f", co2Saved));
                    }

                    if (tvStreak != null) {
                        tvStreak.setText(String.valueOf(streakDays));
                    }
                });

        // --- Settings rows ---
        int[]    rowIds    = {R.id.row_account, R.id.row_privacy, R.id.row_notifications, R.id.row_help};
        String[] rowLabels = {"Account Settings", "Privacy", "Notifications", "Help & Support"};

        for (int i = 0; i < rowIds.length; i++) {
            final String label = rowLabels[i];
            LinearLayout row = view.findViewById(rowIds[i]);
            if (row != null) {
                row.setOnClickListener(v ->
                        Toast.makeText(getContext(), label + " — coming soon!", Toast.LENGTH_SHORT).show()
                );
            }
        }

        // --- View all achievements ---
        TextView tvViewAll = view.findViewById(R.id.tv_view_all);
        if (tvViewAll != null) {
            tvViewAll.setOnClickListener(v ->
                    Toast.makeText(getContext(), "All achievements — coming soon!", Toast.LENGTH_SHORT).show()
            );
        }

        // --- Log out ---
        LinearLayout rowLogout = view.findViewById(R.id.row_logout);
        if (rowLogout != null) {
            rowLogout.setOnClickListener(v -> {
                auth.signOut();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                // Clear the entire back stack so Back can't return to the app
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        }

        // --- Hardcoded rewards list (Half checkpoint) ---
        populateRewards(view);

        return view;
    }

    /**
     * Inflates a row for each hardcoded reward into the rewards_container
     * LinearLayout. Each row shows an emoji, reward name, and points cost.
     * Marked hardcoded — replace with Firestore read in a future sprint.
     *
     * @param root the inflated fragment view containing rewards_container
     */
    private void populateRewards(View root) {
        LinearLayout container = root.findViewById(R.id.rewards_container);
        if (container == null) return;

        int dp16 = dpToPx(16);
        int dp12 = dpToPx(12);

        for (int i = 0; i < REWARDS.length; i++) {
            Reward reward = REWARDS[i];

            // Outer row
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(56));
            row.setLayoutParams(rowParams);
            row.setPadding(dp16, 0, dp16, 0);

            // Emoji
            TextView tvEmoji = new TextView(getContext());
            tvEmoji.setText(reward.emoji);
            tvEmoji.setTextSize(20);
            tvEmoji.setPadding(0, 0, dp12, 0);
            row.addView(tvEmoji);

            // Reward name (expands)
            TextView tvName = new TextView(getContext());
            tvName.setText(reward.name);
            tvName.setTextSize(15);
            tvName.setTextColor(getResources().getColor(R.color.color_text_primary, null));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(nameParams);
            row.addView(tvName);

            // Points cost badge
            TextView tvCost = new TextView(getContext());
            tvCost.setText(reward.pointsCost + " pts");
            tvCost.setTextSize(13);
            tvCost.setTextColor(getResources().getColor(R.color.color_green_text, null));
            tvCost.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvCost);

            container.addView(row);

            // Divider between rows (not after the last one)
            if (i < REWARDS.length - 1) {
                View divider = new View(getContext());
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divParams.setMarginStart(dp16);
                divider.setLayoutParams(divParams);
                divider.setBackgroundColor(getResources().getColor(R.color.color_divider, null));
                container.addView(divider);
            }
        }
    }

    /**
     * Converts dp units to pixels using the current display density.
     *
     * @param dp value in density-independent pixels
     * @return equivalent value in pixels
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}