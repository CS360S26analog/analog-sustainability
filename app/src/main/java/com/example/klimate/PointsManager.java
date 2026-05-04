package com.example.klimate;

import android.content.Context;
import android.util.Log;

import com.example.klimate.local.AppDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Handles points-related logic for verified activity logs.
 * Calculates bonus points from community upvotes, awards base points,
 * and auto-awards milestone badges when users cross point thresholds.
 */
public class PointsManager {

    private static final String TAG = "PointsManager";
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final FirebaseFirestore db;

    public PointsManager() {
        db = FirebaseFirestore.getInstance();
    }

    public int calculateBonusPoints(int voteCount) {
        if (voteCount < 0) return 0;
        return voteCount * 2;
    }

    public static int calculateBonusPointsStatic(int voteCount) {
        if (voteCount < 0) return 0;
        return voteCount * 2;
    }

    /**
     * Calculates net bonus points from upvotes and downvotes.
     *
     * Rules:
     * - each upvote gives +2 points
     * - each downvote subtracts 1 point
     * - negative inputs are treated as 0
     * - final bonus never goes below 0
     */
    public static int calculateNetBonusPoints(int upvoteCount, int downvoteCount) {
        int safeUpvotes = Math.max(0, upvoteCount);
        int safeDownvotes = Math.max(0, downvoteCount);

        int netBonus = (safeUpvotes * 2) - safeDownvotes;

        return Math.max(0, netBonus);
    }

    public void awardBasePoints(Context context, String userId, int basePoints) {
        // ── Room first ───────────────────────────────────────────────────────
        executor.execute(() -> {
            AppDatabase.getInstance(context).userDao().incrementPoints(userId, basePoints);
        });

        // ── Firestore ────────────────────────────────────────────────────────
        db.collection("users")
                .document(userId)
                .update("totalPoints", FieldValue.increment(basePoints))
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Base points added: +" + basePoints + " to user " + userId);
                    checkBadgesForUser(userId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to add base points", e));
    }

    private void checkBadgesForUser(String userId) {
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    Long totalPointsLong = userDoc.getLong("totalPoints");
                    int totalPoints = totalPointsLong != null ? totalPointsLong.intValue() : 0;
                    checkAndAwardBadges(userId, totalPoints);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to check user badges", e));
    }

    public void checkAndAwardBadges(String userId, int totalPoints) {
        for (BadgeDefinition badge : BadgeDefinition.BADGE_CATALOGUE) {
            if (totalPoints >= badge.thresholdPoints) {
                awardBadgeIfMissing(userId, badge);
            }
        }
    }

    private void awardBadgeIfMissing(String userId, BadgeDefinition badge) {
        db.collection("users")
                .document(userId)
                .collection("badges")
                .document(badge.id)
                .get()
                .addOnSuccessListener(existingBadge -> {
                    if (existingBadge.exists()) {
                        return;
                    }

                    Map<String, Object> badgeData = new HashMap<>();
                    badgeData.put("id", badge.id);
                    badgeData.put("name", badge.name);
                    badgeData.put("emoji", badge.emoji);
                    badgeData.put("thresholdPoints", badge.thresholdPoints);
                    badgeData.put("description", badge.description);
                    badgeData.put("earnedAt", FieldValue.serverTimestamp());

                    db.collection("users")
                            .document(userId)
                            .collection("badges")
                            .document(badge.id)
                            .set(badgeData)
                            .addOnSuccessListener(unused ->
                                    Log.d(TAG, "Badge awarded: " + badge.id + " to user " + userId))
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "Failed to award badge " + badge.id, e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to check existing badge", e));
    }

    public void attachVoteListener(String logId, String userId) {
        db.collection("votes")
                .whereEqualTo("logId", logId)
                .whereEqualTo("isUpvote", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    int voteCount = snapshots.size();
                    int newBonusPoints = calculateBonusPoints(voteCount);

                    db.collection("activity_logs")
                            .document(logId)
                            .get()
                            .addOnSuccessListener(logDoc -> {
                                if (!logDoc.exists()) return;

                                Long currentBonusLong = logDoc.getLong("bonusPoints");
                                int currentBonusPoints = currentBonusLong != null
                                        ? currentBonusLong.intValue()
                                        : 0;

                                int delta = newBonusPoints - currentBonusPoints;

                                db.collection("activity_logs")
                                        .document(logId)
                                        .update(
                                                "bonusPoints", newBonusPoints,
                                                "voteCount", voteCount
                                        );

                                if (delta != 0) {
                                    db.collection("users")
                                            .document(userId)
                                            .update("totalPoints", FieldValue.increment(delta))
                                            .addOnSuccessListener(unused -> checkBadgesForUser(userId));
                                }
                            });
                });
    }

    public void attachVoteListener(String logId, String userId, int previousBonus) {
        attachVoteListener(logId, userId);
    }
}