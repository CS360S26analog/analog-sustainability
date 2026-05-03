// ─────────────────────────────────────────────────────────────────────────────
// UserDao.java
// Place in: com/example/klimate/local/
// ─────────────────────────────────────────────────────────────────────────────
package com.example.klimate.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserEntity user);

    @Update
    void update(UserEntity user);

    /** Live version — for ProfileFragment / HomeFragment observers. */
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    LiveData<UserEntity> getUserLive(String uid);

    /** Synchronous version — for WorkManager / background threads. */
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    UserEntity getUserSync(String uid);

    @Query("UPDATE users SET totalPoints = totalPoints + :delta, syncStatus = 'dirty' WHERE uid = :uid")
    void incrementPoints(String uid, int delta);

    @Query("UPDATE users SET co2SavedKg = co2SavedKg + :delta, syncStatus = 'dirty' WHERE uid = :uid")
    void incrementCo2(String uid, double delta);

    @Query("UPDATE users SET totalPoints = :points, co2SavedKg = :co2, streakDays = :streak, syncStatus = 'synced' WHERE uid = :uid")
    void updateStats(String uid, int points, double co2, int streak);

    @Query("UPDATE users SET syncStatus = 'synced' WHERE uid = :uid")
    void markSynced(String uid);
}