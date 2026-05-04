/**
 * HistoryFragment.java
 *
 * Displays only the current user's editable activity logs.
 * Editable logs are quick logs submitted within the 24-hour edit window.
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

    private void loadHistory() {
        if (currentUser == null) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText("Please log in to view editable logs.");
            return;
        }

        historyContainer.removeAllViews();
        tvEmptyState.setVisibility(View.GONE);

        String uid = currentUser.getUid();

        executor.execute(() -> {
            List<ActivityLogEntity> roomLogs =
                    AppDatabase.getInstance(requireContext())
                            .activityLogDao()
                            .getLogsForUserSync(uid);

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    historyContainer.removeAllViews();

                    for (ActivityLogEntity e : roomLogs) {
                        boolean isQuick = "quick".equals(e.status);
                        boolean withinEditWindow =
                                (System.currentTimeMillis() - e.timestampMillis) <= EDIT_WINDOW_MILLIS;

                        if (isQuick && withinEditWindow) {
                            addHistoryCardFromEntity(e);
                        }
                    }

                    if (historyContainer.getChildCount() == 0) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        tvEmptyState.setText("No editable logs right now.");
                    }
                });
            }

            db.collection("activity_logs")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("status", "quick")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!isAdded()) return;

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

                        List<QueryDocumentSnapshot> docs = new java.util.ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            Timestamp timestamp = doc.getTimestamp("timestamp");
                            if (isWithin24Hours(timestamp)) {
                                docs.add(doc);
                            }
                        }

                        docs.sort((a, b) -> {
                            Timestamp ta = a.getTimestamp("timestamp");
                            Timestamp tb = b.getTimestamp("timestamp");

                            if (ta == null && tb == null) return 0;
                            if (ta == null) return 1;
                            if (tb == null) return -1;

                            return tb.toDate().compareTo(ta.toDate());
                        });

                        requireActivity().runOnUiThread(() -> {
                            historyContainer.removeAllViews();

                            if (docs.isEmpty()) {
                                tvEmptyState.setVisibility(View.VISIBLE);
                                tvEmptyState.setText("No editable logs right now.");
                            } else {
                                tvEmptyState.setVisibility(View.GONE);
                                for (QueryDocumentSnapshot doc : docs) {
                                    addHistoryCard(doc);
                                }
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded() && historyContainer.getChildCount() == 0) {
                            requireActivity().runOnUiThread(() -> {
                                tvEmptyState.setVisibility(View.VISIBLE);
                                tvEmptyState.setText("Could not load editable logs.");
                            });
                        }
                    });
        });
    }

    private void addHistoryCardFromEntity(ActivityLogEntity e) {
        if (getContext() == null) return;

        View card = LayoutInflater.from(getContext())
                .inflate(R.layout.item_history_log, historyContainer, false);

        TextView tvEmoji = card.findViewById(R.id.tv_history_emoji);
        TextView tvTitle = card.findViewById(R.id.tv_history_title);
        TextView tvMeta = card.findViewById(R.id.tv_history_meta);
        TextView tvStatus = card.findViewById(R.id.tv_history_status);
        TextView btnEdit = card.findViewById(R.id.btn_edit_log);
        TextView btnDelete = card.findViewById(R.id.btn_delete_log);

        tvEmoji.setText(getActivityEmoji(e.activityType));
        tvTitle.setText(e.activityType);

        String dateText = formatTimestamp(
                e.timestampMillis > 0
                        ? new Timestamp(new java.util.Date(e.timestampMillis))
                        : null
        );

        tvMeta.setText(dateText + " • " + e.points + " pts");
        tvStatus.setText("quick • editable");

        btnEdit.setAlpha(1f);
        btnDelete.setAlpha(1f);

        boolean hasSyncedId = e.logId != null
                && !e.logId.isEmpty()
                && !"pending".equals(e.syncStatus);

        btnEdit.setOnClickListener(v -> {
            if (!hasSyncedId) {
                Toast.makeText(getContext(), "Still uploading — try again shortly.", Toast.LENGTH_SHORT).show();
                return;
            }

            showEditDialog(e.logId, e.activityType);
        });

        btnDelete.setOnClickListener(v -> {
            if (!hasSyncedId) {
                executor.execute(() ->
                        AppDatabase.getInstance(requireContext())
                                .activityLogDao()
                                .deleteByLocalId(e.localId)
                );
                historyContainer.removeView(card);

                if (historyContainer.getChildCount() == 0) {
                    tvEmptyState.setVisibility(View.VISIBLE);
                    tvEmptyState.setText("No editable logs right now.");
                }

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
        String activityType = doc.getString("activityType") != null ? doc.getString("activityType") : "";
        String status = doc.getString("status") != null ? doc.getString("status") : "quick";
        Long points = doc.getLong("points");
        Long quantity = doc.getLong("quantity");
        Double co2 = doc.getDouble("co2SavedKg");
        String unit = doc.getString("unit") != null ? doc.getString("unit") : "";
        String proofUrl = doc.getString("proofUrl");
        Timestamp ts = doc.getTimestamp("timestamp");

        ActivityLogEntity e = new ActivityLogEntity(
                logId,
                userId,
                activityType,
                status,
                points != null ? points.intValue() : 0,
                quantity != null ? quantity.intValue() : 1,
                co2 != null ? co2 : 0.0,
                unit,
                proofUrl,
                ts != null ? ts.toDate().getTime() : System.currentTimeMillis()
        );

        e.syncStatus = "synced";
        return e;
    }

    private void addHistoryCard(QueryDocumentSnapshot doc) {
        if (getContext() == null) return;

        View card = LayoutInflater.from(getContext())
                .inflate(R.layout.item_history_log, historyContainer, false);

        TextView tvEmoji = card.findViewById(R.id.tv_history_emoji);
        TextView tvTitle = card.findViewById(R.id.tv_history_title);
        TextView tvMeta = card.findViewById(R.id.tv_history_meta);
        TextView tvStatus = card.findViewById(R.id.tv_history_status);
        TextView btnEdit = card.findViewById(R.id.btn_edit_log);
        TextView btnDelete = card.findViewById(R.id.btn_delete_log);

        String logId = doc.getString("logId");
        if (logId == null || logId.trim().isEmpty()) {
            logId = doc.getId();
        }

        String activityType = doc.getString("activityType");
        Long points = doc.getLong("points");
        Timestamp timestamp = doc.getTimestamp("timestamp");

        tvEmoji.setText(getActivityEmoji(activityType));
        tvTitle.setText(activityType != null ? activityType : "Activity");

        String dateText = formatTimestamp(timestamp);
        String pointsText = (points != null ? points : 0) + " pts";
        tvMeta.setText(dateText + " • " + pointsText);
        tvStatus.setText("quick • editable");

        btnEdit.setAlpha(1.0f);
        btnDelete.setAlpha(1.0f);

        String finalLogId = logId;

        btnEdit.setOnClickListener(v -> showEditDialog(finalLogId, activityType));
        btnDelete.setOnClickListener(v -> showDeleteDialog(finalLogId));

        historyContainer.addView(card);
    }

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
            if (hasFocus) actvActivity.showDropDown();
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

                            double updatedCo2SavedKg =
                                    CarbonCalculator.calculateCo2SavedKg(updatedActivity, quantity);

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

    private void recalculateUserStats() {
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        executor.execute(() -> {
            AppDatabase localDb = AppDatabase.getInstance(requireContext());
            ActivityLogDao logDao = localDb.activityLogDao();

            double totalCo2 = logDao.getTotalCo2ForUser(uid);
            int totalPoints = logDao.getTotalPointsForUser(uid);
            List<String> days = logDao.getDistinctLogDays(uid);
            int streak = calculateCurrentStreakFromDays(new HashSet<>(days));

            localDb.userDao().updateStats(uid, totalPoints, totalCo2, streak);

            db.collection("users")
                    .document(uid)
                    .update(
                            "co2SavedKg", totalCo2,
                            "streakDays", streak,
                            "totalPoints", totalPoints
                    );
        });
    }

    private boolean isValidActivity(String activity) {
        if (activity == null) return false;

        for (String option : activityOptions) {
            if (option.equals(activity)) return true;
        }

        return false;
    }

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

    private boolean isWithin24Hours(Timestamp timestamp) {
        if (timestamp == null) return false;

        long now = System.currentTimeMillis();
        long loggedAt = timestamp.toDate().getTime();
        return (now - loggedAt) <= EDIT_WINDOW_MILLIS;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "Unknown time";

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(timestamp.toDate());
    }

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

    private void resetTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }
}