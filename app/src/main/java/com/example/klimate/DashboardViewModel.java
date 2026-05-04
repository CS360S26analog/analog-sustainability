/**
 * DashboardViewModel.java
 *
 * ViewModel for the home dashboard screen (HomeFragment).
 *
 * Room-first dashboard loading:
 * 1. Read cached user/profile stats from Room immediately.
 * 2. Read cached activity logs from Room immediately.
 * 3. Refresh both from Firestore.
 * 4. Write the fresh Firestore result back into Room for the next app open.
 *
 * Role in design: Part of the ViewModel layer (MVVM pattern).
 * Sits between HomeFragment (View), Room cache, and Firestore.
 *
 * @author Maryam Ali
 */
package com.example.klimate;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.example.klimate.local.UserEntity;
import com.example.klimate.model.ActivityLog;
import com.example.klimate.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DashboardViewModel extends ViewModel {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final Executor executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<User> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ActivityLog>> logsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> co2LiveData = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> categoryCountLiveData = new MutableLiveData<>();

    /**
     * Returns LiveData holding the current user's profile.
     *
     * @return LiveData wrapping a User object
     */
    public LiveData<User> getUserLiveData() {
        return userLiveData;
    }

    /**
     * Returns LiveData holding the list of activity logs for the current user.
     *
     * @return LiveData wrapping a list of ActivityLog objects
     */
    public LiveData<List<ActivityLog>> getLogsLiveData() {
        return logsLiveData;
    }

    /**
     * Returns LiveData holding total CO2 saved in kilograms.
     *
     * @return LiveData wrapping total kg of CO2 saved
     */
    public LiveData<Double> getCo2LiveData() {
        return co2LiveData;
    }

    /**
     * Returns LiveData holding activity type counts.
     *
     * @return LiveData wrapping a Map of activity type to count
     */
    public LiveData<Map<String, Integer>> getCategoryCountLiveData() {
        return categoryCountLiveData;
    }

    /**
     * Loads the current user's profile using Room first, then Firestore.
     * The cached Room value appears immediately. Firestore then becomes
     * the source of truth and updates Room.
     *
     * @param context any valid Context; applicationContext is used internally
     */
    public void loadUserData(Context context) {
        String uid = getCurrentUid();
        if (uid == null || context == null) return;

        Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            AppDatabase localDb = AppDatabase.getInstance(appContext);

            // ── 1. Room first: instant cached dashboard profile ──────────────
            UserEntity cached = localDb.userDao().getUserSync(uid);
            if (cached != null) {
                userLiveData.postValue(toUserModel(cached));
            }

            // ── 2. Firestore refresh: authoritative latest profile ───────────
            db.collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (!documentSnapshot.exists()) return;

                        User user = documentSnapshot.toObject(User.class);
                        if (user == null) return;

                        user.setUid(uid);
                        userLiveData.postValue(user);

                        // ── 3. Update Room cache for next app launch ─────────
                        executor.execute(() -> {
                            UserEntity existing = localDb.userDao().getUserSync(uid);

                            if (existing == null) {
                                UserEntity newUser = new UserEntity(
                                        uid,
                                        safeString(user.getDisplayName()),
                                        safeString(user.getEmail()),
                                        safeString(user.getUniversity()),
                                        safeString(user.getRole()),
                                        user.getTotalPoints(),
                                        user.getStreakDays(),
                                        user.getCo2SavedKg(),
                                        0
                                );
                                localDb.userDao().insert(newUser);
                                localDb.userDao().markSynced(uid);
                            } else {
                                existing.displayName = safeString(user.getDisplayName());
                                existing.email = safeString(user.getEmail());
                                existing.university = safeString(user.getUniversity());
                                existing.role = safeString(user.getRole());
                                existing.totalPoints = user.getTotalPoints();
                                existing.streakDays = user.getStreakDays();
                                existing.co2SavedKg = user.getCo2SavedKg();
                                localDb.userDao().update(existing);
                                localDb.userDao().markSynced(uid);
                            }
                        });
                    });
        });
    }

    /**
     * Loads the current user's activity logs using Room first, then Firestore.
     * The cached logs appear immediately. Firestore then refreshes the list,
     * recalculates dashboard summaries, and updates Room.
     *
     * @param context any valid Context; applicationContext is used internally
     */
    public void loadActivityLogs(Context context) {
        String uid = getCurrentUid();
        if (uid == null || context == null) return;

        Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            AppDatabase localDb = AppDatabase.getInstance(appContext);

            // ── 1. Room first: instant cached logs ───────────────────────────
            List<ActivityLogEntity> cachedEntities =
                    localDb.activityLogDao().getLogsForUserSync(uid);

            if (cachedEntities != null && !cachedEntities.isEmpty()) {
                List<ActivityLog> cachedLogs = new ArrayList<>();

                for (ActivityLogEntity entity : cachedEntities) {
                    cachedLogs.add(toActivityLogModel(entity));
                }

                publishLogsAndSummaries(cachedLogs);
            } else {
                publishLogsAndSummaries(new ArrayList<>());
            }

            // ── 2. Firestore refresh: authoritative latest logs ──────────────
            db.collection("activity_logs")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<ActivityLog> firestoreLogs = new ArrayList<>();
                        List<ActivityLogEntity> roomEntitiesToCache = new ArrayList<>();

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            ActivityLog log = doc.toObject(ActivityLog.class);
                            log.setLogId(doc.getId());

                            // Some older docs may be missing userId in the object.
                            if (log.getUserId() == null || log.getUserId().trim().isEmpty()) {
                                log.setUserId(uid);
                            }

                            firestoreLogs.add(log);
                            roomEntitiesToCache.add(toActivityLogEntity(doc, uid));
                        }

                        publishLogsAndSummaries(firestoreLogs);

                        // ── 3. Update Room cache for next app launch ─────────
                        executor.execute(() -> {
                            for (ActivityLogEntity entity : roomEntitiesToCache) {
                                long localId = localDb.activityLogDao().insert(entity);
                                localDb.activityLogDao().markSynced((int) localId, entity.logId, "synced");
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        // Keep the Room data already published above.
                        // If Room was empty, the UI will simply remain empty.
                    });
        });
    }

    /**
     * Convenience method for HomeFragment when it wants to refresh everything.
     *
     * @param context any valid Context
     */
    public void refreshDashboard(Context context) {
        loadUserData(context);
        loadActivityLogs(context);
    }

    /**
     * Posts logs plus all derived dashboard summaries.
     * Uses postValue so it is safe from both Room background threads
     * and Firestore callbacks.
     *
     * @param logs list of dashboard activity logs
     */
    private void publishLogsAndSummaries(List<ActivityLog> logs) {
        if (logs == null) {
            logs = new ArrayList<>();
        }

        logsLiveData.postValue(logs);
        co2LiveData.postValue(calculateCo2(logs));
        categoryCountLiveData.postValue(calculateCategoryCounts(logs));
    }

    /**
     * Converts a Room user row into the app's User model.
     *
     * @param entity cached Room user
     * @return User model for LiveData
     */
    private User toUserModel(UserEntity entity) {
        User user = new User();
        user.setUid(entity.uid);
        user.setDisplayName(entity.displayName);
        user.setEmail(entity.email);
        user.setUniversity(entity.university);
        user.setRole(entity.role);
        user.setTotalPoints(entity.totalPoints);
        user.setStreakDays(entity.streakDays);
        user.setCo2SavedKg(entity.co2SavedKg);
        return user;
    }

    /**
     * Converts a Room activity log row into the app's ActivityLog model.
     *
     * @param entity cached Room activity log
     * @return ActivityLog model for LiveData
     */
    private ActivityLog toActivityLogModel(ActivityLogEntity entity) {
        ActivityLog log = new ActivityLog();
        log.setLogId(entity.logId);
        log.setUserId(entity.userId);
        log.setActivityType(entity.activityType);
        log.setStatus(entity.status);
        log.setPoints(entity.points);
        log.setCo2SavedKg(entity.co2SavedKg);
        return log;
    }

    /**
     * Converts a Firestore activity log document into a Room entity.
     * This keeps the Room cache aligned with Firestore after each refresh.
     *
     * Expected ActivityLogEntity constructor, based on the current local model:
     * ActivityLogEntity(logId, userId, activityType, status, points, quantity,
     *                   co2SavedKg, proofUrl, localProofPath, timestampMillis)
     *
     * @param doc Firestore activity log document
     * @param fallbackUid current authenticated user's uid
     * @return ActivityLogEntity ready to insert into Room
     */
    private ActivityLogEntity toActivityLogEntity(QueryDocumentSnapshot doc, String fallbackUid) {
        String logId = doc.getId();

        String userId = doc.getString("userId");
        if (userId == null || userId.trim().isEmpty()) {
            userId = fallbackUid;
        }

        String activityType = safeString(doc.getString("activityType"));
        String status = safeString(doc.getString("status"));

        Long pointsLong = doc.getLong("points");
        int points = pointsLong != null ? pointsLong.intValue() : 0;

        Long quantityLong = doc.getLong("quantity");
        int quantity = quantityLong != null ? quantityLong.intValue() : 1;

        Double co2 = doc.getDouble("co2SavedKg");
        double co2SavedKg = co2 != null ? co2 : 0.0;

        String proofUrl = safeString(doc.getString("proofUrl"));

        Timestamp timestamp = doc.getTimestamp("timestamp");
        long timestampMillis = timestamp != null
                ? timestamp.toDate().getTime()
                : System.currentTimeMillis();

        return new ActivityLogEntity(
                logId,
                userId,
                activityType,
                status,
                points,
                quantity,
                co2SavedKg,
                proofUrl,
                null,
                timestampMillis
        );
    }

    /**
     * Calculates total CO2 saved from loaded logs.
     *
     * @param logs list of ActivityLog objects
     * @return total CO2 saved in kg
     */
    private double calculateCo2(List<ActivityLog> logs) {
        double total = 0.0;

        for (ActivityLog log : logs) {
            if (log != null) {
                total += log.getCo2SavedKg();
            }
        }

        return total;
    }

    /**
     * Counts how many logs exist for each activity type.
     *
     * @param logs list of ActivityLog objects
     * @return map of activity type to count
     */
    private Map<String, Integer> calculateCategoryCounts(List<ActivityLog> logs) {
        Map<String, Integer> counts = new HashMap<>();

        for (ActivityLog log : logs) {
            if (log == null) continue;

            String type = log.getActivityType();
            if (type == null || type.trim().isEmpty()) continue;

            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }

        return counts;
    }

    /**
     * Gets the current Firebase user's uid safely.
     *
     * @return uid, or null if signed out
     */
    private String getCurrentUid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    /**
     * Converts null strings to empty strings for Room-safe writes.
     *
     * @param value nullable string
     * @return non-null string
     */
    private String safeString(String value) {
        return value != null ? value : "";
    }
}
