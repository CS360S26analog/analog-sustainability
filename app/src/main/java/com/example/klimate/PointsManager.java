/**
 * PointsManager.java
 *
 * Handles points-related logic for verified activity logs.
 * Calculates bonus points from community upvotes, awards base points
 * when a log is submitted, and listens for vote changes so the user's
 * total points and the log's bonus metadata stay in sync.
 *
 * Role in design: Part of the model/service layer.
 * Used by LogFragment when awarding base points and by validation/voting
 * features to update bonus points as community votes change.
 *
 * Outstanding issues: The overloaded attachVoteListener method currently
 * ignores its previousBonus parameter and simply delegates to the main
 * listener method.
 *
 * @author Haroon
 */
package com.example.klimate;

import android.util.Log;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class PointsManager {

    private static final String TAG = "PointsManager";
    private final FirebaseFirestore db;

    /**
     * Creates a PointsManager backed by the shared Firebase Firestore instance.
     */
    public PointsManager() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Calculates bonus points earned from the number of upvotes on a log.
     * Each upvote is currently worth 2 bonus points.
     * Negative input is treated defensively as zero.
     *
     * @param voteCount the number of upvotes received
     * @return the calculated bonus points
     */
    public int calculateBonusPoints(int voteCount) {
        if (voteCount < 0) return 0;
        return voteCount * 2;
    }

    /**
     * Static helper version of the bonus-points calculation.
     * Useful for unit tests because it does not require a Firebase instance.
     *
     * @param voteCount the number of upvotes received
     * @return the calculated bonus points
     */
    public static int calculateBonusPointsStatic(int voteCount) {
        if (voteCount < 0) return 0;
        return voteCount * 2;
    }

    /**
     * Awards base points to a user immediately after a log is submitted.
     * The user's totalPoints field is incremented in Firestore.
     *
     * @param userId the UID of the user receiving the points
     * @param basePoints the number of base points to add
     */
    public void awardBasePoints(String userId, int basePoints) {
        db.collection("users")
                .document(userId)
                .update("totalPoints", FieldValue.increment(basePoints))
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Base points added: +" + basePoints + " to user " + userId)
                )
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to add base points", e)
                );
    }

    /**
     * Attaches a Firestore snapshot listener for upvotes on a specific log.
     * Whenever the upvote count changes, this method recalculates the bonus
     * points, updates the activity log's bonusPoints and voteCount fields,
     * and applies only the points delta to the owning user's total points.
     *
     * @param logId the ID of the activity log to observe
     * @param userId the UID of the user who owns the log
     */
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
                                            .update("totalPoints", FieldValue.increment(delta));
                                }
                            });
                });
    }

    /**
     * Overloaded compatibility method for callers that still pass a previous
     * bonus value. The current implementation ignores that parameter and
     * delegates to the main vote listener method.
     *
     * @param logId the ID of the activity log to observe
     * @param userId the UID of the user who owns the log
     * @param previousBonus previously known bonus value not currently used
     */
    public void attachVoteListener(String logId, String userId, int previousBonus) {
        attachVoteListener(logId, userId);
    }
}