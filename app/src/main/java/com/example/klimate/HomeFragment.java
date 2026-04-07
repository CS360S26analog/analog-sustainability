/**
 * HomeFragment.java
 *
 * The main dashboard screen of the Klimate app. Displays the current
 * user's greeting, streak, CO2 saved, points, activity cards, and
 * monthly challenge progress. Reads live data directly from Firestore.
 *
 * Role in design: Part of the View layer (MVVM). Observes Firestore
 * in real time to keep dashboard stats current. Navigates to
 * LogFragment via the cycling and recycling action cards, and to
 * HistoryFragment via the activity history card.
 *
 * Outstanding issues: time period filter (weekly/monthly) not yet
 * connected to UI, currently shows all-time stats.
 *
 * @author Maryam Ali
 */
package com.example.klimate;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

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

public class HomeFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        TextView tvGreetingMessage = view.findViewById(R.id.tv_greeting_message);
        TextView tvUserName = view.findViewById(R.id.tv_user_name);
        TextView tvProfileInitial = view.findViewById(R.id.tv_profile_initial);
        FrameLayout profileAvatarContainer = view.findViewById(R.id.profile_avatar_container);

        TextView tvStreakNumber = view.findViewById(R.id.tv_streak_number);
        TextView tvStreakMessage = view.findViewById(R.id.tv_streak_message);
        TextView tvStreakPercent = view.findViewById(R.id.tv_streak_percent);
        ProgressBar progressStreak = view.findViewById(R.id.progress_streak);

        TextView tvBottomStreakDays = view.findViewById(R.id.tv_bottom_streak_days);

        TextView tvRecent1Icon = view.findViewById(R.id.tv_recent_1_icon);
        TextView tvRecent1Title = view.findViewById(R.id.tv_recent_1_title);
        TextView tvRecent1Subtitle = view.findViewById(R.id.tv_recent_1_subtitle);

        TextView tvRecent2Icon = view.findViewById(R.id.tv_recent_2_icon);
        TextView tvRecent2Title = view.findViewById(R.id.tv_recent_2_title);
        TextView tvRecent2Subtitle = view.findViewById(R.id.tv_recent_2_subtitle);

        TextView btnRecent1 = view.findViewById(R.id.btn_recent_1);
        TextView btnRecent2 = view.findViewById(R.id.btn_recent_2);

        View cardRecent1 = view.findViewById(R.id.card_recent_1);
        View cardRecent2 = view.findViewById(R.id.card_recent_2);

        TextView tvChallengeHelp = view.findViewById(R.id.tv_challenge_help);
        TextView btnChallengeInfo = view.findViewById(R.id.btn_challenge_info);
        TextView tvChallengeTitle = view.findViewById(R.id.tv_challenge_title);
        ProgressBar progressChallenge = view.findViewById(R.id.progress_challenge);
        TextView tvChallengeDays = view.findViewById(R.id.tv_challenge_days);
        TextView tvChallengePercent = view.findViewById(R.id.tv_challenge_percent);

        setRandomGreeting(tvGreetingMessage);

        profileAvatarContainer.setOnClickListener(v -> navigateToProfile());

        btnRecent1.setOnClickListener(v -> showHomeLogChoiceDialog(tvRecent1Title.getText().toString()));
        btnRecent2.setOnClickListener(v -> showHomeLogChoiceDialog(tvRecent2Title.getText().toString()));

        if (cardRecent1 != null) {
            cardRecent1.setOnClickListener(v -> showHomeLogChoiceDialog(tvRecent1Title.getText().toString()));
        }

        if (cardRecent2 != null) {
            cardRecent2.setOnClickListener(v -> showHomeLogChoiceDialog(tvRecent2Title.getText().toString()));
        }

        tvChallengeHelp.setOnClickListener(v -> showMonthlyChallengesHelp());
        btnChallengeInfo.setOnClickListener(v -> showZeroWasteInfo());

        setDefaultDashboard(
                tvStreakNumber,
                tvStreakMessage,
                tvStreakPercent,
                progressStreak,
                tvBottomStreakDays,
                tvRecent1Icon,
                tvRecent1Title,
                tvRecent1Subtitle,
                tvRecent2Icon,
                tvRecent2Title,
                tvRecent2Subtitle,
                tvChallengeTitle,
                progressChallenge,
                tvChallengeDays,
                tvChallengePercent
        );

        if (currentUser != null) {
            loadUserHeader(tvUserName, tvProfileInitial);
            loadUserStreakFromLogs(
                    tvStreakNumber,
                    tvStreakMessage,
                    tvStreakPercent,
                    progressStreak,
                    tvBottomStreakDays
            );
            loadActivityCards(
                    tvRecent1Icon, tvRecent1Title, tvRecent1Subtitle,
                    tvRecent2Icon, tvRecent2Title, tvRecent2Subtitle
            );
            loadMonthlyChallenge(tvChallengeTitle, progressChallenge, tvChallengeDays, tvChallengePercent);
            loadOptionalStats(view);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        View view = getView();
        if (view == null || currentUser == null) return;

        TextView tvUserName = view.findViewById(R.id.tv_user_name);
        TextView tvProfileInitial = view.findViewById(R.id.tv_profile_initial);

        TextView tvStreakNumber = view.findViewById(R.id.tv_streak_number);
        TextView tvStreakMessage = view.findViewById(R.id.tv_streak_message);
        TextView tvStreakPercent = view.findViewById(R.id.tv_streak_percent);
        ProgressBar progressStreak = view.findViewById(R.id.progress_streak);
        TextView tvBottomStreakDays = view.findViewById(R.id.tv_bottom_streak_days);

        TextView tvRecent1Icon = view.findViewById(R.id.tv_recent_1_icon);
        TextView tvRecent1Title = view.findViewById(R.id.tv_recent_1_title);
        TextView tvRecent1Subtitle = view.findViewById(R.id.tv_recent_1_subtitle);

        TextView tvRecent2Icon = view.findViewById(R.id.tv_recent_2_icon);
        TextView tvRecent2Title = view.findViewById(R.id.tv_recent_2_title);
        TextView tvRecent2Subtitle = view.findViewById(R.id.tv_recent_2_subtitle);

        TextView tvChallengeTitle = view.findViewById(R.id.tv_challenge_title);
        ProgressBar progressChallenge = view.findViewById(R.id.progress_challenge);
        TextView tvChallengeDays = view.findViewById(R.id.tv_challenge_days);
        TextView tvChallengePercent = view.findViewById(R.id.tv_challenge_percent);

        loadUserHeader(tvUserName, tvProfileInitial);
        loadUserStreakFromLogs(
                tvStreakNumber,
                tvStreakMessage,
                tvStreakPercent,
                progressStreak,
                tvBottomStreakDays
        );
        loadActivityCards(
                tvRecent1Icon, tvRecent1Title, tvRecent1Subtitle,
                tvRecent2Icon, tvRecent2Title, tvRecent2Subtitle
        );
        loadMonthlyChallenge(tvChallengeTitle, progressChallenge, tvChallengeDays, tvChallengePercent);
        loadOptionalStats(view);
    }

    private void loadOptionalStats(View view) {
        int pointsId = requireContext().getResources().getIdentifier(
                "tv_points_value", "id", requireContext().getPackageName()
        );
        int co2Id = requireContext().getResources().getIdentifier(
                "tv_co2_saved", "id", requireContext().getPackageName()
        );

        TextView tvPoints = pointsId != 0 ? view.findViewById(pointsId) : null;
        TextView tvCo2Saved = co2Id != 0 ? view.findViewById(co2Id) : null;

        if (tvPoints == null && tvCo2Saved == null) {
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return;

                    Long totalPoints = document.getLong("totalPoints");
                    Double co2SavedKg = document.getDouble("co2SavedKg");

                    if (tvPoints != null) {
                        tvPoints.setText(String.valueOf(totalPoints != null ? totalPoints : 0));
                    }

                    if (tvCo2Saved != null) {
                        double value = co2SavedKg != null ? co2SavedKg : 0.0;
                        tvCo2Saved.setText(String.format(Locale.getDefault(), "%.1f kg", value));
                    }
                });
    }

    private void setRandomGreeting(TextView tvGreetingMessage) {
        List<String> greetings = Arrays.asList(
                "Hi sunshine ☀️",
                "Hello lovely 🌿",
                "Ready for a little green win? ✨",
                "Tiny steps, big impact 💚",
                "Glad you’re here 🌎",
                "Let’s do something good today 🌱"
        );

        Random random = new Random();
        tvGreetingMessage.setText(greetings.get(random.nextInt(greetings.size())));
    }

    private void loadUserHeader(TextView tvUserName, TextView tvProfileInitial) {
        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return;

                    String displayName = document.getString("displayName");
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        String cleanName = displayName.trim();
                        String firstName = cleanName.split("\\s+")[0];
                        tvUserName.setText(firstName + "!");
                        tvProfileInitial.setText(String.valueOf(Character.toUpperCase(firstName.charAt(0))));
                    }
                });
    }

    private void loadUserStreakFromLogs(TextView tvStreakNumber,
                                        TextView tvStreakMessage,
                                        TextView tvStreakPercent,
                                        ProgressBar progressStreak,
                                        TextView tvBottomStreakDays) {
        db.collection("activity_logs")
                .whereEqualTo("userId", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Set<String> uniqueDays = new HashSet<>();
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
                        if (ts == null) continue;
                        uniqueDays.add(formatter.format(ts.toDate()));
                    }

                    int currentStreak = calculateCurrentStreakFromDays(uniqueDays);
                    int bestStreak = calculateBestStreakFromDays(uniqueDays);

                    tvStreakNumber.setText(String.valueOf(currentStreak));
                    tvBottomStreakDays.setText(String.valueOf(bestStreak));

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
                        int percent = (int) ((progress / 7.0) * 100);
                        tvStreakPercent.setText(percent + "%");
                    }
                })
                .addOnFailureListener(e -> {
                    tvStreakNumber.setText("0");
                    tvBottomStreakDays.setText("0");
                    tvStreakMessage.setText("Couldn't load streak");
                    tvStreakPercent.setText("0%");
                    progressStreak.setMax(7);
                    progressStreak.setProgress(0);
                });
    }

    private int calculateCurrentStreakFromDays(Set<String> uniqueDays) {
        if (uniqueDays.isEmpty()) return 0;

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        Calendar today = Calendar.getInstance();
        resetTime(today);

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        resetTime(yesterday);

        String todayKey = formatter.format(today.getTime());
        String yesterdayKey = formatter.format(yesterday.getTime());

        if (!uniqueDays.contains(todayKey) && !uniqueDays.contains(yesterdayKey)) {
            return 0;
        }

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
            } catch (Exception ignored) {
            }
        }

        if (sortedDays.isEmpty()) return 0;

        sortedDays.sort((a, b) -> a.getTime().compareTo(b.getTime()));

        int best = 1;
        int currentRun = 1;

        for (int i = 1; i < sortedDays.size(); i++) {
            long diffMillis = sortedDays.get(i).getTimeInMillis() - sortedDays.get(i - 1).getTimeInMillis();
            long diffDays = diffMillis / (24L * 60L * 60L * 1000L);

            if (diffDays == 1) {
                currentRun++;
                best = Math.max(best, currentRun);
            } else {
                currentRun = 1;
            }
        }

        return best;
    }

    private void loadActivityCards(TextView tvRecent1Icon,
                                   TextView tvRecent1Title,
                                   TextView tvRecent1Subtitle,
                                   TextView tvRecent2Icon,
                                   TextView tvRecent2Title,
                                   TextView tvRecent2Subtitle) {
        db.collection("activity_logs")
                .whereEqualTo("userId", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        docs.add(doc);
                    }

                    docs.sort((a, b) -> {
                        com.google.firebase.Timestamp ta = a.getTimestamp("timestamp");
                        com.google.firebase.Timestamp tb = b.getTimestamp("timestamp");

                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.toDate().compareTo(ta.toDate());
                    });

                    if (docs.isEmpty()) {
                        tvRecent1Icon.setText("");
                        tvRecent1Title.setText("MOST FREQUENT");
                        tvRecent1Subtitle.setText("Your most frequent activity will appear here.\nTry logging something.");

                        tvRecent2Icon.setText("");
                        tvRecent2Title.setText("RECENT ACTIVITY");
                        tvRecent2Subtitle.setText("Your latest activity will appear here once you log one.");
                        return;
                    }

                    HashMap<String, Integer> frequencyMap = new HashMap<>();
                    for (QueryDocumentSnapshot doc : docs) {
                        String activityType = doc.getString("activityType");
                        if (activityType == null || activityType.trim().isEmpty()) continue;

                        int count = frequencyMap.containsKey(activityType) ? frequencyMap.get(activityType) : 0;
                        frequencyMap.put(activityType, count + 1);
                    }

                    String mostFrequent = null;
                    int maxCount = 0;

                    for (String key : frequencyMap.keySet()) {
                        int count = frequencyMap.get(key);
                        if (count > maxCount) {
                            maxCount = count;
                            mostFrequent = key;
                        }
                    }

                    String recentDifferent = null;
                    for (QueryDocumentSnapshot doc : docs) {
                        String activityType = doc.getString("activityType");
                        if (activityType == null || activityType.trim().isEmpty()) continue;

                        if (!activityType.equals(mostFrequent)) {
                            recentDifferent = activityType;
                            break;
                        }
                    }

                    tvRecent1Icon.setText(getActivityEmoji(mostFrequent));
                    tvRecent1Title.setText(mostFrequent);
                    tvRecent1Subtitle.setText("You're on a roll with this activity");

                    if (recentDifferent != null) {
                        tvRecent2Icon.setText(getActivityEmoji(recentDifferent));
                        tvRecent2Title.setText(recentDifferent);
                        tvRecent2Subtitle.setText("Want to log this activity again?");
                    } else {
                        String suggestion = getRandomSuggestedActivity(mostFrequent);
                        tvRecent2Icon.setText(getActivityEmoji(suggestion));
                        tvRecent2Title.setText(suggestion);
                        tvRecent2Subtitle.setText("Try this activity too");
                    }
                })
                .addOnFailureListener(e -> {
                    tvRecent1Icon.setText("");
                    tvRecent1Title.setText("MOST FREQUENT");
                    tvRecent1Subtitle.setText("Couldn't load activity data");

                    tvRecent2Icon.setText("");
                    tvRecent2Title.setText("RECENT ACTIVITY");
                    tvRecent2Subtitle.setText("Couldn't load activity data");
                });
    }

    private String getActivityEmoji(String activityType) {
        if (activityType == null) return "";

        switch (activityType) {
            case "Cycling":
                return "🚲";
            case "Public Transit":
                return "🚌";
            case "Recycling":
                return "♻️";
            case "Plant-based meal":
                return "🥗";
            case "Reusable cup":
                return "☕";
            case "Composting":
                return "🍂";
            case "Walked":
                return "🚶";
            case "Energy saving":
                return "💡";
            default:
                return "";
        }
    }

    private String getRandomSuggestedActivity(String excludeActivity) {
        List<String> activities = new ArrayList<>(Arrays.asList(
                "Cycling",
                "Public Transit",
                "Recycling",
                "Plant-based meal",
                "Reusable cup",
                "Composting",
                "Walked",
                "Energy saving"
        ));

        activities.remove(excludeActivity);

        if (activities.isEmpty()) {
            return "Recycling";
        }

        Random random = new Random();
        return activities.get(random.nextInt(activities.size()));
    }

    private void loadMonthlyChallenge(TextView tvChallengeTitle,
                                      ProgressBar progressChallenge,
                                      TextView tvChallengeDays,
                                      TextView tvChallengePercent) {
        db.collection("activity_logs")
                .whereEqualTo("userId", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int logCount = queryDocumentSnapshots.size();
                    int target = 20;
                    int progress = Math.min(logCount, target);
                    int percent = (int) ((progress / (double) target) * 100);

                    tvChallengeTitle.setText("Zero Waste February ♻️");
                    progressChallenge.setMax(target);
                    progressChallenge.setProgress(progress);
                    tvChallengeDays.setText(progress + " / " + target + " logs");

                    if (logCount == 0) {
                        tvChallengePercent.setText("Start logging to make progress");
                    } else {
                        tvChallengePercent.setText(percent + "% complete");
                    }
                })
                .addOnFailureListener(e -> {
                    tvChallengeTitle.setText("Zero Waste February ♻️");
                    progressChallenge.setMax(20);
                    progressChallenge.setProgress(0);
                    tvChallengeDays.setText("0 / 20 logs");
                    tvChallengePercent.setText("Couldn't load challenge");
                });
    }

    private void setDefaultDashboard(TextView tvStreakNumber,
                                     TextView tvStreakMessage,
                                     TextView tvStreakPercent,
                                     ProgressBar progressStreak,
                                     TextView tvBottomStreakDays,
                                     TextView tvRecent1Icon,
                                     TextView tvRecent1Title,
                                     TextView tvRecent1Subtitle,
                                     TextView tvRecent2Icon,
                                     TextView tvRecent2Title,
                                     TextView tvRecent2Subtitle,
                                     TextView tvChallengeTitle,
                                     ProgressBar progressChallenge,
                                     TextView tvChallengeDays,
                                     TextView tvChallengePercent) {
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

    private void showHomeLogChoiceDialog(String activityName) {
        if (getContext() == null) return;

        if (!isValidLoggableActivity(activityName)) {
            Toast.makeText(getContext(), "This activity cannot be logged right now", Toast.LENGTH_SHORT).show();
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

    private void quickLogActivityFromHome(String activityName) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Toast.makeText(getContext(), "You must be logged in to submit a log", Toast.LENGTH_SHORT).show();
            return;
        }

        int basePoints = getBasePoints(activityName);
        if (basePoints <= 0) {
            Toast.makeText(getContext(), "Invalid activity selected", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference docRef = db.collection("activity_logs").document();

        HashMap<String, Object> logData = new HashMap<>();
        logData.put("logId", docRef.getId());
        logData.put("userId", user.getUid());
        logData.put("activityType", activityName);
        logData.put("status", "quick");
        logData.put("points", basePoints);
        logData.put("bonusPoints", 0);
        logData.put("proofUrl", null);
        logData.put("voteCount", 0);
        logData.put("timestamp", Timestamp.now());

        docRef.set(logData)
                .addOnSuccessListener(unused -> {
                    new PointsManager().awardBasePoints(user.getUid(), basePoints);
                    Toast.makeText(getContext(), activityName + " logged successfully ✅", Toast.LENGTH_SHORT).show();
                    View view = getView();
                    if (view != null) {
                        loadOptionalStats(view);

                        TextView tvStreakNumber = view.findViewById(R.id.tv_streak_number);
                        TextView tvStreakMessage = view.findViewById(R.id.tv_streak_message);
                        TextView tvStreakPercent = view.findViewById(R.id.tv_streak_percent);
                        ProgressBar progressStreak = view.findViewById(R.id.progress_streak);
                        TextView tvBottomStreakDays = view.findViewById(R.id.tv_bottom_streak_days);

                        TextView tvRecent1Icon = view.findViewById(R.id.tv_recent_1_icon);
                        TextView tvRecent1Title = view.findViewById(R.id.tv_recent_1_title);
                        TextView tvRecent1Subtitle = view.findViewById(R.id.tv_recent_1_subtitle);

                        TextView tvRecent2Icon = view.findViewById(R.id.tv_recent_2_icon);
                        TextView tvRecent2Title = view.findViewById(R.id.tv_recent_2_title);
                        TextView tvRecent2Subtitle = view.findViewById(R.id.tv_recent_2_subtitle);

                        TextView tvChallengeTitle = view.findViewById(R.id.tv_challenge_title);
                        ProgressBar progressChallenge = view.findViewById(R.id.progress_challenge);
                        TextView tvChallengeDays = view.findViewById(R.id.tv_challenge_days);
                        TextView tvChallengePercent = view.findViewById(R.id.tv_challenge_percent);

                        loadUserStreakFromLogs(
                                tvStreakNumber,
                                tvStreakMessage,
                                tvStreakPercent,
                                progressStreak,
                                tvBottomStreakDays
                        );
                        loadActivityCards(
                                tvRecent1Icon, tvRecent1Title, tvRecent1Subtitle,
                                tvRecent2Icon, tvRecent2Title, tvRecent2Subtitle
                        );
                        loadMonthlyChallenge(tvChallengeTitle, progressChallenge, tvChallengeDays, tvChallengePercent);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to save log: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void openVerifiedLogFromHome(String activityName) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToLog(activityName, true, true);
        }
    }

    private int getBasePoints(String activityType) {
        switch (activityType) {
            case "Cycling":
                return 30;
            case "Public Transit":
                return 20;
            case "Recycling":
                return 15;
            case "Plant-based meal":
                return 25;
            case "Reusable cup":
                return 10;
            case "Composting":
                return 20;
            case "Walked":
                return 20;
            case "Energy saving":
                return 10;
            default:
                return 0;
        }
    }

    private void navigateToLog() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToLog();
        }
    }

    private void navigateToProfile() {
        if (getActivity() != null) {
            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_profile);
            }
        }
    }

    private void showMonthlyChallengesHelp() {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("About Monthly Challenges")
                .setMessage("Campus sustainability challenges are created by staff to encourage simple, shared habits across the community. Your progress updates as you log activities in the app.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void showZeroWasteInfo() {
        if (getContext() == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_info_card, null);
        TextView tvContent = dialogView.findViewById(R.id.tv_dialog_content);

        SpannableStringBuilder content = new SpannableStringBuilder();

        addSection(
                content,
                "What it is",
                "Zero Waste on campus means reducing what you throw away by choosing reusable, refillable, and low-waste options whenever possible."
        );

        addSection(
                content,
                "What it looks like on campus",
                "• Bringing a reusable bottle, cup, or food container\n" +
                        "• Refusing single-use plastics and extra packaging\n" +
                        "• Recycling correctly in campus bins\n" +
                        "• Printing less and submitting work digitally when possible\n" +
                        "• Choosing durable items instead of disposable ones\n" +
                        "• Finishing your food and avoiding waste in cafeterias"
        );

        addSection(
                content,
                "What you can do",
                "• Use a reusable tumbler instead of a takeaway cup\n" +
                        "• Carry your own water bottle to refill on campus\n" +
                        "• Recycle paper, cans, and bottles properly\n" +
                        "• Bring your own cutlery or lunchbox\n" +
                        "• Avoid taking flyers or items you will throw away immediately"
        );

        tvContent.setText(content);

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void addSection(SpannableStringBuilder builder, String heading, String body) {
        int start = builder.length();
        builder.append(heading).append("\n");
        builder.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        builder.append(body).append("\n\n");
    }

    private void resetTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }
}