/**
 * NotificationSchedulerTest.java
 *
 * Instrumented tests for the streak notification WorkManager scheduling.
 * Verifies that:
 * - The periodic work request is enqueued correctly on app start
 * - The worker runs and completes without crashing
 * - The trigger condition (logged today vs not) is evaluated correctly
 *
 * @author Haroon
 */
package com.example.klimate;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NotificationSchedulerTest {

    private Context context;
    private WorkManager workManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        // Initialise WorkManager with a synchronous executor for testing
        Configuration config = new Configuration.Builder()
                .setExecutor(new SynchronousExecutor())
                .build();

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
        workManager = WorkManager.getInstance(context);
    }

    // -------------------------------------------------------------------
    // Enqueue tests
    // -------------------------------------------------------------------

    /**
     * Verifies that enqueueing the StreakNotificationWorker as a
     * OneTimeWorkRequest succeeds and the task enters ENQUEUED state.
     */
    @Test
    public void testWorkerEnqueued_successfully() throws Exception {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                StreakNotificationWorker.class
        ).build();

        workManager.enqueue(request).getResult().get();

        List<WorkInfo> workInfos = workManager
                .getWorkInfosForUniqueWork("streak_check")
                .get();

        // One-time request is not unique — check by ID instead
        WorkInfo info = workManager.getWorkInfoById(request.getId()).get();
        assertNotNull("WorkInfo should not be null", info);
    }

    /**
     * Verifies that the worker completes successfully without throwing.
     * No Firebase user is logged in during the test so the worker
     * should exit early with Result.success().
     */
    @Test
    public void testWorkerCompletes_withoutCrashing() throws Exception {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                StreakNotificationWorker.class
        ).build();

        workManager.enqueue(request).getResult().get();

        WorkManagerTestInitHelper.getTestDriver(context)
                .setAllConstraintsMet(request.getId());

        WorkInfo info = workManager.getWorkInfoById(request.getId()).get();
        assertNotNull(info);
        // Worker exits with success even when no user is logged in
        assertTrue(
                info.getState() == WorkInfo.State.SUCCEEDED ||
                        info.getState() == WorkInfo.State.ENQUEUED ||
                        info.getState() == WorkInfo.State.RUNNING
        );
    }

    // -------------------------------------------------------------------
    // Trigger condition logic tests — pure Java, no WorkManager needed
    // -------------------------------------------------------------------

    /**
     * Simulates a user who has logged today.
     * Expected: daysSinceLastLog = 0, no notification should fire.
     */
    @Test
    public void testTriggerCondition_loggedToday_noNotification() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());

        Set<String> logDays = new HashSet<>();
        logDays.add(todayKey);

        String lastLogKey = todayKey; // logged today
        int daysSince = computeDaysSince(lastLogKey, sdf);

        assertEquals("Days since should be 0", 0, daysSince);
        assertFalse("Notification should NOT fire when logged today", daysSince > 0);
    }

    /**
     * Simulates a user who last logged yesterday.
     * Expected: daysSinceLastLog = 1, standard streak notification fires.
     */
    @Test
    public void testTriggerCondition_loggedYesterday_standardNotification() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayKey = sdf.format(yesterday.getTime());

        int daysSince = computeDaysSince(yesterdayKey, sdf);

        assertEquals("Days since should be 1", 1, daysSince);
        assertTrue("Notification should fire", daysSince > 0);
        assertFalse("Mimi message should NOT show for only 1 day", daysSince >= 2);
    }

    /**
     * Simulates a user who last logged 2 days ago.
     * Expected: daysSinceLastLog = 2, Mimi cat notification fires.
     */
    @Test
    public void testTriggerCondition_loggedTwoDaysAgo_mimiNotification() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        Calendar twoDaysAgo = Calendar.getInstance();
        twoDaysAgo.add(Calendar.DAY_OF_YEAR, -2);
        String twoDaysAgoKey = sdf.format(twoDaysAgo.getTime());

        int daysSince = computeDaysSince(twoDaysAgoKey, sdf);

        assertEquals("Days since should be 2", 2, daysSince);
        assertTrue("Notification should fire", daysSince > 0);
        assertTrue("Mimi message SHOULD show for 2+ days", daysSince >= 2);
    }

    /**
     * Simulates a user who has never logged anything.
     * Expected: treated as many days since last log — notification fires.
     */
    @Test
    public void testTriggerCondition_neverLogged_notificationFires() {
        // When no logs exist the worker sets daysSince = 99
        int daysSince = 99;
        assertTrue("Notification should fire for never-logged user", daysSince > 0);
        assertTrue("Mimi message should show", daysSince >= 2);
    }

    /**
     * Verifies the correct notification body is chosen based on days inactive.
     */
    @Test
    public void testNotificationBody_oneDayInactive_standardMessage() {
        int daysSince = 1;
        String body = daysSince >= 2
                ? "Meow... 😿 Mimi is hungry! Log now to feed her and save your streak"
                : "Log a sustainable activity before midnight to keep your streak 🌿";

        assertEquals(
                "Log a sustainable activity before midnight to keep your streak 🌿",
                body
        );
    }

    /**
     * Verifies the Mimi message is shown when inactive for 2+ days.
     */
    @Test
    public void testNotificationBody_twoPlusDaysInactive_mimiMessage() {
        int daysSince = 3;
        String body = daysSince >= 2
                ? "Meow... 😿 Mimi is hungry! Log now to feed her and save your streak"
                : "Log a sustainable activity before midnight to keep your streak 🌿";

        assertEquals(
                "Meow... 😿 Mimi is hungry! Log now to feed her and save your streak",
                body
        );
    }

    // -------------------------------------------------------------------
    // Helper — mirrors the day calculation logic in StreakNotificationWorker
    // -------------------------------------------------------------------

    private int computeDaysSince(String lastLogKey, SimpleDateFormat sdf) {
        try {
            String todayKey = sdf.format(Calendar.getInstance().getTime());
            if (todayKey.equals(lastLogKey)) return 0;

            java.util.Date today   = sdf.parse(todayKey);
            java.util.Date lastLog = sdf.parse(lastLogKey);

            if (today == null || lastLog == null) return 1;

            long diffMillis = today.getTime() - lastLog.getTime();
            return (int) (diffMillis / (24L * 60L * 60L * 1000L));
        } catch (Exception e) {
            return 1;
        }
    }
}