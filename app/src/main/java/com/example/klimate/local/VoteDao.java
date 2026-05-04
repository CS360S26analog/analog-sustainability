// ─────────────────────────────────────────────────────────────────────────────
// VoteDao.java
// Place in: com/example/klimate/local/
// ─────────────────────────────────────────────────────────────────────────────
package com.example.klimate.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface VoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(VoteEntity vote);

    /** Pending or failed votes that still need to be pushed to Firestore. */
    @Query("SELECT * FROM votes WHERE syncStatus = 'pending' OR syncStatus = 'failed'")
    List<VoteEntity> getPendingVotes();

    /** Check if the current user has already voted locally on a log. */
    @Query("SELECT COUNT(*) FROM votes WHERE logId = :logId AND voterId = :voterId")
    int hasVoted(String logId, String voterId);

    /** Upvote count for a logId (for local UI display). */
    @Query("SELECT COUNT(*) FROM votes WHERE logId = :logId AND isUpvote = 1")
    int getUpvoteCount(String logId);

    @Query("UPDATE votes SET syncStatus = :status, voteId = :firestoreId WHERE localId = :localId")
    void markSynced(int localId, String firestoreId, String status);

    @Query("UPDATE votes SET syncStatus = 'failed', syncAttempts = syncAttempts + 1 WHERE localId = :localId")
    void markFailed(int localId);
}