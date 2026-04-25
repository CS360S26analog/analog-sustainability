/**
 * StreakNotificationWorker.java
 *
 * A WorkManager Worker that runs daily to check whether the current user
 * has logged any activity today. If they haven't, it fires a push
 * notification warning them that their streak is at risk.
 *
 * If the user has gone 2 or more days without logging, the notification
 * uses Mimi the cat's hungry message to add extra motivation.
 *
 * Role in design: Part of the background service layer. Scheduled by
 * MainActivity as a daily PeriodicWorkRequest. Reads lastLogDate from
 * Firestore to determine whether a notification should fire.
 *
 * Outstanding issues: Notification is approximate — WorkManager periodic
 * tasks are not guaranteed to fire at exactly 8 PM due to OS scheduling.
 *
 * @author Haroon
 */
package com.example.klimate;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class StreakNotificationWorker extends Worker {

    private static final String CHANNEL_ID = "streak_alert";
    private static final String CHANNEL_NAME = "Streak Alerts";
    private static final int NOTIFICATION_ID = 1001;

    /**
     * Constructs the worker with the application context and parameters
     * passed in by WorkManager.
     *
     * @param context      the application context
     * @param workerParams parameters passed by WorkManager
     */
    public StreakNotificationWorker(@NonNull Context context,
                                    @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Called by WorkManager when the periodic task fires.
     * Checks how many days have passed since the user last logged
     * an activity, then fires a notification if they haven't logged today.
     *
     * @return Result.success() always — failure would cancel the periodic chain
     */
    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // No user logged in — nothing to notify
        if (currentUser == null) {
            return Result.success();
        }

        String uid = currentUser.getUid();

        // Use a latch to wait for Firestore result inside the synchronous Worker
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger daysSinceLastLog = new AtomicInteger(-1);

        FirebaseFirestore.getInstance()
                .collection("activity_logs")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // Never logged — treat as many days since last log
                        daysSinceLastLog.set(99);
                        latch.countDown();
                        return;
                    }

                    // Find the most recent log timestamp
                    Date mostRecent = null;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
                        if (ts != null) {
                            Date d = ts.toDate();
                            if (mostRecent == null || d.after(mostRecent)) {
                                mostRecent = d;
                            }
                        }
                    }

                    if (mostRecent == null) {
                        daysSinceLastLog.set(99);
                        latch.countDown();
                        return;
                    }

                    // Calculate how many days ago that was
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                    String todayKey = sdf.format(Calendar.getInstance().getTime());
                    String lastLogKey = sdf.format(mostRecent);

                    if (todayKey.equals(lastLogKey)) {
                        // Already logged today — no notification needed
                        daysSinceLastLog.set(0);
                    } else {
                        // Count the days difference
                        try {
                            Date today = sdf.parse(todayKey);
                            Date lastLog = sdf.parse(lastLogKey);
                            if (today != null && lastLog != null) {
                                long diffMillis = today.getTime() - lastLog.getTime();
                                int days = (int) (diffMillis / (24L * 60L * 60L * 1000L));
                                daysSinceLastLog.set(days);
                            }
                        } catch (Exception ex) {
                            daysSinceLastLog.set(1);
                        }
                    }

                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    daysSinceLastLog.set(-1);
                    latch.countDown();
                });

        // Wait up to 10 seconds for Firestore to respond
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return Result.success();
        }

        int days = daysSinceLastLog.get();

        // Fire notification if they haven't logged today
        if (days > 0) {
            sendStreakNotification(days);
        }

        return Result.success();
    }

    /**
     * Builds and fires the streak risk notification.
     * Uses the Mimi cat message if the user has been inactive for 2+ days.
     *
     * @param daysSinceLastLog how many days have passed since the last log
     */
    private void sendStreakNotification(int daysSinceLastLog) {
        Context context = getApplicationContext();

        createNotificationChannel(context);

        String title = "Your streak is at risk! 🔥";
        String body;

        if (daysSinceLastLog >= 2) {
            body = "Meow... 😿 Mimi is hungry! Log now to feed her and save your streak";
        } else {
            body = "Log a sustainable activity before midnight to keep your streak 🌿";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify();
        }
    }

    /**
     * Creates the notification channel required on Android 8.0 and above.
     * Safe to call multiple times — the system ignores it if already created.
     *
     * @param context the application context
     */
    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminds you to log before your streak breaks");

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}