package com.example.klimate.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * ActivityLogEntity.java
 *
 * Room entity mirroring the Firestore activity_logs collection.
 * Added fields vs the Firestore model:
 *   syncStatus — "pending" | "synced" | "failed"
 *   localId    — auto-generated local primary key (Firestore logId may be blank until synced)
 *
 * Place in: com/example/klimate/local/
 *
 * Build dependency required in build.gradle (app):
 *   implementation "androidx.room:room-runtime:2.6.1"
 *   annotationProcessor "androidx.room:room-compiler:2.6.1"
 */
@Entity(tableName = "activity_logs")
public class ActivityLogEntity {

    @PrimaryKey(autoGenerate = true)
    public int localId;

    /** Firestore document ID — empty string until the record is synced. */
    @NonNull
    public String logId = "";

    @NonNull
    public String userId = "";

    @NonNull
    public String activityType = "";

    /** "quick" | "pending_verification" */
    @NonNull
    public String status = "quick";

    public int points;
    public int bonusPoints;
    public int voteCount;
    public int quantity;
    public double co2SavedKg;

    @NonNull
    public String unit = "";

    /** Nullable — only set for verified logs. */
    public String proofUrl;

    /** Epoch millis — Firestore Timestamp converted. */
    public long timestampMillis;

    /**
     * "pending"  — written locally, not yet pushed to Firestore.
     * "synced"   — successfully written to Firestore.
     * "failed"   — Firestore write attempted and failed; will retry.
     */
    @NonNull
    public String syncStatus = "pending";

    /** Number of sync attempts made so far. */
    public int syncAttempts = 0;

    // ── constructors ──────────────────────────────────────────────────────
    /**
     * Convenience constructor for creating a new pending log.
     */
    public ActivityLogEntity(@NonNull String logId,
                             @NonNull String userId,
                             @NonNull String activityType,
                             @NonNull String status,
                             int points,
                             int quantity,
                             double co2SavedKg,
                             @NonNull String unit,
                             String proofUrl,
                             long timestampMillis) {
        this.logId          = logId;
        this.userId         = userId;
        this.activityType   = activityType;
        this.status         = status;
        this.points         = points;
        this.quantity       = quantity;
        this.co2SavedKg     = co2SavedKg;
        this.unit           = unit;
        this.proofUrl       = proofUrl;
        this.timestampMillis = timestampMillis;
        this.syncStatus     = "pending";
    }
}