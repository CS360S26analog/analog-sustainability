package com.example.klimate.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * AppDatabase.java
 *
 * Room database singleton. Get the instance via AppDatabase.getInstance(context).
 */
@Database(
        entities = {
                ActivityLogEntity.class,
                UserEntity.class,
                VoteEntity.class,
                FeedCacheEntity.class,
                ChallengeCacheEntity.class
        },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract ActivityLogDao activityLogDao();
    public abstract UserDao userDao();
    public abstract VoteDao voteDao();
    public abstract FeedCacheDao feedCacheDao();
    public abstract ChallengeCacheDao challengeCacheDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "klimate_db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
