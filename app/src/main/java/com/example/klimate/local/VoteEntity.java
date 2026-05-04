package com.example.klimate.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * VoteEntity.java
 *
 * Room entity mirroring the Firestore votes collection.
 *
 * Place in: com/example/klimate/local/
 */
@Entity(tableName = "votes")
public class VoteEntity {

    @PrimaryKey(autoGenerate = true)
    public int localId;

    /** Firestore vote document ID — empty until synced. */
    @NonNull
    public String voteId = "";

    /** Firestore logId this vote is against. */
    @NonNull
    public String logId = "";

    @NonNull
    public String voterId = "";

    public boolean isUpvote;

    public long timestampMillis;

    /**
     * "pending" — not yet written to Firestore.
     * "synced"  — confirmed written.
     * "failed"  — retry needed.
     */
    @NonNull
    public String syncStatus = "pending";

    public int syncAttempts = 0;


    public VoteEntity(@NonNull String voteId,
                      @NonNull String logId,
                      @NonNull String voterId,
                      boolean isUpvote,
                      long timestampMillis) {
        this.voteId         = voteId;
        this.logId          = logId;
        this.voterId        = voterId;
        this.isUpvote       = isUpvote;
        this.timestampMillis = timestampMillis;
        this.syncStatus     = "pending";
    }
}