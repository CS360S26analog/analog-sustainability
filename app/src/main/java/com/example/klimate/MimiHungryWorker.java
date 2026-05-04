package com.example.klimate;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MimiHungryWorker extends Worker {

    private static final long HUNGRY_AFTER_HOURS = 4;

    public MimiHungryWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return Result.success();
        }

        String uid = user.getUid();

        try {
            List<ActivityLogEntity> logs = AppDatabase
                    .getInstance(getApplicationContext())
                    .activityLogDao()
                    .getLogsForUserSync(uid);

            if (logs == null || logs.isEmpty()) {
                NotificationHelper.showMimiHungryNotification(getApplicationContext());
                return Result.success();
            }

            long latestLogTimeMillis = 0L;

            for (ActivityLogEntity log : logs) {
                if (log != null && log.timestampMillis > latestLogTimeMillis) {
                    latestLogTimeMillis = log.timestampMillis;
                }
            }

            long now = System.currentTimeMillis();
            long hungryAfterMillis = TimeUnit.HOURS.toMillis(HUNGRY_AFTER_HOURS);

            if (latestLogTimeMillis <= 0L || now - latestLogTimeMillis >= hungryAfterMillis) {
                NotificationHelper.showMimiHungryNotification(getApplicationContext());
            }

            return Result.success();

        } catch (Exception e) {
            return Result.retry();
        }
    }
}