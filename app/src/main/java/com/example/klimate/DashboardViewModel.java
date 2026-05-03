/**
 * DashboardViewModel.java
 *
 * ViewModel for the home dashboard screen (HomeFragment).
 * Queries Firestore to retrieve the current user's profile stats
 * (streak, points, CO2 saved) and their activity logs.
 * Exposes LiveData objects that HomeFragment observes to update the UI
 * whenever data changes.
 *
 * Role in design: Part of the ViewModel layer (MVVM pattern).
 * Sits between HomeFragment (View) and Firestore (Model).
 * Uses User.java and ActivityLog.java model classes.
 *
 * Outstanding issues: Time period filter (weekly/monthly) not yet
 * implemented — currently loads all logs for the current user.
 *
 * @author Maryam Ali
 */
package com.example.klimate;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.app.Application;
import android.content.Context;

import com.example.klimate.local.ActivityLogDao;
import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.example.klimate.local.UserEntity;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.example.klimate.model.ActivityLog;
import com.example.klimate.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardViewModel extends ViewModel {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private final MutableLiveData<User> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ActivityLog>> logsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> co2LiveData = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> categoryCountLiveData = new MutableLiveData<>();

    /**
     * Returns LiveData holding the current user's profile (name, streak, points).
     * HomeFragment observes this to update the greeting and stat cards.
     *
     * @return LiveData wrapping a User object
     */
    public LiveData<User> getUserLiveData() {
        return userLiveData;
    }

    /**
     * Returns LiveData holding the list of activity logs for the current user.
     * HomeFragment observes this to display activity counts.
     *
     * @return LiveData wrapping a list of ActivityLog objects
     */
    public LiveData<List<ActivityLog>> getLogsLiveData() {
        return logsLiveData;
    }

    /**
     * Returns LiveData holding the calculated total CO2 saved in kilograms.
     * Value is derived from the co2SavedKg value stored on each activity log.
     *
     * @return LiveData wrapping a Double representing kg of CO2 saved
     */
    public LiveData<Double> getCo2LiveData() {
        return co2LiveData;
    }

    /**
     * Returns LiveData holding a map of activity category names to log counts.
     * Used to display a breakdown of activities by category on the dashboard.
     *
     * @return LiveData wrapping a Map of category name to count
     */
    public LiveData<Map<String, Integer>> getCategoryCountLiveData() {
        return categoryCountLiveData;
    }

    /**
     * Loads the current user's profile document from Firestore.
     * Updates userLiveData on success so the UI can display
     * the real display name, streak, and points balance.
     */
    public void loadUserData(Context context) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        // ── Room first ───────────────────────────────────────────────────────
        executor.execute(() -> {
            UserEntity cached = AppDatabase.getInstance(context).userDao().getUserSync(uid);
            if (cached != null) {
                // Convert UserEntity → model User to keep LiveData type consistent
                com.example.klimate.model.User u = new com.example.klimate.model.User();
                u.setUid(cached.uid);
                u.setDisplayName(cached.displayName);
                u.setEmail(cached.email);
                u.setTotalPoints(cached.totalPoints);
                u.setStreakDays(cached.streakDays);
                u.setCo2SavedKg(cached.co2SavedKg);
                userLiveData.postValue(u);
            }

            // ── Firestore refresh ────────────────────────────────────────────
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        com.example.klimate.model.User user =
                                documentSnapshot.toObject(com.example.klimate.model.User.class);
                        userLiveData.setValue(user);

                        // Update Room cache
                        if (user != null) {
                            executor.execute(() -> {
                                UserEntity u = AppDatabase.getInstance(context)
                                        .userDao().getUserSync(uid);
                                if (u == null) {
                                    u = new UserEntity(uid, user.getDisplayName(),
                                            user.getEmail(), user.getUniversity(),
                                            user.getRole(), user.getTotalPoints(),
                                            user.getStreakDays(), user.getCo2SavedKg(), 0);
                                } else {
                                    u.totalPoints = user.getTotalPoints();
                                    u.streakDays  = user.getStreakDays();
                                    u.co2SavedKg  = user.getCo2SavedKg();
                                    u.displayName = user.getDisplayName();
                                }
                                AppDatabase.getInstance(context).userDao().update(u);
                            });
                        }
                    });
        });
    }

    /**
     * Loads all activity logs belonging to the current user from Firestore.
     * After loading, calculates CO2 savings and category counts and posts
     * results to their respective LiveData objects.
     */
    public void loadActivityLogs(Context context) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        // ── Room first ───────────────────────────────────────────────────────
        executor.execute(() -> {
            List<ActivityLogEntity> roomEntities =
                    AppDatabase.getInstance(context).activityLogDao().getLogsForUserSync(uid);

            if (!roomEntities.isEmpty()) {
                List<com.example.klimate.model.ActivityLog> roomLogs = new java.util.ArrayList<>();
                for (ActivityLogEntity e : roomEntities) {
                    com.example.klimate.model.ActivityLog log =
                            new com.example.klimate.model.ActivityLog();
                    log.setLogId(e.logId);
                    log.setUserId(e.userId);
                    log.setActivityType(e.activityType);
                    log.setStatus(e.status);
                    log.setPoints(e.points);
                    log.setCo2SavedKg(e.co2SavedKg);
                    roomLogs.add(log);
                }
                logsLiveData.postValue(roomLogs);
                calculateCo2(roomLogs);
                calculateCategoryCounts(roomLogs);
            }

            // ── Firestore refresh ────────────────────────────────────────────
            db.collection("activity_logs")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<com.example.klimate.model.ActivityLog> logs = new java.util.ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            com.example.klimate.model.ActivityLog log =
                                    doc.toObject(com.example.klimate.model.ActivityLog.class);
                            log.setLogId(doc.getId());
                            logs.add(log);
                        }
                        logsLiveData.setValue(logs);
                        calculateCo2(logs);
                        calculateCategoryCounts(logs);
                    });
        });
    }

    /**
     * Calculates the total estimated CO2 saved from a list of activity logs.
     * Uses the co2SavedKg value saved when each log was created.
     * Logs without a saved CO2 value contribute 0.0 kg.
     *
     * @param logs list of ActivityLog objects to calculate from
     */
    private void calculateCo2(List<ActivityLog> logs) {
        double total = 0.0;

        for (ActivityLog log : logs) {
            total += log.getCo2SavedKg();
        }

        co2LiveData.setValue(total);
    }

    /**
     * Counts how many logs exist per activity category.
     * Posts the resulting map to categoryCountLiveData.
     *
     * @param logs list of ActivityLog objects to count
     */
    private void calculateCategoryCounts(List<ActivityLog> logs) {
        Map<String, Integer> counts = new HashMap<>();
        for (ActivityLog log : logs) {
            String type = log.getActivityType();
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        categoryCountLiveData.setValue(counts);
    }
}
