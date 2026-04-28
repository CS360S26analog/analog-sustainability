/**
 * TeamProgressFragment.java
 *
 * Shows real-time progress for a user's team within a sustainability challenge.
 * Reads member contribution documents from teams/{teamId}/memberProgress and
 * displays total progress against the challenge target, plus each member's
 * individual contribution count.
 *
 * Firestore read:
 * teams/{teamId}/memberProgress/{uid}
 * {uid, contributionCount}
 *
 * Role in design: View layer for US-04 Team's real-time challenge progress.
 * Uses Firestore snapshot listeners as an Observer-style real-time update.
 *
 * Outstanding issues: Member display names are loaded from users/{uid}; if a
 * user document is missing, the UID is shown as a fallback.
 *
 * @author Izza
 */
package com.example.klimate;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class TeamProgressFragment extends Fragment {

    private static final String ARG_TEAM_ID = "teamId";
    private static final String ARG_CHALLENGE_ID = "challengeId";
    private static final String ARG_CHALLENGE_NAME = "challengeName";
    private static final String ARG_JOIN_CODE = "joinCode";
    private static final String ARG_TARGET = "target";

    private FirebaseFirestore db;
    private ListenerRegistration progressListener;

    private String teamId;
    private String challengeId;
    private String challengeName;
    private String joinCode;
    private int target;

    private TextView tvTeamChallengeName;
    private TextView tvTeamJoinCode;
    private TextView tvProgressSummary;
    private ProgressBar progressTeam;
    private TextView tvProgressPercent;
    private LinearLayout llMemberProgressList;
    private TextView tvTeamProgressEmpty;

    /**
     * Required empty public constructor for Fragment recreation.
     */
    public TeamProgressFragment() {
        // Required empty constructor.
    }

    /**
     * Creates a TeamProgressFragment with the selected team details.
     *
     * @param teamId Firestore team document ID
     * @param challengeId Firestore challenge document ID
     * @param challengeName display name of the challenge
     * @param joinCode team join code
     * @param target target contribution count
     * @return configured TeamProgressFragment
     */
    public static TeamProgressFragment newInstance(String teamId,
                                                   String challengeId,
                                                   String challengeName,
                                                   String joinCode,
                                                   int target) {
        TeamProgressFragment fragment = new TeamProgressFragment();

        Bundle args = new Bundle();
        args.putString(ARG_TEAM_ID, teamId);
        args.putString(ARG_CHALLENGE_ID, challengeId);
        args.putString(ARG_CHALLENGE_NAME, challengeName);
        args.putString(ARG_JOIN_CODE, joinCode);
        args.putInt(ARG_TARGET, target);

        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_team_progress, container, false);

        db = FirebaseFirestore.getInstance();

        readArguments();
        bindViews(view);
        renderHeader();

        if (teamId == null || teamId.trim().isEmpty()) {
            showError("Team details are missing.");
        } else {
            listenToTeamProgress();
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (progressListener != null) {
            progressListener.remove();
            progressListener = null;
        }
    }

    /**
     * Reads fragment arguments into fields.
     */
    private void readArguments() {
        Bundle args = getArguments();
        if (args == null) return;

        teamId = args.getString(ARG_TEAM_ID);
        challengeId = args.getString(ARG_CHALLENGE_ID);
        challengeName = args.getString(ARG_CHALLENGE_NAME);
        joinCode = args.getString(ARG_JOIN_CODE);
        target = args.getInt(ARG_TARGET, 0);
    }

    /**
     * Binds XML views to Java fields.
     *
     * @param view root view
     */
    private void bindViews(View view) {
        tvTeamChallengeName = view.findViewById(R.id.tv_team_challenge_name);
        tvTeamJoinCode = view.findViewById(R.id.tv_team_join_code);
        tvProgressSummary = view.findViewById(R.id.tv_progress_summary);
        progressTeam = view.findViewById(R.id.progress_team);
        tvProgressPercent = view.findViewById(R.id.tv_progress_percent);
        llMemberProgressList = view.findViewById(R.id.ll_member_progress_list);
        tvTeamProgressEmpty = view.findViewById(R.id.tv_team_progress_empty);
    }

    /**
     * Displays challenge title and team code.
     */
    private void renderHeader() {
        tvTeamChallengeName.setText(
                challengeName != null && !challengeName.trim().isEmpty()
                        ? challengeName
                        : "Team Progress"
        );

        if (joinCode != null && !joinCode.trim().isEmpty()) {
            tvTeamJoinCode.setText("Team code: " + joinCode);
        } else {
            tvTeamJoinCode.setText("");
        }
    }

    /**
     * Starts a real-time Firestore listener for member progress.
     */
    private void listenToTeamProgress() {
        progressListener = db.collection("teams")
                .document(teamId)
                .collection("memberProgress")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        showError("Could not load team progress.");
                        return;
                    }

                    llMemberProgressList.removeAllViews();

                    if (snapshots.isEmpty()) {
                        tvTeamProgressEmpty.setVisibility(View.VISIBLE);
                        tvProgressSummary.setText("No progress yet");
                        progressTeam.setProgress(0);
                        tvProgressPercent.setText("0%");
                        return;
                    }

                    tvTeamProgressEmpty.setVisibility(View.GONE);

                    int totalContribution = 0;

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String uid = doc.getString("uid");
                        Long contribution = doc.getLong("contributionCount");

                        int contributionCount = contribution != null ? contribution.intValue() : 0;
                        totalContribution += contributionCount;

                        addMemberProgressRow(uid, contributionCount);
                    }

                    updateProgressSummary(totalContribution, snapshots.size());
                });
    }

    /**
     * Updates total progress summary and progress bar.
     *
     * @param totalContribution total contribution count across all team members
     * @param memberCount number of team members
     */
    private void updateProgressSummary(int totalContribution, int memberCount) {
        if (target > 0) {
            int percent = (int) Math.min((totalContribution * 100.0 / target), 100);

            tvProgressSummary.setText(totalContribution + " / " + target + " contributions");
            progressTeam.setProgress(percent);
            tvProgressPercent.setText(percent + "% complete · " + memberCount + " member"
                    + (memberCount == 1 ? "" : "s"));
        } else {
            tvProgressSummary.setText(totalContribution + " total contributions");
            progressTeam.setProgress(0);
            tvProgressPercent.setText(memberCount + " member" + (memberCount == 1 ? "" : "s"));
        }
    }

    /**
     * Adds one member row, loading the display name from users/{uid}.
     *
     * @param uid member UID
     * @param contributionCount member contribution count
     */
    private void addMemberProgressRow(@Nullable String uid, int contributionCount) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackgroundResource(R.drawable.bg_card_outline);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);

        TextView tvName = new TextView(requireContext());
        tvName.setText(uid != null ? uid : "Unknown member");
        tvName.setTextColor(requireContext().getColor(R.color.color_text_primary));
        tvName.setTextSize(15);
        tvName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        tvName.setLayoutParams(nameParams);

        TextView tvCount = new TextView(requireContext());
        tvCount.setText(contributionCount + " logs");
        tvCount.setTextColor(requireContext().getColor(R.color.color_green_header));
        tvCount.setTextSize(14);
        tvCount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        row.addView(tvName);
        row.addView(tvCount);

        llMemberProgressList.addView(row);

        if (uid != null && !uid.trim().isEmpty()) {
            loadDisplayName(uid, tvName);
        }
    }

    /**
     * Loads a user's display name from Firestore.
     *
     * @param uid user UID
     * @param tvName TextView to update
     */
    private void loadDisplayName(String uid, TextView tvName) {
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    String displayName = userDoc.getString("displayName");
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        tvName.setText(displayName);
                    }
                });
    }

    /**
     * Displays an error/empty state.
     *
     * @param message error message
     */
    private void showError(String message) {
        tvProgressSummary.setText(message);
        progressTeam.setProgress(0);
        tvProgressPercent.setText("0%");
        llMemberProgressList.removeAllViews();
        tvTeamProgressEmpty.setText(message);
        tvTeamProgressEmpty.setVisibility(View.VISIBLE);
    }

    /**
     * Converts density-independent pixels to pixels.
     *
     * @param value dp value
     * @return pixel value
     */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}