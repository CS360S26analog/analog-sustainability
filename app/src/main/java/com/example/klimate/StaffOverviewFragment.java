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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.view.Window;
import androidx.core.content.ContextCompat;

public class StaffOverviewFragment extends Fragment {

    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_staff_overview, container, false);
        db = FirebaseFirestore.getInstance();

        forceGreenHeaderColor();

        loadStats(view);
        wireQuickActions(view);

        return view;
    }

    private void loadStats(View view) {
        TextView tvCo2      = view.findViewById(R.id.tv_staff_total_co2);
        TextView tvUsers    = view.findViewById(R.id.tv_staff_active_users);
        TextView tvChall    = view.findViewById(R.id.tv_staff_active_challenges);
        TextView tvFlagged  = view.findViewById(R.id.tv_staff_flagged_count);
        TextView tvPending  = view.findViewById(R.id.tv_staff_pending_count);
        TextView btnReview  = view.findViewById(R.id.btn_go_review);
        LinearLayout llTopActivities = view.findViewById(R.id.ll_top_activities);

        // Wire review button → validation feed tab
        if (btnReview != null) {
            btnReview.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_feed));
        }

        // CO₂ + active users (live)
        db.collection("activity_logs")
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || getContext() == null || snap == null) return;

                    double totalCo2 = 0.0;
                    Set<String> users = new HashSet<>();
                    java.util.Map<String, Integer> activityCount = new java.util.HashMap<>();

                    // Only count logs from the past 7 days for top activities
                    long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);

                    for (QueryDocumentSnapshot doc : snap) {
                        Double co2 = doc.getDouble("co2SavedKg");
                        if (co2 != null) totalCo2 += co2;

                        String uid = doc.getString("userId");
                        if (uid != null) users.add(uid);

                        // Count activity types for this week
                        com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
                        if (ts != null && ts.toDate().getTime() >= sevenDaysAgo) {
                            String type = doc.getString("activityType");
                            if (type != null) {
                                activityCount.put(type, activityCount.getOrDefault(type, 0) + 1);
                            }
                        }
                    }

                    if (tvCo2 != null)
                        tvCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂", totalCo2));
                    if (tvUsers != null)
                        tvUsers.setText(users.size() + " students logging");

                    // Render top activities
                    if (llTopActivities != null) {
                        llTopActivities.removeAllViews();

                        if (activityCount.isEmpty()) {
                            TextView empty = new TextView(requireContext());
                            empty.setText("No activity logs this week yet.");
                            empty.setTextSize(13f);
                            empty.setTextColor(requireContext().getColor(R.color.color_text_secondary));
                            llTopActivities.addView(empty);
                        } else {
                            // Sort by count descending
                            List<Map.Entry<String, Integer>> sorted =
                                    new ArrayList<>(activityCount.entrySet());
                            sorted.sort((a2, b) -> b.getValue() - a2.getValue());

                            // Find max for bar scaling
                            int max = sorted.get(0).getValue();

                            for (java.util.Map.Entry<String, Integer> entry : sorted) {
                                llTopActivities.addView(
                                        buildActivityBar(entry.getKey(), entry.getValue(), max));
                            }
                        }
                    }
                });

        // Active challenges (live)
        db.collection("challenges")
                .whereEqualTo("active", true)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || tvChall == null || snap == null) return;
                    tvChall.setText(snap.size() + " active");
                });

        // Flagged submissions (live)
        db.collection("activity_logs")
                .whereEqualTo("status", "flagged")
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || tvFlagged == null || snap == null) return;
                    int n = snap.size();
                    tvFlagged.setText(n + " flagged");
                    tvFlagged.setTextColor(requireContext().getColor(
                            n > 0 ? android.R.color.holo_red_dark : R.color.color_green_text));
                });

        // Pending verification count (live)
        db.collection("activity_logs")
                .whereEqualTo("status", "pending_verification")
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || tvPending == null || snap == null) return;
                    int n = snap.size();
                    tvPending.setText(String.valueOf(n));
                    tvPending.setTextColor(requireContext().getColor(
                            n > 0 ? R.color.color_amber : R.color.color_green_header));
                });
    }

    private void wireQuickActions(View view) {
        View btnManage  = view.findViewById(R.id.btn_quick_manage);
        View btnTips    = view.findViewById(R.id.btn_quick_tips);
        View btnReports = view.findViewById(R.id.btn_quick_reports);
        View btnLogs    = view.findViewById(R.id.btn_quick_logs);

        if (btnManage != null)
            btnManage.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_manage));
        if (btnTips != null)
            btnTips.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new StaffTipsFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
        if (btnReports != null)
            btnReports.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        if (btnLogs != null)
            btnLogs.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new StaffLogsFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
    }

    private void switchStaffTab(int navItemId) {
        if (getActivity() != null) {
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    getActivity().findViewById(R.id.bottom_nav);
            if (nav != null) nav.setSelectedItemId(navItemId);
        }
    }
    private void forceGreenHeaderColor() {
        if (getActivity() == null) return;

        Window window = getActivity().getWindow();
        window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.color_green_header));
    }

    /**
     * Builds a horizontal bar row showing an activity type and its
     * log count relative to the most-logged activity this week.
     *
     * @param activityType the activity name
     * @param count        number of logs this week
     * @param max          highest count (used to scale the bar width)
     */
    private View buildActivityBar(String activityType, int count, int max) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dpToPx(10));
        row.setLayoutParams(rowLp);

        // Label row: emoji + name + count
        LinearLayout labelRow = new LinearLayout(requireContext());
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(0, 0, 0, dpToPx(4));
        labelRow.setLayoutParams(labelLp);

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvLabel.setText(getEmoji(activityType) + "  " + activityType);
        tvLabel.setTextSize(13f);
        tvLabel.setTextColor(requireContext().getColor(R.color.color_text_primary));

        TextView tvCount = new TextView(requireContext());
        tvCount.setText(count + " log" + (count != 1 ? "s" : ""));
        tvCount.setTextSize(12f);
        tvCount.setTextColor(requireContext().getColor(R.color.color_green_text));
        tvCount.setTypeface(null, android.graphics.Typeface.BOLD);

        labelRow.addView(tvLabel);
        labelRow.addView(tvCount);

        // Progress bar
        android.widget.FrameLayout barBg = new android.widget.FrameLayout(requireContext());
        LinearLayout.LayoutParams barBgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(6));
        barBg.setLayoutParams(barBgLp);
        barBg.setBackgroundColor(0xFFE8E2D8);

        View barFill = new View(requireContext());
        int fillWidth = max > 0 ? (int) ((count / (float) max) * 100) : 0;
        // Use post to set width after layout
        barBg.post(() -> {
            int totalWidth = barBg.getWidth();
            android.widget.FrameLayout.LayoutParams fillLp =
                    new android.widget.FrameLayout.LayoutParams(
                            (int) (totalWidth * (fillWidth / 100f)),
                            ViewGroup.LayoutParams.MATCH_PARENT);
            barFill.setLayoutParams(fillLp);
        });
        barFill.setBackgroundColor(requireContext().getColor(R.color.color_green_header));
        barBg.addView(barFill);

        row.addView(labelRow);
        row.addView(barBg);
        return row;
    }

    private String getEmoji(String activityType) {
        if (activityType == null) return "🌿";
        switch (activityType) {
            case "Cycling":          return "🚲";
            case "Public Transit":   return "🚌";
            case "Recycling":        return "♻️";
            case "Plant-based meal": return "🥗";
            case "Reusable cup":     return "☕";
            case "Composting":       return "🍂";
            case "Walked":           return "🚶";
            case "Energy saving":    return "💡";
            default:                 return "🌿";
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}