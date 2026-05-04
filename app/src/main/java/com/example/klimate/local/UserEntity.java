package com.example.klimate.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * UserEntity.java
 *
 * Room entity mirroring the Firestore users collection.
 * Primary key is the Firebase Auth UID (same as Firestore document ID).
 *
 * Place in: com/example/klimate/local/
 */
@Entity(tableName = "users")
public class UserEntity {

    /** Firebase Auth UID — also the Firestore document ID. */
    @PrimaryKey
    @NonNull
    public String uid = "";

    @NonNull
    public String displayName = "";

    @NonNull
    public String email = "";

    @NonNull
    public String university = "LUMS";

    @NonNull
    public String role = "student";

    public int totalPoints;
    public int streakDays;
    public double co2SavedKg;
    public double monthlyGoalKg;

    /** Epoch millis of account creation. */
    public long createdAtMillis;

    /**
     * "synced" — matches Firestore.
     * "dirty"  — local change not yet pushed (e.g. points incremented locally).
     */
    @NonNull
    public String syncStatus = "synced";
    public UserEntity(@NonNull String uid,
                      @NonNull String displayName,
                      @NonNull String email,
                      @NonNull String university,
                      @NonNull String role,
                      int totalPoints,
                      int streakDays,
                      double co2SavedKg,
                      long createdAtMillis) {
        this.uid             = uid;
        this.displayName     = displayName;
        this.email           = email;
        this.university      = university;
        this.role            = role;
        this.totalPoints     = totalPoints;
        this.streakDays      = streakDays;
        this.co2SavedKg      = co2SavedKg;
        this.createdAtMillis = createdAtMillis;
        this.syncStatus      = "synced";
    }
}