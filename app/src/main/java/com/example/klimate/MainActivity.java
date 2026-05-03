package com.example.klimate;

import android.Manifest;
import android.content.Context;
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
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.example.klimate.local.AppDatabase;
import com.google.firebase.firestore.FirebaseFirestoreSettings;


import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.PersistentCacheSettings;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private Bundle pendingLogArguments;
    private boolean isStaff = false;

    private ViewPager2 viewPager;
    private boolean isUpdatingBottomNavFromPager = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {}
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.view_pager);

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current != null) {
                showFragmentContainer();
            } else {
                showPager();
            }
        });

        requestNotificationPermissionIfNeeded();
        enableFirestoreOfflinePersistence();
        initRoomDatabase();
        scheduleStreakNotificationWorker();
        scheduleSyncWorker();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

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
                            setupStudentNavigation(bottomNav);
                        }
                    })
                    .addOnFailureListener(e -> setupStudentNavigation(bottomNav));
        } else {
            setupStudentNavigation(bottomNav);
        }
    }

    // ═══════════════════════════════════════════════════
    // STUDENT NAVIGATION
    // ═══════════════════════════════════════════════════

    private void setupStudentNavigation(BottomNavigationView bottomNav) {
        bottomNav.getMenu().clear();
        bottomNav.inflateMenu(R.menu.bottom_nav_menu);

        viewPager.setAdapter(new MainPagerAdapter(this));
        viewPager.setOffscreenPageLimit(2);
        viewPager.setCurrentItem(0, false);
        showPager();

        bottomNav.setOnItemSelectedListener(item -> {
            if (isUpdatingBottomNavFromPager) {
                return true;
            }

            int id = item.getItemId();

            if (id == R.id.nav_log && pendingLogArguments != null) {
                Bundle args = pendingLogArguments;
                pendingLogArguments = null;

                LogFragment logFragment = new LogFragment();
                logFragment.setArguments(args);
                loadFragment(logFragment);
                return true;
            }

            int position = getStudentPositionForNavId(id);
            if (position == -1) {
                return false;
            }

            showPager();
            viewPager.setCurrentItem(position, true);
            return true;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int navId = getStudentNavIdForPosition(position);
                if (navId == -1) return;

                isUpdatingBottomNavFromPager = true;
                bottomNav.setSelectedItemId(navId);
                isUpdatingBottomNavFromPager = false;
            }
        });

        bottomNav.setSelectedItemId(R.id.nav_home);
        showNavigationTutorialIfNeeded(bottomNav);
    }

    private int getStudentPositionForNavId(int id) {
        if (id == R.id.nav_home) {
            return 0; // Home
        } else if (id == R.id.nav_friends) {
            return 1; // Friends (Updated)
        } else if (id == R.id.nav_log) {
            return 2; // Log (Updated)
        } else if (id == R.id.nav_rankings) {
            return 3; // Rankings (Updated)
        } else if (id == R.id.nav_profile) {
            return 4; // Profile
        } else {
            return -1;
        }
    }

    private int getStudentNavIdForPosition(int position) {
        if (position == 0) {
            return R.id.nav_home;
        } else if (position == 1) {
            return R.id.nav_friends; // Updated
        } else if (position == 2) {
            return R.id.nav_log;     // Updated
        } else if (position == 3) {
            return R.id.nav_rankings; // Updated
        } else if (position == 4) {
            return R.id.nav_profile;
        } else {
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════
    // STAFF NAVIGATION
    // ═══════════════════════════════════════════════════

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
            } else if (id == R.id.staff_nav_feed) {
                // Staff see the Validation Feed — they can upvote/downvote
                fragment = new ValidationFeedFragment();
            } else if (id == R.id.staff_nav_profile) {
                fragment = new StaffProfileFragment();
            } else {
                return false;
            }

            loadFragment(fragment);
            return true;
        });

        showStaffTutorialIfNeeded(bottomNav);
    }

    private int getStaffPositionForNavId(int id) {
        if (id == R.id.staff_nav_overview) {
            return 0;
        } else if (id == R.id.staff_nav_manage) {
            return 1;
        } else if (id == R.id.staff_nav_reports) {
            return 2;
        } else if (id == R.id.staff_nav_feed) {
            return 3;
        } else if (id == R.id.staff_nav_profile) {
            return 4;
        } else {
            return -1;
        }
    }

    private int getStaffNavIdForPosition(int position) {
        if (position == 0) {
            return R.id.staff_nav_overview;
        } else if (position == 1) {
            return R.id.staff_nav_manage;
        } else if (position == 2) {
            return R.id.staff_nav_reports;
        } else if (position == 3) {
            return R.id.staff_nav_feed;
        } else if (position == 4) {
            return R.id.staff_nav_profile;
        } else {
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════
    // NAVIGATION TUTORIAL
    // ═══════════════════════════════════════════════════

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
                            public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {}

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
                                                bottomNav.findViewById(R.id.staff_nav_feed),
                                                "Step 4 of 5 — Validation Feed",
                                                "Review student-submitted verified logs here. Upvote genuine sustainable activities to award bonus points. Downvote suspicious or fake submissions — at -5 net votes a log is automatically flagged for your review in the Reports tab."
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
                            public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {}

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

    private void showPager() {
        if (viewPager != null) {
            viewPager.setVisibility(View.VISIBLE);
        }

        View fragmentContainer = findViewById(R.id.fragment_container);
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }
    }

    private void enableFirestoreOfflinePersistence() {
        FirebaseFirestoreSettings settings =
                new FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(
                                PersistentCacheSettings.newBuilder()
                                        .setSizeBytes(100*1024*1024)
                                        .build()
                        )
                        .build();

        FirebaseFirestore.getInstance().setFirestoreSettings(settings);
    }
    private void initRoomDatabase() {
        // Kick off the initialisation off the main thread
        new Thread(() ->
                AppDatabase.getInstance(getApplicationContext()),
                "room-init"
        ).start();
    }

    private void scheduleSyncWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
                com.example.klimate.SyncWorker.class,
                15, TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "klimate_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncWork
        );
    }

    public static void triggerImmediateSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        androidx.work.OneTimeWorkRequest syncNow =
                new androidx.work.OneTimeWorkRequest.Builder(com.example.klimate.SyncWorker.class)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(context).enqueue(syncNow);
    }

    private void showFragmentContainer() {
        if (viewPager != null) {
            viewPager.setVisibility(View.GONE);
        }

        View fragmentContainer = findViewById(R.id.fragment_container);
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
    }

    public void loadFragment(Fragment fragment) {
        showFragmentContainer();

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
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    public void navigateToLog() {
        pendingLogArguments = null;

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_log);
        }

        if (viewPager != null) {
            showPager();
            viewPager.setCurrentItem(1, true);
        }
    }

    public void navigateToLog(String activityName, boolean openVerifiedFlow, boolean openProofPicker) {
        Bundle args = new Bundle();
        args.putString("preselected_activity", activityName);
        args.putBoolean("open_verified_flow", openVerifiedFlow);
        args.putBoolean("open_proof_picker", openProofPicker);

        pendingLogArguments = args;

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_log);
        }

        if (pendingLogArguments != null) {
            Bundle logArgs = pendingLogArguments;
            pendingLogArguments = null;

            LogFragment logFragment = new LogFragment();
            logFragment.setArguments(logArgs);
            loadFragment(logFragment);
        }
    }

    public void navigateToEcoPicks() {
        loadFragment(new EcoPicksFragment());
    }

    public void navigateToStaffManage() {
        if (isStaff && viewPager != null) {
            BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.staff_nav_manage);
            }
            showPager();
            viewPager.setCurrentItem(1, true);
        } else {
            loadFragment(new StaffManageFragment());
        }
    }

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

    public void setBottomNavVisible(boolean visible) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }


    public void navigateToHome() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        if (isStaff) {
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.staff_nav_overview);
            }
            if (viewPager != null) {
                showPager();
                viewPager.setCurrentItem(0, true);
            }
        } else {
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_home);
            }
            if (viewPager != null) {
                showPager();
                viewPager.setCurrentItem(0, true);
            }
        }
    }
}