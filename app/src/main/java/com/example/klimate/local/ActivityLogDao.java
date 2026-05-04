// ─────────────────────────────────────────────────────────────────────────────
// ActivityLogDao.java
// Place in: com/example/klimate/local/
// ─────────────────────────────────────────────────────────────────────────────
package com.example.klimate.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ActivityLogDao {

    // ── writes ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ActivityLogEntity log);

    @Update
    void update(ActivityLogEntity log);

    @Query("DELETE FROM activity_logs WHERE logId = :logId")
    void deleteByLogId(String logId);

    @Query("DELETE FROM activity_logs WHERE localId = :localId")
    void deleteByLocalId(int localId);

    // ── reads — user-specific ─────────────────────────────────────────────────

    /** All logs for a user, newest first. Used by HistoryFragment and HomeFragment. */
    @Query("SELECT * FROM activity_logs WHERE userId = :userId ORDER BY timestampMillis DESC")
    LiveData<List<ActivityLogEntity>> getLogsForUserLive(String userId);

    /** Synchronous version for background workers. */
    @Query("SELECT * FROM activity_logs WHERE userId = :userId ORDER BY timestampMillis DESC")
    List<ActivityLogEntity> getLogsForUserSync(String userId);

    // ── reads — sync queue ────────────────────────────────────────────────────

    /** Records that have not yet been pushed to Firestore. */
    @Query("SELECT * FROM activity_logs WHERE syncStatus = 'pending' OR syncStatus = 'failed'")
    List<ActivityLogEntity> getPendingLogs();

    /** Check whether a specific logId already exists locally. */
    @Query("SELECT COUNT(*) FROM activity_logs WHERE logId = :logId")
    int countByLogId(String logId);

    // ── status updates ────────────────────────────────────────────────────────

    @Query("UPDATE activity_logs SET syncStatus = :status, logId = :firestoreId WHERE localId = :localId")
    void markSynced(int localId, String firestoreId, String status);

    @Query("UPDATE activity_logs SET syncStatus = 'failed', syncAttempts = syncAttempts + 1 WHERE localId = :localId")
    void markFailed(int localId);

    // ── aggregates (used for streak / CO2 recalc from Room) ──────────────────

    @Query("SELECT SUM(co2SavedKg) FROM activity_logs WHERE userId = :userId AND syncStatus != 'failed'")
    double getTotalCo2ForUser(String userId);

    @Query("SELECT SUM(points) + SUM(bonusPoints) FROM activity_logs WHERE userId = :userId AND syncStatus != 'failed'")
    int getTotalPointsForUser(String userId);

    /** Distinct days logged (yyyyMMdd formatted) for streak calculation. */
    @Query("SELECT DISTINCT strftime('%Y%m%d', timestampMillis / 1000, 'unixepoch', 'localtime') " +
            "FROM activity_logs WHERE userId = :userId AND syncStatus != 'failed'")
    List<String> getDistinctLogDays(String userId);
}