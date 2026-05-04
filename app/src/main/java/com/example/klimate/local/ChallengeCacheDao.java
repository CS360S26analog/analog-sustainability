package com.example.klimate.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/**
 * DAO for ChallengesFragment's local cache.
 */
@Dao
public interface ChallengeCacheDao {

    @Query("SELECT * FROM challenge_cache WHERE ownerUid = :ownerUid AND mode = :mode ORDER BY sortMillis DESC")
    List<ChallengeCacheEntity> getItems(String ownerUid, String mode);

    @Query("DELETE FROM challenge_cache WHERE ownerUid = :ownerUid AND mode = :mode")
    void deleteMode(String ownerUid, String mode);

    @Query("DELETE FROM challenge_cache WHERE ownerUid = :ownerUid")
    void deleteForOwner(String ownerUid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChallengeCacheEntity> items);

    @Transaction
    default void replaceMode(String ownerUid, String mode, List<ChallengeCacheEntity> items) {
        deleteMode(ownerUid, mode);
        if (items != null && !items.isEmpty()) {
            insertAll(items);
        }
    }
}
