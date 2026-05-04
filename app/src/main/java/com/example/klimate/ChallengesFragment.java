/**
 * ChallengesFragment.java
 *
 * Displays active campus sustainability challenges and allows students
 * to browse challenges, create teams, join teams using a code, and view
 * joined team progress from the My Teams tab.
 *
 * Room-first implementation:
 * - Browse mode renders cached active challenges immediately.
 * - My Teams mode renders cached joined teams immediately.
 * - Firestore refreshes in the background and replaces the local cache.
 * - Per-card progress still updates from Firestore listeners so progress stays fresh.
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
 * Compatibility mirror:
 * challenges/{challengeId}/members/{uid}
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

import com.example.klimate.local.AppDatabase;
import com.example.klimate.local.ChallengeCacheDao;
import com.example.klimate.local.ChallengeCacheEntity;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ChallengesFragment extends Fragment {

    private static final String COLLECTION_CHALLENGES = "challenges";
    private static final String COLLECTION_TEAMS = "teams";
    private static final String SUBCOLLECTION_MEMBER_PROGRESS = "memberProgress";
    private static final String SUBCOLLECTION_LEGACY_MEMBERS = "members";

    private static final String CACHE_MODE_BROWSE = "browse";
    private static final String CACHE_MODE_TEAMS = "teams";

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private AppDatabase appDatabase;
    private ChallengeCacheDao challengeCacheDao;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private LinearLayout llChallengesList;
    private LinearLayout llMyTeamsList;
    private LinearLayout panelBrowse;
    private LinearLayout panelMyTeams;
    private TextView tabBrowse;
    private TextView tabMyTeams;
    private TextView tvBrowseEmpty;
    private TextView tvMyTeamsEmpty;

    /**
     * Used to ignore old async callbacks when a newer load has started.
     */
    private int renderVersion = 0;

    private interface AlreadyJoinedCallback {
        void onResult(boolean alreadyJoined);
    }

    /**
     * Simple UI model that can come from either Firestore or Room.
     */
    private static class ChallengeCardData {
        String challengeId;
        String name;
        String description;
        long endDateMillis;
        Long target;
        boolean joined;
        String teamId;
        String joinCode;
        long sortMillis;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        String mode = getArguments() != null ? getArguments().getString("mode") : CACHE_MODE_BROWSE;

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        appDatabase = AppDatabase.getInstance(requireContext());
        challengeCacheDao = appDatabase.challengeCacheDao();

        View view;

        if (CACHE_MODE_TEAMS.equals(mode)) {
            view = inflater.inflate(R.layout.fragment_challenges_my_teams, container, false);

            llMyTeamsList = view.findViewById(R.id.ll_my_teams_list);
            tvMyTeamsEmpty = view.findViewById(R.id.tv_my_teams_empty);

            loadMyTeams();
        } else {
            view = inflater.inflate(R.layout.fragment_challenges, container, false);

            llChallengesList = view.findViewById(R.id.ll_challenges_list);
            tvBrowseEmpty = view.findViewById(R.id.tv_browse_empty);

            loadActiveChallenges();
        }

        return view;
    }

    /**
     * Keeps compatibility if an older layout still uses internal Browse/My Teams tabs.
     *
     * @param showBrowse true to show Browse, false to show My Teams
     */
    private void showTab(boolean showBrowse) {
        if (showBrowse) {
            if (panelBrowse != null) panelBrowse.setVisibility(View.VISIBLE);
            if (panelMyTeams != null) panelMyTeams.setVisibility(View.GONE);

            if (tabBrowse != null) {
                tabBrowse.setBackgroundResource(R.drawable.bg_tab_selected);
                tabBrowse.setTextColor(getResources().getColor(R.color.color_green_header, null));
            }

            if (tabMyTeams != null) {
                tabMyTeams.setBackgroundColor(getResources().getColor(R.color.white, null));
                tabMyTeams.setTextColor(getResources().getColor(R.color.color_text_secondary, null));
            }

            loadActiveChallenges();
        } else {
            if (panelBrowse != null) panelBrowse.setVisibility(View.GONE);
            if (panelMyTeams != null) panelMyTeams.setVisibility(View.VISIBLE);

            if (tabMyTeams != null) {
                tabMyTeams.setBackgroundResource(R.drawable.bg_tab_selected);
                tabMyTeams.setTextColor(getResources().getColor(R.color.color_green_header, null));
            }

            if (tabBrowse != null) {
                tabBrowse.setBackgroundColor(getResources().getColor(R.color.white, null));
                tabBrowse.setTextColor(getResources().getColor(R.color.color_text_secondary, null));
            }

            loadMyTeams();
        }
    }

    // ─────────────────────────────────────────────────
    // BROWSE — ROOM FIRST, FIRESTORE REFRESH SECOND
    // ─────────────────────────────────────────────────

    /**
     * Loads all active challenges.
     *
     * Room renders first so the tab does not appear blank while Firestore responds.
     */
    private void loadActiveChallenges() {
        if (currentUser == null) {
            showBrowseEmpty("Please log in to view challenges.");
            return;
        }

        int version = ++renderVersion;
        String uid = currentUser.getUid();

        showBrowseLoading();

        // 1. Room first.
        executor.execute(() -> {
            List<ChallengeCacheEntity> cached = challengeCacheDao.getItems(uid, CACHE_MODE_BROWSE);

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (!isCurrent(version)) return;

                if (!cached.isEmpty()) {
                    renderBrowseCards(toCardDataList(cached, false));
                }
            });
        });

        // 2. Firestore refresh second.
        db.collection(COLLECTION_CHALLENGES)
                .whereEqualTo("active", true)
                .orderBy("startDate", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    List<ChallengeCardData> freshCards = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        freshCards.add(mapChallengeDocument(doc, false, null, null, getStartMillis(doc)));
                    }

                    executor.execute(() ->
                            challengeCacheDao.replaceMode(
                                    uid,
                                    CACHE_MODE_BROWSE,
                                    toCacheEntities(uid, CACHE_MODE_BROWSE, freshCards)
                            )
                    );

                    if (!isCurrent(version)) return;

                    if (freshCards.isEmpty()) {
                        showBrowseEmpty("No active challenges right now.\nCheck back soon! 🌿");
                    } else {
                        renderBrowseCards(freshCards);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || !isCurrent(version)) return;

                    // Keep cached cards if they are already visible.
                    if (llChallengesList == null || llChallengesList.getChildCount() == 0) {
                        showBrowseEmpty("Could not load challenges: " + e.getMessage());
                    }
                });
    }

    private void renderBrowseCards(@NonNull List<ChallengeCardData> cards) {
        if (llChallengesList == null || tvBrowseEmpty == null) return;

        llChallengesList.removeAllViews();

        if (cards.isEmpty()) {
            showBrowseEmpty("No active challenges right now.\nCheck back soon! 🌿");
            return;
        }

        tvBrowseEmpty.setVisibility(View.GONE);

        for (ChallengeCardData data : cards) {
            addChallengeCard(data);
        }
    }

    private void showBrowseLoading() {
        if (llChallengesList != null) {
            llChallengesList.removeAllViews();
        }
        if (tvBrowseEmpty != null) {
            tvBrowseEmpty.setText("Loading challenges…");
            tvBrowseEmpty.setVisibility(View.VISIBLE);
        }
    }

    // ─────────────────────────────────────────────────
    // MY TEAMS — ROOM FIRST, FIRESTORE REFRESH SECOND
    // ─────────────────────────────────────────────────

    /**
     * Loads teams joined by the current user.
     *
     * Room renders first so My Teams appears immediately on repeated visits.
     */
    private void loadMyTeams() {
        if (currentUser == null) {
            showMyTeamsEmpty("Please log in to view your teams.");
            return;
        }

        int version = ++renderVersion;
        String uid = currentUser.getUid();

        showMyTeamsLoading();

        // 1. Room first.
        executor.execute(() -> {
            List<ChallengeCacheEntity> cached = challengeCacheDao.getItems(uid, CACHE_MODE_TEAMS);

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (!isCurrent(version)) return;

                if (!cached.isEmpty()) {
                    renderMyTeamCards(toCardDataList(cached, true));
                }
            });
        });

        // 2. Firestore refresh second.
        db.collection(COLLECTION_TEAMS)
                .whereArrayContains("memberUids", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(teamSnapshots -> {
                    if (!isAdded() || !isCurrent(version)) return;

                    if (teamSnapshots.isEmpty()) {
                        loadLegacyJoinedChallenges(uid, version);
                        return;
                    }

                    final int totalTeams = teamSnapshots.size();
                    final int[] finished = {0};
                    List<ChallengeCardData> freshCards = new ArrayList<>();

                    for (QueryDocumentSnapshot teamDoc : teamSnapshots) {
                        String challengeId = teamDoc.getString("challengeId");
                        String joinCode = teamDoc.getString("joinCode");
                        String teamId = teamDoc.getId();
                        long teamSortMillis = getCreatedAtMillis(teamDoc);

                        if (challengeId == null || challengeId.trim().isEmpty()) {
                            finished[0]++;
                            maybeFinishMyTeamsRefresh(uid, version, finished[0], totalTeams, freshCards);
                            continue;
                        }

                        db.collection(COLLECTION_CHALLENGES)
                                .document(challengeId)
                                .get()
                                .addOnSuccessListener(challengeDoc -> {
                                    Boolean active = challengeDoc.getBoolean("active");
                                    if (challengeDoc.exists() && Boolean.TRUE.equals(active)) {
                                        freshCards.add(mapChallengeDocument(
                                                challengeDoc,
                                                true,
                                                teamId,
                                                joinCode,
                                                teamSortMillis
                                        ));
                                    }
                                })
                                .addOnCompleteListener(task -> {
                                    finished[0]++;
                                    maybeFinishMyTeamsRefresh(uid, version, finished[0], totalTeams, freshCards);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || !isCurrent(version)) return;

                    if (llMyTeamsList == null || llMyTeamsList.getChildCount() == 0) {
                        showMyTeamsEmpty("Could not load your teams: " + e.getMessage());
                    }
                });
    }

    private void maybeFinishMyTeamsRefresh(@NonNull String uid,
                                           int version,
                                           int finished,
                                           int total,
                                           @NonNull List<ChallengeCardData> freshCards) {
        if (finished < total) return;
        if (!isAdded() || !isCurrent(version)) return;

        executor.execute(() ->
                challengeCacheDao.replaceMode(
                        uid,
                        CACHE_MODE_TEAMS,
                        toCacheEntities(uid, CACHE_MODE_TEAMS, freshCards)
                )
        );

        if (freshCards.isEmpty()) {
            showMyTeamsEmpty("You haven't joined any challenges yet.\nBrowse and tap Join to get started!");
        } else {
            renderMyTeamCards(freshCards);
        }
    }

    private void renderMyTeamCards(@NonNull List<ChallengeCardData> cards) {
        if (llMyTeamsList == null || tvMyTeamsEmpty == null) return;

        llMyTeamsList.removeAllViews();

        if (cards.isEmpty()) {
            showMyTeamsEmpty("You haven't joined any challenges yet.\nBrowse and tap Join to get started!");
            return;
        }

        tvMyTeamsEmpty.setVisibility(View.GONE);

        for (ChallengeCardData data : cards) {
            addChallengeCard(data);
        }
    }

    private void showMyTeamsLoading() {
        if (llMyTeamsList != null) {
            llMyTeamsList.removeAllViews();
        }
        if (tvMyTeamsEmpty != null) {
            tvMyTeamsEmpty.setText("Loading your teams…");
            tvMyTeamsEmpty.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Loads old challenge membership records for users who joined before
     * the teams collection was added.
     *
     * @param uid current user UID
     */
    private void loadLegacyJoinedChallenges(String uid, int version) {
        db.collection(COLLECTION_CHALLENGES)
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(challenges -> {
                    if (!isAdded() || !isCurrent(version)) return;

                    if (challenges.isEmpty()) {
                        executor.execute(() ->
                                challengeCacheDao.replaceMode(uid, CACHE_MODE_TEAMS, new ArrayList<>())
                        );
                        showMyTeamsEmpty("You haven't joined any challenges yet.\nBrowse and tap Join to get started!");
                        return;
                    }

                    final int total = challenges.size();
                    final int[] finished = {0};
                    List<ChallengeCardData> freshCards = new ArrayList<>();

                    for (QueryDocumentSnapshot challengeDoc : challenges) {
                        db.collection(COLLECTION_CHALLENGES)
                                .document(challengeDoc.getId())
                                .collection(SUBCOLLECTION_LEGACY_MEMBERS)
                                .document(uid)
                                .get()
                                .addOnSuccessListener(memberDoc -> {
                                    if (memberDoc.exists()) {
                                        String legacyCode = memberDoc.getString("teamCode");
                                        freshCards.add(mapChallengeDocument(
                                                challengeDoc,
                                                true,
                                                null,
                                                legacyCode,
                                                getStartMillis(challengeDoc)
                                        ));
                                    }
                                })
                                .addOnCompleteListener(task -> {
                                    finished[0]++;
                                    maybeFinishLegacyMyTeamsRefresh(uid, version, finished[0], total, freshCards);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || !isCurrent(version)) return;

                    if (llMyTeamsList == null || llMyTeamsList.getChildCount() == 0) {
                        showMyTeamsEmpty("Could not load your teams: " + e.getMessage());
                    }
                });
    }

    private void maybeFinishLegacyMyTeamsRefresh(@NonNull String uid,
                                                 int version,
                                                 int finished,
                                                 int total,
                                                 @NonNull List<ChallengeCardData> freshCards) {
        if (finished < total) return;
        if (!isAdded() || !isCurrent(version)) return;

        executor.execute(() ->
                challengeCacheDao.replaceMode(
                        uid,
                        CACHE_MODE_TEAMS,
                        toCacheEntities(uid, CACHE_MODE_TEAMS, freshCards)
                )
        );

        if (freshCards.isEmpty()) {
            showMyTeamsEmpty("You haven't joined any challenges yet.\nBrowse and tap Join to get started!");
        } else {
            renderMyTeamCards(freshCards);
        }
    }

    // ─────────────────────────────────────────────────
    // CARD RENDERING
    // ─────────────────────────────────────────────────

    /**
     * Creates and displays one challenge card.
     */
    private void addChallengeCard(@NonNull ChallengeCardData data) {
        boolean joined = data.joined;

        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_challenge_card,
                        joined ? llMyTeamsList : llChallengesList,
                        false);

        String challengeId = data.challengeId;
        String safeName = data.name != null && !data.name.trim().isEmpty()
                ? data.name
                : "Unnamed Challenge";
        String safeDescription = data.description != null ? data.description : "";
        Long target = data.target;
        String teamId = data.teamId;
        String joinCode = data.joinCode;

        TextView tvName = card.findViewById(R.id.tv_challenge_name);
        TextView tvMeta = card.findViewById(R.id.tv_challenge_meta);
        TextView tvDesc = card.findViewById(R.id.tv_challenge_desc);
        TextView btnJoin = card.findViewById(R.id.btn_join_challenge);
        ProgressBar progBar = card.findViewById(R.id.progress_challenge_bar);
        TextView tvProgress = card.findViewById(R.id.tv_challenge_progress);
        LinearLayout rowCode = card.findViewById(R.id.row_team_code);
        TextView tvCode = card.findViewById(R.id.tv_team_code);
        TextView btnShare = card.findViewById(R.id.btn_share_code);

        tvName.setText(safeName);
        tvDesc.setText(safeDescription);

        final String endStr;
        if (data.endDateMillis > 0) {
            endStr = "Ends " + new SimpleDateFormat("dd MMM", Locale.getDefault())
                    .format(new java.util.Date(data.endDateMillis));
        } else {
            endStr = "No end date";
        }

        tvMeta.setText(endStr);
        tvProgress.setText("Loading progress...");
        progBar.setProgress(0);

        if (joined) {
            btnJoin.setVisibility(View.GONE);
            rowCode.setVisibility(View.VISIBLE);

            String visibleCode = joinCode != null && !joinCode.trim().isEmpty() ? joinCode : "—";
            tvCode.setText("Team code: " + visibleCode);
            setShareButton(btnShare, safeName, visibleCode);

            updateJoinedTeamProgress(challengeId, teamId, target, progBar, tvProgress, tvMeta, endStr);

            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                if (teamId == null || teamId.trim().isEmpty()) {
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

    // ─────────────────────────────────────────────────
    // JOIN / CREATE TEAM
    // ─────────────────────────────────────────────────

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

                                        invalidateChallengeCacheForCurrentUser();
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

                                                    invalidateChallengeCacheForCurrentUser();
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

    // ─────────────────────────────────────────────────
    // LIVE PROGRESS / SUMMARIES
    // ─────────────────────────────────────────────────

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

    private void updateJoinedTeamProgress(String challengeId,
                                          @Nullable String teamId,
                                          @Nullable Long target,
                                          ProgressBar progBar,
                                          TextView tvProgress,
                                          TextView tvMeta,
                                          String endStr) {
        if (teamId == null || teamId.trim().isEmpty()) {
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

    // ─────────────────────────────────────────────────
    // CACHE MAPPING
    // ─────────────────────────────────────────────────

    private ChallengeCardData mapChallengeDocument(@NonNull DocumentSnapshot doc,
                                                   boolean joined,
                                                   @Nullable String teamId,
                                                   @Nullable String joinCode,
                                                   long sortMillis) {
        ChallengeCardData data = new ChallengeCardData();
        data.challengeId = doc.getId();
        data.name = doc.getString("name");
        data.description = doc.getString("description");

        Timestamp endDate = doc.getTimestamp("endDate");
        data.endDateMillis = endDate != null ? endDate.toDate().getTime() : 0L;

        data.target = doc.getLong("target");
        data.joined = joined;
        data.teamId = teamId;
        data.joinCode = joinCode;
        data.sortMillis = sortMillis > 0 ? sortMillis : System.currentTimeMillis();

        return data;
    }

    private List<ChallengeCardData> toCardDataList(@NonNull List<ChallengeCacheEntity> entities,
                                                   boolean joined) {
        List<ChallengeCardData> result = new ArrayList<>();

        for (ChallengeCacheEntity entity : entities) {
            ChallengeCardData data = new ChallengeCardData();
            data.challengeId = entity.challengeId;
            data.name = entity.name;
            data.description = entity.description;
            data.endDateMillis = entity.endDateMillis;
            data.target = entity.target;
            data.joined = joined;
            data.teamId = entity.teamId;
            data.joinCode = entity.joinCode;
            data.sortMillis = entity.sortMillis;
            result.add(data);
        }

        return result;
    }

    private List<ChallengeCacheEntity> toCacheEntities(@NonNull String ownerUid,
                                                       @NonNull String mode,
                                                       @NonNull List<ChallengeCardData> cards) {
        List<ChallengeCacheEntity> result = new ArrayList<>();

        for (ChallengeCardData card : cards) {
            String cacheId = ownerUid
                    + "_" + mode
                    + "_" + safe(card.challengeId)
                    + "_" + safe(card.teamId);

            result.add(new ChallengeCacheEntity(
                    cacheId,
                    ownerUid,
                    mode,
                    safe(card.challengeId),
                    safe(card.teamId),
                    safe(card.joinCode),
                    safe(card.name),
                    safe(card.description),
                    card.endDateMillis,
                    card.target,
                    card.sortMillis,
                    System.currentTimeMillis()
            ));
        }

        return result;
    }

    private long getStartMillis(@NonNull DocumentSnapshot doc) {
        Timestamp ts = doc.getTimestamp("startDate");
        return ts != null ? ts.toDate().getTime() : System.currentTimeMillis();
    }

    private long getCreatedAtMillis(@NonNull DocumentSnapshot doc) {
        Timestamp ts = doc.getTimestamp("createdAt");
        return ts != null ? ts.toDate().getTime() : System.currentTimeMillis();
    }

    private void invalidateChallengeCacheForCurrentUser() {
        if (currentUser == null) return;
        String uid = currentUser.getUid();

        executor.execute(() -> challengeCacheDao.deleteForOwner(uid));
    }

    // ─────────────────────────────────────────────────
    // EMPTY STATES / HELPERS
    // ─────────────────────────────────────────────────

    private void showBrowseEmpty(String message) {
        if (llChallengesList != null) {
            llChallengesList.removeAllViews();
        }
        if (tvBrowseEmpty != null) {
            tvBrowseEmpty.setText(message);
            tvBrowseEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void showMyTeamsEmpty(String message) {
        if (llMyTeamsList != null) {
            llMyTeamsList.removeAllViews();
        }
        if (tvMyTeamsEmpty != null) {
            tvMyTeamsEmpty.setText(message);
            tvMyTeamsEmpty.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Kept for compatibility with older async flow.
     */
    private void maybeShowMyTeamsEmpty(int finished, int total, int shown) {
        if (finished >= total && shown == 0) {
            showMyTeamsEmpty("You haven't joined any challenges yet.\nBrowse and tap Join to get started!");
        } else if (shown > 0 && tvMyTeamsEmpty != null) {
            tvMyTeamsEmpty.setVisibility(View.GONE);
        }
    }

    private boolean isCurrent(int version) {
        return version == renderVersion;
    }

    private String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    private String generateTeamCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < 4; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }

        return code.toString();
    }
}
