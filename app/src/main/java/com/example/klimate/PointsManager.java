/**
 * PointsManager.java
 *
 * Handles points-related logic for verified activity logs.
 * Calculates bonus points from community upvotes and downvotes,
 * awards base points when a log is submitted, and listens for
 * vote changes so the user's total points and the log's bonus
 * metadata stay in sync.
 *
 * Role in design: Part of the model/service layer.
 * Used by LogFragment when awarding base points and by
 * ValidationFeedFragment to update bonus points as votes change.
 *
 * Outstanding issues: None.
 *
 * @author Haroon
 */
package com.example.klimate;

import android.util.Log;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

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
     * Calculates bonus points earned from upvotes only.
     * Each upvote is worth 2 bonus points.
     * Negative input is treated as zero.
     *
     * @param voteCount the number of upvotes received
     * @return the calculated bonus points
     */
    public int calculateBonusPoints(int voteCount) {
        if (voteCount < 0) return 0;
        return voteCount * 2;
    }

    /**
     * Static helper version of the basic bonus-points calculation.
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
     * Calculates net bonus points accounting for both upvotes and downvotes.
     * Each upvote contributes +2, each downvote subtracts -1.
     * Result is floored at 0 — bonus points can never go negative.
     *
     * @param upvotes   the number of upvotes received
     * @param downvotes the number of downvotes received
     * @return the net bonus points, minimum 0
     */
    public static int calculateNetBonusPoints(int upvotes, int downvotes) {
        return Math.max(0, (upvotes * 2) - (downvotes * 1));
    }

    /**
     * Awards base points to a user immediately after a log is submitted.
     * The user's totalPoints field is incremented in Firestore.
     *
     * @param userId     the UID of the user receiving the points
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
     * Attaches a Firestore snapshot listener for all votes on a specific log.
     * Whenever votes change, this method:
     * - Counts upvotes and downvotes separately
     * - Recalculates net bonus points using calculateNetBonusPoints()
     * - Updates bonusPoints and voteCount on the activity log
     * - Applies only the delta to the owning user's totalPoints
     * - If net vote count reaches -5 or below, sets the log status
     *   to "rejected" and deducts all previously awarded bonus points
     *
     * @param logId  the Firestore document ID of the activity log to observe
     * @param userId the UID of the user who owns the log
     */
    public void attachVoteListener(String logId, String userId) {
        db.collection("votes")
                .whereEqualTo("logId", logId)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    int upvoteCount = 0;
                    int downvoteCount = 0;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        Boolean isUpvote = doc.getBoolean("isUpvote");
                        if (Boolean.TRUE.equals(isUpvote)) {
                            upvoteCount++;
                        } else {
                            downvoteCount++;
                        }
                    }

                    int newBonusPoints = calculateNetBonusPoints(upvoteCount, downvoteCount);
                    int netVoteCount = upvoteCount - downvoteCount;

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

                                // Update voteCount and bonusPoints on the log
                                db.collection("activity_logs")
                                        .document(logId)
                                        .update(
                                                "bonusPoints", newBonusPoints,
                                                "voteCount", netVoteCount
                                        );

                                // If net downvotes reach -5, reject the log
                                if (netVoteCount <= -5) {
                                    db.collection("activity_logs")
                                            .document(logId)
                                            .update("status", "rejected");

                                    // Deduct all bonus points previously awarded to the owner
                                    if (currentBonusPoints > 0) {
                                        db.collection("users")
                                                .document(userId)
                                                .update("totalPoints",
                                                        FieldValue.increment(-currentBonusPoints));
                                    }

                                    Log.d(TAG, "Log " + logId + " rejected due to downvotes");
                                    return;
                                }

                                // Apply the bonus point delta to user's total
                                if (delta != 0) {
                                    db.collection("users")
                                            .document(userId)
                                            .update("totalPoints", FieldValue.increment(delta));
                                }
                            });
                });
    }

    /**
     * Overloaded compatibility method — ignores previousBonus and delegates
     * to the main attachVoteListener method.
     *
     * @param logId         the Firestore document ID of the activity log
     * @param userId        the UID of the user who owns the log
     * @param previousBonus previously known bonus value — not used
     */
    public void attachVoteListener(String logId, String userId, int previousBonus) {
        attachVoteListener(logId, userId);
    }
}