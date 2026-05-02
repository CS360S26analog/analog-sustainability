/**
 * StaffOverviewFragment.java
 *
 * Staff Tab 1 — Campus Overview dashboard.
 * Shows live campus-wide stats: total CO₂ saved, active students,
 * active challenges, and flagged submission count.
 * Provides quick-action buttons to the other staff tabs.
 *
 * Role in design: Part of the View layer (staff section).
 * Uses Observer pattern via Firestore aggregate queries.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class StaffOverviewFragment extends Fragment {

    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_staff_overview, container, false);
        db = FirebaseFirestore.getInstance();

        loadStats(view);
        wireQuickActions(view);

        return view;
    }

    private void loadStats(View view) {
        TextView tvCo2    = view.findViewById(R.id.tv_staff_total_co2);
        TextView tvUsers  = view.findViewById(R.id.tv_staff_active_users);
        TextView tvChall  = view.findViewById(R.id.tv_staff_active_challenges);
        TextView tvFlagged = view.findViewById(R.id.tv_staff_flagged_count);
        LinearLayout recentList = view.findViewById(R.id.ll_staff_recent_logs);

        // Campus CO₂ + active users
        db.collection("activity_logs").get().addOnSuccessListener(snap -> {
            double totalCo2 = 0.0;
            Set<String> users = new HashSet<>();
            for (QueryDocumentSnapshot doc : snap) {
                Double co2 = doc.getDouble("co2SavedKg");
                if (co2 != null) totalCo2 += co2;
                String uid = doc.getString("userId");
                if (uid != null) users.add(uid);
            }
            if (tvCo2 != null)
                tvCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂ saved", totalCo2));
            if (tvUsers != null)
                tvUsers.setText(users.size() + " students logging");

            // Recent 5 logs
            if (recentList != null) {
                recentList.removeAllViews();
                int count = 0;
                for (QueryDocumentSnapshot doc : snap) {
                    if (count++ >= 5) break;
                    String activity = doc.getString("activityType");
                    String uid      = doc.getString("userId");
                    TextView row = new TextView(requireContext());
                    row.setText("🌿  " + (activity != null ? activity : "Activity")
                            + "   ·   " + (uid != null ? uid.substring(0, 6) + "…" : ""));
                    row.setTextSize(13f);
                    row.setPadding(0, 8, 0, 8);
                    row.setTextColor(requireContext().getColor(R.color.color_text_secondary));
                    recentList.addView(row);
                }
            }
        });

        // Active challenges count
        db.collection("challenges").whereEqualTo("active", true).get()
                .addOnSuccessListener(snap -> {
                    if (tvChall != null)
                        tvChall.setText(snap.size() + " active challenges");
                });

        // Flagged submissions count
        db.collection("activity_logs").whereEqualTo("status", "flagged").get()
                .addOnSuccessListener(snap -> {
                    if (tvFlagged != null) {
                        int n = snap.size();
                        tvFlagged.setText(n + " flagged submission" + (n != 1 ? "s" : ""));
                        tvFlagged.setTextColor(requireContext().getColor(
                                n > 0 ? android.R.color.holo_red_dark : R.color.color_green_text));
                    }
                });
    }

    private void wireQuickActions(View view) {
        View btnManage    = view.findViewById(R.id.btn_quick_manage);
        View btnTips      = view.findViewById(R.id.btn_quick_tips);
        View btnReports   = view.findViewById(R.id.btn_quick_reports);

        if (btnManage != null)
            btnManage.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_manage));
        if (btnTips != null)
            btnTips.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_community));
        if (btnReports != null)
            btnReports.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
    }

    private void switchStaffTab(int navItemId) {
        if (getActivity() != null) {
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    getActivity().findViewById(R.id.bottom_nav);
            if (nav != null) nav.setSelectedItemId(navItemId);
        }
    }
}