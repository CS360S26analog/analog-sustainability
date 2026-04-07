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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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

        TextView tvRecent1Title = view.findViewById(R.id.tv_recent_1_title);
        TextView tvRecent1Subtitle = view.findViewById(R.id.tv_recent_1_subtitle);
        TextView tvRecent2Title = view.findViewById(R.id.tv_recent_2_title);
        TextView tvRecent2Subtitle = view.findViewById(R.id.tv_recent_2_subtitle);
        TextView btnRecent1 = view.findViewById(R.id.btn_recent_1);
        TextView btnRecent2 = view.findViewById(R.id.btn_recent_2);

        TextView tvChallengeHelp = view.findViewById(R.id.tv_challenge_help);
        TextView btnChallengeInfo = view.findViewById(R.id.btn_challenge_info);
        TextView tvChallengeTitle = view.findViewById(R.id.tv_challenge_title);
        ProgressBar progressChallenge = view.findViewById(R.id.progress_challenge);
        TextView tvChallengeDays = view.findViewById(R.id.tv_challenge_days);
        TextView tvChallengePercent = view.findViewById(R.id.tv_challenge_percent);

        setRandomGreeting(tvGreetingMessage);

        profileAvatarContainer.setOnClickListener(v -> navigateToProfile());
        btnRecent1.setOnClickListener(v -> navigateToLog());
        btnRecent2.setOnClickListener(v -> navigateToLog());
        tvChallengeHelp.setOnClickListener(v -> showMonthlyChallengesHelp());
        btnChallengeInfo.setOnClickListener(v -> showZeroWasteInfo());

        setDefaultDashboard(
                tvStreakNumber, tvStreakMessage, tvStreakPercent, progressStreak,
                tvRecent1Title, tvRecent1Subtitle, tvRecent2Title, tvRecent2Subtitle,
                tvChallengeTitle, progressChallenge, tvChallengeDays, tvChallengePercent
        );

        if (currentUser != null) {
            loadUserHeader(tvUserName, tvProfileInitial);
            loadUserStreak(tvStreakNumber, tvStreakMessage, tvStreakPercent, progressStreak);
            loadActivityCards(tvRecent1Title, tvRecent1Subtitle, tvRecent2Title, tvRecent2Subtitle);
            loadMonthlyChallenge(tvChallengeTitle, progressChallenge, tvChallengeDays, tvChallengePercent);
        }

        return view;
    }

    private void setRandomGreeting(TextView tvGreetingMessage) {
        List<String> greetings = Arrays.asList(
                "Hi sunshine 🌱",
                "Hello lovely 🌷",
                "Ready for a little green win? ✨",
                "Tiny steps, big impact 💚",
                "Glad you’re here 🌼",
                "Let’s do something good today 🍃"
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
                        tvProfileInitial.setText(
                                String.valueOf(Character.toUpperCase(firstName.charAt(0)))
                        );
                    }
                });
    }

    private void loadUserStreak(TextView tvStreakNumber,
                                TextView tvStreakMessage,
                                TextView tvStreakPercent,
                                ProgressBar progressStreak) {
        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return;

                    Long streakLong = document.getLong("streakDays");
                    int streakDays = streakLong != null ? streakLong.intValue() : 0;

                    tvStreakNumber.setText(String.valueOf(streakDays));

                    if (streakDays <= 0) {
                        tvStreakMessage.setText("Log to start your streak");
                        tvStreakPercent.setText("0%");
                        progressStreak.setMax(7);
                        progressStreak.setProgress(0);
                    } else {
                        tvStreakMessage.setText("You're on a " + streakDays + "-day streak");
                        progressStreak.setMax(7);

                        int progress = Math.min(streakDays, 7);
                        progressStreak.setProgress(progress);

                        int percent = (int) ((progress / 7.0) * 100);
                        tvStreakPercent.setText(percent + "%");
                    }
                });
    }

    private void loadActivityCards(TextView tvRecent1Title,
                                   TextView tvRecent1Subtitle,
                                   TextView tvRecent2Title,
                                   TextView tvRecent2Subtitle) {
        db.collection("activity_logs")
                .whereEqualTo("userId", currentUser.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> recentActivities = new ArrayList<>();
                    java.util.HashMap<String, Integer> frequencyMap = new java.util.HashMap<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String activityType = doc.getString("activityType");
                        if (activityType == null || activityType.trim().isEmpty()) continue;

                        if (recentActivities.size() < 1) {
                            recentActivities.add(activityType);
                        }

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

                    if (frequencyMap.isEmpty()) {
                        tvRecent1Title.setText("MOST FREQUENT");
                        tvRecent1Subtitle.setText("Your most frequent activity will appear here. Try logging something.");

                        tvRecent2Title.setText("RECENT ACTIVITY");
                        tvRecent2Subtitle.setText("Your latest activity will appear here once you log one.");
                    } else {
                        tvRecent1Title.setText("MOST FREQUENT");
                        tvRecent1Subtitle.setText(mostFrequent + " • " + maxCount + " time" + (maxCount == 1 ? "" : "s"));

                        tvRecent2Title.setText("RECENT ACTIVITY");
                        if (!recentActivities.isEmpty()) {
                            tvRecent2Subtitle.setText(recentActivities.get(0));
                        } else {
                            tvRecent2Subtitle.setText("Your latest activity will appear here once you log one.");
                        }
                    }
                });
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

                    tvChallengeTitle.setText("Zero Waste February 🗑️");
                    progressChallenge.setMax(target);
                    progressChallenge.setProgress(progress);
                    tvChallengeDays.setText(progress + " / " + target + " logs");

                    if (logCount == 0) {
                        tvChallengePercent.setText("Start logging to make progress");
                    } else {
                        tvChallengePercent.setText(percent + "% complete");
                    }
                });
    }

    private void setDefaultDashboard(TextView tvStreakNumber,
                                     TextView tvStreakMessage,
                                     TextView tvStreakPercent,
                                     ProgressBar progressStreak,
                                     TextView tvRecent1Title,
                                     TextView tvRecent1Subtitle,
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

        tvRecent1Title.setText("MOST FREQUENT");
        tvRecent1Subtitle.setText("Your most frequent activity will appear here. Try logging something.");

        tvRecent2Title.setText("RECENT ACTIVITY");
        tvRecent2Subtitle.setText("Your latest activity will appear here once you log one.");

        tvChallengeTitle.setText("Zero Waste February 🗑️");
        progressChallenge.setMax(20);
        progressChallenge.setProgress(0);
        tvChallengeDays.setText("0 / 20 logs");
        tvChallengePercent.setText("Start logging to make progress");
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

        addSection(content,
                "What it is",
                "Zero Waste on campus means reducing what you throw away by choosing reusable, refillable, and low-waste options whenever possible.");

        addSection(content,
                "What it looks like on campus",
                "• Bringing a reusable bottle, cup, or food container\n" +
                        "• Refusing single-use plastics and extra packaging\n" +
                        "• Recycling correctly in campus bins\n" +
                        "• Printing less and submitting work digitally when possible\n" +
                        "• Choosing durable items instead of disposable ones\n" +
                        "• Finishing your food and avoiding waste in cafeterias");

        addSection(content,
                "What you can do",
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

    private void addSection(SpannableStringBuilder builder, String heading, String body) {
        int start = builder.length();
        builder.append(heading).append("\n");
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(body).append("\n\n");
    }

}