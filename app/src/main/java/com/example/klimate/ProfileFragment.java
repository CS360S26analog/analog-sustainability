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
        TextView tvName      = view.findViewById(R.id.tv_display_name);
        TextView tvPointsBadge = view.findViewById(R.id.tv_points_badge);
        TextView tvCo2       = view.findViewById(R.id.tv_co2_value);
        TextView tvStreak    = view.findViewById(R.id.tv_streak_value);
        TextView tvAvatar    = view.findViewById(R.id.tv_avatar_initials);

        // --- Read user document from Firestore ---
        db.collection("users").document(uid)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;

                    String displayName = doc.getString("displayName");
                    long totalPoints   = doc.getLong("totalPoints")  != null ? doc.getLong("totalPoints")  : 0;
                    long streakDays    = doc.getLong("streakDays")   != null ? doc.getLong("streakDays")   : 0;
                    double co2Saved    = doc.getDouble("co2SavedKg") != null ? doc.getDouble("co2SavedKg") : 0.0;

                    // Display name
                    if (tvName != null && displayName != null) {
                        tvName.setText(displayName);
                    }

                    // Avatar initials (first 2 letters of display name)
                    if (tvAvatar != null && displayName != null && displayName.length() >= 2) {
                        tvAvatar.setText(displayName.substring(0, 2).toUpperCase());
                    }

                    // Points badge (e.g. "• 1,840 pts")
                    if (tvPointsBadge != null) {
                        tvPointsBadge.setText("• " + totalPoints + " pts");
                    }

                    // CO2 saved
                    if (tvCo2 != null) {
                        tvCo2.setText(String.format("%.1f", co2Saved));
                    }

                    // Streak
                    if (tvStreak != null) {
                        tvStreak.setText(String.valueOf(streakDays));
                    }
                });

        // --- Settings rows (existing behaviour kept) ---
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

        return view;
    }
}