package com.example.klimate.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/**
 * DAO for FeedFragment's local News/Tips cache.
 */
@Dao
public interface FeedCacheDao {

    @Query("SELECT * FROM feed_cache WHERE type = :type ORDER BY timestampMillis DESC")
    List<FeedCacheEntity> getItemsByType(String type);

    @Query("DELETE FROM feed_cache WHERE type = :type")
    void deleteByType(String type);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FeedCacheEntity> items);

    @Transaction
    default void replaceType(String type, List<FeedCacheEntity> items) {
        deleteByType(type);
        if (items != null && !items.isEmpty()) {
            insertAll(items);
        }
    }
}
