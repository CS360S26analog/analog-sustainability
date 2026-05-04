/**
 * StaffOverviewFragment.java
 *
 * Staff Tab 1 — Campus Overview dashboard.
 * Shows live campus-wide stats: total CO₂ saved, active students,
 * active challenges, flagged submission count, and pending EcoMap suggestions.
 *
 * Big fix:
 * - The pending/review card now opens StaffTipsFragment.
 * - The review buttons now open StaffTipsFragment.
 * - The pending count is now used for suggested tips instead of peer log reviews.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StaffOverviewFragment extends Fragment {

    private FirebaseFirestore db;

    private final List<ListenerRegistration> listenerRegistrations = new ArrayList<>();
    private final Map<String, Integer> pendingSuggestionCounts = new HashMap<>();

    private static final String[] SUGGESTION_COLLECTION_CANDIDATES = {
            "eco_map_notes"
    };
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

    private void loadStats(@NonNull View view) {
        TextView tvCo2 = view.findViewById(R.id.tv_staff_total_co2);
        TextView tvUsers = view.findViewById(R.id.tv_staff_active_users);
        TextView tvChall = view.findViewById(R.id.tv_staff_active_challenges);
        TextView tvFlagged = view.findViewById(R.id.tv_staff_flagged_count);
        TextView tvPending = view.findViewById(R.id.tv_staff_pending_count);

        TextView btnReview = view.findViewById(R.id.btn_go_review);
        TextView btnReviewDuplicate = view.findViewById(R.id.btn_go_review_duplicate);

        LinearLayout llTopActivities = view.findViewById(R.id.ll_top_activities);

        if (btnReview != null) {
            btnReview.setOnClickListener(v -> openSuggestedTipsPage());
        }

        if (btnReviewDuplicate != null) {
            btnReviewDuplicate.setOnClickListener(v -> openSuggestedTipsPage());
        }

        ListenerRegistration activityLogsListener = db.collection("activity_logs")
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || getContext() == null || snap == null) return;

                    double totalCo2 = 0.0;
                    Set<String> users = new HashSet<>();
                    Map<String, Integer> activityCount = new HashMap<>();

                    long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);

                    for (QueryDocumentSnapshot doc : snap) {
                        Double co2 = doc.getDouble("co2SavedKg");
                        if (co2 != null) {
                            totalCo2 += co2;
                        }

                        String uid = doc.getString("userId");
                        if (uid != null) {
                            users.add(uid);
                        }

                        com.google.firebase.Timestamp timestamp = doc.getTimestamp("timestamp");

                        if (timestamp != null && timestamp.toDate().getTime() >= sevenDaysAgo) {
                            String type = doc.getString("activityType");

                            if (type != null) {
                                activityCount.put(type, activityCount.getOrDefault(type, 0) + 1);
                            }
                        }
                    }

                    if (tvCo2 != null) {
                        tvCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂", totalCo2));
                    }

                    if (tvUsers != null) {
                        tvUsers.setText(users.size() + " students logging");
                    }

                    renderTopActivities(llTopActivities, activityCount);
                });

        listenerRegistrations.add(activityLogsListener);

        ListenerRegistration challengesListener = db.collection("challenges")
                .whereEqualTo("active", true)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || tvChall == null || snap == null) return;
                    tvChall.setText(snap.size() + " active");
                });

        listenerRegistrations.add(challengesListener);

        ListenerRegistration flaggedListener = db.collection("activity_logs")
                .whereEqualTo("status", "flagged")
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || getContext() == null || tvFlagged == null || snap == null) {
                        return;
                    }

                    int flaggedCount = snap.size();

                    tvFlagged.setText(flaggedCount + " flagged");
                    tvFlagged.setTextColor(
                            ContextCompat.getColor(
                                    requireContext(),
                                    flaggedCount > 0
                                            ? android.R.color.holo_red_dark
                                            : R.color.color_green_text
                            )
                    );
                });

        listenerRegistrations.add(flaggedListener);

        attachPendingSuggestionCountListeners(tvPending);
    }

    private void renderTopActivities(@Nullable LinearLayout llTopActivities,
                                     @NonNull Map<String, Integer> activityCount) {
        if (llTopActivities == null || getContext() == null) return;

        llTopActivities.removeAllViews();

        if (activityCount.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No activity logs this week yet.");
            empty.setTextSize(13f);
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary));
            llTopActivities.addView(empty);
            return;
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(activityCount.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        int max = sorted.get(0).getValue();

        for (Map.Entry<String, Integer> entry : sorted) {
            llTopActivities.addView(buildActivityBar(entry.getKey(), entry.getValue(), max));
        }
    }

    private void attachPendingSuggestionCountListeners(@Nullable TextView tvPending) {
        if (tvPending == null) return;

        pendingSuggestionCounts.clear();
        tvPending.setText("0");

        for (String collectionName : SUGGESTION_COLLECTION_CANDIDATES) {
            ListenerRegistration listener = db.collection(collectionName)
                    .addSnapshotListener((snap, e) -> {
                        if (!isAdded() || getContext() == null || tvPending == null) return;

                        if (e != null || snap == null) {
                            pendingSuggestionCounts.put(collectionName, 0);
                            updatePendingSuggestionCountText(tvPending);
                            return;
                        }

                        int count = 0;

                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            if (isPendingSuggestion(doc)) {
                                count++;
                            }
                        }

                        pendingSuggestionCounts.put(collectionName, count);
                        updatePendingSuggestionCountText(tvPending);
                    });

            listenerRegistrations.add(listener);
        }
    }

    private boolean isPendingSuggestion(@NonNull DocumentSnapshot doc) {
        Boolean approved = doc.getBoolean("approved");
        Boolean isApproved = doc.getBoolean("isApproved");
        Boolean rejected = doc.getBoolean("rejected");

        if (Boolean.TRUE.equals(approved)
                || Boolean.TRUE.equals(isApproved)
                || Boolean.TRUE.equals(rejected)) {
            return false;
        }

        String status = doc.getString("status");
        String approvalStatus = doc.getString("approvalStatus");

        if (status == null && approvalStatus == null) {
            return true;
        }

        if (status != null) {
            String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);

            return normalizedStatus.equals("pending")
                    || normalizedStatus.equals("pending_review")
                    || normalizedStatus.equals("submitted")
                    || normalizedStatus.equals("open");
        }

        String normalizedApprovalStatus = approvalStatus.trim().toLowerCase(Locale.ROOT);

        return normalizedApprovalStatus.equals("pending")
                || normalizedApprovalStatus.equals("pending_review")
                || normalizedApprovalStatus.equals("submitted")
                || normalizedApprovalStatus.equals("open");
    }

    private void updatePendingSuggestionCountText(@NonNull TextView tvPending) {
        int total = 0;

        for (Integer value : pendingSuggestionCounts.values()) {
            if (value != null) {
                total += value;
            }
        }

        tvPending.setText(String.valueOf(total));
        tvPending.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.white)
        );
    }

    private void wireQuickActions(@NonNull View view) {
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
            btnTips.setOnClickListener(v -> openSuggestedTipsPage());
        }

        if (btnReports != null) {
            btnReports.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        }

        if (summaryCard != null) {
            summaryCard.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        }

        if (btnLogs != null) {
            btnLogs.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        }

        if (btnReview != null) {
            btnReview.setOnClickListener(v -> openSuggestedTipsPage());
        }

        if (btnReviewDuplicate != null) {
            btnReviewDuplicate.setOnClickListener(v -> openSuggestedTipsPage());
        }

        if (challengesStat != null) {
            challengesStat.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_manage));
        }

        if (flaggedStat != null) {
            flaggedStat.setOnClickListener(v -> switchStaffTab(R.id.staff_nav_reports));
        }

        if (pendingStat != null) {
            pendingStat.setOnClickListener(v -> openSuggestedTipsPage());
        }
    }

    private void openSuggestedTipsPage() {
        if (getActivity() == null) return;

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new StaffTipsFragment())
                .addToBackStack(null)
                .commit();
    }

    private void switchStaffTab(int navItemId) {
        if (getActivity() == null) return;

        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                requireActivity().findViewById(R.id.bottom_nav);

        if (nav != null) {
            nav.setSelectedItemId(navItemId);
        }
    }

    private void forceGreenHeaderColor() {
        if (getActivity() == null || getContext() == null) return;

        Window window = requireActivity().getWindow();
        window.setStatusBarColor(
                ContextCompat.getColor(requireContext(), R.color.color_green_header)
        );
    }

    private View buildActivityBar(String activityType, int count, int max) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLayoutParams.setMargins(0, 0, 0, dpToPx(12));
        row.setLayoutParams(rowLayoutParams);

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView name = new TextView(requireContext());
        name.setText(getActivityEmoji(activityType) + "  " + activityType);
        name.setTextSize(15f);
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));

        LinearLayout.LayoutParams nameLayoutParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        name.setLayoutParams(nameLayoutParams);
        top.addView(name);

        TextView countText = new TextView(requireContext());
        countText.setText(count + (count == 1 ? " log" : " logs"));
        countText.setTextSize(14f);
        countText.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green_text));
        countText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(countText);

        row.addView(top);

        ProgressBar bar = new ProgressBar(
                requireContext(),
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        LinearLayout.LayoutParams barLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(7)
        );
        barLayoutParams.setMargins(0, dpToPx(5), 0, 0);
        bar.setLayoutParams(barLayoutParams);

        bar.setMax(Math.max(max, 1));
        bar.setProgress(count);

        bar.setProgressTintList(
                ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.color_green_header)
                )
        );

        bar.setProgressBackgroundTintList(
                ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.color_auth_input_stroke)
                )
        );

        row.addView(bar);

        return row;
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
        if (getContext() == null) return dp;
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        for (ListenerRegistration listenerRegistration : listenerRegistrations) {
            if (listenerRegistration != null) {
                listenerRegistration.remove();
            }
        }

        listenerRegistrations.clear();
        pendingSuggestionCounts.clear();
    }
}