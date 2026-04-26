/**
 * ChallengesFragment.java
 *
 * Displays active campus sustainability challenges and allows students
 * to browse and join them. Shows the user's joined challenges in the
 * My Teams tab with real-time progress.
 *
 * Reads from the Firestore "challenges" collection (written by staff
 * via StaffManageFragment). Writes join records to
 * challenges/{id}/members/{uid} when the user taps Join.
 *
 * Role in design: Part of the View layer (MVVM). Uses Observer pattern
 * via Firestore snapshot listeners for real-time progress updates.
 *
 * Outstanding issues:
 * - Team creation and join-by-code flow to be added by Izza (US-05).
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ChallengesFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private LinearLayout llChallengesList, llMyTeamsList;
    private LinearLayout panelBrowse, panelMyTeams;
    private TextView tabBrowse, tabMyTeams;
    private TextView tvBrowseEmpty, tvMyTeamsEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_challenges, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        llChallengesList = view.findViewById(R.id.ll_challenges_list);
        llMyTeamsList    = view.findViewById(R.id.ll_my_teams_list);
        panelBrowse      = view.findViewById(R.id.panel_browse);
        panelMyTeams     = view.findViewById(R.id.panel_my_teams);
        tabBrowse        = view.findViewById(R.id.tab_browse);
        tabMyTeams       = view.findViewById(R.id.tab_my_teams);
        tvBrowseEmpty    = view.findViewById(R.id.tv_browse_empty);
        tvMyTeamsEmpty   = view.findViewById(R.id.tv_my_teams_empty);

        tabBrowse.setOnClickListener(v -> showTab(true));
        tabMyTeams.setOnClickListener(v -> showTab(false));

        loadActiveChallenges();

        return view;
    }

    /**
     * Switches between the Browse and My Teams tabs.
     *
     * @param showBrowse true to show Browse tab, false for My Teams tab
     */
    private void showTab(boolean showBrowse) {
        if (showBrowse) {
            panelBrowse.setVisibility(View.VISIBLE);
            panelMyTeams.setVisibility(View.GONE);
            tabBrowse.setBackgroundResource(R.drawable.bg_tab_selected);
            tabBrowse.setTextColor(getResources().getColor(R.color.color_green_header, null));
            tabMyTeams.setBackgroundColor(getResources().getColor(R.color.white, null));
            tabMyTeams.setTextColor(getResources().getColor(R.color.color_text_secondary, null));
            loadActiveChallenges();
        } else {
            panelBrowse.setVisibility(View.GONE);
            panelMyTeams.setVisibility(View.VISIBLE);
            tabMyTeams.setBackgroundResource(R.drawable.bg_tab_selected);
            tabMyTeams.setTextColor(getResources().getColor(R.color.color_green_header, null));
            tabBrowse.setBackgroundColor(getResources().getColor(R.color.white, null));
            tabBrowse.setTextColor(getResources().getColor(R.color.color_text_secondary, null));
            loadMyTeams();
        }
    }

    /**
     * Loads all active challenges from Firestore ordered by end date.
     * Renders each as a card in the Browse panel.
     */
    private void loadActiveChallenges() {
        if (currentUser == null) return;

        db.collection("challenges")
                .whereEqualTo("active", true)
                .orderBy("endDate", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    llChallengesList.removeAllViews();

                    if (snapshots.isEmpty()) {
                        tvBrowseEmpty.setVisibility(View.VISIBLE);
                        return;
                    }
                    tvBrowseEmpty.setVisibility(View.GONE);

                    for (QueryDocumentSnapshot doc : snapshots) {
                        addChallengeCard(doc, false);
                    }
                })
                .addOnFailureListener(e -> {
                    tvBrowseEmpty.setVisibility(View.VISIBLE);
                    tvBrowseEmpty.setText("Could not load challenges.");
                });
    }

    /**
     * Loads challenges the current user has joined.
     * Renders each in the My Teams panel.
     */
    private void loadMyTeams() {
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        db.collection("challenges")
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    llMyTeamsList.removeAllViews();
                    final int[] joinedCount = {0};
                    final int total = snapshots.size();
                    if (total == 0) {
                        tvMyTeamsEmpty.setVisibility(View.VISIBLE);
                        return;
                    }

                    for (QueryDocumentSnapshot doc : snapshots) {
                        // Check if user is a member of this challenge
                        db.collection("challenges")
                                .document(doc.getId())
                                .collection("members")
                                .document(uid)
                                .get()
                                .addOnSuccessListener(memberDoc -> {
                                    if (memberDoc.exists()) {
                                        joinedCount[0]++;
                                        addChallengeCard(doc, true);
                                    }
                                    // After checking all, show empty if none joined
                                    if (llMyTeamsList.getChildCount() == 0
                                            && joinedCount[0] == 0) {
                                        tvMyTeamsEmpty.setVisibility(View.VISIBLE);
                                    }
                                });
                    }
                });
    }

    /**
     * Inflates a challenge card and binds all its data.
     * If joined is true, shows the team code row instead of the Join button.
     *
     * @param doc    the Firestore document for this challenge
     * @param joined whether the current user has already joined
     */
    private void addChallengeCard(QueryDocumentSnapshot doc, boolean joined) {
        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_challenge_card,
                        joined ? llMyTeamsList : llChallengesList,
                        false);

        String challengeId = doc.getId();
        String name        = doc.getString("name");
        String description = doc.getString("description");
        Long participantCount = doc.getLong("participantCount");
        com.google.firebase.Timestamp endDate = doc.getTimestamp("endDate");

        TextView tvName     = card.findViewById(R.id.tv_challenge_name);
        TextView tvMeta     = card.findViewById(R.id.tv_challenge_meta);
        TextView tvDesc     = card.findViewById(R.id.tv_challenge_desc);
        TextView btnJoin    = card.findViewById(R.id.btn_join_challenge);
        ProgressBar progBar = card.findViewById(R.id.progress_challenge_bar);
        TextView tvProgress = card.findViewById(R.id.tv_challenge_progress);
        LinearLayout rowCode = card.findViewById(R.id.row_team_code);
        TextView tvCode     = card.findViewById(R.id.tv_team_code);
        TextView btnShare   = card.findViewById(R.id.btn_share_code);

        tvName.setText(name != null ? name : "Unnamed Challenge");
        tvDesc.setText(description != null ? description : "");

        // Format end date and participant count
        final String endStr;
        if (endDate != null) {
            endStr = "Ends " + new SimpleDateFormat("dd MMM", Locale.getDefault())
                    .format(endDate.toDate());
        } else {
            endStr = "";
        }
        long count = participantCount != null ? participantCount : 0;
        tvMeta.setText(endStr + "  ·  " + count + " joined");

        // Load participant count from members subcollection for accuracy
        db.collection("challenges").document(challengeId)
                .collection("members").get()
                .addOnSuccessListener(members -> {
                    int memberCount = members.size();
                    tvMeta.setText(endStr + "  ·  " + memberCount + " joined");

                    // Show progress as % of a target if set
                    Long target = doc.getLong("target");
                    if (target != null && target > 0) {
                        int pct = (int) Math.min((memberCount * 100.0 / target), 100);
                        progBar.setProgress(pct);
                        tvProgress.setText(memberCount + " / " + target + " members  (" + pct + "%)");
                    }
                });

        if (joined) {
            // Show team code, hide join button
            btnJoin.setVisibility(View.GONE);
            rowCode.setVisibility(View.VISIBLE);

            // Load team code from members subcollection
            db.collection("challenges").document(challengeId)
                    .collection("members").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(memberDoc -> {
                        String code = memberDoc.getString("teamCode");
                        tvCode.setText("Team code: " + (code != null ? code : "—"));

                        btnShare.setOnClickListener(v -> {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_TEXT,
                                    "Join me on the \"" + name + "\" challenge on Klimate! "
                                            + "Team code: " + code + " 🌿");
                            startActivity(Intent.createChooser(shareIntent, "Share team code"));
                        });
                    });
        } else {
            // Show join button
            btnJoin.setOnClickListener(v -> joinChallenge(challengeId, name, btnJoin, rowCode, tvCode, btnShare));
        }

        if (joined) {
            llMyTeamsList.addView(card);
        } else {
            llChallengesList.addView(card);
        }
    }

    /**
     * Joins the given challenge by writing the user's UID to the
     * members subcollection. Generates a random 4-character team code.
     * Updates the participantCount on the challenge document.
     *
     * @param challengeId the Firestore document ID of the challenge
     * @param challengeName the display name of the challenge
     * @param btnJoin    the Join button to hide after joining
     * @param rowCode    the team code row to show after joining
     * @param tvCode     the TextView to display the team code
     * @param btnShare   the share button for the team code
     */
    private void joinChallenge(String challengeId, String challengeName,
                               TextView btnJoin, LinearLayout rowCode,
                               TextView tvCode, TextView btnShare) {
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        // Check if already joined
        db.collection("challenges").document(challengeId)
                .collection("members").document(uid)
                .get()
                .addOnSuccessListener(memberDoc -> {
                    if (memberDoc.exists()) {
                        Toast.makeText(getContext(),
                                "You're already in this challenge!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Generate a random 4-character team code
                    String teamCode = generateTeamCode();

                    Map<String, Object> memberData = new HashMap<>();
                    memberData.put("uid", uid);
                    memberData.put("joinedAt",
                            com.google.firebase.Timestamp.now());
                    memberData.put("teamCode", teamCode);
                    memberData.put("contributionCount", 0);

                    db.collection("challenges").document(challengeId)
                            .collection("members").document(uid)
                            .set(memberData)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(getContext(),
                                        "Joined! 🌿 Share code: " + teamCode,
                                        Toast.LENGTH_LONG).show();

                                // Update UI immediately
                                btnJoin.setVisibility(View.GONE);
                                rowCode.setVisibility(View.VISIBLE);
                                tvCode.setText("Team code: " + teamCode);

                                btnShare.setOnClickListener(v -> {
                                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                    shareIntent.setType("text/plain");
                                    shareIntent.putExtra(Intent.EXTRA_TEXT,
                                            "Join me on the \""
                                                    + challengeName
                                                    + "\" challenge on Klimate! "
                                                    + "Team code: " + teamCode + " 🌿");
                                    startActivity(Intent.createChooser(
                                            shareIntent, "Share team code"));
                                });
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(),
                                            "Could not join: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show()
                            );
                });
    }

    /**
     * Generates a random 4-character uppercase alphanumeric team code.
     *
     * @return team code string e.g. "GRNX"
     */
    private String generateTeamCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }
}