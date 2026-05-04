package com.example.klimate.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Local cache for ChallengesFragment.
 *
 * mode:
 * - "browse" for active challenge cards
 * - "teams" for current user's joined team cards
 */
@Entity(tableName = "challenge_cache")
public class ChallengeCacheEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String ownerUid;

    @NonNull
    public String mode;

    public String challengeId;
    public String teamId;
    public String joinCode;

    public String name;
    public String description;

    public long endDateMillis;
    public Long target;

    /**
     * Used for ordering.
     * Browse: challenge startDate.
     * My Teams: team createdAt.
     */
    public long sortMillis;

    public long cachedAtMillis;

    public ChallengeCacheEntity(@NonNull String id,
                                @NonNull String ownerUid,
                                @NonNull String mode,
                                String challengeId,
                                String teamId,
                                String joinCode,
                                String name,
                                String description,
                                long endDateMillis,
                                Long target,
                                long sortMillis,
                                long cachedAtMillis) {
        this.id = id;
        this.ownerUid = ownerUid;
        this.mode = mode;
        this.challengeId = challengeId;
        this.teamId = teamId;
        this.joinCode = joinCode;
        this.name = name;
        this.description = description;
        this.endDateMillis = endDateMillis;
        this.target = target;
        this.sortMillis = sortMillis;
        this.cachedAtMillis = cachedAtMillis;
    }
}
