package com.example.klimate;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class NotificationHelper {

    public static final String CHANNEL_ID = "klimate_notifications";
    private static final String CHANNEL_NAME = "Klimate Reminders";

    public static final int NOTIFICATION_ID_MIMI_HUNGRY = 2001;
    public static final int NOTIFICATION_ID_STREAK_RISK = 2002;
    public static final int NOTIFICATION_ID_BADGE_EARNED = 2003;

    public static final String WORK_MIMI_HUNGRY = "mimi_hungry_reminder";

    private NotificationHelper() {
        // Utility class.
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders about logging, streaks, Mimi, and badges");

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static boolean canShowNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static void showNotification(Context context, int notificationId, String title, String body) {
        createNotificationChannel(context);

        if (!canShowNotifications(context)) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    public static void showMimiHungryNotification(Context context) {
        showNotification(
                context,
                NOTIFICATION_ID_MIMI_HUNGRY,
                "Mimi is hungry 🐾",
                "Log a sustainable action to feed her!"
        );
    }

    public static void showStreakRiskNotification(Context context) {
        showNotification(
                context,
                NOTIFICATION_ID_STREAK_RISK,
                "Your streak is about to break 🔥",
                "Log a sustainable activity before midnight to keep your streak alive."
        );
    }

    public static void showBadgeEarnedNotification(Context context, String badgeName) {
        String safeBadgeName = badgeName != null && !badgeName.trim().isEmpty()
                ? badgeName
                : "a new badge";

        showNotification(
                context,
                NOTIFICATION_ID_BADGE_EARNED,
                "New badge earned 🏅",
                "You earned " + safeBadgeName + "!"
        );
    }

    public static void scheduleMimiHungryReminder(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MimiHungryWorker.class)
                .setInitialDelay(4, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_MIMI_HUNGRY,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    public static void cancelMimiHungryReminder(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_MIMI_HUNGRY);
    }
}