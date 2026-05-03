/**
 * StaffReportsFragment.java
 *
 * Staff-only reports screen for Klimate.
 *
 * Shows admin metrics, a staff leaderboard with log count,
 * and flagged pending logs with approve / reject actions.
 *
 * This fragment is standalone for now.
 * Staff navigation can later open it with new StaffReportsFragment().
 *
 * @author Karar
 */
package com.example.klimate;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class StaffReportsFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private ListenerRegistration usersListener;
    private ListenerRegistration logsListener;

    private LinearLayout reportsContentContainer;
    private View accessDeniedCard;

    private TextView textStatus;
    private TextView textTotalUsers;
    private TextView textTotalLogs;
    private TextView textTotalCo2;
    private TextView textFlaggedCount;

    private LinearLayout leaderboardContainer;
    private LinearLayout flaggedLogsContainer;
    private View noFlaggedCard;

    private final List<StaffUserRow> latestUsers = new ArrayList<>();
    private final Map<String, Long> latestLogCounts = new HashMap<>();
    private long latestTotalLogs = 0;
    private double latestTotalCo2 = 0.0;

    private static class StaffUserRow {
        String uid;
        String name;
        long points;

        StaffUserRow(String uid, String name, long points) {
            this.uid = uid;
            this.name = name;
            this.points = points;
        }
    }

    private static class FlaggedLogRow {
        String documentId;
        String logId;
        String userId;
        String activityType;
        String status;
        long points;
        long bonusPoints;
        long upvotes;
        long downvotes;
        Timestamp timestamp;

        FlaggedLogRow(String documentId,
                      String logId,
                      String userId,
                      String activityType,
                      String status,
                      long points,
                      long bonusPoints,
                      long upvotes,
                      long downvotes,
                      Timestamp timestamp) {
            this.documentId = documentId;
            this.logId = logId;
            this.userId = userId;
            this.activityType = activityType;
            this.status = status;
            this.points = points;
            this.bonusPoints = bonusPoints;
            this.upvotes = upvotes;
            this.downvotes = downvotes;
            this.timestamp = timestamp;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_staff_reports, container, false);

        db = FirebaseFirestore.getInstance();
        forceGreenHeaderColor();
        auth = FirebaseAuth.getInstance();

        reportsContentContainer = view.findViewById(R.id.reports_content_container);
        accessDeniedCard = view.findViewById(R.id.card_staff_access_denied);

        textStatus = view.findViewById(R.id.text_staff_reports_status);
        textTotalUsers = view.findViewById(R.id.text_total_users);
        textTotalLogs = view.findViewById(R.id.text_total_logs);
        textTotalCo2 = view.findViewById(R.id.text_total_co2);
        textFlaggedCount = view.findViewById(R.id.text_flagged_count);

        leaderboardContainer = view.findViewById(R.id.staff_leaderboard_container);
        flaggedLogsContainer = view.findViewById(R.id.flagged_logs_container);
        noFlaggedCard = view.findViewById(R.id.card_no_flagged_logs);

        checkStaffAccess();

        return view;
    }

    private void forceGreenHeaderColor() {
        if (getActivity() == null) return;

        Window window = getActivity().getWindow();
        window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.color_green_header));
    }

    private void checkStaffAccess() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            showAccessDenied("Please sign in with a staff account.");
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");

                    if ("staff".equalsIgnoreCase(role)) {
                        showReports();
                        loadStaffData();
                    } else {
                        showAccessDenied("This screen is only for staff accounts.");
                    }
                })
                .addOnFailureListener(e ->
                        showAccessDenied("Could not verify staff access.")
                );
    }

    private void showAccessDenied(String message) {
        if (reportsContentContainer != null) {
            reportsContentContainer.setVisibility(View.GONE);
        }

        if (accessDeniedCard != null) {
            accessDeniedCard.setVisibility(View.VISIBLE);
        }

        if (textStatus != null) {
            textStatus.setText(message);
        }
    }

    private void showReports() {
        if (reportsContentContainer != null) {
            reportsContentContainer.setVisibility(View.VISIBLE);
        }

        if (accessDeniedCard != null) {
            accessDeniedCard.setVisibility(View.GONE);
        }

        if (textStatus != null) {
            textStatus.setText("Staff reports are live");
        }
    }

    private void loadStaffData() {
        usersListener = db.collection("users")
                .orderBy("totalPoints", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded() || getContext() == null) return;

                    if (e != null || snapshots == null) {
                        textStatus.setText("Could not load staff leaderboard");
                        return;
                    }

                    latestUsers.clear();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String role = doc.getString("role");

                        // Staff reports leaderboard should show students only.
                        if (!"student".equalsIgnoreCase(role != null ? role.trim() : "")) {
                            continue;
                        }

                        String uid = doc.getId();
                        String name = doc.getString("displayName");

                        if (TextUtils.isEmpty(name)) {
                            name = doc.getString("email");
                        }

                        if (TextUtils.isEmpty(name)) {
                            name = "Student";
                        }

                        long points = readLong(doc, "totalPoints");
                        latestUsers.add(new StaffUserRow(uid, name, points));
                    }

                    renderMetrics();
                    renderLeaderboard();
                });

        logsListener = db.collection("activity_logs")
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded() || getContext() == null) return;

                    if (e != null || snapshots == null) {
                        textStatus.setText("Could not load reports");
                        return;
                    }

                    latestLogCounts.clear();
                    latestTotalLogs = snapshots.size();
                    latestTotalCo2 = 0.0;

                    List<DocumentSnapshot> reportedLogs = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String userId = doc.getString("userId");

                        if (!TextUtils.isEmpty(userId)) {
                            long oldCount = latestLogCounts.containsKey(userId)
                                    ? latestLogCounts.get(userId)
                                    : 0;
                            latestLogCounts.put(userId, oldCount + 1);
                        }

                        latestTotalCo2 += readDouble(doc, "co2SavedKg");

                        Boolean reported = doc.getBoolean("reported");
                        String status = doc.getString("status");

                        if (Boolean.TRUE.equals(reported) || "reported".equals(status)) {
                            reportedLogs.add(doc);
                        }
                    }

                    renderMetrics();
                    renderLeaderboard();
                    loadFlaggedLogs(reportedLogs);
                });
    }

    private void renderMetrics() {
        if (textTotalUsers != null) {
            textTotalUsers.setText(String.valueOf(latestUsers.size()));
        }

        if (textTotalLogs != null) {
            textTotalLogs.setText(String.valueOf(latestTotalLogs));
        }

        if (textTotalCo2 != null) {
            textTotalCo2.setText(String.format(Locale.getDefault(), "%.1f kg", latestTotalCo2));
        }
    }

    private void renderLeaderboard() {
        if (leaderboardContainer == null || getContext() == null) return;

        leaderboardContainer.removeAllViews();

        if (latestUsers.isEmpty()) {
            TextView empty = createSmallText("No users found yet.");
            leaderboardContainer.addView(empty);
            return;
        }

        int limit = Math.min(latestUsers.size(), 25);

        for (int i = 0; i < limit; i++) {
            StaffUserRow user = latestUsers.get(i);
            long logCount = latestLogCounts.containsKey(user.uid)
                    ? latestLogCounts.get(user.uid)
                    : 0;

            View row = createLeaderboardRow(i + 1, user, logCount);
            leaderboardContainer.addView(row);
        }
    }

    private View createLeaderboardRow(int rank, StaffUserRow user, long logCount) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        row.setBackgroundResource(R.drawable.bg_leaderboard_row);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dpToPx(10));
        row.setLayoutParams(rowParams);

        TextView rankText = createSmallText(String.format(Locale.getDefault(), "%02d", rank));
        rankText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rankText.setWidth(dpToPx(36));
        row.addView(rankText);

        TextView nameText = createMainText(user.name);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        nameText.setLayoutParams(nameParams);
        row.addView(nameText);

        TextView pointsText = createSmallText(user.points + " pts");
        pointsText.setGravity(Gravity.END);
        pointsText.setWidth(dpToPx(80));
        pointsText.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green_text));
        row.addView(pointsText);

        TextView logsText = createSmallText(logCount + " logs");
        logsText.setGravity(Gravity.END);
        logsText.setWidth(dpToPx(70));
        row.addView(logsText);

        return row;
    }

    private void loadFlaggedLogs(List<DocumentSnapshot> reportedLogs) {
        if (flaggedLogsContainer == null || getContext() == null) return;

        flaggedLogsContainer.removeAllViews();

        if (reportedLogs.isEmpty()) {
            showNoFlaggedLogs(0);
            return;
        }

        int reportedCount = 0;

        for (DocumentSnapshot logDoc : reportedLogs) {
            String logId = logDoc.getString("logId");

            if (TextUtils.isEmpty(logId)) {
                logId = logDoc.getId();
            }

            FlaggedLogRow reportedLog = new FlaggedLogRow(
                    logDoc.getId(),
                    logId,
                    logDoc.getString("userId"),
                    safeText(logDoc.getString("activityType"), "Unknown activity"),
                    safeText(logDoc.getString("status"), "reported"),
                    readLong(logDoc, "points"),
                    readLong(logDoc, "bonusPoints"),
                    readLong(logDoc, "upvotes"),
                    readLong(logDoc, "downvotes"),
                    logDoc.getTimestamp("timestamp")
            );

            flaggedLogsContainer.addView(createFlaggedLogCard(reportedLog));
            reportedCount++;
        }

        showNoFlaggedLogs(reportedCount);
    }

    private void showNoFlaggedLogs(int flaggedCount) {
        if (textFlaggedCount != null) {
            textFlaggedCount.setText(String.valueOf(flaggedCount));
        }

        if (noFlaggedCard != null) {
            noFlaggedCard.setVisibility(flaggedCount == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private View createFlaggedLogCard(FlaggedLogRow log) {
        CardView card = new CardView(requireContext());
        card.setRadius(dpToPx(18));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        TextView title = createMainText(log.activityType);
        content.addView(title);

        TextView meta = createSmallText(
                "User: " + safeText(log.userId, "Unknown")
                        + "\nStatus: " + log.status
                        + "\nSubmitted: " + formatTimestamp(log.timestamp)
        );
        meta.setPadding(0, dpToPx(4), 0, 0);
        content.addView(meta);

        TextView votes = createSmallText(
                "Upvotes: " + log.upvotes
                        + "   Downvotes: " + log.downvotes
                        + "   Points at risk: " + (log.points + log.bonusPoints)
        );
        votes.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_red_text));
        votes.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        votes.setPadding(0, dpToPx(8), 0, 0);
        content.addView(votes);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        buttonRow.setPadding(0, dpToPx(12), 0, 0);

        TextView keepButton = createActionButton("Keep", true);
        TextView deleteButton = createActionButton("Delete", false);

        keepButton.setOnClickListener(v -> confirmKeep(log));
        deleteButton.setOnClickListener(v -> confirmDelete(log));

        buttonRow.addView(keepButton);
        buttonRow.addView(deleteButton);

        content.addView(buttonRow);
        card.addView(content);

        return card;
    }

    private void confirmKeep(FlaggedLogRow log) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Keep log?")
                .setMessage("This will remove the report flag and keep the log visible.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Keep", (dialog, which) -> keepLog(log))
                .show();
    }

    private void keepLog(FlaggedLogRow log) {
        FirebaseUser reviewer = auth.getCurrentUser();
        String reviewerId = reviewer != null ? reviewer.getUid() : "unknown";

        Map<String, Object> updates = new HashMap<>();
        updates.put("reported", false);
        updates.put("status", "pending_verification");
        updates.put("reviewDecision", "kept");
        updates.put("reviewedBy", reviewerId);
        updates.put("reviewedAt", Timestamp.now());

        db.collection("activity_logs")
                .document(log.documentId)
                .update(updates)
                .addOnSuccessListener(unused ->
                        Toast.makeText(getContext(), "Log kept", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Could not keep log", Toast.LENGTH_SHORT).show()
                );
    }

    private void confirmDelete(FlaggedLogRow log) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete reported log?")
                .setMessage("This will permanently delete this activity log.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteReportedLog(log))
                .show();
    }

    private void deleteReportedLog(FlaggedLogRow log) {
        db.collection("activity_logs")
                .document(log.documentId)
                .delete()
                .addOnSuccessListener(unused ->
                        Toast.makeText(getContext(), "Log deleted", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Delete failed", Toast.LENGTH_SHORT).show()
                );
    }

    private TextView createActionButton(String text, boolean positive) {
        TextView button = new TextView(requireContext());
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextSize(13);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dpToPx(18), dpToPx(8), dpToPx(18), dpToPx(8));
        button.setBackgroundResource(positive ? R.drawable.bg_badge_green : R.drawable.bg_leaderboard_row);
        button.setTextColor(ContextCompat.getColor(
                requireContext(),
                positive ? R.color.color_green_text : R.color.color_red_text
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dpToPx(8), 0, 0, 0);
        button.setLayoutParams(params);

        return button;
    }

    private void confirmApprove(FlaggedLogRow log) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Approve log?")
                .setMessage("This will mark the submission as verified.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Approve", (dialog, which) -> approveLog(log))
                .show();
    }

    private void confirmReject(FlaggedLogRow log) {
        long deduction = Math.max(0, log.points + log.bonusPoints);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reject log?")
                .setMessage("This will mark the submission as rejected and deduct up to "
                        + deduction + " points from the student.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reject", (dialog, which) -> rejectLog(log))
                .show();
    }

    private void approveLog(FlaggedLogRow log) {
        FirebaseUser reviewer = auth.getCurrentUser();
        String reviewerId = reviewer != null ? reviewer.getUid() : "unknown";

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "verified");
        updates.put("reviewDecision", "approved");
        updates.put("reviewedBy", reviewerId);
        updates.put("reviewedAt", Timestamp.now());

        db.collection("activity_logs")
                .document(log.documentId)
                .update(updates)
                .addOnSuccessListener(unused ->
                        Toast.makeText(getContext(), "Log approved", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Approve failed", Toast.LENGTH_SHORT).show()
                );
    }

    private void rejectLog(FlaggedLogRow log) {
        FirebaseUser reviewer = auth.getCurrentUser();
        String reviewerId = reviewer != null ? reviewer.getUid() : "unknown";

        DocumentReference logRef = db.collection("activity_logs").document(log.documentId);

        if (TextUtils.isEmpty(log.userId)) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "rejected");
            updates.put("reviewDecision", "rejected");
            updates.put("reviewedBy", reviewerId);
            updates.put("reviewedAt", Timestamp.now());

            logRef.update(updates)
                    .addOnSuccessListener(unused ->
                            Toast.makeText(getContext(), "Log rejected", Toast.LENGTH_SHORT).show()
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Reject failed", Toast.LENGTH_SHORT).show()
                    );
            return;
        }

        DocumentReference userRef = db.collection("users").document(log.userId);

        db.runTransaction(transaction -> {
            DocumentSnapshot freshLog = transaction.get(logRef);
            DocumentSnapshot freshUser = transaction.get(userRef);

            long points = readLong(freshLog, "points");
            long bonusPoints = readLong(freshLog, "bonusPoints");
            long deduction = Math.max(0, points + bonusPoints);

            long currentUserPoints = readLong(freshUser, "totalPoints");
            long newUserPoints = Math.max(0, currentUserPoints - deduction);

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "rejected");
            updates.put("reviewDecision", "rejected");
            updates.put("reviewedBy", reviewerId);
            updates.put("reviewedAt", Timestamp.now());
            updates.put("pointsDeductedOnReject", deduction);

            transaction.update(logRef, updates);
            transaction.update(userRef, "totalPoints", newUserPoints);

            return null;
        }).addOnSuccessListener(unused ->
                Toast.makeText(getContext(), "Log rejected and points updated", Toast.LENGTH_SHORT).show()
        ).addOnFailureListener(e ->
                Toast.makeText(getContext(), "Reject failed", Toast.LENGTH_SHORT).show()
        );
    }

    private TextView createMainText(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));
        return view;
    }

    private TextView createSmallText(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary));
        return view;
    }

    private long readLong(DocumentSnapshot doc, String field) {
        if (doc == null) return 0;

        Object value = doc.get(field);

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return 0;
    }

    private double readDouble(DocumentSnapshot doc, String field) {
        if (doc == null) return 0.0;

        Object value = doc.get(field);

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        return 0.0;
    }

    private String safeText(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "Unknown";

        SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        return format.format(timestamp.toDate());
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (usersListener != null) {
            usersListener.remove();
            usersListener = null;
        }

        if (logsListener != null) {
            logsListener.remove();
            logsListener = null;
        }
    }
}