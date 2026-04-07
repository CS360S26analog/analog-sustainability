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

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * HistoryFragment displays the user's past sustainability logs.
 * It allows users to review, edit, and delete recent logs.
 *
 * Role in design: UI layer Fragment that reads from Firestore and updates
 * user statistics after log changes.
 *
 * Outstanding issues:
 * - Logs are currently fetched all at once instead of paginated.
 * - Editing is limited to activity type only.
 */

/**
 * Fragment responsible for displaying the user's activity history.
 *
 * <p>This fragment retrieves activity logs from Firebase Firestore and displays them
 * in a scrollable list. Users can view, edit, or delete logs within a 24-hour window.
 * It also recalculates and updates user statistics such as total points, CO₂ saved,
 * and activity streak when logs are modified.</p>
 *
 * <p>Part of the UI layer (MVC/MVVM). Interacts with Firestore collections:
 * <ul>
 *     <li>"activity_logs" – stores user activity logs</li>
 *     <li>"users" – stores aggregated user statistics</li>
 * </ul>
 * </p>
 * @author izza
 */

public class HistoryFragment extends Fragment {

    private FirebaseFirestore db;
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

    private static final Map<String, Double> CO2_PER_ACTIVITY = new HashMap<String, Double>() {{
        put("Cycling", 0.21);
        put("Walked", 0.10);
        put("Recycling", 0.15);
        put("Composting", 0.12);
        put("Public Transit", 0.08);
        put("Plant-based meal", 0.50);
        put("Reusable cup", 0.05);
        put("Energy saving", 0.18);
    }};

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
     * <p>Fetches all logs belonging to the current user, sorts them by timestamp
     * (most recent first), and displays them as cards. If no logs exist or an error
     * occurs, an appropriate message is shown.</p>
     */

    private void loadHistory() {
        if (currentUser == null) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText("Please log in to view history.");
            return;
        }

        historyContainer.removeAllViews();
        tvEmptyState.setVisibility(View.GONE);

        db.collection("activity_logs")
                .whereEqualTo("userId", currentUser.getUid())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        docs.add(doc);
                    }

                    docs.sort((a, b) -> {
                        Timestamp ta = a.getTimestamp("timestamp");
                        Timestamp tb = b.getTimestamp("timestamp");

                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.toDate().compareTo(ta.toDate());
                    });

                    if (docs.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        tvEmptyState.setText("No activity history yet.");
                        return;
                    }

                    for (QueryDocumentSnapshot doc : docs) {
                        addHistoryCard(doc);
                    }
                })
                .addOnFailureListener(e -> {
                    tvEmptyState.setVisibility(View.VISIBLE);
                    tvEmptyState.setText("Could not load history.");
                });
    }

    /**
     * Creates and populates a UI card for a single activity log.
     *
     * <p>Displays activity type, timestamp, points, status, and allows editing or
     * deletion if within the allowed time window.</p>
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
     * <p>The user can select a new activity type from predefined options.
     * Updates Firestore and recalculates user statistics on success.</p>
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
                        .update(
                                "activityType", updatedActivity,
                                "points", updatedPoints
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
            });
        });

        dialog.show();
    }

    /**
     * Displays a confirmation dialog to delete a log.
     *
     * <p>If confirmed, deletes the log from Firestore and updates user statistics.</p>
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
     * <p>Computes:
     * <ul>
     *     <li>Total CO₂ saved</li>
     *     <li>Total points (including bonus points)</li>
     *     <li>Current activity streak</li>
     * </ul>
     * Updates the "users" collection in Firestore.</p>
     */

    private void recalculateUserStats() {
        if (currentUser == null) return;

        db.collection("activity_logs")
                .whereEqualTo("userId", currentUser.getUid())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double totalCo2 = 0.0;
                    int totalPoints = 0;
                    Set<String> uniqueDays = new HashSet<>();
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String activityType = doc.getString("activityType");
                        Long points = doc.getLong("points");
                        Long bonusPoints = doc.getLong("bonusPoints");
                        Timestamp ts = doc.getTimestamp("timestamp");

                        if (activityType != null) {
                            Double co2 = CO2_PER_ACTIVITY.get(activityType);
                            if (co2 != null) {
                                totalCo2 += co2;
                            }
                        }

                        totalPoints += (points != null ? points.intValue() : 0);
                        totalPoints += (bonusPoints != null ? bonusPoints.intValue() : 0);

                        if (ts != null) {
                            uniqueDays.add(formatter.format(ts.toDate()));
                        }
                    }

                    int streakDays = calculateCurrentStreakFromDays(uniqueDays);

                    db.collection("users")
                            .document(currentUser.getUid())
                            .update(
                                    "co2SavedKg", totalCo2,
                                    "streakDays", streakDays,
                                    "totalPoints", totalPoints
                            );
                });
    }

    /**
     * Checks whether the given activity is a valid predefined option.
     *
     * @param activity the activity to validate
     * @return true if valid, false otherwise
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
     * <p>The streak counts consecutive days (including today or yesterday)
     * where the user has logged at least one activity.</p>
     *
     * @param uniqueDays set of dates (formatted as yyyyMMdd) with activity logs
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
     * Determines whether a log is within the editable 24-hour window.
     *
     * @param timestamp the timestamp of the log
     * @return true if within 24 hours, false otherwise
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
     * @return formatted date string or "Unknown time" if null
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