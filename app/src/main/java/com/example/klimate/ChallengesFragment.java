/**
 * ChallengesFragment.java
 *
 * Displays active campus sustainability challenges and allows students
 * to browse challenges, create teams, join teams using a code, and view
 * joined team progress from the My Teams tab.
 *
 * Firestore schemas used:
 *
 * challenges/{challengeId}
 * {name, description, startDate, endDate, target, allowedActivities[], active, createdBy}
 *
 * teams/{teamId}
 * {teamId, challengeId, joinCode, memberUids[], createdBy, createdAt}
 *
 * teams/{teamId}/memberProgress/{uid}
 * {uid, contributionCount}
 *
 * A compatibility mirror is also written to:
 * challenges/{challengeId}/members/{uid}
 * so older challenge UI and participant count logic do not break.
 *
 * Role in design: View layer for Challenges and Teams.
 * Uses Firestore snapshot/query reads as Observer-style updates.
 *
 * Outstanding issues: None.
 *
 * @author Izza
 * @author Maryam Waseem
 */
package com.example.klimate;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChallengesFragment extends Fragment {

    private static final String COLLECTION_CHALLENGES = "challenges";
    private static final String COLLECTION_TEAMS = "teams";
    private static final String SUBCOLLECTION_MEMBER_PROGRESS = "memberProgress";
    private static final String SUBCOLLECTION_LEGACY_MEMBERS = "members";

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private LinearLayout llChallengesList;
    private LinearLayout llMyTeamsList;
    private LinearLayout panelBrowse;
    private LinearLayout panelMyTeams;
    private TextView tabBrowse;
    private TextView tabMyTeams;
    private TextView tvBrowseEmpty;
    private TextView tvMyTeamsEmpty;

    private interface AlreadyJoinedCallback {
        void onResult(boolean alreadyJoined);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_challenges, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        llChallengesList = view.findViewById(R.id.ll_challenges_list);
        llMyTeamsList = view.findViewById(R.id.ll_my_teams_list);
        panelBrowse = view.findViewById(R.id.panel_browse);
        panelMyTeams = view.findViewById(R.id.panel_my_teams);
        tabBrowse = view.findViewById(R.id.tab_browse);
        tabMyTeams = view.findViewById(R.id.tab_my_teams);
        tvBrowseEmpty = view.findViewById(R.id.tv_browse_empty);
        tvMyTeamsEmpty = view.findViewById(R.id.tv_my_teams_empty);

        tabBrowse.setOnClickListener(v -> showTab(true));
        tabMyTeams.setOnClickListener(v -> showTab(false));

        loadActiveChallenges();

        return view;
    }

    /**
     * Switches between Browse and My Teams tabs.
     *
     * @param showBrowse true to show Browse, false to show My Teams
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
     * Loads all active challenges from Firestore.
     */
    private void loadActiveChallenges() {
        if (currentUser == null) {
            showBrowseEmpty("Please log in to view challenges.");
            return;
        }

        db.collection(COLLECTION_CHALLENGES)
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    llChallengesList.removeAllViews();

                    if (snapshots.isEmpty()) {
                        showBrowseEmpty("No active challenges right now.\nCheck back soon! 🌿");
                        return;
                    }

                    tvBrowseEmpty.setVisibility(View.GONE);

                    for (QueryDocumentSnapshot doc : snapshots) {
                        addChallengeCard(doc, false, null, null);
                    }
                })
                .addOnFailureListener(e ->
                        showBrowseEmpty("Could not load challenges: " + e.getMessage())
                );
    }

    /**
     * Loads teams joined by the current user.
     */
    private void loadMyTeams() {
        if (currentUser == null) {
            showMyTeamsEmpty("Please log in to view your teams.");
            return;
        }

        String uid = currentUser.getUid();

        llMyTeamsList.removeAllViews();
        tvMyTeamsEmpty.setVisibility(View.GONE);

        db.collection(COLLECTION_TEAMS)
                .whereArrayContains("memberUids", uid)
                .get()
                .addOnSuccessListener(teamSnapshots -> {
                    if (teamSnapshots.isEmpty()) {
                        loadLegacyJoinedChallenges(uid);
                        return;
                    }

                    final int totalTeams = teamSnapshots.size();
                    final int[] finished = {0};
                    final int[] shown = {0};

                    for (QueryDocumentSnapshot teamDoc : teamSnapshots) {
                        String challengeId = teamDoc.getString("challengeId");
                        String joinCode = teamDoc.getString("joinCode");
                        String teamId = teamDoc.getId();

                        if (challengeId == null || challengeId.trim().isEmpty()) {
                            finished[0]++;
                            maybeShowMyTeamsEmpty(finished[0], totalTeams, shown[0]);
                            continue;
                        }

                        db.collection(COLLECTION_CHALLENGES)
                                .document(challengeId)
                                .get()
                                .addOnSuccessListener(challengeDoc -> {
                                    Boolean active = challengeDoc.getBoolean("active");
                                    if (challengeDoc.exists() && Boolean.TRUE.equals(active)) {
                                        shown[0]++;
                                        addChallengeCard(challengeDoc, true, teamId, joinCode);
                                    }
                                })
                                .addOnCompleteListener(task -> {
                                    finished[0]++;
                                    maybeShowMyTeamsEmpty(finished[0], totalTeams, shown[0]);
                                });
                    }
                })
                .addOnFailureListener(e ->
                        showMyTeamsEmpty("Could not load your teams: " + e.getMessage())
                );
    }

    /**
     * Loads old challenge membership records for users who joined before
     * the teams collection was added.
     *
     * @param uid current user UID
     */
    private void loadLegacyJoinedChallenges(String uid) {
        db.collection(COLLECTION_CHALLENGES)
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(challenges -> {
                    if (challenges.isEmpty()) {
                        showMyTeamsEmpty("You haven't joined any challenges yet.\nBrowse and tap Join to get started!");
                        return;
                    }

                    final int total = challenges.size();
                    final int[] finished = {0};
                    final int[] shown = {0};

                    for (QueryDocumentSnapshot challengeDoc : challenges) {
                        db.collection(COLLECTION_CHALLENGES)
                                .document(challengeDoc.getId())
                                .collection(SUBCOLLECTION_LEGACY_MEMBERS)
                                .document(uid)
                                .get()
                                .addOnSuccessListener(memberDoc -> {
                                    if (memberDoc.exists()) {
                                        shown[0]++;
                                        String legacyCode = memberDoc.getString("teamCode");
                                        addChallengeCard(challengeDoc, true, null, legacyCode);
                                    }
                                })
                                .addOnCompleteListener(task -> {
                                    finished[0]++;
                                    maybeShowMyTeamsEmpty(finished[0], total, shown[0]);
                                });
                    }
                })
                .addOnFailureListener(e ->
                        showMyTeamsEmpty("Could not load your teams: " + e.getMessage())
                );
    }

    /**
     * Creates and displays one challenge card.
     *
     * @param doc challenge document
     * @param joined true if this is shown in My Teams
     * @param teamId team document ID, or null for legacy membership
     * @param joinCode team join code, or null if unavailable
     */
    private void addChallengeCard(DocumentSnapshot doc,
                                  boolean joined,
                                  @Nullable String teamId,
                                  @Nullable String joinCode) {

        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_challenge_card,
                        joined ? llMyTeamsList : llChallengesList,
                        false);

        String challengeId = doc.getId();
        String name = doc.getString("name");
        String description = doc.getString("description");
        Timestamp endDate = doc.getTimestamp("endDate");
        Long target = doc.getLong("target");

        TextView tvName = card.findViewById(R.id.tv_challenge_name);
        TextView tvMeta = card.findViewById(R.id.tv_challenge_meta);
        TextView tvDesc = card.findViewById(R.id.tv_challenge_desc);
        TextView btnJoin = card.findViewById(R.id.btn_join_challenge);
        ProgressBar progBar = card.findViewById(R.id.progress_challenge_bar);
        TextView tvProgress = card.findViewById(R.id.tv_challenge_progress);
        LinearLayout rowCode = card.findViewById(R.id.row_team_code);
        TextView tvCode = card.findViewById(R.id.tv_team_code);
        TextView btnShare = card.findViewById(R.id.btn_share_code);

        String safeName = name != null ? name : "Unnamed Challenge";
        String safeDescription = description != null ? description : "";

        tvName.setText(safeName);
        tvDesc.setText(safeDescription);

        final String endStr;
        if (endDate != null) {
            endStr = "Ends " + new SimpleDateFormat("dd MMM", Locale.getDefault())
                    .format(endDate.toDate());
        } else {
            endStr = "No end date";
        }

        tvMeta.setText(endStr);
        tvProgress.setText("Loading progress...");
        progBar.setProgress(0);

        if (joined) {
            btnJoin.setVisibility(View.GONE);
            rowCode.setVisibility(View.VISIBLE);

            String visibleCode = joinCode != null ? joinCode : "—";
            tvCode.setText("Team code: " + visibleCode);
            setShareButton(btnShare, safeName, visibleCode);

            updateJoinedTeamProgress(challengeId, teamId, target, progBar, tvProgress, tvMeta, endStr);

            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                if (teamId == null) {
                    Toast.makeText(getContext(),
                            "This older team record does not have a full progress screen yet.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                int safeTarget = target != null ? target.intValue() : 0;

                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToTeamProgress(
                            teamId,
                            challengeId,
                            safeName,
                            visibleCode,
                            safeTarget
                    );
                }
            });
        } else {
            rowCode.setVisibility(View.GONE);
            btnJoin.setVisibility(View.VISIBLE);
            btnJoin.setText("Join / Create Team");

            updateBrowseChallengeSummary(challengeId, target, progBar, tvProgress, tvMeta, endStr);

            btnJoin.setOnClickListener(v ->
                    showTeamOptionsDialog(challengeId, safeName, btnJoin, rowCode, tvCode, btnShare)
            );
        }

        if (joined) {
            llMyTeamsList.addView(card);
        } else {
            llChallengesList.addView(card);
        }
    }

    /**
     * Shows a choice dialog for creating a new team or joining with a code.
     *
     * @param challengeId challenge ID
     * @param challengeName challenge name
     * @param btnJoin join button
     * @param rowCode team code row
     * @param tvCode team code text
     * @param btnShare share button
     */
    private void showTeamOptionsDialog(String challengeId,
                                       String challengeName,
                                       TextView btnJoin,
                                       LinearLayout rowCode,
                                       TextView tvCode,
                                       TextView btnShare) {
        String[] options = {"Create a new team", "Join with team code"};

        new AlertDialog.Builder(requireContext())
                .setTitle("Join Challenge")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        createNewTeam(challengeId, challengeName, btnJoin, rowCode, tvCode, btnShare);
                    } else {
                        showJoinCodeDialog(challengeId, challengeName, btnJoin, rowCode, tvCode, btnShare);
                    }
                })
                .show();
    }

    /**
     * Creates a new team for the selected challenge.
     *
     * @param challengeId challenge ID
     * @param challengeName challenge name
     * @param btnJoin join button
     * @param rowCode team code row
     * @param tvCode team code text
     * @param btnShare share button
     */
    private void createNewTeam(String challengeId,
                               String challengeName,
                               TextView btnJoin,
                               LinearLayout rowCode,
                               TextView tvCode,
                               TextView btnShare) {
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        checkAlreadyJoinedChallenge(challengeId, alreadyJoined -> {
            if (alreadyJoined) {
                Toast.makeText(getContext(),
                        "You're already in a team for this challenge.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String teamId = db.collection(COLLECTION_TEAMS).document().getId();
            String joinCode = generateTeamCode();

            Map<String, Object> teamData = new HashMap<>();
            teamData.put("teamId", teamId);
            teamData.put("challengeId", challengeId);
            teamData.put("joinCode", joinCode);
            teamData.put("memberUids", java.util.Collections.singletonList(uid));
            teamData.put("createdBy", uid);
            teamData.put("createdAt", Timestamp.now());

            Map<String, Object> progressData = new HashMap<>();
            progressData.put("uid", uid);
            progressData.put("contributionCount", 0);

            db.collection(COLLECTION_TEAMS)
                    .document(teamId)
                    .set(teamData)
                    .addOnSuccessListener(unused ->
                            db.collection(COLLECTION_TEAMS)
                                    .document(teamId)
                                    .collection(SUBCOLLECTION_MEMBER_PROGRESS)
                                    .document(uid)
                                    .set(progressData)
                                    .addOnSuccessListener(progressUnused -> {
                                        writeLegacyMembership(challengeId, teamId, joinCode, uid);
                                        db.collection(COLLECTION_CHALLENGES)
                                                .document(challengeId)
                                                .update("participantCount", FieldValue.increment(1));

                                        showJoinedUi(challengeName, joinCode, btnJoin, rowCode, tvCode, btnShare);

                                        Toast.makeText(getContext(),
                                                "Team created! Share code: " + joinCode,
                                                Toast.LENGTH_LONG).show();
                                    })
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(),
                                    "Could not create team: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show()
                    );
        });
    }

    /**
     * Opens a dialog where the user can enter a 4-character join code.
     *
     * @param challengeId challenge ID
     * @param challengeName challenge name
     * @param btnJoin join button
     * @param rowCode team code row
     * @param tvCode team code text
     * @param btnShare share button
     */
    private void showJoinCodeDialog(String challengeId,
                                    String challengeName,
                                    TextView btnJoin,
                                    LinearLayout rowCode,
                                    TextView tvCode,
                                    TextView btnShare) {
        EditText input = new EditText(requireContext());
        input.setHint("Enter code, e.g. GRNX");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(4)});
        input.setPadding(32, 24, 32, 24);

        new AlertDialog.Builder(requireContext())
                .setTitle("Join Team")
                .setMessage("Enter the 4-character code shared by your teammate.")
                .setView(input)
                .setPositiveButton("Join", (dialog, which) -> {
                    String code = input.getText().toString().trim().toUpperCase(Locale.ROOT);
                    if (code.length() != 4) {
                        Toast.makeText(getContext(),
                                "Please enter a valid 4-character code.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    joinTeamWithCode(challengeId, challengeName, code, btnJoin, rowCode, tvCode, btnShare);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Joins an existing team using a team code.
     *
     * @param challengeId challenge ID
     * @param challengeName challenge name
     * @param joinCode entered team code
     * @param btnJoin join button
     * @param rowCode team code row
     * @param tvCode team code text
     * @param btnShare share button
     */
    private void joinTeamWithCode(String challengeId,
                                  String challengeName,
                                  String joinCode,
                                  TextView btnJoin,
                                  LinearLayout rowCode,
                                  TextView tvCode,
                                  TextView btnShare) {
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        checkAlreadyJoinedChallenge(challengeId, alreadyJoined -> {
            if (alreadyJoined) {
                Toast.makeText(getContext(),
                        "You're already in a team for this challenge.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            db.collection(COLLECTION_TEAMS)
                    .whereEqualTo("joinCode", joinCode)
                    .get()
                    .addOnSuccessListener(teamSnapshots -> {
                        DocumentSnapshot matchedTeam = null;

                        for (DocumentSnapshot teamDoc : teamSnapshots.getDocuments()) {
                            String teamChallengeId = teamDoc.getString("challengeId");
                            if (challengeId.equals(teamChallengeId)) {
                                matchedTeam = teamDoc;
                                break;
                            }
                        }

                        if (matchedTeam == null) {
                            Toast.makeText(getContext(),
                                    "No team found for this challenge with code " + joinCode,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Object memberObj = matchedTeam.get("memberUids");
                        if (memberObj instanceof List) {
                            List<?> memberUids = (List<?>) memberObj;
                            if (memberUids.contains(uid)) {
                                Toast.makeText(getContext(),
                                        "You're already in this team.",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }

                        String teamId = matchedTeam.getId();

                        Map<String, Object> progressData = new HashMap<>();
                        progressData.put("uid", uid);
                        progressData.put("contributionCount", 0);

                        db.collection(COLLECTION_TEAMS)
                                .document(teamId)
                                .update("memberUids", FieldValue.arrayUnion(uid))
                                .addOnSuccessListener(unused ->
                                        db.collection(COLLECTION_TEAMS)
                                                .document(teamId)
                                                .collection(SUBCOLLECTION_MEMBER_PROGRESS)
                                                .document(uid)
                                                .set(progressData)
                                                .addOnSuccessListener(progressUnused -> {
                                                    writeLegacyMembership(challengeId, teamId, joinCode, uid);
                                                    db.collection(COLLECTION_CHALLENGES)
                                                            .document(challengeId)
                                                            .update("participantCount", FieldValue.increment(1));

                                                    showJoinedUi(challengeName, joinCode, btnJoin, rowCode, tvCode, btnShare);

                                                    Toast.makeText(getContext(),
                                                            "Joined team " + joinCode + "!",
                                                            Toast.LENGTH_LONG).show();
                                                })
                                )
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(),
                                                "Could not join team: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show()
                                );
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(),
                                    "Could not find team: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show()
                    );
        });
    }

    /**
     * Checks whether the current user is already in any team for this challenge.
     *
     * @param challengeId challenge ID
     * @param callback callback returning true if already joined
     */
    private void checkAlreadyJoinedChallenge(String challengeId,
                                             AlreadyJoinedCallback callback) {
        if (currentUser == null) {
            callback.onResult(false);
            return;
        }

        String uid = currentUser.getUid();

        db.collection(COLLECTION_CHALLENGES)
                .document(challengeId)
                .collection(SUBCOLLECTION_LEGACY_MEMBERS)
                .document(uid)
                .get()
                .addOnSuccessListener(legacyDoc -> {
                    if (legacyDoc.exists()) {
                        callback.onResult(true);
                        return;
                    }

                    db.collection(COLLECTION_TEAMS)
                            .whereArrayContains("memberUids", uid)
                            .get()
                            .addOnSuccessListener(teamSnapshots -> {
                                boolean alreadyJoined = false;

                                for (DocumentSnapshot teamDoc : teamSnapshots.getDocuments()) {
                                    String teamChallengeId = teamDoc.getString("challengeId");
                                    if (challengeId.equals(teamChallengeId)) {
                                        alreadyJoined = true;
                                        break;
                                    }
                                }

                                callback.onResult(alreadyJoined);
                            })
                            .addOnFailureListener(e -> callback.onResult(false));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    /**
     * Writes a compatibility membership record under challenges/{id}/members/{uid}.
     *
     * @param challengeId challenge ID
     * @param teamId team ID
     * @param joinCode join code
     * @param uid user ID
     */
    private void writeLegacyMembership(String challengeId,
                                       String teamId,
                                       String joinCode,
                                       String uid) {
        Map<String, Object> memberData = new HashMap<>();
        memberData.put("uid", uid);
        memberData.put("teamId", teamId);
        memberData.put("teamCode", joinCode);
        memberData.put("joinedAt", Timestamp.now());
        memberData.put("contributionCount", 0);

        db.collection(COLLECTION_CHALLENGES)
                .document(challengeId)
                .collection(SUBCOLLECTION_LEGACY_MEMBERS)
                .document(uid)
                .set(memberData);
    }

    /**
     * Updates the challenge card after the user joins or creates a team.
     *
     * @param challengeName challenge name
     * @param joinCode team code
     * @param btnJoin join button
     * @param rowCode team code row
     * @param tvCode team code text
     * @param btnShare share button
     */
    private void showJoinedUi(String challengeName,
                              String joinCode,
                              TextView btnJoin,
                              LinearLayout rowCode,
                              TextView tvCode,
                              TextView btnShare) {
        btnJoin.setVisibility(View.GONE);
        rowCode.setVisibility(View.VISIBLE);
        tvCode.setText("Team code: " + joinCode);
        setShareButton(btnShare, challengeName, joinCode);
    }

    /**
     * Configures the share-code button.
     *
     * @param btnShare share button
     * @param challengeName challenge name
     * @param joinCode team code
     */
    private void setShareButton(TextView btnShare,
                                String challengeName,
                                String joinCode) {
        btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "Join my Klimate team for \"" + challengeName + "\"! "
                            + "Team code: " + joinCode + " 🌿");
            startActivity(Intent.createChooser(shareIntent, "Share team code"));
        });
    }

    /**
     * Updates the Browse card summary with total teams and joined members.
     *
     * @param challengeId challenge ID
     * @param target challenge target
     * @param progBar progress bar
     * @param tvProgress progress text
     * @param tvMeta metadata text
     * @param endStr formatted end-date string
     */
    private void updateBrowseChallengeSummary(String challengeId,
                                              @Nullable Long target,
                                              ProgressBar progBar,
                                              TextView tvProgress,
                                              TextView tvMeta,
                                              String endStr) {
        db.collection(COLLECTION_TEAMS)
                .whereEqualTo("challengeId", challengeId)
                .get()
                .addOnSuccessListener(teamSnapshots -> {
                    int teamCount = teamSnapshots.size();
                    int memberCount = 0;

                    for (DocumentSnapshot teamDoc : teamSnapshots.getDocuments()) {
                        Object memberObj = teamDoc.get("memberUids");
                        if (memberObj instanceof List) {
                            memberCount += ((List<?>) memberObj).size();
                        }
                    }

                    if (teamCount == 0) {
                        loadLegacyMemberSummary(challengeId, target, progBar, tvProgress, tvMeta, endStr);
                        return;
                    }

                    tvMeta.setText(endStr + "  ·  " + memberCount + " joined");
                    tvProgress.setText(teamCount + " team" + (teamCount == 1 ? "" : "s") + " active");

                    if (target != null && target > 0) {
                        int pct = (int) Math.min((memberCount * 100.0 / target), 100);
                        progBar.setProgress(pct);
                    }
                })
                .addOnFailureListener(e -> {
                    tvMeta.setText(endStr);
                    tvProgress.setText("Team info unavailable");
                });
    }

    /**
     * Loads old member summary from challenges/{id}/members for compatibility.
     *
     * @param challengeId challenge ID
     * @param target challenge target
     * @param progBar progress bar
     * @param tvProgress progress text
     * @param tvMeta metadata text
     * @param endStr formatted end-date string
     */
    private void loadLegacyMemberSummary(String challengeId,
                                         @Nullable Long target,
                                         ProgressBar progBar,
                                         TextView tvProgress,
                                         TextView tvMeta,
                                         String endStr) {
        db.collection(COLLECTION_CHALLENGES)
                .document(challengeId)
                .collection(SUBCOLLECTION_LEGACY_MEMBERS)
                .get()
                .addOnSuccessListener(members -> {
                    int memberCount = members.size();

                    tvMeta.setText(endStr + "  ·  " + memberCount + " joined");
                    tvProgress.setText(memberCount == 0
                            ? "No teams yet. Create the first one!"
                            : memberCount + " member" + (memberCount == 1 ? "" : "s") + " joined");

                    if (target != null && target > 0) {
                        int pct = (int) Math.min((memberCount * 100.0 / target), 100);
                        progBar.setProgress(pct);
                    }
                });
    }

    /**
     * Updates a joined team card with team contribution progress.
     *
     * @param challengeId challenge ID
     * @param teamId team ID, or null for legacy progress
     * @param target challenge target
     * @param progBar progress bar
     * @param tvProgress progress text
     * @param tvMeta metadata text
     * @param endStr formatted end-date string
     */
    private void updateJoinedTeamProgress(String challengeId,
                                          @Nullable String teamId,
                                          @Nullable Long target,
                                          ProgressBar progBar,
                                          TextView tvProgress,
                                          TextView tvMeta,
                                          String endStr) {
        if (teamId == null) {
            updateLegacyTeamProgress(challengeId, target, progBar, tvProgress, tvMeta, endStr);
            return;
        }

        db.collection(COLLECTION_TEAMS)
                .document(teamId)
                .collection(SUBCOLLECTION_MEMBER_PROGRESS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        tvProgress.setText("Could not load team progress");
                        return;
                    }

                    int totalContribution = 0;
                    int memberCount = snapshots.size();

                    for (DocumentSnapshot memberProgress : snapshots.getDocuments()) {
                        Long contribution = memberProgress.getLong("contributionCount");
                        totalContribution += contribution != null ? contribution.intValue() : 0;
                    }

                    tvMeta.setText(endStr + "  ·  " + memberCount + " in your team");

                    if (target != null && target > 0) {
                        int pct = (int) Math.min((totalContribution * 100.0 / target), 100);
                        progBar.setProgress(pct);
                        tvProgress.setText(totalContribution + " / " + target
                                + " contributions  (" + pct + "%)");
                    } else {
                        progBar.setProgress(0);
                        tvProgress.setText(totalContribution + " team contributions");
                    }
                });
    }

    /**
     * Updates progress for older challenge membership records.
     *
     * @param challengeId challenge ID
     * @param target challenge target
     * @param progBar progress bar
     * @param tvProgress progress text
     * @param tvMeta metadata text
     * @param endStr formatted end-date string
     */
    private void updateLegacyTeamProgress(String challengeId,
                                          @Nullable Long target,
                                          ProgressBar progBar,
                                          TextView tvProgress,
                                          TextView tvMeta,
                                          String endStr) {
        db.collection(COLLECTION_CHALLENGES)
                .document(challengeId)
                .collection(SUBCOLLECTION_LEGACY_MEMBERS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        tvProgress.setText("Could not load progress");
                        return;
                    }

                    int totalContribution = 0;
                    int memberCount = snapshots.size();

                    for (DocumentSnapshot memberDoc : snapshots.getDocuments()) {
                        Long contribution = memberDoc.getLong("contributionCount");
                        totalContribution += contribution != null ? contribution.intValue() : 0;
                    }

                    tvMeta.setText(endStr + "  ·  " + memberCount + " joined");

                    if (target != null && target > 0) {
                        int pct = (int) Math.min((totalContribution * 100.0 / target), 100);
                        progBar.setProgress(pct);
                        tvProgress.setText(totalContribution + " / " + target
                                + " contributions  (" + pct + "%)");
                    } else {
                        tvProgress.setText(totalContribution + " contributions");
                    }
                });
    }

    /**
     * Shows the empty Browse message.
     *
     * @param message message to display
     */
    private void showBrowseEmpty(String message) {
        llChallengesList.removeAllViews();
        tvBrowseEmpty.setText(message);
        tvBrowseEmpty.setVisibility(View.VISIBLE);
    }

    /**
     * Shows the empty My Teams message.
     *
     * @param message message to display
     */
    private void showMyTeamsEmpty(String message) {
        llMyTeamsList.removeAllViews();
        tvMyTeamsEmpty.setText(message);
        tvMyTeamsEmpty.setVisibility(View.VISIBLE);
    }

    /**
     * Shows My Teams empty state after all async checks complete.
     *
     * @param finished number of completed checks
     * @param total total checks
     * @param shown number of teams shown
     */
    private void maybeShowMyTeamsEmpty(int finished, int total, int shown) {
        if (finished >= total && shown == 0) {
            showMyTeamsEmpty("You haven't joined any challenges yet.\nBrowse and tap Join to get started!");
        } else if (shown > 0) {
            tvMyTeamsEmpty.setVisibility(View.GONE);
        }
    }

    /**
     * Generates a random 4-character uppercase alphanumeric team code.
     *
     * @return team code string, e.g. GRNX
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