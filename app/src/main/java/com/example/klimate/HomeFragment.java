/**
 * HomeFragment.java
 *
 * The main dashboard screen of the Klimate app. Displays the current
 * user's greeting, streak, CO2 saved, points, activity cards, and
 * monthly challenge progress.
 *
 * Role in design: Part of the View layer (MVVM). Uses a Room-first,
 * Firestore-refresh pattern for offline support. Observes Firestore
 * in real time to keep dashboard stats current. Navigates to
 * LogFragment via the cycling and recycling action cards, and to
 * HistoryFragment via the activity history card.
 *
 * Outstanding issues: multiple monthly challenge rings are supported by
 * DoubleProgressRingView, but HomeFragment currently sends one default
 * monthly challenge progress value. Time period filter (weekly/monthly)
 * not yet connected to UI.
 *
 * @author Maryam Ali
 */
package com.example.klimate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.example.klimate.local.UserEntity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private FirebaseFirestore db;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private FirebaseUser currentUser;

    // ── V2: Impact ring UI references ────────────────────────────────────────
    private DoubleProgressRingView progressRingMonthly;
    private TextView tvStatsCurrentStreak;

    // ── V2: Impact ring state ────────────────────────────────────────────────
    private float impactCo2SavedKg = 0f;
    private float impactPersonalGoalKg = 0f;
    private float impactChallengeProgress = 0f;
    private boolean impactHasMonthlyChallenge = true;

    // ── V2: Callback for monthly CO₂ calculation ─────────────────────────────
    private interface Co2ResultCallback {
        void onResult(double monthlyCo2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // ── Header ────────────────────────────────────────────────────────────
        TextView tvGreetingMessage        = view.findViewById(R.id.tv_greeting_message);
        TextView tvUserName               = view.findViewById(R.id.tv_user_name);
        TextView tvProfileInitial         = view.findViewById(R.id.tv_profile_initial);
        ImageView ivProfilePhoto          = view.findViewById(R.id.iv_profile_photo);
        FrameLayout profileAvatarContainer = view.findViewById(R.id.profile_avatar_container);

        // ── Streak bar ────────────────────────────────────────────────────────
        TextView tvStreakNumber    = view.findViewById(R.id.tv_streak_number);
        TextView tvStreakMessage   = view.findViewById(R.id.tv_streak_message);
        TextView tvStreakPercent   = view.findViewById(R.id.tv_streak_percent);
        ProgressBar progressStreak = view.findViewById(R.id.progress_streak);
        TextView tvBottomStreakDays = view.findViewById(R.id.tv_bottom_streak_days);

        // ── V2: Impact ring + stats streak label ──────────────────────────────
        tvStatsCurrentStreak = view.findViewById(R.id.tv_stats_current_streak);
        progressRingMonthly  = view.findViewById(R.id.progress_ring_monthly);

        // ── Activity cards ────────────────────────────────────────────────────
        TextView tvRecent1Icon     = view.findViewById(R.id.tv_recent_1_icon);
        TextView tvRecent1Title    = view.findViewById(R.id.tv_recent_1_title);
        TextView tvRecent1Subtitle = view.findViewById(R.id.tv_recent_1_subtitle);
        TextView tvRecent2Icon     = view.findViewById(R.id.tv_recent_2_icon);
        TextView tvRecent2Title    = view.findViewById(R.id.tv_recent_2_title);
        TextView tvRecent2Subtitle = view.findViewById(R.id.tv_recent_2_subtitle);

        TextView btnRecent1 = view.findViewById(R.id.btn_recent_1);
        TextView btnRecent2 = view.findViewById(R.id.btn_recent_2);
        View cardRecent1    = view.findViewById(R.id.card_recent_1);
        View cardRecent2    = view.findViewById(R.id.card_recent_2);

        // ── V2: "See more logs" link ──────────────────────────────────────────
        TextView tvSeeMoreLogs = view.findViewById(R.id.tv_see_more_logs);

        // ── Monthly challenge ─────────────────────────────────────────────────
        TextView tvChallengeHelp     = view.findViewById(R.id.tv_challenge_help);
        TextView tvSetGoal           = view.findViewById(R.id.tv_set_goal);
        TextView btnChallengeInfo    = view.findViewById(R.id.btn_challenge_info);
        TextView tvChallengeTitle    = view.findViewById(R.id.tv_challenge_title);
        ProgressBar progressChallenge = view.findViewById(R.id.progress_challenge);
        TextView tvChallengeDays     = view.findViewById(R.id.tv_challenge_days);
        TextView tvChallengePercent  = view.findViewById(R.id.tv_challenge_percent);

        // ── Action buttons (role-gated below) ─────────────────────────────────
        TextView btnShare                 = view.findViewById(R.id.btn_share_progress);
        TextView btnBrowseMoreChallenges  = view.findViewById(R.id.btn_browse_more_challenges);
        TextView btnStaffManageChallenges = view.findViewById(R.id.btn_staff_manage_challenges);
        TextView btnStaffTips             = view.findViewById(R.id.btn_staff_tips);

        // ── Static click listeners ────────────────────────────────────────────
        setRandomGreeting(tvGreetingMessage);

        if (profileAvatarContainer != null) {
            profileAvatarContainer.setOnClickListener(v -> navigateToProfile());
        }

        if (tvSeeMoreLogs != null) {
            tvSeeMoreLogs.setOnClickListener(v -> navigateToLog());
        }

        if (btnRecent1 != null) {
            btnRecent1.setOnClickListener(v ->
                    showHomeLogChoiceDialog(tvRecent1Title.getText().toString()));
        }
        if (btnRecent2 != null) {
            btnRecent2.setOnClickListener(v ->
                    showHomeLogChoiceDialog(tvRecent2Title.getText().toString()));
        }
        if (cardRecent1 != null) {
            cardRecent1.setOnClickListener(v ->
                    showHomeLogChoiceDialog(tvRecent1Title.getText().toString()));
        }
        if (cardRecent2 != null) {
            cardRecent2.setOnClickListener(v ->
                    showHomeLogChoiceDialog(tvRecent2Title.getText().toString()));
        }

        if (tvChallengeHelp != null) tvChallengeHelp.setOnClickListener(v -> showMonthlyChallengesHelp());
        if (tvSetGoal != null)        tvSetGoal.setOnClickListener(v -> showSetGoalDialog());
        if (btnChallengeInfo != null) btnChallengeInfo.setOnClickListener(v -> showZeroWasteInfo());

        // ── EcoPicks widget – supports both V1 button id and V2 card id ───────
        View ecoPicksWidget = view.findViewById(R.id.card_ecopicks_widget);
        if (ecoPicksWidget != null) {
            ecoPicksWidget.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToEcoPicks();
                }
            });
        }
//        TextView btnEcoPicks = view.findViewById(R.id.card_ecopicks_widget);
//        if (btnEcoPicks != null) {
//            btnEcoPicks.setOnClickListener(v -> {
//                if (getActivity() instanceof MainActivity) {
//                    ((MainActivity) getActivity()).navigateToEcoPicks();
//                }
//            });
//        }

        // ── Role-gated buttons ────────────────────────────────────────────────
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(roleDoc -> {
                        if (!isAdded()) return;

                        String role = roleDoc.getString("role");
                        boolean isStaffUser = "staff".equals(role);

                        if (isStaffUser) {
                            // Staff: hide student-only buttons, show staff buttons
                            if (btnShare != null)                btnShare.setVisibility(View.GONE);
                            if (btnBrowseMoreChallenges != null) btnBrowseMoreChallenges.setVisibility(View.GONE);

                            if (btnStaffManageChallenges != null) {
                                btnStaffManageChallenges.setVisibility(View.VISIBLE);
                                btnStaffManageChallenges.setOnClickListener(v -> {
                                    if (getActivity() instanceof MainActivity) {
                                        ((MainActivity) getActivity()).navigateToStaffManage();
                                    }
                                });
                            }
                            if (btnStaffTips != null) {
                                btnStaffTips.setVisibility(View.VISIBLE);
                                btnStaffTips.setOnClickListener(v -> navigateToStaffTips());
                            }
                        } else {
                            // Student: show student-only buttons, hide staff buttons
                            if (btnShare != null) {
                                btnShare.setVisibility(View.VISIBLE);
                                btnShare.setOnClickListener(v -> shareProgress());
                            }
                            if (btnBrowseMoreChallenges != null) {
                                btnBrowseMoreChallenges.setVisibility(View.VISIBLE);
                                btnBrowseMoreChallenges.setOnClickListener(v -> navigateToFeedExplore());
                            }
                            if (btnStaffManageChallenges != null) btnStaffManageChallenges.setVisibility(View.GONE);
                            if (btnStaffTips != null)             btnStaffTips.setVisibility(View.GONE);
                        }
                    });
        }

        // ── Default / placeholder state before data loads ─────────────────────
        setDefaultDashboard(
                tvStreakNumber, tvStreakMessage, tvStreakPercent, progressStreak,
                tvBottomStreakDays,
                tvRecent1Icon, tvRecent1Title, tvRecent1Subtitle,
                tvRecent2Icon, tvRecent2Title, tvRecent2Subtitle,
                tvChallengeTitle, progressChallenge, tvChallengeDays, tvChallengePercent
        );

        if (tvStatsCurrentStreak != null) tvStatsCurrentStreak.setText("0");
        refreshImpactRing();

        // ── Live data load ────────────────────────────────────────────────────
        if (currentUser != null) {
            loadUserHeader(tvUserName, tvProfileInitial, ivProfilePhoto);
            loadUserStreakFromLogs(tvStreakNumber, tvStreakMessage, tvStreakPercent,
                    progressStreak, tvBottomStreakDays);
            loadActivityCards(tvRecent1Icon, tvRecent1Title, tvRecent1Subtitle,
                    tvRecent2Icon, tvRecent2Title, tvRecent2Subtitle);
            loadMonthlyChallenge(tvChallengeTitle, progressChallenge, tvChallengeDays, tvChallengePercent);
            loadOptionalStats(view);
            loadCampusImpact(view);
            loadGoalStatus(view);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        View view = getView();
        if (view == null || currentUser == null) return;

        // Re-bind ring refs in case the view was recreated
        progressRingMonthly  = view.findViewById(R.id.progress_ring_monthly);
        tvStatsCurrentStreak = view.findViewById(R.id.tv_stats_current_streak);

        TextView tvUserName        = view.findViewById(R.id.tv_user_name);
        TextView tvProfileInitial  = view.findViewById(R.id.tv_profile_initial);
        ImageView ivProfilePhoto   = view.findViewById(R.id.iv_profile_photo);

        TextView tvStreakNumber    = view.findViewById(R.id.tv_streak_number);
        TextView tvStreakMessage   = view.findViewById(R.id.tv_streak_message);
        TextView tvStreakPercent   = view.findViewById(R.id.tv_streak_percent);
        ProgressBar progressStreak = view.findViewById(R.id.progress_streak);
        TextView tvBottomStreakDays = view.findViewById(R.id.tv_bottom_streak_days);

        TextView tvRecent1Icon     = view.findViewById(R.id.tv_recent_1_icon);
        TextView tvRecent1Title    = view.findViewById(R.id.tv_recent_1_title);
        TextView tvRecent1Subtitle = view.findViewById(R.id.tv_recent_1_subtitle);
        TextView tvRecent2Icon     = view.findViewById(R.id.tv_recent_2_icon);
        TextView tvRecent2Title    = view.findViewById(R.id.tv_recent_2_title);
        TextView tvRecent2Subtitle = view.findViewById(R.id.tv_recent_2_subtitle);

        TextView tvChallengeTitle    = view.findViewById(R.id.tv_challenge_title);
        ProgressBar progressChallenge = view.findViewById(R.id.progress_challenge);
        TextView tvChallengeDays     = view.findViewById(R.id.tv_challenge_days);
        TextView tvChallengePercent  = view.findViewById(R.id.tv_challenge_percent);

        loadUserHeader(tvUserName, tvProfileInitial, ivProfilePhoto);
        loadUserStreakFromLogs(tvStreakNumber, tvStreakMessage, tvStreakPercent,
                progressStreak, tvBottomStreakDays);
        loadActivityCards(tvRecent1Icon, tvRecent1Title, tvRecent1Subtitle,
                tvRecent2Icon, tvRecent2Title, tvRecent2Subtitle);
        loadMonthlyChallenge(tvChallengeTitle, progressChallenge, tvChallengeDays, tvChallengePercent);
        loadOptionalStats(view);
        loadCampusImpact(view);
        loadGoalStatus(view);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data loaders (Room-first → Firestore refresh)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads points and monthly CO₂ stats.
     * Room gives instant display; Firestore updates ring state fields and
     * kicks off the month-scoped CO₂ calculation.
     */
    private void loadOptionalStats(View view) {
        String uid = currentUser.getUid();

        TextView tvPoints   = view.findViewById(R.id.tv_points_value);
        TextView tvCo2Saved = view.findViewById(R.id.tv_co2_saved);

        if (tvPoints == null && tvCo2Saved == null && progressRingMonthly == null) return;

        // ── Room first (instant) ─────────────────────────────────────────────
        executor.execute(() -> {
            AppDatabase localDb = AppDatabase.getInstance(requireContext());
            int    roomPoints = localDb.activityLogDao().getTotalPointsForUser(uid);
            double roomCo2    = localDb.activityLogDao().getTotalCo2ForUser(uid);

            if (isAdded()) requireActivity().runOnUiThread(() -> {
                if (tvPoints != null) tvPoints.setText(formatWholeNumber(roomPoints));
                // Room gives all-time CO₂; monthly value is refined below
                if (tvCo2Saved != null)
                    tvCo2Saved.setText(String.format(Locale.getDefault(), "%.1f kg", roomCo2));
            });

            // ── Firestore refresh ────────────────────────────────────────────
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(document -> {
                        if (!document.exists() || !isAdded()) return;

                        Long totalPoints             = document.getLong("totalPoints");
                        Double monthlyGoalKg         = document.getDouble("monthlyGoalKg");
                        Boolean joinedMonthlyChallenge = document.getBoolean("joinedMonthlyChallenge");
                        Long streakDays              = document.getLong("streakDays");
                        Double co2SavedKg            = document.getDouble("co2SavedKg");

                        long pts   = totalPoints != null ? totalPoints : 0L;
                        double co2 = co2SavedKg  != null ? co2SavedKg  : 0.0;

                        // Update Room cache
                        executor.execute(() ->
                                localDb.userDao().updateStats(
                                        uid, (int) pts, co2,
                                        (int)(streakDays != null ? streakDays : 0))
                        );

                        if (tvPoints != null) tvPoints.setText(formatWholeNumber(pts));

                        // Resolve personal goal (Firestore wins over SharedPreferences)
                        if (monthlyGoalKg != null && monthlyGoalKg > 0) {
                            impactPersonalGoalKg = monthlyGoalKg.floatValue();
                        } else if (getContext() != null) {
                            SharedPreferences prefs =
                                    PreferenceManager.getDefaultSharedPreferences(requireContext());
                            impactPersonalGoalKg = prefs.getFloat("monthly_goal_kg", 0f);
                        }

                        impactHasMonthlyChallenge =
                                joinedMonthlyChallenge != null ? joinedMonthlyChallenge : true;

                        // Now fetch month-scoped CO₂ for the ring and the co2 label
                        loadCurrentMonthCo2ForDashboard(view, tvCo2Saved);
                    });
        });
    }

    /**
     * Calculates the current month's CO₂ saved and updates the CO₂ label,
     * the equivalent string, and the impact ring.
     */
    private void loadCurrentMonthCo2ForDashboard(View view, TextView tvCo2Saved) {
        calculateCurrentMonthCo2(monthlyCo2 -> {
            if (!isAdded()) return;

            impactCo2SavedKg = (float) monthlyCo2;

            if (tvCo2Saved != null) {
                tvCo2Saved.setText(String.format(Locale.getDefault(), "%.1f kg", monthlyCo2));
            }

            TextView tvEquiv = view.findViewById(R.id.tv_co2_equivalent);
            if (tvEquiv != null) {
                tvEquiv.setText(monthlyCo2 > 0
                        ? CarbonEquivalentHelper.buildEquivalentString(monthlyCo2)
                        : "");
            }

            refreshImpactRing();
        });
    }

    /**
     * Queries activity_logs for the current month and sums CO₂ saved.
     * Falls back to per-activity estimates when co2SavedKg is absent on a doc.
     */
    private void calculateCurrentMonthCo2(Co2ResultCallback callback) {
        long monthStartMillis = getStartOfCurrentMonthMillis();

        db.collection("activity_logs")
                .whereEqualTo("userId", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double monthlyCo2 = 0.0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Timestamp ts = doc.getTimestamp("timestamp");
                        if (ts == null || ts.toDate().getTime() < monthStartMillis) continue;

                        Double saved = doc.getDouble("co2SavedKg");
                        monthlyCo2 += saved != null
                                ? saved
                                : getCo2SavedForActivity(doc.getString("activityType"));
                    }
                    if (callback != null) callback.onResult(monthlyCo2);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onResult(0.0);
                });
    }

    /**
     * Loads the user's display name and avatar initial.
     * Room gives an instant result; Firestore refreshes and updates the cache.
     */
    private void loadUserHeader(TextView tvUserName, TextView tvProfileInitial, ImageView ivProfilePhoto) {
        String uid = currentUser.getUid();

        executor.execute(() -> {
            // ── Room first: instant initials/name ───────────────────────────────
            UserEntity cached = AppDatabase.getInstance(requireContext())
                    .userDao().getUserSync(uid);

            if (cached != null && cached.displayName != null && !cached.displayName.isEmpty()) {
                String first = cached.displayName.trim().split("\\s+")[0];

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        tvUserName.setText(first + "!");
                        tvProfileInitial.setText(
                                String.valueOf(Character.toUpperCase(first.charAt(0)))
                        );

                        // Room does not currently store the Base64 profile photo,
                        // so keep initials visible until Firestore refreshes.
                        updateHomeAvatar(tvProfileInitial, ivProfilePhoto, null);
                    });
                }
            }

            // ── Firestore refresh: name + saved avatar photo ─────────────────────
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(document -> {
                        if (!document.exists() || !isAdded()) return;

                        String displayName = document.getString("displayName");
                        String profilePhotoBase64 = document.getString("profilePhotoBase64");

                        if (displayName != null && !displayName.trim().isEmpty()) {
                            String cleanName = displayName.trim();
                            String firstName = cleanName.split("\\s+")[0];

                            executor.execute(() -> {
                                UserEntity u = AppDatabase.getInstance(requireContext())
                                        .userDao().getUserSync(uid);

                                if (u != null) {
                                    u.displayName = displayName;
                                    AppDatabase.getInstance(requireContext()).userDao().update(u);
                                }
                            });

                            tvUserName.setText(firstName + "!");
                            tvProfileInitial.setText(
                                    String.valueOf(Character.toUpperCase(firstName.charAt(0)))
                            );
                        }

                        updateHomeAvatar(tvProfileInitial, ivProfilePhoto, profilePhotoBase64);
                    });
        });
    }

    private void updateHomeAvatar(TextView tvProfileInitial, ImageView ivProfilePhoto, String profilePhotoBase64) {
        if (tvProfileInitial == null || ivProfilePhoto == null) return;

        if (profilePhotoBase64 == null || profilePhotoBase64.trim().isEmpty()) {
            ivProfilePhoto.setVisibility(View.GONE);
            tvProfileInitial.setVisibility(View.VISIBLE);
            return;
        }

        try {
            byte[] imageBytes = Base64.decode(profilePhotoBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            if (bitmap != null) {
                RoundedBitmapDrawable roundedDrawable =
                        RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                roundedDrawable.setCircular(true);

                ivProfilePhoto.setImageDrawable(roundedDrawable);
                ivProfilePhoto.setVisibility(View.VISIBLE);
                tvProfileInitial.setVisibility(View.GONE);
            } else {
                ivProfilePhoto.setVisibility(View.GONE);
                tvProfileInitial.setVisibility(View.VISIBLE);
            }

        } catch (Exception e) {
            ivProfilePhoto.setVisibility(View.GONE);
            tvProfileInitial.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Calculates and displays the current and best activity streaks.
     * Room gives an instant result; Firestore refreshes with the authoritative set.
     * Also updates tvStatsCurrentStreak (V2 ring panel label).
     */
    private void loadUserStreakFromLogs(TextView tvStreakNumber,
                                        TextView tvStreakMessage,
                                        TextView tvStreakPercent,
                                        ProgressBar progressStreak,
                                        TextView tvBottomStreakDays) {
        String uid = currentUser.getUid();

        executor.execute(() -> {
            // ── Room first ───────────────────────────────────────────────────
            List<String> roomDays = AppDatabase.getInstance(requireContext())
                    .activityLogDao().getDistinctLogDays(uid);

            Set<String> uniqueDays = new HashSet<>(roomDays);
            int currentStreak = calculateCurrentStreakFromDays(uniqueDays);
            int bestStreak    = calculateBestStreakFromDays(uniqueDays);

            if (isAdded()) requireActivity().runOnUiThread(() ->
                    applyStreakToUI(currentStreak, bestStreak,
                            tvStreakNumber, tvStreakMessage, tvStreakPercent,
                            progressStreak, tvBottomStreakDays)
            );

            // ── Firestore refresh ────────────────────────────────────────────
            db.collection("activity_logs")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!isAdded()) return;
                        Set<String> firestoreDays = new HashSet<>();
                        SimpleDateFormat fmt =
                                new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Timestamp ts = doc.getTimestamp("timestamp");
                            if (ts != null) firestoreDays.add(fmt.format(ts.toDate()));
                        }
                        int cs = calculateCurrentStreakFromDays(firestoreDays);
                        int bs = calculateBestStreakFromDays(firestoreDays);
                        requireActivity().runOnUiThread(() ->
                                applyStreakToUI(cs, bs,
                                        tvStreakNumber, tvStreakMessage, tvStreakPercent,
                                        progressStreak, tvBottomStreakDays)
                        );
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            tvStreakNumber.setText("0");
                            tvBottomStreakDays.setText("0");
                            if (tvStatsCurrentStreak != null) tvStatsCurrentStreak.setText("0");
                            tvStreakMessage.setText("Couldn't load streak");
                            tvStreakPercent.setText("0%");
                            progressStreak.setMax(7);
                            progressStreak.setProgress(0);
                        });
                    });
        });
    }

    private void applyStreakToUI(int currentStreak, int bestStreak,
                                 TextView tvStreakNumber, TextView tvStreakMessage,
                                 TextView tvStreakPercent, ProgressBar progressStreak,
                                 TextView tvBottomStreakDays) {
        tvStreakNumber.setText(String.valueOf(currentStreak));
        tvBottomStreakDays.setText(formatWholeNumber(bestStreak));

        if (tvStatsCurrentStreak != null) {
            tvStatsCurrentStreak.setText(formatWholeNumber(currentStreak));
        }

        if (currentStreak <= 0) {
            tvStreakMessage.setText("Log to start your streak");
            tvStreakPercent.setText("0%");
            progressStreak.setMax(7);
            progressStreak.setProgress(0);
        } else {
            tvStreakMessage.setText("You're on a " + currentStreak + "-day streak");
            progressStreak.setMax(7);
            int progress = Math.min(currentStreak, 7);
            progressStreak.setProgress(progress);
            tvStreakPercent.setText((int) ((progress / 7.0) * 100) + "%");
        }
    }

    /**
     * Populates the two activity shortcut cards.
     * Room gives an instant result; Firestore refreshes with improved logic:
     * Card 1 = most frequent activity, Card 2 = most recent different activity.
     * New users see two random suggestions.
     */
    private void loadActivityCards(TextView tvRecent1Icon, TextView tvRecent1Title,
                                   TextView tvRecent1Subtitle, TextView tvRecent2Icon,
                                   TextView tvRecent2Title, TextView tvRecent2Subtitle) {
        String uid = currentUser.getUid();

        executor.execute(() -> {
            // ── Room first ───────────────────────────────────────────────────
            List<ActivityLogEntity> roomLogs =
                    AppDatabase.getInstance(requireContext())
                            .activityLogDao().getLogsForUserSync(uid);

            if (!roomLogs.isEmpty() && isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    List<String> types = new ArrayList<>();
                    for (ActivityLogEntity e : roomLogs) types.add(e.activityType);
                    bindActivityCards(types,
                            tvRecent1Icon, tvRecent1Title, tvRecent1Subtitle,
                            tvRecent2Icon, tvRecent2Title, tvRecent2Subtitle);
                });
            }

            // ── Firestore refresh ────────────────────────────────────────────
            db.collection("activity_logs")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!isAdded()) return;

                        List<QueryDocumentSnapshot> docs = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) docs.add(doc);

                        // New user: show two random suggested activities
                        if (docs.isEmpty()) {
                            List<String> suggestions = getRandomSuggestedActivities(2);
                            String first  = suggestions.get(0);
                            String second = suggestions.get(1);
                            requireActivity().runOnUiThread(() -> {
                                tvRecent1Icon.setText(getActivityEmoji(first));
                                tvRecent1Title.setText(first);
                                tvRecent1Subtitle.setText("");
                                tvRecent2Icon.setText(getActivityEmoji(second));
                                tvRecent2Title.setText(second);
                                tvRecent2Subtitle.setText("");
                            });
                            return;
                        }

                        // Sort newest first
                        docs.sort((a, b) -> {
                            Timestamp ta = a.getTimestamp("timestamp");
                            Timestamp tb = b.getTimestamp("timestamp");
                            if (ta == null && tb == null) return 0;
                            if (ta == null) return 1;
                            if (tb == null) return -1;
                            return tb.toDate().compareTo(ta.toDate());
                        });

                        // Most recent valid activity
                        String mostRecent = null;
                        for (QueryDocumentSnapshot doc : docs) {
                            String t = doc.getString("activityType");
                            if (isValidLoggableActivity(t)) { mostRecent = t; break; }
                        }

                        // Most frequent valid activity
                        HashMap<String, Integer> freq = new HashMap<>();
                        for (QueryDocumentSnapshot doc : docs) {
                            String t = doc.getString("activityType");
                            if (!isValidLoggableActivity(t)) continue;
                            freq.put(t, freq.getOrDefault(t, 0) + 1);
                        }
                        String mostFrequent = null; int maxCount = 0;
                        for (String key : freq.keySet()) {
                            if (freq.get(key) > maxCount) {
                                maxCount = freq.get(key);
                                mostFrequent = key;
                            }
                        }

                        // Fallback if something strange happened
                        if (mostFrequent == null || mostRecent == null) {
                            List<String> suggestions = getRandomSuggestedActivities(2);
                            mostFrequent = suggestions.get(0);
                            mostRecent   = suggestions.get(1);
                        }

                        // Avoid duplicate cards
                        if (mostRecent.equals(mostFrequent)) {
                            String differentRecent = null;
                            for (QueryDocumentSnapshot doc : docs) {
                                String t = doc.getString("activityType");
                                if (isValidLoggableActivity(t) && !t.equals(mostFrequent)) {
                                    differentRecent = t;
                                    break;
                                }
                            }
                            mostRecent = differentRecent != null
                                    ? differentRecent
                                    : getRandomSuggestedActivity(mostFrequent);
                        }

                        final String finalMostFrequent = mostFrequent;
                        final String finalMostRecent   = mostRecent;

                        requireActivity().runOnUiThread(() -> {
                            tvRecent1Icon.setText(getActivityEmoji(finalMostFrequent));
                            tvRecent1Title.setText(finalMostFrequent);
                            tvRecent1Subtitle.setText("");
                            tvRecent2Icon.setText(getActivityEmoji(finalMostRecent));
                            tvRecent2Title.setText(finalMostRecent);
                            tvRecent2Subtitle.setText("");
                        });
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        List<String> suggestions = getRandomSuggestedActivities(2);
                        String first = suggestions.get(0), second = suggestions.get(1);
                        requireActivity().runOnUiThread(() -> {
                            tvRecent1Icon.setText(getActivityEmoji(first));
                            tvRecent1Title.setText(first);
                            tvRecent1Subtitle.setText("");
                            tvRecent2Icon.setText(getActivityEmoji(second));
                            tvRecent2Title.setText(second);
                            tvRecent2Subtitle.setText("");
                        });
                    });
        });
    }

    /**
     * Binds activity cards from an already-ordered list of type strings.
     * Used only by the Room-first path in loadActivityCards.
     */
    private void bindActivityCards(List<String> orderedTypes,
                                   TextView tvRecent1Icon, TextView tvRecent1Title,
                                   TextView tvRecent1Subtitle, TextView tvRecent2Icon,
                                   TextView tvRecent2Title, TextView tvRecent2Subtitle) {
        if (orderedTypes.isEmpty()) {
            List<String> suggestions = getRandomSuggestedActivities(2);
            tvRecent1Icon.setText(getActivityEmoji(suggestions.get(0)));
            tvRecent1Title.setText(suggestions.get(0));
            tvRecent1Subtitle.setText("");
            tvRecent2Icon.setText(getActivityEmoji(suggestions.get(1)));
            tvRecent2Title.setText(suggestions.get(1));
            tvRecent2Subtitle.setText("");
            return;
        }

        HashMap<String, Integer> freq = new HashMap<>();
        for (String t : orderedTypes) freq.put(t, freq.getOrDefault(t, 0) + 1);

        String mostFrequent = null; int maxCount = 0;
        for (String key : freq.keySet()) {
            if (freq.get(key) > maxCount) { maxCount = freq.get(key); mostFrequent = key; }
        }

        String mostRecent = null;
        for (String t : orderedTypes) {
            if (isValidLoggableActivity(t)) { mostRecent = t; break; }
        }

        if (mostFrequent == null || mostRecent == null) {
            List<String> suggestions = getRandomSuggestedActivities(2);
            mostFrequent = suggestions.get(0);
            mostRecent   = suggestions.get(1);
        }

        if (mostRecent.equals(mostFrequent)) {
            String differentRecent = null;
            for (String t : orderedTypes) {
                if (isValidLoggableActivity(t) && !t.equals(mostFrequent)) {
                    differentRecent = t; break;
                }
            }
            mostRecent = differentRecent != null
                    ? differentRecent
                    : getRandomSuggestedActivity(mostFrequent);
        }

        tvRecent1Icon.setText(getActivityEmoji(mostFrequent));
        tvRecent1Title.setText(mostFrequent);
        tvRecent1Subtitle.setText("");
        tvRecent2Icon.setText(getActivityEmoji(mostRecent));
        tvRecent2Title.setText(mostRecent);
        tvRecent2Subtitle.setText("");
    }

    /**
     * Loads the monthly challenge progress, scoped to the current calendar month (V2).
     * Room gives an instant total (all-time) for immediate display;
     * Firestore provides month-filtered count and updates the impact ring.
     */
    private void loadMonthlyChallenge(TextView tvChallengeTitle,
                                      ProgressBar progressChallenge,
                                      TextView tvChallengeDays,
                                      TextView tvChallengePercent) {
        String uid = currentUser.getUid();
        int target = 20;

        executor.execute(() -> {
            // ── Room first (all-time count as placeholder) ────────────────────
            List<ActivityLogEntity> roomLogs =
                    AppDatabase.getInstance(requireContext())
                            .activityLogDao().getLogsForUserSync(uid);
            int roomProgress = Math.min(roomLogs.size(), target);
            int roomPercent  = (int) ((roomProgress / (double) target) * 100);

            if (isAdded()) requireActivity().runOnUiThread(() -> {
                tvChallengeTitle.setText("Zero Waste February ♻️");
                progressChallenge.setMax(target);
                progressChallenge.setProgress(roomProgress);
                tvChallengeDays.setText(roomProgress + " / " + target + " logs");
                tvChallengePercent.setText(roomLogs.isEmpty()
                        ? "Start logging to make progress"
                        : roomPercent + "% complete");
            });

            // ── Firestore refresh (month-scoped, per V2) ─────────────────────
            long monthStartMillis = getStartOfCurrentMonthMillis();
            db.collection("activity_logs")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!isAdded()) return;
                        int logCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Timestamp ts = doc.getTimestamp("timestamp");
                            if (ts != null && ts.toDate().getTime() >= monthStartMillis) logCount++;
                        }
                        int progress = Math.min(logCount, target);
                        int percent  = (int) ((progress / (double) target) * 100);

                        impactChallengeProgress = progress / (float) target;
                        refreshImpactRing();

                        final int finalLogCount = logCount;
                        requireActivity().runOnUiThread(() -> {
                            progressChallenge.setMax(target);
                            progressChallenge.setProgress(progress);
                            tvChallengeDays.setText(progress + " / " + target + " logs");
                            tvChallengePercent.setText(finalLogCount == 0
                                    ? "Start logging to make progress"
                                    : percent + "% complete");
                        });
                    })
                    .addOnFailureListener(e -> {
                        impactChallengeProgress = 0f;
                        refreshImpactRing();
                        if (isAdded()) requireActivity().runOnUiThread(() ->
                                tvChallengePercent.setText("Couldn't load challenge"));
                    });
        });
    }

    /**
     * Loads the live campus-wide CO₂ impact summary.
     * Queries ALL activity_logs to aggregate total co2SavedKg and count
     * distinct active users. (US-13)
     */
    private void loadCampusImpact(View view) {
        TextView tvCampusCo2      = view.findViewById(R.id.tv_campus_co2);
        TextView tvCampusStudents = view.findViewById(R.id.tv_campus_students);

        if (tvCampusCo2 == null) return;

        db.collection("activity_logs")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalCo2 = 0.0;
                    Set<String> activeUsers = new HashSet<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double co2 = doc.getDouble("co2SavedKg");
                        if (co2 != null) totalCo2 += co2;
                        String uid = doc.getString("userId");
                        if (uid != null) activeUsers.add(uid);
                    }

                    String co2Text = totalCo2 >= 1000
                            ? String.format(Locale.getDefault(), "%.1f tonnes CO₂ saved", totalCo2 / 1000.0)
                            : String.format(Locale.getDefault(), "%.1f kg CO₂ saved", totalCo2);

                    tvCampusCo2.setText(co2Text);

                    if (tvCampusStudents != null) {
                        int count = activeUsers.size();
                        tvCampusStudents.setText(count + (count == 1 ? " student logging" : " students logging"));
                    }
                })
                .addOnFailureListener(e -> {
                    tvCampusCo2.setText("Campus data unavailable");
                    if (tvCampusStudents != null) tvCampusStudents.setText("");
                });
    }

    /**
     * Loads the user's saved monthly goal and updates the status label and ring. (US-14)
     */
    private void loadGoalStatus(View view) {
        if (getContext() == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        float goalKg = prefs.getFloat("monthly_goal_kg", 0f);

        if (goalKg > 0) {
            impactPersonalGoalKg = goalKg;
            refreshImpactRing();
            updateGoalStatus(view, goalKg);
        }
    }

    /**
     * Updates the goal status label below the challenge progress bar.
     * Uses month-scoped CO₂ (via calculateCurrentMonthCo2) to match the ring display.
     */
    private void updateGoalStatus(View view, float goalKg) {
        TextView tvGoalStatus = view.findViewById(R.id.tv_goal_status);
        if (tvGoalStatus == null) return;

        calculateCurrentMonthCo2(monthlyCo2 -> {
            if (!isAdded()) return;
            int percent = goalKg > 0 ? (int) ((monthlyCo2 / goalKg) * 100) : 0;
            percent = Math.min(percent, 100);
            tvGoalStatus.setText(String.format(Locale.getDefault(),
                    "Personal goal: %.1f / %.1f kg  (%d%%)", monthlyCo2, goalKg, percent));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logging
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Quick-logs an activity: writes to Room immediately for instant UI feedback,
     * then writes to Firestore in the background and awards points.
     */
    private void quickLogActivityFromHome(String activityName) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "You must be logged in to submit a log",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int basePoints = getBasePoints(activityName);
        if (basePoints <= 0) {
            Toast.makeText(getContext(), "Invalid activity selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid   = user.getUid();
        DocumentReference docRef = db.collection("activity_logs").document();
        double co2   = CarbonCalculator.calculateCo2SavedKg(activityName, 1);
        Context context = requireContext();

        // ── 1. Room write (instant) ──────────────────────────────────────────
        ActivityLogEntity entity = new ActivityLogEntity(
                docRef.getId(), uid, activityName, "quick",
                basePoints, 1, co2, "", null, System.currentTimeMillis()
        );

        executor.execute(() -> {
            AppDatabase localDb = AppDatabase.getInstance(context);
            int localId = (int) localDb.activityLogDao().insert(entity);
            localDb.userDao().incrementPoints(uid, basePoints);
            localDb.userDao().incrementCo2(uid, co2);

            // Show success immediately — don't wait for Firestore
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(),
                        activityName + " logged successfully ✅", Toast.LENGTH_SHORT).show();
                View view = getView();
                if (view != null) refreshDashboardUI(view);
            });

            // ── 2. Firestore write ───────────────────────────────────────────
            HashMap<String, Object> logData = new HashMap<>();
            logData.put("logId",        docRef.getId());
            logData.put("userId",       uid);
            logData.put("activityType", activityName);
            logData.put("status",       "quick");
            logData.put("points",       basePoints);
            logData.put("bonusPoints",  0);
            logData.put("proofUrl",     null);
            logData.put("voteCount",    0);
            logData.put("co2SavedKg",   co2);
            logData.put("timestamp",    Timestamp.now());

            docRef.set(logData)
                    .addOnSuccessListener(unused -> {
                        executor.execute(() ->
                                localDb.activityLogDao().markSynced(localId, docRef.getId(), "synced")
                        );
                        new PointsManager().awardBasePoints(context, uid, basePoints);
                    })
                    .addOnFailureListener(e -> {
                        executor.execute(() ->
                                localDb.activityLogDao().markFailed(localId)
                        );
                        // Silent retry — sync worker will pick this up
                        MainActivity.triggerImmediateSync(context);
                    });
        });
    }

    private void openVerifiedLogFromHome(String activityName) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToLog(activityName, true, true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dialogs
    // ─────────────────────────────────────────────────────────────────────────

    private void showHomeLogChoiceDialog(String activityName) {
        if (getContext() == null) return;

        if (!isValidLoggableActivity(activityName)) {
            Toast.makeText(getContext(), "This activity cannot be logged right now",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(activityName)
                .setMessage("How would you like to log this activity?")
                .setPositiveButton("Quick Log", (dialog, which) -> quickLogActivityFromHome(activityName))
                .setNegativeButton("Verified Log", (dialog, which) -> openVerifiedLogFromHome(activityName))
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void showMonthlyChallengesHelp() {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("About Monthly Challenges")
                .setMessage("Campus sustainability challenges are created by staff to encourage " +
                        "simple, shared habits across the community. Your progress updates as " +
                        "you log activities in the app.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void showZeroWasteInfo() {
        if (getContext() == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_info_card, null);
        TextView tvContent = dialogView.findViewById(R.id.tv_dialog_content);

        SpannableStringBuilder content = new SpannableStringBuilder();
        addSection(content, "What it is",
                "Zero Waste on campus means reducing what you throw away by choosing reusable, " +
                        "refillable, and low-waste options whenever possible.");
        addSection(content, "What it looks like on campus",
                "• Bringing a reusable bottle, cup, or food container\n" +
                        "• Refusing single-use plastics and extra packaging\n" +
                        "• Recycling correctly in campus bins\n" +
                        "• Printing less and submitting work digitally when possible\n" +
                        "• Choosing durable items instead of disposable ones\n" +
                        "• Finishing your food and avoiding waste in cafeterias");
        addSection(content, "What you can do",
                "• Use a reusable tumbler instead of a takeaway cup\n" +
                        "• Carry your own water bottle to refill on campus\n" +
                        "• Recycle paper, cans, and bottles properly\n" +
                        "• Bring your own cutlery or lunchbox\n" +
                        "• Avoid taking flyers or items you will throw away immediately");

        tvContent.setText(content);

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Shows a dialog letting the user set their monthly CO₂ goal in kg.
     * Saves the goal to SharedPreferences and Firestore, updates the ring. (US-14)
     */
    private void showSetGoalDialog() {
        if (getContext() == null || currentUser == null) return;

        EditText input = new EditText(requireContext());
        input.setHint("e.g. 20");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setPadding(50, 30, 50, 10);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        float existing = prefs.getFloat("monthly_goal_kg", 0f);
        if (existing > 0) input.setText(String.valueOf((int) existing));

        new AlertDialog.Builder(requireContext())
                .setTitle("Set Monthly CO₂ Goal")
                .setMessage("How many kg of CO₂ do you want to save this month?")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String raw = input.getText().toString().trim();
                    if (raw.isEmpty()) return;
                    try {
                        float goalKg = Float.parseFloat(raw);
                        if (goalKg <= 0) {
                            Toast.makeText(getContext(), "Please enter a value above 0",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        prefs.edit().putFloat("monthly_goal_kg", goalKg).apply();
                        impactPersonalGoalKg = goalKg;
                        refreshImpactRing();

                        db.collection("users")
                                .document(currentUser.getUid())
                                .update("monthlyGoalKg", goalKg)
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(getContext(),
                                                "Goal set: " + goalKg + " kg CO₂ this month 🌿",
                                                Toast.LENGTH_SHORT).show()
                                );

                        View view = getView();
                        if (view != null) {
                            updateGoalStatus(view, goalKg);
                            loadOptionalStats(view);
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "Please enter a valid number",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void navigateToLog() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToLog();
        }
    }

    private void navigateToProfile() {
        if (getActivity() != null) {
            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_profile);
        }
    }

    private void navigateToStaffTips() {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new StaffTipsFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void navigateToChallenges() {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new ChallengesFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void navigateToFeed() {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new FeedFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

    /** Navigates to the Feed and opens the Explore tab directly (V2). */
    private void navigateToFeedExplore() {
        if (getActivity() != null) {
            Bundle args = new Bundle();
            args.putString("start_tab", "explore");
            FeedFragment feedFragment = new FeedFragment();
            feedFragment.setArguments(args);
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, feedFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Share
    // ─────────────────────────────────────────────────────────────────────────

    private void shareProgress() {
        if (currentUser == null || getContext() == null) return;

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;

                    String name   = doc.getString("displayName");
                    Long points   = doc.getLong("totalPoints");
                    Double co2    = doc.getDouble("co2SavedKg");
                    Long streak   = doc.getLong("streakDays");

                    String firstName = (name != null && !name.isEmpty())
                            ? name.split("\\s+")[0] : "I";
                    double co2Val = co2 != null ? co2 : 0.0;
                    long pts      = points != null ? points : 0;
                    String equiv  = co2Val > 0
                            ? " (" + CarbonEquivalentHelper.buildEquivalentString(co2Val) + ")"
                            : "";

                    String message = "🌿 " + firstName + " is making a difference on Klimate!\n\n"
                            + "♻️  CO₂ saved: "
                            + String.format(Locale.getDefault(), "%.1f kg", co2Val) + equiv + "\n"
                            + "⭐  Points: " + pts + "\n"
                            + "🔥  Streak: " + (streak != null ? streak : 0) + " days\n\n"
                            + "Join me on Klimate — track your sustainability impact! 🌍";

                    android.content.Intent shareIntent =
                            new android.content.Intent(android.content.Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, message);
                    startActivity(android.content.Intent.createChooser(shareIntent, "Share your progress"));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Could not load stats to share",
                                Toast.LENGTH_SHORT).show()
                );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void setRandomGreeting(TextView tvGreetingMessage) {
        List<String> greetings = Arrays.asList(
                "Hi sunshine ☀️",
                "Hello lovely 🌿",
                "Ready for a little green win? ✨",
                "Tiny steps, big impact 💚",
                "Glad you're here 🌎",
                "Let's do something good today 🌱"
        );
        tvGreetingMessage.setText(greetings.get(new Random().nextInt(greetings.size())));
    }

    private void setDefaultDashboard(TextView tvStreakNumber, TextView tvStreakMessage,
                                     TextView tvStreakPercent, ProgressBar progressStreak,
                                     TextView tvBottomStreakDays,
                                     TextView tvRecent1Icon, TextView tvRecent1Title,
                                     TextView tvRecent1Subtitle, TextView tvRecent2Icon,
                                     TextView tvRecent2Title, TextView tvRecent2Subtitle,
                                     TextView tvChallengeTitle, ProgressBar progressChallenge,
                                     TextView tvChallengeDays, TextView tvChallengePercent) {
        tvStreakNumber.setText("0");
        tvStreakMessage.setText("Log to start your streak");
        tvStreakPercent.setText("0%");
        progressStreak.setMax(7);
        progressStreak.setProgress(0);
        tvBottomStreakDays.setText("0");

        tvRecent1Icon.setText("");
        tvRecent1Title.setText("MOST FREQUENT");
        tvRecent1Subtitle.setText("Your most frequent activity will appear here.\nTry logging something.");

        tvRecent2Icon.setText("");
        tvRecent2Title.setText("RECENT ACTIVITY");
        tvRecent2Subtitle.setText("Your latest activity will appear here once you log one.");

        tvChallengeTitle.setText("Zero Waste February ♻️");
        progressChallenge.setMax(20);
        progressChallenge.setProgress(0);
        tvChallengeDays.setText("0 / 20 logs");
        tvChallengePercent.setText("Start logging to make progress");
    }

    /**
     * Refreshes all dashboard sections after a quick log (triggered from Room-first path).
     */
    private void refreshDashboardUI(View view) {
        loadOptionalStats(view);

        TextView tvStreakNumber    = view.findViewById(R.id.tv_streak_number);
        TextView tvStreakMessage   = view.findViewById(R.id.tv_streak_message);
        TextView tvStreakPercent   = view.findViewById(R.id.tv_streak_percent);
        ProgressBar progressStreak = view.findViewById(R.id.progress_streak);
        TextView tvBottomStreakDays = view.findViewById(R.id.tv_bottom_streak_days);
        TextView tvRecent1Icon     = view.findViewById(R.id.tv_recent_1_icon);
        TextView tvRecent1Title    = view.findViewById(R.id.tv_recent_1_title);
        TextView tvRecent1Subtitle = view.findViewById(R.id.tv_recent_1_subtitle);
        TextView tvRecent2Icon     = view.findViewById(R.id.tv_recent_2_icon);
        TextView tvRecent2Title    = view.findViewById(R.id.tv_recent_2_title);
        TextView tvRecent2Subtitle = view.findViewById(R.id.tv_recent_2_subtitle);
        TextView tvChallengeTitle  = view.findViewById(R.id.tv_challenge_title);
        ProgressBar progressChall  = view.findViewById(R.id.progress_challenge);
        TextView tvChallengeDays   = view.findViewById(R.id.tv_challenge_days);
        TextView tvChallengePercent = view.findViewById(R.id.tv_challenge_percent);

        loadUserStreakFromLogs(tvStreakNumber, tvStreakMessage, tvStreakPercent,
                progressStreak, tvBottomStreakDays);
        loadActivityCards(tvRecent1Icon, tvRecent1Title, tvRecent1Subtitle,
                tvRecent2Icon, tvRecent2Title, tvRecent2Subtitle);
        loadMonthlyChallenge(tvChallengeTitle, progressChall, tvChallengeDays, tvChallengePercent);
    }

    /** Pushes current impact state into the DoubleProgressRingView (V2). */
    private void refreshImpactRing() {
        if (progressRingMonthly == null) return;

        boolean hasPersonalGoal   = impactPersonalGoalKg > 0f;
        float   personalProgress  = hasPersonalGoal
                ? impactCo2SavedKg / impactPersonalGoalKg
                : 0f;

        progressRingMonthly.setProgressState(
                hasPersonalGoal, personalProgress,
                impactHasMonthlyChallenge, impactChallengeProgress
        );
    }

    private void addSection(SpannableStringBuilder builder, String heading, String body) {
        int start = builder.length();
        builder.append(heading).append("\n");
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(body).append("\n\n");
    }

    private void resetTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pure helpers (no side effects)
    // ─────────────────────────────────────────────────────────────────────────

    private int calculateCurrentStreakFromDays(Set<String> uniqueDays) {
        if (uniqueDays.isEmpty()) return 0;

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        Calendar today     = Calendar.getInstance(); resetTime(today);
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1); resetTime(yesterday);

        String todayKey     = formatter.format(today.getTime());
        String yesterdayKey = formatter.format(yesterday.getTime());

        if (!uniqueDays.contains(todayKey) && !uniqueDays.contains(yesterdayKey)) return 0;

        int streak = 0;
        Calendar cursor = Calendar.getInstance();
        if (uniqueDays.contains(todayKey)) {
            resetTime(cursor);
        } else {
            cursor.add(Calendar.DAY_OF_YEAR, -1);
            resetTime(cursor);
        }

        while (true) {
            String key = formatter.format(cursor.getTime());
            if (uniqueDays.contains(key)) {
                streak++;
                cursor.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return streak;
    }

    private int calculateBestStreakFromDays(Set<String> uniqueDays) {
        if (uniqueDays.isEmpty()) return 0;

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        List<Calendar> sortedDays = new ArrayList<>();

        for (String dayKey : uniqueDays) {
            try {
                Calendar cal = Calendar.getInstance();
                cal.setTime(formatter.parse(dayKey));
                resetTime(cal);
                sortedDays.add(cal);
            } catch (Exception ignored) { }
        }

        if (sortedDays.isEmpty()) return 0;
        sortedDays.sort((a, b) -> a.getTime().compareTo(b.getTime()));

        int best = 1, currentRun = 1;
        for (int i = 1; i < sortedDays.size(); i++) {
            long diffDays = (sortedDays.get(i).getTimeInMillis()
                    - sortedDays.get(i - 1).getTimeInMillis()) / (24L * 60L * 60L * 1000L);
            if (diffDays == 1) {
                currentRun++;
                best = Math.max(best, currentRun);
            } else {
                currentRun = 1;
            }
        }
        return best;
    }

    private boolean isValidLoggableActivity(String activityName) {
        return activityName != null &&
                (activityName.equals("Cycling")
                        || activityName.equals("Public Transit")
                        || activityName.equals("Recycling")
                        || activityName.equals("Plant-based meal")
                        || activityName.equals("Reusable cup")
                        || activityName.equals("Composting")
                        || activityName.equals("Walked")
                        || activityName.equals("Energy saving"));
    }

    private String getActivityEmoji(String activityType) {
        if (activityType == null) return "";
        switch (activityType) {
            case "Cycling":          return "🚲";
            case "Public Transit":   return "🚌";
            case "Recycling":        return "♻️";
            case "Plant-based meal": return "🥗";
            case "Reusable cup":     return "☕";
            case "Composting":       return "🍂";
            case "Walked":           return "🚶";
            case "Energy saving":    return "💡";
            default:                 return "";
        }
    }

    private int getBasePoints(String activityType) {
        switch (activityType) {
            case "Cycling":          return 30;
            case "Public Transit":   return 20;
            case "Recycling":        return 15;
            case "Plant-based meal": return 25;
            case "Reusable cup":     return 10;
            case "Composting":       return 20;
            case "Walked":           return 20;
            case "Energy saving":    return 10;
            default:                 return 0;
        }
    }

    /** Fallback CO₂ estimate per activity when the log doc has no co2SavedKg field (V2). */
    private double getCo2SavedForActivity(String activityType) {
        if (activityType == null) return 0.0;
        switch (activityType) {
            case "Cycling":          return 2.6;
            case "Public Transit":   return 1.8;
            case "Recycling":        return 0.7;
            case "Plant-based meal": return 1.5;
            case "Reusable cup":     return 0.2;
            case "Composting":       return 0.5;
            case "Walked":           return 1.2;
            case "Energy saving":    return 0.8;
            default:                 return 0.0;
        }
    }

    private String getRandomSuggestedActivity(String excludeActivity) {
        List<String> activities = new ArrayList<>(Arrays.asList(
                "Cycling", "Public Transit", "Recycling", "Plant-based meal",
                "Reusable cup", "Composting", "Walked", "Energy saving"));
        activities.remove(excludeActivity);
        if (activities.isEmpty()) return "Recycling";
        return activities.get(new Random().nextInt(activities.size()));
    }

    /** Returns {@code count} distinct randomly chosen activity names (V2). */
    private List<String> getRandomSuggestedActivities(int count) {
        List<String> activities = new ArrayList<>(Arrays.asList(
                "Cycling", "Public Transit", "Recycling", "Plant-based meal",
                "Reusable cup", "Composting", "Walked", "Energy saving"));
        List<String> selected = new ArrayList<>();
        Random random = new Random();
        while (!activities.isEmpty() && selected.size() < count) {
            selected.add(activities.remove(random.nextInt(activities.size())));
        }
        return selected;
    }

    /** Returns the epoch-millis timestamp for the start (00:00:00) of the current month (V2). */
    private long getStartOfCurrentMonthMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        resetTime(cal);
        return cal.getTimeInMillis();
    }

    /** Formats a whole number with locale-appropriate thousands separators (V2). */
    private String formatWholeNumber(long value) {
        return String.format(Locale.getDefault(), "%,d", value);
    }
}