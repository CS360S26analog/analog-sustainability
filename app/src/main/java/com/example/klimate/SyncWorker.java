package com.example.klimate;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.klimate.local.ActivityLogDao;
import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.example.klimate.local.UserDao;
import com.example.klimate.local.UserEntity;
import com.example.klimate.local.VoteDao;
import com.example.klimate.local.VoteEntity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SyncWorker.java
 *
 * WorkManager Worker that flushes locally-queued (pending/failed) Room records
 * to Firestore. Called:
 *   (a) On a periodic schedule (every 15 min) — enqueued from MainActivity.
 *   (b) Immediately after a write if the device was offline — enqueued from
 *       LogFragment / ValidationFeedFragment via WorkManager.OneTimeWorkRequest.
 *
 * Sync order: ActivityLogs → Votes → dirty UserEntity stats
 *
 * Notifications:
 *   • Verified log sync failure  → user-visible notification (important action)
 *   • Vote sync failure          → user-visible notification
 *   • Quick log failure          → silent retry only (not shown to user)
 *
 * Place in: com/example/klimate/
 *
 * Enqueue from MainActivity (periodic):
 *   PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
 *       SyncWorker.class, 15, TimeUnit.MINUTES).build();
 *   WorkManager.getInstance(this).enqueueUniquePeriodicWork(
 *       "klimate_sync", ExistingPeriodicWorkPolicy.KEEP, syncWork);
 *
 * Enqueue one-time after a write:
 *   WorkManager.getInstance(context)
 *       .enqueue(new OneTimeWorkRequest.Builder(SyncWorker.class).build());
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    // Notification channels
    private static final String CHANNEL_SYNC    = "sync_status";
    private static final String CHANNEL_VOTES   = "vote_status";

    // Notification IDs
    private static final int NOTIF_VERIFIED_LOG_FAILED = 2001;
    private static final int NOTIF_VOTE_FAILED         = 2002;

    // Firestore write timeout per record
    private static final long WRITE_TIMEOUT_SEC = 10;

    private AppDatabase db;
    private ActivityLogDao logDao;
    private UserDao userDao;
    private VoteDao voteDao;
    private FirebaseFirestore firestore;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        db       = AppDatabase.getInstance(context);
        logDao   = db.activityLogDao();
        userDao  = db.userDao();
        voteDao  = db.voteDao();
        firestore = FirebaseFirestore.getInstance();

        createNotificationChannels(context);

        boolean allSucceeded = true;

        allSucceeded &= syncPendingLogs(context);
        allSucceeded &= syncPendingVotes(context);
        syncDirtyUsers();   // best-effort, does not affect retry decision

        return allSucceeded ? Result.success() : Result.retry();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACTIVITY LOGS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pushes every pending/failed ActivityLogEntity to Firestore.
     *
     * @return true if all syncs succeeded (or there was nothing to sync)
     */
    private boolean syncPendingLogs(Context context) {
        List<ActivityLogEntity> pending = logDao.getPendingLogs();

        if (pending.isEmpty()) return true;

        Log.d(TAG, "Syncing " + pending.size() + " pending activity logs");

        boolean allOk = true;

        for (ActivityLogEntity entity : pending) {
            boolean ok = pushLogToFirestore(entity, context);
            if (!ok) allOk = false;
        }

        return allOk;
    }

    /**
     * Writes a single ActivityLogEntity to Firestore.
     * On success: marks entity as synced in Room, increments user CO2 in Firestore.
     * On failure: marks entity as failed in Room; if it was a verified log, fires a notification.
     *
     * @param entity  the local log to push
     * @param context application context (for notifications)
     * @return true if the Firestore write succeeded
     */
    private boolean pushLogToFirestore(ActivityLogEntity entity, Context context) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        // Build Firestore document — use the logId already generated locally
        String firestoreDocId = entity.logId.isEmpty()
                ? firestore.collection("activity_logs").document().getId()
                : entity.logId;

        Map<String, Object> data = new HashMap<>();
        data.put("logId",        firestoreDocId);
        data.put("userId",       entity.userId);
        data.put("activityType", entity.activityType);
        data.put("status",       entity.status);
        data.put("points",       entity.points);
        data.put("bonusPoints",  entity.bonusPoints);
        data.put("voteCount",    entity.voteCount);
        data.put("quantity",     entity.quantity);
        data.put("co2SavedKg",   entity.co2SavedKg);
        data.put("unit",         entity.unit);
        data.put("proofUrl",     entity.proofUrl);
        data.put("timestamp",    new Timestamp(new Date(entity.timestampMillis)));

        firestore.collection("activity_logs")
                .document(firestoreDocId)
                .set(data)
                .addOnSuccessListener(unused -> {
                    // Mark synced in Room
                    logDao.markSynced(entity.localId, firestoreDocId, "synced");

                    // Increment user CO2 in Firestore (idempotent if already done, but safe)
                    if (!entity.userId.isEmpty() && entity.co2SavedKg > 0) {
                        firestore.collection("users")
                                .document(entity.userId)
                                .update("co2SavedKg", FieldValue.increment(entity.co2SavedKg));
                    }

                    // Award base points via Firestore (safe to call again — points already in
                    // Room; this keeps Firestore in sync)
                    if (!entity.userId.isEmpty() && entity.points > 0) {
                        firestore.collection("users")
                                .document(entity.userId)
                                .update("totalPoints", FieldValue.increment(entity.points));
                    }

                    Log.d(TAG, "Log synced: " + firestoreDocId);
                    success.set(true);
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Log sync failed: " + firestoreDocId, e);
                    logDao.markFailed(entity.localId);

                    // Only notify the user if it was a verified log — they took the time to
                    // attach proof and deserve to know it hasn't gone through yet.
                    boolean isVerified = "pending_verification".equals(entity.status);
                    if (isVerified && entity.syncAttempts >= 2) {
                        notifyVerifiedLogFailed(context, entity.activityType);
                    }

                    success.set(false);
                    latch.countDown();
                });

        try {
            latch.await(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Log sync interrupted", e);
            return false;
        }

        return success.get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VOTES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pushes every pending/failed VoteEntity to Firestore.
     *
     * @return true if all syncs succeeded
     */
    private boolean syncPendingVotes(Context context) {
        List<VoteEntity> pending = voteDao.getPendingVotes();

        if (pending.isEmpty()) return true;

        Log.d(TAG, "Syncing " + pending.size() + " pending votes");

        boolean allOk = true;

        for (VoteEntity entity : pending) {
            boolean ok = pushVoteToFirestore(entity, context);
            if (!ok) allOk = false;
        }

        return allOk;
    }

    /**
     * Writes a single VoteEntity to Firestore.
     * On failure after 2+ attempts, fires a notification so the user knows
     * their vote didn't register.
     */
    private boolean pushVoteToFirestore(VoteEntity entity, Context context) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        String firestoreVoteId = entity.voteId.isEmpty()
                ? firestore.collection("votes").document().getId()
                : entity.voteId;

        Map<String, Object> data = new HashMap<>();
        data.put("voteId",    firestoreVoteId);
        data.put("logId",     entity.logId);
        data.put("voterId",   entity.voterId);
        data.put("isUpvote",  entity.isUpvote);
        data.put("timestamp", new Timestamp(new Date(entity.timestampMillis)));

        firestore.collection("votes")
                .document(firestoreVoteId)
                .set(data)
                .addOnSuccessListener(unused -> {
                    voteDao.markSynced(entity.localId, firestoreVoteId, "synced");

                    // Update the log's voteCount in Firestore (increment by ±1)
                    if (!entity.logId.isEmpty()) {
                        // voteCount tracks upvotes only for the feed display
                        if (entity.isUpvote) {
                            firestore.collection("activity_logs")
                                    .document(entity.logId)
                                    .update("voteCount", FieldValue.increment(1));
                        }
                    }

                    Log.d(TAG, "Vote synced: " + firestoreVoteId);
                    success.set(true);
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Vote sync failed: " + firestoreVoteId, e);
                    voteDao.markFailed(entity.localId);

                    // Votes are explicit user actions — notify if persistently failing
                    if (entity.syncAttempts >= 2) {
                        notifyVoteFailed(context);
                    }

                    success.set(false);
                    latch.countDown();
                });

        try {
            latch.await(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Vote sync interrupted", e);
            return false;
        }

        return success.get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // USER STATS (dirty push)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pushes any locally-mutated user stats back to Firestore.
     * This is best-effort — failures here are silently retried next cycle.
     * No user notification is shown for this.
     */
    private void syncDirtyUsers() {
        // We don't have a getAllDirtyUsers() query defined, but we can look up
        // the currently signed-in user. If they have dirty status, push their stats.
        // Extend this if you need multi-account support.

        com.google.firebase.auth.FirebaseUser firebaseUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

        if (firebaseUser == null) return;

        String uid = firebaseUser.getUid();
        UserEntity user = userDao.getUserSync(uid);

        if (user == null || !"dirty".equals(user.syncStatus)) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("totalPoints", user.totalPoints);
        updates.put("co2SavedKg",  user.co2SavedKg);
        updates.put("streakDays",  user.streakDays);

        firestore.collection("users")
                .document(uid)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    userDao.markSynced(uid);
                    Log.d(TAG, "User stats synced for: " + uid);
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "User stats sync failed for: " + uid, e)
                );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fires a notification when a verified log has persistently failed to sync.
     * The user attached proof and submitted — they need to know it didn't reach the server.
     *
     * @param context      application context
     * @param activityType the activity type for the failed log
     */
    private void notifyVerifiedLogFailed(Context context, String activityType) {
        String title = "Verified log not sent yet";
        String body  = "Your \"" + activityType + "\" verified log is waiting to be uploaded. "
                + "Open Klimate when you have a connection to retry.";

        fireNotification(context, CHANNEL_SYNC, NOTIF_VERIFIED_LOG_FAILED, title, body);
    }

    /**
     * Fires a notification when a vote has persistently failed to sync.
     * Votes are explicit user actions and the user expects them to register.
     *
     * @param context application context
     */
    private void notifyVoteFailed(Context context) {
        String title = "Vote not submitted yet";
        String body  = "One of your votes on the community feed is still pending. "
                + "Open Klimate when you have a connection.";

        fireNotification(context, CHANNEL_VOTES, NOTIF_VOTE_FAILED, title, body);
    }

    private void fireNotification(Context context,
                                  String channelId,
                                  int notifId,
                                  String title,
                                  String body) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(notifId, builder.build());
        }
    }

    /**
     * Creates notification channels required on Android 8.0+.
     * Safe to call multiple times.
     */
    private void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        NotificationChannel syncChannel = new NotificationChannel(
                CHANNEL_SYNC,
                "Sync Status",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        syncChannel.setDescription("Alerts when verified logs fail to upload");

        NotificationChannel voteChannel = new NotificationChannel(
                CHANNEL_VOTES,
                "Vote Status",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        voteChannel.setDescription("Alerts when community votes fail to upload");

        manager.createNotificationChannel(syncChannel);
        manager.createNotificationChannel(voteChannel);
    }
}