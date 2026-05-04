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
import androidx.core.content.ContextCompat;
import java.util.Locale;
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
        TextView btnReviewDuplicate = view.findViewById(R.id.btn_go_review_duplicate);
        LinearLayout llTopActivities = view.findViewById(R.id.ll_top_activities);

        if (btnReview != null) {
            btnReview.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_feed));
        }

        if (btnReviewDuplicate != null) {
            btnReviewDuplicate.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_feed));
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
        View btnManage = view.findViewById(R.id.btn_quick_manage);
        View btnTips = view.findViewById(R.id.btn_quick_tips);
        View btnReports = view.findViewById(R.id.btn_quick_reports);
        View btnLogs = view.findViewById(R.id.btn_quick_logs);

        TextView btnReview = view.findViewById(R.id.btn_go_review);
        TextView btnReviewDuplicate = view.findViewById(R.id.btn_go_review_duplicate);

        View challengesStat = view.findViewById(R.id.card_staff_challenges_stat);
        View flaggedStat = view.findViewById(R.id.card_staff_flagged_stat);
        View pendingStat = view.findViewById(R.id.card_staff_pending_stat);
        View summaryCard = view.findViewById(R.id.card_staff_summary);

        if (btnManage != null) {
            btnManage.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_manage));
        }

        if (btnTips != null) {
            btnTips.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new StaffTipsFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
        }

        if (btnReports != null) {
            btnReports.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        }
        if (summaryCard != null) {
            summaryCard.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        }

        /*
         * If your project has a separate staff_nav_logs item, change this to:
         * switchStaffTab(R.id.staff_nav_logs)
         *
         * For now, this safely opens Reports because logs/flagged submissions
         * are handled there in your current staff flow.
         */
        if (btnLogs != null) {
            btnLogs.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        }

        if (btnReview != null) {
            btnReview.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_feed));
        }

        if (btnReviewDuplicate != null) {
            btnReviewDuplicate.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_feed));
        }

        if (challengesStat != null) {
            challengesStat.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_manage));
        }

        if (flaggedStat != null) {
            flaggedStat.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        }

        if (pendingStat != null) {
            pendingStat.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_feed));
        }
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

    private View buildActivityBar(String activityType, int count, int max) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.setMargins(0, 0, 0, dpToPx(12));
        row.setLayoutParams(rowLp);

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView name = new TextView(requireContext());
        name.setText(getActivityEmoji(activityType) + "  " + activityType);
        name.setTextSize(15f);
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));

        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        name.setLayoutParams(nameLp);
        top.addView(name);

        TextView countText = new TextView(requireContext());
        countText.setText(count + (count == 1 ? " log" : " logs"));
        countText.setTextSize(14f);
        countText.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green_text));
        countText.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        top.addView(countText);

        row.addView(top);

        android.widget.ProgressBar bar = new android.widget.ProgressBar(
                requireContext(),
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(7)
        );
        barLp.setMargins(0, dpToPx(5), 0, 0);
        bar.setLayoutParams(barLp);
        bar.setMax(Math.max(max, 1));
        bar.setProgress(count);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.color_green_header)
        ));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.color_auth_input_stroke)
        ));

        row.addView(bar);

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

    private String getActivityEmoji(String activityType) {
        if (activityType == null) return "🌿";

        String lower = activityType.toLowerCase(Locale.ROOT);

        if (lower.contains("plant")) return "🥗";
        if (lower.contains("cycling") || lower.contains("bike")) return "🚲";
        if (lower.contains("recycling")) return "♻️";
        if (lower.contains("transit") || lower.contains("bus")) return "🚌";
        if (lower.contains("energy")) return "💡";
        if (lower.contains("compost")) return "🍂";
        if (lower.contains("walk")) return "🚶";
        if (lower.contains("cup")) return "☕";

        return "🌿";
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

}