/**
 * HistoryFragment.java
 *
 * Displays the current user's full activity log history, sorted by
 * most recent first. Each log entry can be edited or deleted within
 * 24 hours of submission. Edits and deletions trigger a full
 * recalculation of the user's total points, CO2 saved, and streak.
 *
 * Role in design: Part of the View layer (MVC). Reads and writes to
 * the activity_logs Firestore collection. Also updates the users
 * collection via recalculateUserStats() after any modification.
 * Accessible from the Home screen.
 *
 * Outstanding issues: streak calculation uses a simplified consecutive
 * day check and may not handle timezone edge cases correctly.
 *
 * @author Team Analog
 */
package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.klimate.local.ActivityLogDao;
import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HistoryFragment extends Fragment {

    private FirebaseFirestore db;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private FirebaseUser currentUser;

    private LinearLayout historyContainer;
    private TextView tvEmptyState;

    private static final long EDIT_WINDOW_MILLIS = 24L * 60L * 60L * 1000L;

    private final String[] activityOptions = {
            "Cycling",
            "Public Transit",
            "Recycling",
            "Plant-based meal",
            "Reusable cup",
            "Composting",
            "Walked",
            "Energy saving"
    };

    /**
     * Inflates the layout and initializes Firebase instances and UI components.
     *
     * @param inflater the LayoutInflater object
     * @param container the parent view
     * @param savedInstanceState previous saved state
     * @return the inflated view for this fragment
     */

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        ImageView btnBack = view.findViewById(R.id.btn_back_history);
        historyContainer = view.findViewById(R.id.history_container);
        tvEmptyState = view.findViewById(R.id.tv_empty_history);

        btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        loadHistory();
        return view;
    }

    /**
     * Loads the user's activity history from Firestore.
     *
     * Fetches all logs belonging to the current user, sorts them by timestamp
     * most recent first, and displays them as cards.
     */

    private void loadHistory() {
        if (currentUser == null) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText("Please log in to view history.");
            return;
        }
        historyContainer.removeAllViews();
        tvEmptyState.setVisibility(View.GONE);

        String uid = currentUser.getUid();

        // ── Room first (instant render) ──────────────────────────────────────
        executor.execute(() -> {
            List<ActivityLogEntity> roomLogs =
                    AppDatabase.getInstance(requireContext())
                            .activityLogDao().getLogsForUserSync(uid);

            if (isAdded()) requireActivity().runOnUiThread(() -> {
                if (!roomLogs.isEmpty()) {
                    for (ActivityLogEntity e : roomLogs) addHistoryCardFromEntity(e);
                }
            });

            // ── Firestore refresh ────────────────────────────────────────────
            db.collection("activity_logs")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!isAdded()) return;

                        // Upsert every Firestore doc into Room
                        executor.execute(() -> {
                            ActivityLogDao dao =
                                    AppDatabase.getInstance(requireContext()).activityLogDao();
                            for (QueryDocumentSnapshot doc : querySnapshot) {
                                String logId = doc.getString("logId");
                                if (logId == null || logId.isEmpty()) logId = doc.getId();
                                if (dao.countByLogId(logId) == 0) {
                                    ActivityLogEntity e = firestoreDocToEntity(doc);
                                    dao.insert(e);
                                }
                            }
                        });

                        // Re-render from Firestore data
                        List<QueryDocumentSnapshot> docs = new java.util.ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) docs.add(doc);
                        docs.sort((a, b) -> {
                            Timestamp ta = a.getTimestamp("timestamp");
                            Timestamp tb = b.getTimestamp("timestamp");
                            if (ta == null && tb == null) return 0;
                            if (ta == null) return 1; if (tb == null) return -1;
                            return tb.toDate().compareTo(ta.toDate());
                        });

                        requireActivity().runOnUiThread(() -> {
                            historyContainer.removeAllViews();
                            if (docs.isEmpty()) {
                                tvEmptyState.setVisibility(View.VISIBLE);
                                tvEmptyState.setText("No activity history yet.");
                            } else {
                                for (QueryDocumentSnapshot doc : docs) addHistoryCard(doc);
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded() && historyContainer.getChildCount() == 0) {
                            requireActivity().runOnUiThread(() -> {
                                tvEmptyState.setVisibility(View.VISIBLE);
                                tvEmptyState.setText("Could not load history.");
                            });
                        }
                    });
        });
    }

    private void addHistoryCardFromEntity(ActivityLogEntity e) {
        // Reuse the same layout; synthesise only the fields addHistoryCard() uses
        View card = LayoutInflater.from(getContext())
                .inflate(R.layout.item_history_log, historyContainer, false);
        TextView tvEmoji  = card.findViewById(R.id.tv_history_emoji);
        TextView tvTitle  = card.findViewById(R.id.tv_history_title);
        TextView tvMeta   = card.findViewById(R.id.tv_history_meta);
        TextView tvStatus = card.findViewById(R.id.tv_history_status);
        TextView btnEdit  = card.findViewById(R.id.btn_edit_log);
        TextView btnDelete = card.findViewById(R.id.btn_delete_log);

        tvEmoji.setText(getActivityEmoji(e.activityType));
        tvTitle.setText(e.activityType);

        String dateText = formatTimestamp(
                e.timestampMillis > 0
                        ? new com.google.firebase.Timestamp(
                        new java.util.Date(e.timestampMillis)) : null);
        tvMeta.setText(dateText + " • " + e.points + " pts");

        boolean canModify = (System.currentTimeMillis() - e.timestampMillis)
                <= EDIT_WINDOW_MILLIS;

        String prettyStatus = e.status.replace("_", " ");
        tvStatus.setText(prettyStatus + (canModify ? " • editable" : " • locked"));

        btnEdit.setAlpha(canModify ? 1f : 0.45f);
        btnDelete.setAlpha(canModify ? 1f : 0.45f);

        // Edit/Delete work the same as before — they use the Firestore logId.
        // If the entity is still "pending" (no logId yet), disable both buttons.
        boolean hasSyncedId = !e.logId.isEmpty() && !"pending".equals(e.syncStatus);

        btnEdit.setOnClickListener(v -> {
            if (!canModify) {
                Toast.makeText(getContext(), "Logs can only be edited within 24 hours.",
                        Toast.LENGTH_SHORT).show(); return;
            }
            if (!hasSyncedId) {
                Toast.makeText(getContext(), "Still uploading — try again shortly.",
                        Toast.LENGTH_SHORT).show(); return;
            }
            showEditDialog(e.logId, e.activityType);
        });

        btnDelete.setOnClickListener(v -> {
            if (!canModify) {
                Toast.makeText(getContext(), "Logs can only be deleted within 24 hours.",
                        Toast.LENGTH_SHORT).show(); return;
            }
            if (!hasSyncedId) {
                // If still pending, delete only from Room
                executor.execute(() ->
                        AppDatabase.getInstance(requireContext())
                                .activityLogDao().deleteByLocalId(e.localId)
                );
                historyContainer.removeView(card);
                return;
            }
            showDeleteDialog(e.logId);
        });

        historyContainer.addView(card);
    }

    private ActivityLogEntity firestoreDocToEntity(QueryDocumentSnapshot doc) {
        String logId = doc.getString("logId");
        if (logId == null || logId.isEmpty()) logId = doc.getId();
        String userId = doc.getString("userId") != null ? doc.getString("userId") : "";
        String activityType = doc.getString("activityType") != null
                ? doc.getString("activityType") : "";
        String status = doc.getString("status") != null ? doc.getString("status") : "quick";
        Long points = doc.getLong("points");
        Long quantity = doc.getLong("quantity");
        Double co2 = doc.getDouble("co2SavedKg");
        String unit = doc.getString("unit") != null ? doc.getString("unit") : "";
        String proofUrl = doc.getString("proofUrl");
        Timestamp ts = doc.getTimestamp("timestamp");

        ActivityLogEntity e = new ActivityLogEntity(
                logId, userId, activityType, status,
                points != null ? points.intValue() : 0,
                quantity != null ? quantity.intValue() : 1,
                co2 != null ? co2 : 0.0,
                unit, proofUrl,
                ts != null ? ts.toDate().getTime() : System.currentTimeMillis()
        );
        e.syncStatus = "synced";
        return e;
    }


    /**
     * Creates and populates a UI card for a single activity log.
     *
     * @param doc Firestore document representing an activity log
     */

    private void addHistoryCard(QueryDocumentSnapshot doc) {
        if (getContext() == null) return;

        View card = LayoutInflater.from(getContext()).inflate(R.layout.item_history_log, historyContainer, false);

        TextView tvEmoji = card.findViewById(R.id.tv_history_emoji);
        TextView tvTitle = card.findViewById(R.id.tv_history_title);
        TextView tvMeta = card.findViewById(R.id.tv_history_meta);
        TextView tvStatus = card.findViewById(R.id.tv_history_status);
        TextView btnEdit = card.findViewById(R.id.btn_edit_log);
        TextView btnDelete = card.findViewById(R.id.btn_delete_log);

        String logId = doc.getString("logId");
        String activityType = doc.getString("activityType");
        String status = doc.getString("status");
        Long points = doc.getLong("points");
        Timestamp timestamp = doc.getTimestamp("timestamp");

        tvEmoji.setText(getActivityEmoji(activityType));
        tvTitle.setText(activityType != null ? activityType : "Activity");

        String dateText = formatTimestamp(timestamp);
        String pointsText = (points != null ? points : 0) + " pts";
        tvMeta.setText(dateText + " • " + pointsText);

        boolean canModify = isWithin24Hours(timestamp);

        String prettyStatus = status != null ? status.replace("_", " ") : "quick";
        tvStatus.setText(prettyStatus + (canModify ? " • editable" : " • locked"));

        if (!canModify) {
            btnEdit.setAlpha(0.45f);
            btnDelete.setAlpha(0.45f);
        } else {
            btnEdit.setAlpha(1.0f);
            btnDelete.setAlpha(1.0f);
        }

        btnEdit.setOnClickListener(v -> {
            if (!canModify) {
                Toast.makeText(getContext(), "Logs can only be edited within 24 hours.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (logId == null || logId.trim().isEmpty()) {
                Toast.makeText(getContext(), "Invalid log ID.", Toast.LENGTH_SHORT).show();
                return;
            }

            showEditDialog(logId, activityType);
        });

        btnDelete.setOnClickListener(v -> {
            if (!canModify) {
                Toast.makeText(getContext(), "Logs can only be deleted within 24 hours.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (logId == null || logId.trim().isEmpty()) {
                Toast.makeText(getContext(), "Invalid log ID.", Toast.LENGTH_SHORT).show();
                return;
            }

            showDeleteDialog(logId);
        });

        historyContainer.addView(card);
    }

    /**
     * Displays a dialog allowing the user to edit an existing activity log.
     *
     * @param logId the ID of the log to edit
     * @param currentActivity the current activity type of the log
     */

    private void showEditDialog(String logId, String currentActivity) {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_log, null);
        AutoCompleteTextView actvActivity = dialogView.findViewById(R.id.actv_edit_activity);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                activityOptions
        );
        actvActivity.setAdapter(adapter);
        actvActivity.setThreshold(1);
        actvActivity.setText(currentActivity != null ? currentActivity : "", false);

        actvActivity.setOnClickListener(v -> actvActivity.showDropDown());
        actvActivity.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                actvActivity.showDropDown();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Edit Log")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> {
            actvActivity.requestFocus();
            actvActivity.post(actvActivity::showDropDown);

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String updatedActivity = actvActivity.getText().toString().trim();

                if (!isValidActivity(updatedActivity)) {
                    actvActivity.setError("Choose a valid activity");
                    actvActivity.showDropDown();
                    return;
                }

                int updatedPoints = getBasePoints(updatedActivity);

                db.collection("activity_logs")
                        .document(logId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            Double quantity = documentSnapshot.getDouble("quantity");

                            if (quantity == null || quantity <= 0) {
                                quantity = 1.0;
                            }

                            double updatedCo2SavedKg = CarbonCalculator.calculateCo2SavedKg(updatedActivity, quantity);

                            db.collection("activity_logs")
                                    .document(logId)
                                    .update(
                                            "activityType", updatedActivity,
                                            "points", updatedPoints,
                                            "co2SavedKg", updatedCo2SavedKg
                                    )
                                    .addOnSuccessListener(unused -> {
                                        recalculateUserStats();
                                        Toast.makeText(getContext(), "Log updated.", Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                        loadHistory();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(getContext(), "Failed to update log.", Toast.LENGTH_SHORT).show()
                                    );
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(), "Failed to update log.", Toast.LENGTH_SHORT).show()
                        );
            });
        });

        dialog.show();
    }

    /**
     * Displays a confirmation dialog to delete a log.
     *
     * @param logId the ID of the log to delete
     */

    private void showDeleteDialog(String logId) {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Log")
                .setMessage("Delete this log? This will also update your points, CO₂, and streak.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("activity_logs")
                            .document(logId)
                            .delete()
                            .addOnSuccessListener(unused -> {
                                recalculateUserStats();
                                Toast.makeText(getContext(), "Log deleted.", Toast.LENGTH_SHORT).show();
                                loadHistory();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(), "Failed to delete log.", Toast.LENGTH_SHORT).show()
                            );

                })
                .show();
    }

    /**
     * Recalculates and updates user statistics based on all activity logs.
     *
     * Computes total CO2 saved, total points, and current activity streak.
     */

    private void recalculateUserStats() {
        if (currentUser == null) return;
        String uid = currentUser.getUid();

        executor.execute(() -> {
            AppDatabase localDb = AppDatabase.getInstance(requireContext());
            ActivityLogDao logDao = localDb.activityLogDao();

            double totalCo2  = logDao.getTotalCo2ForUser(uid);
            int totalPoints  = logDao.getTotalPointsForUser(uid);
            List<String> days = logDao.getDistinctLogDays(uid);
            int streak        = calculateCurrentStreakFromDays(new HashSet<>(days));

            // Update Room user record
            localDb.userDao().updateStats(uid, totalPoints, totalCo2, streak);

            // Push to Firestore
            db.collection("users").document(uid)
                    .update("co2SavedKg", totalCo2,
                            "streakDays", streak,
                            "totalPoints", totalPoints);
        });
    }

    /**
     * Checks whether the given activity is a valid predefined option.
     *
     * @param activity the activity to validate
     * @return true if valid and false otherwise
     */

    private boolean isValidActivity(String activity) {
        if (activity == null) return false;

        for (String option : activityOptions) {
            if (option.equals(activity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates the user's current activity streak based on unique active days.
     *
     * @param uniqueDays set of dates formatted as yyyyMMdd with activity logs
     * @return the number of consecutive days in the streak
     */

    private int calculateCurrentStreakFromDays(Set<String> uniqueDays) {
        if (uniqueDays.isEmpty()) return 0;

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        Calendar today = Calendar.getInstance();
        resetTime(today);

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        resetTime(yesterday);

        String todayKey = formatter.format(today.getTime());
        String yesterdayKey = formatter.format(yesterday.getTime());

        if (!uniqueDays.contains(todayKey) && !uniqueDays.contains(yesterdayKey)) {
            return 0;
        }

        int streak = 0;
        Calendar cursor = Calendar.getInstance();

        if (uniqueDays.contains(todayKey)) {
            resetTime(cursor);
        } else {
            cursor.add(Calendar.DAY_OF_YEAR, -1);
            resetTime(cursor);
        }

        while (true) {
            String key = formatter.format(cursor.getTime());
            if (uniqueDays.contains(key)) {
                streak++;
                cursor.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }

        return streak;
    }

    /**
     * Determines whether a log is within the editable 24 hour window.
     *
     * @param timestamp the timestamp of the log
     * @return true if within 24 hours and false otherwise
     */

    private boolean isWithin24Hours(Timestamp timestamp) {
        if (timestamp == null) return false;

        long now = System.currentTimeMillis();
        long loggedAt = timestamp.toDate().getTime();
        return (now - loggedAt) <= EDIT_WINDOW_MILLIS;
    }

    /**
     * Formats a Firestore timestamp into a readable date string.
     *
     * @param timestamp the timestamp to format
     * @return formatted date string or unknown time if null
     */

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "Unknown time";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(timestamp.toDate());
    }

    /**
     * Returns an emoji representation for a given activity type.
     *
     * @param activityType the activity type
     * @return corresponding emoji string
     */

    private String getActivityEmoji(String activityType) {
        if (activityType == null) return "🌿";

        switch (activityType) {
            case "Cycling":
                return "🚲";
            case "Public Transit":
                return "🚌";
            case "Recycling":
                return "♻️";
            case "Plant-based meal":
                return "🥗";
            case "Reusable cup":
                return "💧";
            case "Composting":
                return "🌱";
            case "Walked":
                return "👟";
            case "Energy saving":
                return "💡";
            default:
                return "🌿";
        }
    }

    /**
     * Returns the base point value associated with an activity type.
     *
     * @param activityType the activity type
     * @return the number of points assigned to the activity
     */

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
                return 5;
        }
    }

    /**
     * Resets the time components of a Calendar object to midnight.
     *
     * @param cal the Calendar instance to reset
     */

    private void resetTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }
}