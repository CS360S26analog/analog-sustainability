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
 * - Shows rank, initials, name, and points
 * - Highlights the current user row
 * - Updates the top 3 podium from live data
 * - Shows nemesis users directly above and below current user
 *
 * @author Karar
 */

package com.example.klimate;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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

    private TextView textNemesisStatus;
    private LinearLayout nemesisAboveRow;
    private LinearLayout nemesisBelowRow;
    private TextView textNemesisAboveInitials;
    private TextView textNemesisAboveName;
    private TextView textNemesisAboveGap;
    private TextView textNemesisBelowInitials;
    private TextView textNemesisBelowName;
    private TextView textNemesisBelowGap;

    private static class LeaderboardUser {
        String uid;
        String displayName;
        long totalPoints;

        LeaderboardUser(String uid, String displayName, long totalPoints) {
            this.uid = uid;
            this.displayName = displayName;
            this.totalPoints = totalPoints;
        }
    }

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

        textNemesisStatus = view.findViewById(R.id.text_nemesis_status);
        nemesisAboveRow = view.findViewById(R.id.row_nemesis_above);
        nemesisBelowRow = view.findViewById(R.id.row_nemesis_below);
        textNemesisAboveInitials = view.findViewById(R.id.text_nemesis_above_initials);
        textNemesisAboveName = view.findViewById(R.id.text_nemesis_above_name);
        textNemesisAboveGap = view.findViewById(R.id.text_nemesis_above_gap);
        textNemesisBelowInitials = view.findViewById(R.id.text_nemesis_below_initials);
        textNemesisBelowName = view.findViewById(R.id.text_nemesis_below_name);
        textNemesisBelowGap = view.findViewById(R.id.text_nemesis_below_gap);

        loadLiveLeaderboard();

        return view;
    }

    private void loadLiveLeaderboard() {
        textLeaderboardStatus.setText("Loading leaderboard...");

        leaderboardListener = db.collection("users")
                .orderBy("totalPoints", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded() || getContext() == null) return;

                    if (e != null || snapshots == null) {
                        textLeaderboardStatus.setText("Could not load leaderboard");
                        leaderboardContainer.removeAllViews();
                        resetPodium();
                        resetNemesis("Could not load nemesis");
                        return;
                    }

                    List<LeaderboardUser> users = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String uid = doc.getId();
                        String displayName = doc.getString("displayName");

                        if (TextUtils.isEmpty(displayName)) {
                            String email = doc.getString("email");
                            displayName = !TextUtils.isEmpty(email) ? email : "Student";
                        }

                        Long pointsValue = doc.getLong("totalPoints");
                        long totalPoints = pointsValue != null ? pointsValue : 0;

                        users.add(new LeaderboardUser(uid, displayName, totalPoints));
                    }

                    bindLeaderboard(users);
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

        updateNemesis(users, currentUid);

        for (int i = 0; i < users.size(); i++) {
            LeaderboardUser user = users.get(i);
            boolean isCurrentUser = user.uid.equals(currentUid);
            View row = createLeaderboardRow(i + 1, user, isCurrentUser);
            leaderboardContainer.addView(row);
        }
    }

    private void updateNemesis(@NonNull List<LeaderboardUser> users, String currentUid) {
        if (TextUtils.isEmpty(currentUid)) {
            resetNemesis("Sign in to view your nemesis");
            return;
        }

        int currentIndex = -1;

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).uid.equals(currentUid)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            resetNemesis("You are not on the leaderboard yet");
            return;
        }

        LeaderboardUser currentUser = users.get(currentIndex);
        LeaderboardUser aboveUser = currentIndex > 0 ? users.get(currentIndex - 1) : null;
        LeaderboardUser belowUser = currentIndex < users.size() - 1 ? users.get(currentIndex + 1) : null;

        textNemesisStatus.setText("Your closest leaderboard rivals");

        if (aboveUser == null) {
            nemesisAboveRow.setVisibility(View.GONE);
        } else {
            nemesisAboveRow.setVisibility(View.VISIBLE);
            textNemesisAboveInitials.setText(getInitials(aboveUser.displayName));
            textNemesisAboveName.setText("Above you: " + aboveUser.displayName);
            long gap = Math.max(0, aboveUser.totalPoints - currentUser.totalPoints);
            textNemesisAboveGap.setText(formatPoints(gap) + " pts ahead");
        }

        if (belowUser == null) {
            nemesisBelowRow.setVisibility(View.GONE);
        } else {
            nemesisBelowRow.setVisibility(View.VISIBLE);
            textNemesisBelowInitials.setText(getInitials(belowUser.displayName));
            textNemesisBelowName.setText("Below you: " + belowUser.displayName);
            long gap = Math.max(0, currentUser.totalPoints - belowUser.totalPoints);
            textNemesisBelowGap.setText(formatPoints(gap) + " pts behind");
        }

        if (aboveUser == null && belowUser == null) {
            textNemesisStatus.setText("You are the only student on the leaderboard");
        } else if (aboveUser == null) {
            textNemesisStatus.setText("You are rank #1 keep your lead");
        } else if (belowUser == null) {
            textNemesisStatus.setText("Catch the student above you");
        }
    }

    private void resetNemesis(String message) {
        textNemesisStatus.setText(message);
        nemesisAboveRow.setVisibility(View.GONE);
        nemesisBelowRow.setVisibility(View.GONE);
    }

    private View createLeaderboardRow(int rank,
                                      @NonNull LeaderboardUser user,
                                      boolean isCurrentUser) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        row.setBackgroundResource(isCurrentUser ? R.drawable.bg_you_row : R.drawable.bg_leaderboard_row);

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

        TextView avatarText = new TextView(requireContext());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        avatarParams.setMargins(dpToPx(8), 0, 0, 0);
        avatarText.setLayoutParams(avatarParams);
        avatarText.setGravity(Gravity.CENTER);
        avatarText.setText(getInitials(user.displayName));
        avatarText.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        avatarText.setTextSize(13);
        avatarText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatarText.setBackgroundResource(getAvatarBackground(rank, isCurrentUser));
        row.addView(avatarText);

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

        TextView medalText = new TextView(requireContext());
        medalText.setText(getMedalText(rank));
        medalText.setTextSize(18);
        medalText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        medalText.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_amber));
        row.addView(medalText);

        return row;
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
            pointsView.setText("0 pts");
            return;
        }

        LeaderboardUser user = users.get(index);
        initialsView.setText(getInitials(user.displayName));
        nameView.setText(user.displayName);
        pointsView.setText(formatPoints(user.totalPoints) + " pts");
    }

    private void resetPodium() {
        textRank1Initials.setText("--");
        textRank1Name.setText("Waiting");
        textRank1Points.setText("0 pts");

        textRank2Initials.setText("--");
        textRank2Name.setText("Waiting");
        textRank2Points.setText("0 pts");

        textRank3Initials.setText("--");
        textRank3Name.setText("Waiting");
        textRank3Points.setText("0 pts");
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

    private String getMedalText(int rank) {
        if (rank == 1) return "🥇";
        if (rank == 2) return "🥈";
        if (rank == 3) return "🥉";
        return "";
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