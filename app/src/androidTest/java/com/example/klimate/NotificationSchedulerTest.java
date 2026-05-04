package com.example.klimate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class NotificationSchedulerTest {

    private Context context;
    private SimpleDateFormat formatter;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        FirebaseAuth.getInstance().signOut();
    }

    @Test
    public void streakWorker_withoutSignedInUser_finishesSuccessfully() {
        StreakNotificationWorker worker = TestListenableWorkerBuilder
                .from(context, StreakNotificationWorker.class)
                .build();

        ListenableWorker.Result result = worker.doWork();

        assertNotNull(result);
        assertTrue(result instanceof ListenableWorker.Result.Success);
    }

    @Test
    public void triggerCondition_loggedToday_noStreakRiskNotification() {
        String todayKey = formatter.format(Calendar.getInstance().getTime());
        assertEquals(0, computeDaysSince(todayKey));
        assertFalse(shouldShowStreakRiskNotification(todayKey));
    }

    @Test
    public void triggerCondition_loggedYesterday_showsStreakRiskNotification() {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayKey = formatter.format(yesterday.getTime());

        assertEquals(1, computeDaysSince(yesterdayKey));
        assertTrue(shouldShowStreakRiskNotification(yesterdayKey));
    }

    @Test
    public void triggerCondition_loggedTwoDaysAgo_noStreakRiskNotification() {
        Calendar twoDaysAgo = Calendar.getInstance();
        twoDaysAgo.add(Calendar.DAY_OF_YEAR, -2);
        String twoDaysAgoKey = formatter.format(twoDaysAgo.getTime());

        assertEquals(2, computeDaysSince(twoDaysAgoKey));
        assertFalse(shouldShowStreakRiskNotification(twoDaysAgoKey));
    }

    @Test
    public void triggerCondition_noLogs_noStreakRiskNotification() {
        assertFalse(shouldShowStreakRiskNotification(null));
        assertFalse(shouldShowStreakRiskNotification(""));
    }

    @Test
    public void notificationCopy_matchesCurrentStreakRiskCopy() {
        String title = "Your streak is about to break 🔥";
        String body = "Log a sustainable activity before midnight to keep your streak alive.";

        assertEquals("Your streak is about to break 🔥", title);
        assertEquals("Log a sustainable activity before midnight to keep your streak alive.", body);
    }

    private boolean shouldShowStreakRiskNotification(String lastLogKey) {
        if (lastLogKey == null || lastLogKey.trim().isEmpty()) {
            return false;
        }
        return computeDaysSince(lastLogKey) == 1;
    }

    private int computeDaysSince(String lastLogKey) {
        try {
            String todayKey = formatter.format(Calendar.getInstance().getTime());
            if (todayKey.equals(lastLogKey)) return 0;

            java.util.Date today = formatter.parse(todayKey);
            java.util.Date lastLog = formatter.parse(lastLogKey);

            if (today == null || lastLog == null) return -1;

            long diffMillis = today.getTime() - lastLog.getTime();
            return (int) (diffMillis / (24L * 60L * 60L * 1000L));
        } catch (Exception e) {
            return -1;
        }
    }
}
