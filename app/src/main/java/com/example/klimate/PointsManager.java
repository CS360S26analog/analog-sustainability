package com.example.klimate;

import android.util.Log;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class PointsManager {

    private static final String TAG = "PointsManager";
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
                .update("totalPoints", FieldValue.increment(basePoints))
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Base points added: +" + basePoints + " to user " + userId)
                )
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to add base points", e)
                );
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