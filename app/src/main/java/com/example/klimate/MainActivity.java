/**
 * MainActivity.java
 *
 * The primary host activity of the Klimate app. Manages the bottom
 * navigation bar and switches between the five main fragments:
 * HomeFragment, LogFragment, RankingsFragment, FriendsFragment,
 * and ProfileFragment.
 *
 * Also responsible for scheduling the daily streak notification
 * WorkManager task and requesting POST_NOTIFICATIONS permission
 * on Android 13+.
 *
 * Role in design: Part of the Navigation layer. Acts as the container
 * for all main screen fragments.
 *
 * Outstanding issues: none.
 *
 * @author Team Analog
 */
package com.example.klimate;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private Bundle pendingLogArguments;

    // Launcher for requesting POST_NOTIFICATIONS permission on Android 13+
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        // Permission granted or denied — worker is already enqueued either way
                        // Notifications will simply not appear if denied
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request notification permission on Android 13+
        requestNotificationPermissionIfNeeded();

        // Schedule the daily streak notification worker
        scheduleStreakNotificationWorker();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_log) {
                LogFragment logFragment = new LogFragment();
                if (pendingLogArguments != null) {
                    logFragment.setArguments(pendingLogArguments);
                    pendingLogArguments = null;
                }
                fragment = logFragment;
            } else if (id == R.id.nav_rankings) {
                fragment = new RankingsFragment();
            }  else if (id == R.id.nav_friends) {
            // Staff users see the Tips management screen here
                fragment = new FriendsFragment();
            } else if (id == R.id.nav_profile) {
                fragment = new ProfileFragment();
            } else {
                return false;
            }

            loadFragment(fragment);
            return true;
        });
    }

    /**
     * Loads a fragment into the main fragment container.
     *
     * @param fragment the fragment to display
     */
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * Schedules a daily PeriodicWorkRequest for StreakNotificationWorker.
     * Uses KEEP policy so re-launching the app does not duplicate the task.
     * Fires approximately every 24 hours — exact timing depends on OS scheduling.
     */
    private void scheduleStreakNotificationWorker() {
        PeriodicWorkRequest streakWorkRequest =
                new PeriodicWorkRequest.Builder(
                        StreakNotificationWorker.class,
                        24, TimeUnit.HOURS
                ).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "streak_check",
                ExistingPeriodicWorkPolicy.KEEP,
                streakWorkRequest
        );
    }

    /**
     * Requests POST_NOTIFICATIONS permission on Android 13 (API 33) and above.
     * On older versions the permission is granted automatically at install time.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    /**
     * Navigates to the Log tab with no pre-selected activity.
     */
    public void navigateToLog() {
        pendingLogArguments = null;
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_log);
    }

    /**
     * Navigates to the Log tab with a pre-selected activity and optional
     * verified flow and proof picker flags.
     *
     * @param activityName    the activity to pre-select
     * @param openVerifiedFlow whether to open the verified log tab automatically
     * @param openProofPicker  whether to open the image picker automatically
     */
    public void navigateToLog(String activityName, boolean openVerifiedFlow, boolean openProofPicker) {
        Bundle args = new Bundle();
        args.putString("preselected_activity", activityName);
        args.putBoolean("open_verified_flow", openVerifiedFlow);
        args.putBoolean("open_proof_picker", openProofPicker);
        pendingLogArguments = args;

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_log);
    }
    /**
     * Opens the Eco Picks screen from Home.
     */
    public void navigateToEcoPicks() {
        loadFragment(new EcoPicksFragment());
    }
    /**
     * Opens the staff challenge management screen.
     */
    public void navigateToStaffManage() {
        loadFragment(new StaffManageFragment());
    }
    /**
     * Opens the real-time progress screen for a joined team.
     *
     * @param teamId Firestore team document ID
     * @param challengeId Firestore challenge document ID
     * @param challengeName challenge display name
     * @param joinCode team join code
     * @param target target contribution count
     */
    public void navigateToTeamProgress(String teamId,
                                       String challengeId,
                                       String challengeName,
                                       String joinCode,
                                       int target) {
        loadFragment(TeamProgressFragment.newInstance(
                teamId,
                challengeId,
                challengeName,
                joinCode,
                target
        ));
    }
}