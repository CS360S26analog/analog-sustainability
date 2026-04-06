/**
 * PointsManager.java
 *
 * Handles points calculation and awarding for verified activity logs.
 * Listens for vote changes on a given log and updates bonusPoints on
 * the ActivityLog document and totalPoints on the User document.
 * Also awards base points when any log is submitted.
 *
 * Role in design: Service class in the Model layer. Reacts to votes
 * written by the community verification flow. Updates Firestore directly.
 *
 * Outstanding issues: Point cap per activity type not yet enforced —
 * requires Activity model class to be implemented.
 *
 * @author Haroon
 * @author Izza
 */
package com.example.klimate;

import com.google.firebase.firestore.FirebaseFirestore;

public class PointsManager {

    private FirebaseFirestore db;

    /**
     * Constructs a PointsManager and initialises the Firestore instance.
     */
    public PointsManager() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Calculates bonus points earned based on number of upvotes received.
     * Each upvote earns 2 bonus points. Returns 0 for negative input.
     *
     * TODO: replace with logarithmic scaling + downvote penalty in full release.
     * Current linear formula is intentionally simple for Half checkpoint.
     *
     * @param voteCount number of upvotes on a verified log
     * @return bonus points to award
     */
    public int calculateBonusPoints(int voteCount) {
        if (voteCount < 0) return 0;
        return voteCount * 2;
    }

    /**
     * Static version of calculateBonusPoints for unit testing without
     * requiring a PointsManager instance (avoids Firebase init in tests).
     *
     * @param voteCount number of upvotes
     * @return bonus points earned
     */
    public static int calculateBonusPointsStatic(int voteCount) {
        if (voteCount < 0) return 0;
        return voteCount * 2;
    }

    /**
     * Awards base points to a user when they submit any log (quick or verified).
     * Reads the current totalPoints from Firestore, adds basePoints, and writes
     * the updated value back. Should be called by LogFragment immediately after
     * a successful ActivityLog write.
     *
     * @param userId     UID of the student who submitted the log
     * @param basePoints base points for the activity type
     */
    public void awardBasePoints(String userId, int basePoints) {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) return;

                    Long current = userDoc.getLong("totalPoints");
                    int currentTotal = (current != null) ? current.intValue() : 0;
                    int updatedTotal = currentTotal + basePoints;

                    db.collection("users").document(userId)
                            .update("totalPoints", updatedTotal);
                });
    }

    /**
     * Attaches a real-time Firestore listener to the votes collection
     * for a specific log. Recalculates and updates bonusPoints on the
     * ActivityLog and totalPoints on the User whenever votes change.
     *
     * @param logId         the Firestore document ID of the ActivityLog
     * @param userId        the UID of the student who owns the log
     * @param previousBonus the bonus points this log was previously awarded,
     *                      so we can subtract the old value before adding the new one
     */
    public void attachVoteListener(String logId, String userId, int previousBonus) {
        db.collection("votes")
                .whereEqualTo("logId", logId)
                .whereEqualTo("isUpvote", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    int voteCount   = snapshots.size();
                    int bonusPoints = calculateBonusPoints(voteCount);

                    // Step 1: update bonusPoints and voteCount on the ActivityLog
                    db.collection("activity_logs").document(logId)
                            .update("bonusPoints", bonusPoints, "voteCount", voteCount);

                    // Step 2: adjust the user's totalPoints correctly
                    db.collection("users").document(userId)
                            .get()
                            .addOnSuccessListener(userDoc -> {
                                if (!userDoc.exists()) return;

                                Long current = userDoc.getLong("totalPoints");
                                int currentTotal = (current != null) ? current.intValue() : 0;

                                int updatedTotal = currentTotal - previousBonus + bonusPoints;
                                if (updatedTotal < 0) updatedTotal = 0;

                                db.collection("users").document(userId)
                                        .update("totalPoints", updatedTotal);
                            });
                });
    }
}