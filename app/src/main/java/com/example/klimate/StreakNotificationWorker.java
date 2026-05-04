package com.example.klimate;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.klimate.local.AppDatabase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class StreakNotificationWorker extends Worker {

    public StreakNotificationWorker(@NonNull Context context,
                                    @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            return Result.success();
        }

        String uid = currentUser.getUid();

        try {
            List<String> days = AppDatabase.getInstance(getApplicationContext())
                    .activityLogDao()
                    .getDistinctLogDays(uid);

            if (days == null || days.isEmpty()) {
                return Result.success();
            }

            String mostRecentDay = Collections.max(days);

            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            String todayKey = formatter.format(Calendar.getInstance().getTime());

            if (todayKey.equals(mostRecentDay)) {
                return Result.success();
            }

            java.util.Date today = formatter.parse(todayKey);
            java.util.Date lastLog = formatter.parse(mostRecentDay);

            if (today == null || lastLog == null) {
                return Result.success();
            }

            long diffMillis = today.getTime() - lastLog.getTime();
            int daysSinceLastLog = (int) (diffMillis / (24L * 60L * 60L * 1000L));

            /*
             * Streak is "about to break" when the user logged yesterday
             * but has not logged today yet.
             * If daysSinceLastLog > 1, the streak is already broken,
             * so this notification is no longer accurate.
             */
            if (daysSinceLastLog == 1) {
                NotificationHelper.showStreakRiskNotification(getApplicationContext());
            }

            return Result.success();

        } catch (Exception e) {
            return Result.retry();
        }
    }
}