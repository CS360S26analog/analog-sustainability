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

    private boolean isValidActivity(String activity) {
        if (activity == null) return false;

        for (String option : activityOptions) {
            if (option.equals(activity)) {
                return true;
            }
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