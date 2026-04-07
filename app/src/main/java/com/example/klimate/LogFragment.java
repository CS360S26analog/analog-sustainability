package com.example.klimate;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

public class LogFragment extends Fragment {

    private LinearLayout selectedCard = null;
    private String selectedActivityName = null;
    private boolean isSaving = false;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private PointsManager pointsManager;

    private final int[] cardIds = {
            R.id.card_cycling, R.id.card_transit, R.id.card_recycling,
            R.id.card_plantbased, R.id.card_reusable, R.id.card_composting,
            R.id.card_walked, R.id.card_energy
    };

    private final String[] activityNames = {
            "Cycling", "Public Transit", "Recycling", "Plant-based meal",
            "Reusable cup", "Composting", "Walked", "Energy saving"
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
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        pointsManager = new PointsManager();

        TextView btnInfo = view.findViewById(R.id.btn_info);
        ImageView btnHistory = view.findViewById(R.id.btn_history);

        btnInfo.setOnClickListener(v -> showActivityInfoDialog());
        btnHistory.setOnClickListener(v -> openHistory());

        LinearLayout[] cards = new LinearLayout[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            final int index = i;
            cards[i] = view.findViewById(cardIds[i]);
            cards[i].setOnClickListener(v -> selectCard(cards, (LinearLayout) v, activityNames[index]));
        }

        TextView btnLog = view.findViewById(R.id.btn_log_activity);
        btnLog.setOnClickListener(v -> saveQuickLog(cards, btnLog));

        return view;
    }

    private void showActivityInfoDialog() {
        if (getContext() == null) return;

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_log_info);
        dialog.setCancelable(true);

        TextView btnClose = dialog.findViewById(R.id.btn_close_log_info);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        dialog.show();
    }

    private void openHistory() {
        if (getActivity() == null) return;

        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new HistoryFragment())
                .addToBackStack(null)
                .commit();
    }

    private void selectCard(LinearLayout[] cards, LinearLayout card, String name) {
        deselectAll(cards);
        card.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card_selected));
        selectedCard = card;
        selectedActivityName = name;
    }

    private void deselectAll(LinearLayout[] cards) {
        for (LinearLayout c : cards) {
            c.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card));
        }
    }

    private void saveQuickLog(LinearLayout[] cards, TextView btnLog) {
        if (isSaving) return;

        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to save an activity", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCard == null || selectedActivityName == null) {
            Toast.makeText(getContext(), "Please select an activity first", Toast.LENGTH_SHORT).show();
            return;
        }

        isSaving = true;
        btnLog.setEnabled(false);
        btnLog.setAlpha(0.6f);

        String activityName = selectedActivityName;
        int basePoints = getBasePoints(activityName);
        String logId = db.collection("activity_logs").document().getId();

        Map<String, Object> activityLog = new HashMap<>();
        activityLog.put("logId", logId);
        activityLog.put("userId", currentUser.getUid());
        activityLog.put("activityType", activityName);
        activityLog.put("status", "quick");
        activityLog.put("points", basePoints);
        activityLog.put("bonusPoints", 0);
        activityLog.put("proofUrl", null);
        activityLog.put("voteCount", 0);
        activityLog.put("timestamp", Timestamp.now());

        db.collection("activity_logs")
                .document(logId)
                .set(activityLog)
                .addOnSuccessListener(unused -> {
                    pointsManager.awardBasePoints(currentUser.getUid(), basePoints);
                    refreshUserStats();

                    Toast.makeText(
                            getContext(),
                            activityName + " logged successfully ✅ +" + basePoints + " pts",
                            Toast.LENGTH_SHORT
                    ).show();

                    deselectAll(cards);
                    selectedCard = null;
                    selectedActivityName = null;
                    isSaving = false;
                    btnLog.setEnabled(true);
                    btnLog.setAlpha(1.0f);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to save log: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    isSaving = false;
                    btnLog.setEnabled(true);
                    btnLog.setAlpha(1.0f);
                });
    }

    private void refreshUserStats() {
        if (currentUser == null) return;

        db.collection("activity_logs")
                .whereEqualTo("userId", currentUser.getUid())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double totalCo2 = 0.0;
                    Set<String> uniqueDays = new HashSet<>();
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String activityType = doc.getString("activityType");
                        if (activityType != null) {
                            Double co2 = CO2_PER_ACTIVITY.get(activityType);
                            if (co2 != null) {
                                totalCo2 += co2;
                            }
                        }

                        Timestamp ts = doc.getTimestamp("timestamp");
                        if (ts != null) {
                            uniqueDays.add(formatter.format(ts.toDate()));
                        }
                    }

                    int streakDays = calculateStreakFromDays(uniqueDays);

                    db.collection("users")
                            .document(currentUser.getUid())
                            .update(
                                    "co2SavedKg", totalCo2,
                                    "streakDays", streakDays
                            );
                });
    }

    private int calculateStreakFromDays(Set<String> uniqueDays) {
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

    private void resetTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
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
}