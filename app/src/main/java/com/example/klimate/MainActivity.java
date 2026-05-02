/**
 * MainActivity.java
 *
 * The primary host activity of the Klimate app. Manages the bottom
 * navigation bar and switches between the five main fragments.
 *
 * On launch, checks the current user's role from Firestore:
 * - "staff" → loads the staff navigation (Overview, Manage, Reports,
 *   Community, Profile) with admin-only screens
 * - "student" (default) → loads the student navigation (Home, Log,
 *   Rankings, Friends, Profile)
 *
 * Also schedules the daily streak notification WorkManager task
 * and requests POST_NOTIFICATIONS permission on Android 13+.
 *
 * Role in design: Part of the Navigation layer. Acts as the container
 * for all main screen fragments.
 *
 * Outstanding issues: None.
 *
 * @author Team Analog
 */
package com.example.klimate;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private Bundle pendingLogArguments;
    private boolean isStaff = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {}
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestNotificationPermissionIfNeeded();
        scheduleStreakNotificationWorker();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        // Check role, then set up the correct navigation
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        String role = doc.getString("role");
                        isStaff = "staff".equals(role);

                        if (isStaff) {
                            setupStaffNavigation(bottomNav);
                        } else {
                            setupStudentNavigation(bottomNav, savedInstanceState);
                        }
                    })
                    .addOnFailureListener(e ->
                            // Default to student if role check fails
                            setupStudentNavigation(bottomNav, savedInstanceState)
                    );
        } else {
            setupStudentNavigation(bottomNav, savedInstanceState);
        }
    }

    // ═══════════════════════════════════════════════════
    // STUDENT NAVIGATION
    // ═══════════════════════════════════════════════════

    /**
     * Sets up the student bottom navigation with 5 student-specific tabs.
     * Shows the Home screen and the navigation tutorial on first launch.
     */
    private void setupStudentNavigation(BottomNavigationView bottomNav,
                                        Bundle savedInstanceState) {
        bottomNav.getMenu().clear();
        bottomNav.inflateMenu(R.menu.bottom_nav_menu);

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
            } else if (id == R.id.nav_friends) {
                fragment = new FriendsFragment();
            } else if (id == R.id.nav_profile) {
                fragment = new ProfileFragment();
            } else {
                return false;
            }

            loadFragment(fragment);
            return true;
        });

        // Show tutorial on first launch
        showNavigationTutorialIfNeeded(bottomNav);
    }

    // ═══════════════════════════════════════════════════
    // STAFF NAVIGATION
    // ═══════════════════════════════════════════════════

    /**
     * Sets up the staff bottom navigation with 5 admin-specific tabs.
     * Staff cannot access student screens (Log, Leaderboard, etc.).
     */
    private void setupStaffNavigation(BottomNavigationView bottomNav) {
        bottomNav.getMenu().clear();
        bottomNav.inflateMenu(R.menu.staff_nav_menu);
        loadFragment(new StaffOverviewFragment());

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();

            if (id == R.id.staff_nav_overview) {
                fragment = new StaffOverviewFragment();
            } else if (id == R.id.staff_nav_manage) {
                fragment = new StaffManageFragment();
            } else if (id == R.id.staff_nav_reports) {
                fragment = new StaffReportsFragment();
            } else if (id == R.id.staff_nav_community) {
                fragment = new StaffTipsFragment();
            } else if (id == R.id.staff_nav_profile) {
                fragment = new ProfileFragment();
            } else {
                return false;
            }

            loadFragment(fragment);
            return true;
        });

        // Show staff-specific tutorial on first staff login
        showStaffTutorialIfNeeded(bottomNav);
    }

    // ═══════════════════════════════════════════════════
    // NAVIGATION TUTORIAL (EXTRA-3)
    // ═══════════════════════════════════════════════════

    /**
     * Shows a detailed TapTargetView navigation tutorial on first launch.
     * Explains each tab with a meaningful description so new students
     * understand the app immediately without any guesswork.
     * Marked done in SharedPreferences so it only ever shows once.
     *
     * @param bottomNav the BottomNavigationView to highlight
     */
    private void showNavigationTutorialIfNeeded(BottomNavigationView bottomNav) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("tutorial_shown", false)) return;

        bottomNav.postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;

            try {
                new TapTargetSequence(this)
                        .targets(
                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.nav_log),
                                                "Step 1 of 5 — Log Activities",
                                                "This is your most-used screen. Tap here to log a sustainable activity like cycling, recycling, or choosing a plant-based meal. Quick Log takes 5 seconds. Verified Log lets you attach a proof photo for bonus points from the community."
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13),

                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.nav_home),
                                                "Step 2 of 5 — Your Dashboard",
                                                "Your personal home screen shows your current streak, total CO₂ saved, points earned, and your monthly challenge progress. The campus impact card shows how much CO₂ all Klimate users have saved together — in real time."
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13),

                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.nav_rankings),
                                                "Step 3 of 5 — Rankings & Challenges",
                                                "See where you rank on the campus leaderboard. Browse active sustainability challenges and join one to compete with other students as a team. Your nemesis — the person just above and just below you — is highlighted so you always know who to chase."
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13),

                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.nav_friends),
                                                "Step 4 of 5 — Community Feed",
                                                "Browse the community feed to see recent activity from other students. Switch to the Tips tab to read sustainability advice published by LUMS staff. Use Challenges to discover and join active team challenges directly from the feed."
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13),

                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.nav_profile),
                                                "Step 5 of 5 — Your Profile",
                                                "View your badges (earned and locked), redeem your points for real campus rewards like free coffee or a bike voucher, and track your total CO₂ impact. The more you log, the more badges you unlock and the more rewards you can redeem. You're all set — happy logging! 🌿"
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13)
                        )
                        .listener(new TapTargetSequence.Listener() {
                            @Override
                            public void onSequenceFinish() {
                                prefs.edit().putBoolean("tutorial_shown", true).apply();
                            }

                            @Override
                            public void onSequenceStep(TapTarget lastTarget,
                                                       boolean targetClicked) {}

                            @Override
                            public void onSequenceCanceled(TapTarget lastTarget) {
                                prefs.edit().putBoolean("tutorial_shown", true).apply();
                            }
                        })
                        .start();

            } catch (Exception e) {
                prefs.edit().putBoolean("tutorial_shown", true).apply();
            }
        }, 800);
    }

    /**
     * Shows a staff-specific TapTargetView tutorial on first staff login.
     * Uses a separate SharedPreferences flag 'staff_tutorial_shown' so
     * staff and student tutorials are tracked independently.
     *
     * @param bottomNav the BottomNavigationView to highlight
     */
    private void showStaffTutorialIfNeeded(BottomNavigationView bottomNav) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("staff_tutorial_shown", false)) return;

        bottomNav.postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;

            try {
                new TapTargetSequence(this)
                        .targets(
                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.staff_nav_overview),
                                                "Step 1 of 5 — Campus Overview",
                                                "Your admin dashboard. See live campus stats: total CO₂ saved, active students, running challenges, and any flagged submissions that need your attention."
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13),

                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.staff_nav_manage),
                                                "Step 2 of 5 — Manage Challenges",
                                                "Create and configure campus sustainability challenges here. Set a name, description, start and end dates, a target, and choose which activity types count toward this challenge. Students will see it immediately after you publish."
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13),

                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.staff_nav_reports),
                                                "Step 3 of 5 — Reports & Flagged",
                                                "View the full student leaderboard with detailed stats. Switch to the Flagged tab to review submissions that received 5 or more downvotes — you can approve or reject each one directly from here."
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13),

                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.staff_nav_community),
                                                "Step 4 of 5 — Community & Tips",
                                                "Publish sustainability tips that appear in the student community feed. Write a title, body, and choose a category. You can edit or delete your published tips at any time. Students see these under the Tips tab in their feed."
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13),

                                TapTarget.forView(
                                                bottomNav.findViewById(R.id.staff_nav_profile),
                                                "Step 5 of 5 — Staff Profile",
                                                "View your admin stats: how many challenges you've created, tips published, and submissions reviewed. Manage your account settings and notification preferences here. You're all set — welcome to the Klimate staff panel! 🌿"
                                        ).outerCircleColor(R.color.color_green_header)
                                        .targetCircleColor(android.R.color.white)
                                        .titleTextColor(android.R.color.white)
                                        .descriptionTextColor(android.R.color.white)
                                        .cancelable(false)
                                        .tintTarget(true)
                                        .titleTextSize(18)
                                        .descriptionTextSize(13)
                        )
                        .listener(new TapTargetSequence.Listener() {
                            @Override
                            public void onSequenceFinish() {
                                prefs.edit().putBoolean("staff_tutorial_shown", true).apply();
                            }

                            @Override
                            public void onSequenceStep(TapTarget lastTarget,
                                                       boolean targetClicked) {}

                            @Override
                            public void onSequenceCanceled(TapTarget lastTarget) {
                                prefs.edit().putBoolean("staff_tutorial_shown", true).apply();
                            }
                        })
                        .start();

            } catch (Exception e) {
                prefs.edit().putBoolean("staff_tutorial_shown", true).apply();
            }
        }, 800);
    }

    // ═══════════════════════════════════════════════════
    // SHARED HELPERS
    // ═══════════════════════════════════════════════════

    /**
     * Loads a fragment into the main fragment container.
     *
     * @param fragment the fragment to display
     */
    public void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void scheduleStreakNotificationWorker() {
        PeriodicWorkRequest streakWorkRequest =
                new PeriodicWorkRequest.Builder(
                        StreakNotificationWorker.class, 24, TimeUnit.HOURS
                ).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "streak_check",
                ExistingPeriodicWorkPolicy.KEEP,
                streakWorkRequest
        );
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    public void navigateToLog() {
        pendingLogArguments = null;
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_log);
    }

    public void navigateToLog(String activityName, boolean openVerifiedFlow,
                              boolean openProofPicker) {
        Bundle args = new Bundle();
        args.putString("preselected_activity", activityName);
        args.putBoolean("open_verified_flow", openVerifiedFlow);
        args.putBoolean("open_proof_picker", openProofPicker);
        pendingLogArguments = args;
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_log);
    }

    public void navigateToEcoPicks() { loadFragment(new EcoPicksFragment()); }

    public void navigateToStaffManage() { loadFragment(new StaffManageFragment()); }

    public void navigateToTeamProgress(String teamId, String challengeId,
                                       String challengeName, String joinCode,
                                       int target) {
        loadFragment(TeamProgressFragment.newInstance(
                teamId, challengeId, challengeName, joinCode, target));
    }

    public void setBottomNavVisible(boolean visible) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void navigateToHome() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (isStaff) {
            bottomNav.setSelectedItemId(R.id.staff_nav_overview);
        } else {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }
}