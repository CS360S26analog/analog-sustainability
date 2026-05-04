/**
 * RankingsFragment.java
 *
 * Displays the live campus leaderboard and nemesis system.
 *
 * Role in design: Part of the UI layer. Reached from the
 * Rankings tab in the bottom navigation.
 *
 * Current sprint work:
 * - Reads users from Firestore ordered by totalPoints descending
 * - Shows rank initials name and points
 * - Highlights the current user row
 * - Updates the top 3 podium from live data
 * - Shows a dynamic nemesis card
 * - Shows close gap suggestions based on the point gap
 *
 * @author Karar
 */

package com.example.klimate;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.klimate.local.AppDatabase;
import com.example.klimate.local.UserEntity;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RankingsFragment extends Fragment {

    private FirebaseFirestore db;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private FirebaseAuth auth;
    private ListenerRegistration leaderboardListener;

    private LinearLayout leaderboardContainer;
    private TextView textLeaderboardStatus;

    private TextView textRank1Initials;
    private TextView textRank1Name;
    private TextView textRank1Points;

    private TextView textRank2Initials;
    private TextView textRank2Name;
    private TextView textRank2Points;

    private TextView textRank3Initials;
    private TextView textRank3Name;
    private TextView textRank3Points;

    private LinearLayout cardNemesisWatch;
    private LinearLayout layoutCloseGapSuggestions;
    private TextView textNemesisStatus;
    private TextView textNemesisInitials;
    private TextView textNemesisName;
    private TextView textNemesisRank;
    private TextView textNemesisGap;
    private TextView textNemesisGapLabel;
    private TextView textNemesisLowerName;
    private TextView textNemesisHigherName;
    private TextView textNemesisMessage;
    private TextView btnCloseGap;
    private TextView btnNemesisLogActivity;
    private TextView textSuggestionOne;
    private TextView textSuggestionTwo;
    private TextView textSuggestionThree;
    private ProgressBar progressNemesisGap;

    private boolean nemesisGlowOn = false;
    private String currentNemesisUid = "";

    private static class LeaderboardUser {
        String uid;
        String displayName;
        String profilePhotoBase64;
        long totalPoints;

        LeaderboardUser(String uid, String displayName, String profilePhotoBase64, long totalPoints) {
            this.uid = uid;
            this.displayName = displayName;
            this.profilePhotoBase64 = profilePhotoBase64;
            this.totalPoints = totalPoints;
        }
    }

    /**
     * Inflates the rankings screen and starts the live leaderboard listener.
     *
     * @param inflater the LayoutInflater used to inflate views
     * @param container the parent view group
     * @param savedInstanceState previously saved fragment state
     * @return the root view for the rankings screen
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rankings, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        leaderboardContainer = view.findViewById(R.id.leaderboard_container);
        textLeaderboardStatus = view.findViewById(R.id.text_leaderboard_status);

        textRank1Initials = view.findViewById(R.id.text_rank_1_initials);
        textRank1Name = view.findViewById(R.id.text_rank_1_name);
        textRank1Points = view.findViewById(R.id.text_rank_1_points);

        textRank2Initials = view.findViewById(R.id.text_rank_2_initials);
        textRank2Name = view.findViewById(R.id.text_rank_2_name);
        textRank2Points = view.findViewById(R.id.text_rank_2_points);

        textRank3Initials = view.findViewById(R.id.text_rank_3_initials);
        textRank3Name = view.findViewById(R.id.text_rank_3_name);
        textRank3Points = view.findViewById(R.id.text_rank_3_points);

        cardNemesisWatch = view.findViewById(R.id.card_nemesis_watch);
        layoutCloseGapSuggestions = view.findViewById(R.id.layout_close_gap_suggestions);
        textNemesisStatus = view.findViewById(R.id.text_nemesis_status);
        textNemesisInitials = view.findViewById(R.id.text_nemesis_initials);
        textNemesisName = view.findViewById(R.id.text_nemesis_name);
        textNemesisRank = view.findViewById(R.id.text_nemesis_rank);
        textNemesisGap = view.findViewById(R.id.text_nemesis_gap);
        textNemesisGapLabel = view.findViewById(R.id.text_nemesis_gap_label);
        textNemesisLowerName = view.findViewById(R.id.text_nemesis_lower_name);
        textNemesisHigherName = view.findViewById(R.id.text_nemesis_higher_name);
        textNemesisMessage = view.findViewById(R.id.text_nemesis_message);
        btnCloseGap = view.findViewById(R.id.btn_close_gap);
        btnNemesisLogActivity = view.findViewById(R.id.btn_nemesis_log_activity);
        textSuggestionOne = view.findViewById(R.id.text_suggestion_one);
        textSuggestionTwo = view.findViewById(R.id.text_suggestion_two);
        textSuggestionThree = view.findViewById(R.id.text_suggestion_three);
        progressNemesisGap = view.findViewById(R.id.progress_nemesis_gap);

        cardNemesisWatch.setOnClickListener(v -> toggleNemesisGlow());

        btnCloseGap.setOnClickListener(v -> toggleSuggestionsPanel());

        btnNemesisLogActivity.setOnClickListener(v -> navigateToLogScreen());

        loadLiveLeaderboard();

        return view;
    }

    private void loadLiveLeaderboard() {
        textLeaderboardStatus.setText("Loading leaderboard...");

        // ── Room first: show cached leaderboard instantly ────────────────────
        executor.execute(() -> {
            // We store all users in Room. Filter to students, sort by points.
            // (Room doesn't have a cross-user query by default; we load all and filter.)
            // If you only ever cache the current user, skip this block.
            // For a full leaderboard cache, insert all Firestore users into Room
            // on each snapshot (see the snapshot listener below).

            // This block shows whatever was cached from the last Firestore fetch:
            // (no change needed here if Room only holds the current user —
            //  the Firestore snapshot will populate the list as before)


            // ── Firestore live snapshot (unchanged logic, add Room upsert) ───
            leaderboardListener = db.collection("users")
                    .orderBy("totalPoints", Query.Direction.DESCENDING)
                    .limit(50)
                    .addSnapshotListener((snapshots, e) -> {
                        if (!isAdded() || getContext() == null) return;
                        if (e != null || snapshots == null) {
                            textLeaderboardStatus.setText("Could not load leaderboard");
                            leaderboardContainer.removeAllViews();
                            resetPodium(); resetNemesis("Could not load nemesis");
                            return;
                        }

                        List<LeaderboardUser> users = new java.util.ArrayList<>();

                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String role = doc.getString("role");
                            if ("staff".equalsIgnoreCase(role != null ? role.trim() : ""))
                                continue;

                            String uid = doc.getId();
                            String displayName = doc.getString("displayName");
                            if (TextUtils.isEmpty(displayName)) {
                                String email = doc.getString("email");
                                displayName = !TextUtils.isEmpty(email) ? email : "Student";
                            }
                            Long pointsValue = doc.getLong("totalPoints");
                            long totalPoints = pointsValue != null ? pointsValue : 0;
                            String profilePhotoBase64 = doc.getString("profilePhotoBase64");
                            users.add(new LeaderboardUser(uid, displayName, profilePhotoBase64, totalPoints));

                            // ── Upsert into Room (cache for offline display) ──
                            final String finalName = displayName;
                            final long finalPoints  = totalPoints;
                            final String finalRole  = role != null ? role : "student";
                            executor.execute(() -> {
                                AppDatabase localDb =
                                        AppDatabase.getInstance(requireContext());
                                UserEntity existing = localDb.userDao().getUserSync(uid);
                                if (existing == null) {
                                    UserEntity u = new UserEntity(
                                            uid, finalName,
                                            doc.getString("email") != null
                                                    ? doc.getString("email") : "",
                                            "LUMS", finalRole,
                                            (int) finalPoints, 0, 0, 0);
                                    localDb.userDao().insert(u);
                                } else {
                                    existing.displayName = finalName;
                                    existing.totalPoints = (int) finalPoints;
                                    localDb.userDao().update(existing);
                                }
                            });
                        }

                        bindLeaderboard(users);
                    });
        });
    }

    private void bindLeaderboard(@NonNull List<LeaderboardUser> users) {
        leaderboardContainer.removeAllViews();

        if (users.isEmpty()) {
            textLeaderboardStatus.setText("No students on the leaderboard yet");
            resetPodium();
            resetNemesis("No nemesis yet");
            return;
        }

        textLeaderboardStatus.setText("Live ranking by total points");
        updatePodium(users);

        FirebaseUser currentUser = auth.getCurrentUser();
        String currentUid = currentUser != null ? currentUser.getUid() : "";

        currentNemesisUid = updateNemesis(users, currentUid);

        for (int i = 0; i < users.size(); i++) {
            LeaderboardUser user = users.get(i);
            boolean isCurrentUser = user.uid.equals(currentUid);
            boolean isNemesisUser = user.uid.equals(currentNemesisUid);
            View row = createLeaderboardRow(i + 1, user, isCurrentUser, isNemesisUser);
            leaderboardContainer.addView(row);
        }
    }

    private String updateNemesis(@NonNull List<LeaderboardUser> users, String currentUid) {
        if (TextUtils.isEmpty(currentUid)) {
            resetNemesis("Sign in to view your nemesis");
            return "";
        }

        int currentIndex = findUserIndex(users, currentUid);

        if (currentIndex == -1) {
            resetNemesis("You are not on the leaderboard yet");
            return "";
        }

        LeaderboardUser currentUser = users.get(currentIndex);
        LeaderboardUser aboveUser = currentIndex > 0 ? users.get(currentIndex - 1) : null;
        LeaderboardUser belowUser = currentIndex < users.size() - 1 ? users.get(currentIndex + 1) : null;

        LeaderboardUser nemesisUser;
        boolean isCatchUpMode;

        if (aboveUser != null) {
            nemesisUser = aboveUser;
            isCatchUpMode = true;
        } else if (belowUser != null) {
            nemesisUser = belowUser;
            isCatchUpMode = false;
        } else {
            resetNemesis("You are the only student on the leaderboard");
            return "";
        }

        int nemesisRank = findUserIndex(users, nemesisUser.uid) + 1;
        long gap = Math.abs(nemesisUser.totalPoints - currentUser.totalPoints);

        LeaderboardUser lowerUser;
        LeaderboardUser higherUser;

        if (currentUser.totalPoints <= nemesisUser.totalPoints) {
            lowerUser = currentUser;
            higherUser = nemesisUser;
        } else {
            lowerUser = nemesisUser;
            higherUser = currentUser;
        }

        String lowerName = lowerUser.uid.equals(currentUid) ? "You" : lowerUser.displayName;
        String higherName = higherUser.uid.equals(currentUid) ? "You" : higherUser.displayName;

        textNemesisInitials.setText(getInitials(nemesisUser.displayName));
        textNemesisInitials.setBackgroundResource(getAvatarBackground(nemesisRank, false));
        textNemesisName.setText(nemesisUser.displayName);
        textNemesisRank.setText("RANK #" + nemesisRank);
        textNemesisGap.setText(formatPoints(gap) + " pts");

        textNemesisLowerName.setText(lowerName);
        textNemesisHigherName.setText(higherName);

        int progress = calculateProgress(lowerUser.totalPoints, higherUser.totalPoints);
        progressNemesisGap.setProgress(progress);

        if (isCatchUpMode) {
            textNemesisStatus.setText("Catch the student above you");
            textNemesisGapLabel.setText("to overtake");
            textNemesisMessage.setText("");
            btnCloseGap.setText("⚔ Close Gap");
        } else {
            textNemesisStatus.setText("You are rank #1 keep your lead");
            textNemesisGapLabel.setText("lead gap");
            textNemesisMessage.setText("");
            btnCloseGap.setText("🛡 Defend Rank");
        }

        updateSuggestions(gap, isCatchUpMode);

        return nemesisUser.uid;
    }

    private int findUserIndex(@NonNull List<LeaderboardUser> users, @NonNull String uid) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).uid.equals(uid)) {
                return i;
            }
        }

        return -1;
    }

    private int calculateProgress(long lowerPoints, long higherPoints) {
        if (higherPoints <= 0) {
            return 0;
        }

        return (int) Math.min(100, Math.round((lowerPoints * 100.0) / higherPoints));
    }

    private void updateSuggestions(long gap, boolean isCatchUpMode) {
        long targetGap;

        if (isCatchUpMode) {
            targetGap = Math.max(1, gap);
        } else {
            targetGap = Math.max(60, Math.round(gap * 0.4));
        }

        int cyclingCount = ceilDivide(targetGap, 30);
        int plantCount = ceilDivide(targetGap, 25);
        int walkedCount = ceilDivide(targetGap, 20);

        if (isCatchUpMode) {
            textSuggestionOne.setText("🚲 " + cyclingCount + " x Cycling  •  " + (cyclingCount * 30) + " pts");
            textSuggestionTwo.setText("🥗 " + plantCount + " x Plant-based meal  •  " + (plantCount * 25) + " pts");
            textSuggestionThree.setText("👟 " + walkedCount + " x Walked  •  " + (walkedCount * 20) + " pts");
        } else {
            textSuggestionOne.setText("🚲 " + cyclingCount + " x Cycling  •  +" + (cyclingCount * 30) + " pts  •  protect your lead");
            textSuggestionTwo.setText("🥗 " + plantCount + " x Plant-based meal  •  +" + (plantCount * 25) + " pts  •  stay ahead");
            textSuggestionThree.setText("👟 " + walkedCount + " x Walked  •  +" + (walkedCount * 20) + " pts  •  defend your rank");
        }
    }

    private int ceilDivide(long value, int divisor) {
        return (int) ((value + divisor - 1) / divisor);
    }

    private void resetNemesis(String message) {
        textNemesisStatus.setText(message);
        textNemesisInitials.setText("--");
        textNemesisInitials.setBackgroundResource(R.drawable.bg_avatar_teal);
        textNemesisName.setText("No rival");
        textNemesisRank.setText("RANK --");
        textNemesisGap.setText("0 pts");
        textNemesisGapLabel.setText("gap");
        textNemesisLowerName.setText("You");
        textNemesisHigherName.setText("Rival");
        textNemesisMessage.setText("");
        textSuggestionOne.setText("🚲 Log activities to unlock suggestions");
        textSuggestionTwo.setText("🥗 Verified logs can help you climb");
        textSuggestionThree.setText("👟 Keep your streak alive");
        progressNemesisGap.setProgress(0);
        layoutCloseGapSuggestions.setVisibility(View.GONE);
        currentNemesisUid = "";
    }

    private void toggleNemesisGlow() {
        nemesisGlowOn = !nemesisGlowOn;

        if (nemesisGlowOn) {
            cardNemesisWatch.setBackgroundResource(R.drawable.bg_nemesis_card_glow);
            cardNemesisWatch.animate()
                    .scaleX(1.015f)
                    .scaleY(1.015f)
                    .setDuration(140)
                    .start();
            cardNemesisWatch.setElevation(dpToPx(8));
        } else {
            cardNemesisWatch.setBackgroundResource(R.drawable.bg_nemesis_card);
            cardNemesisWatch.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140)
                    .start();
            cardNemesisWatch.setElevation(dpToPx(4));
        }
    }

    private void toggleSuggestionsPanel() {
        if (layoutCloseGapSuggestions.getVisibility() == View.VISIBLE) {
            layoutCloseGapSuggestions.setVisibility(View.GONE);
        } else {
            layoutCloseGapSuggestions.setVisibility(View.VISIBLE);
        }
    }

    private void navigateToLogScreen() {
        View bottomNavView = requireActivity().findViewById(R.id.bottom_nav);

        if (bottomNavView instanceof BottomNavigationView) {
            ((BottomNavigationView) bottomNavView).setSelectedItemId(R.id.nav_log);
        }
    }

    private View createLeaderboardRow(int rank,
                                      @NonNull LeaderboardUser user,
                                      boolean isCurrentUser,
                                      boolean isNemesisUser) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        if (isCurrentUser) {
            row.setBackgroundResource(R.drawable.bg_you_row);
        } else if (isNemesisUser) {
            row.setBackgroundResource(R.drawable.bg_rival_row);
        } else {
            row.setBackgroundResource(R.drawable.bg_leaderboard_row);
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(72)
        );
        rowParams.setMargins(0, 0, 0, dpToPx(10));
        row.setLayoutParams(rowParams);

        TextView rankText = new TextView(requireContext());
        rankText.setWidth(dpToPx(34));
        rankText.setText(formatRank(rank));
        rankText.setTextSize(14);
        rankText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rankText.setTextColor(ContextCompat.getColor(
                requireContext(),
                rank <= 3 || isCurrentUser ? R.color.color_green_text : R.color.color_text_secondary
        ));
        row.addView(rankText);

        FrameLayout avatarContainer = new FrameLayout(requireContext());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        avatarParams.setMargins(dpToPx(8), 0, 0, 0);
        avatarContainer.setLayoutParams(avatarParams);

        TextView avatarText = new TextView(requireContext());
        avatarText.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        avatarText.setGravity(Gravity.CENTER);
        avatarText.setText(getInitials(user.displayName));
        avatarText.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        avatarText.setTextSize(13);
        avatarText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatarText.setBackgroundResource(getAvatarBackground(rank, isCurrentUser));
        avatarContainer.addView(avatarText);

        ImageView avatarImage = new ImageView(requireContext());
        avatarImage.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarImage.setBackgroundResource(getAvatarBackground(rank, isCurrentUser));
        avatarImage.setClipToOutline(true);
        avatarImage.setVisibility(View.GONE);
        avatarContainer.addView(avatarImage);

        bindProfilePhoto(avatarText, avatarImage, user.profilePhotoBase64);

        row.addView(avatarContainer);

        LinearLayout textColumn = new LinearLayout(requireContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColumnParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textColumnParams.setMargins(dpToPx(10), 0, 0, 0);
        textColumn.setLayoutParams(textColumnParams);

        TextView nameText = new TextView(requireContext());
        nameText.setText(isCurrentUser ? user.displayName + " (You)" : user.displayName);
        nameText.setTextSize(15);
        nameText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nameText.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));
        textColumn.addView(nameText);

        TextView pointsText = new TextView(requireContext());
        pointsText.setText("🍃 " + formatPoints(user.totalPoints) + " pts");
        pointsText.setTextSize(12);
        pointsText.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green_text));
        textColumn.addView(pointsText);

        row.addView(textColumn);

        if (isNemesisUser) {
            TextView rivalChip = new TextView(requireContext());
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dpToPx(28)
            );
            chipParams.setMargins(0, 0, dpToPx(10), 0);
            rivalChip.setLayoutParams(chipParams);
            rivalChip.setGravity(Gravity.CENTER);
            rivalChip.setPadding(dpToPx(10), 0, dpToPx(10), 0);
            rivalChip.setBackgroundResource(R.drawable.bg_live_chip);
            rivalChip.setText("Rival");
            rivalChip.setTextSize(12);
            rivalChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary));
            row.addView(rivalChip);
        }

        ImageView medalImage = new ImageView(requireContext());
        LinearLayout.LayoutParams medalParams = new LinearLayout.LayoutParams(dpToPx(28), dpToPx(28));
        medalImage.setLayoutParams(medalParams);

        int medalDrawable = getMedalDrawable(rank);
        if (medalDrawable == 0) {
            medalImage.setVisibility(View.INVISIBLE);
        } else {
            medalImage.setImageResource(medalDrawable);
        }

        row.addView(medalImage);

        return row;
    }

    private void bindProfilePhoto(TextView avatarText, ImageView avatarImage, String profilePhotoBase64) {
        if (TextUtils.isEmpty(profilePhotoBase64)) {
            avatarImage.setVisibility(View.GONE);
            avatarText.setVisibility(View.VISIBLE);
            return;
        }

        try {
            byte[] imageBytes = Base64.decode(profilePhotoBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            if (bitmap != null) {
                avatarImage.setImageBitmap(bitmap);
                avatarImage.setVisibility(View.VISIBLE);
                avatarText.setVisibility(View.GONE);
                return;
            }
        } catch (Exception ignored) {
        }

        avatarImage.setVisibility(View.GONE);
        avatarText.setVisibility(View.VISIBLE);
    }

    private void updatePodium(@NonNull List<LeaderboardUser> users) {
        bindPodiumPlace(users, 0, textRank1Initials, textRank1Name, textRank1Points);
        bindPodiumPlace(users, 1, textRank2Initials, textRank2Name, textRank2Points);
        bindPodiumPlace(users, 2, textRank3Initials, textRank3Name, textRank3Points);
    }

    private void bindPodiumPlace(@NonNull List<LeaderboardUser> users,
                                 int index,
                                 TextView initialsView,
                                 TextView nameView,
                                 TextView pointsView) {
        if (index >= users.size()) {
            initialsView.setText("--");
            nameView.setText("Waiting");
            pointsView.setText("");
            return;
        }

        LeaderboardUser user = users.get(index);
        initialsView.setText(getInitials(user.displayName));
        nameView.setText(user.displayName);
        pointsView.setText("");
    }

    private void resetPodium() {
        textRank1Initials.setText("--");
        textRank1Name.setText("Waiting");
        textRank1Points.setText("");

        textRank2Initials.setText("--");
        textRank2Name.setText("Waiting");
        textRank2Points.setText("");

        textRank3Initials.setText("--");
        textRank3Name.setText("Waiting");
        textRank3Points.setText("");
    }

    private String formatRank(int rank) {
        return String.format(Locale.getDefault(), "%02d", rank);
    }

    private String formatPoints(long points) {
        return String.format(Locale.getDefault(), "%,d", points);
    }

    private String getInitials(String name) {
        if (TextUtils.isEmpty(name)) return "ST";

        String cleanName = name.trim();
        if (cleanName.isEmpty()) return "ST";

        String[] parts = cleanName.split("\\s+");

        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }

        if (cleanName.length() >= 2) {
            return cleanName.substring(0, 2).toUpperCase();
        }

        return cleanName.substring(0, 1).toUpperCase();
    }

    private int getMedalDrawable(int rank) {
        if (rank == 1) return R.drawable.ic_medal_gold;
        if (rank == 2) return R.drawable.ic_medal_silver;
        if (rank == 3) return R.drawable.ic_medal_bronze;
        return 0;
    }

    private int getAvatarBackground(int rank, boolean isCurrentUser) {
        if (isCurrentUser) return R.drawable.bg_avatar_green;
        if (rank == 1) return R.drawable.bg_avatar_slate;
        if (rank == 2) return R.drawable.bg_avatar_teal;
        if (rank == 3) return R.drawable.bg_avatar_purple;
        if (rank % 3 == 0) return R.drawable.bg_avatar_amber;
        if (rank % 3 == 1) return R.drawable.bg_avatar_brown;
        return R.drawable.bg_avatar_teal;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (leaderboardListener != null) {
            leaderboardListener.remove();
            leaderboardListener = null;
        }
    }
}