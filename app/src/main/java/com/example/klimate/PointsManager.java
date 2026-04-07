/**
 * PointsManager.java
 *
 * Handles points calculation and awarding for verified activity logs.
 * Listens for vote changes on a given log and updates bonusPoints on
 * the ActivityLog document and totalPoints on the User document.
 * Also awards base points when any log is submitted.
 *
 * @author Haroon
 * @author Izza
 */
package com.example.klimate;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class PointsManager {

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

    public void awardBasePoints(String userId, int basePoints) {
        db.collection("users")
                .document(userId)
                .update("totalPoints", FieldValue.increment(basePoints));
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
                                int currentBonusPoints = currentBonusLong != null ? currentBonusLong.intValue() : 0;

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

	public void attachVoteListener(String logId, String userId, int previousBonus) {
	    attachVoteListener(logId, userId);
	}
}