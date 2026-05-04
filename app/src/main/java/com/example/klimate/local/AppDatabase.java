package com.example.klimate.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * AppDatabase.java
 *
 * Room database singleton. Get the instance via AppDatabase.getInstance(context).
 *
 * Place in: com/example/klimate/local/
 *
 * When you add a new entity or change a schema, increment the version number
 * and provide a Migration or use fallbackToDestructiveMigration() during dev.
 */
@Database(
        entities = {
                ActivityLogEntity.class,
                UserEntity.class,
                VoteEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract ActivityLogDao activityLogDao();
    public abstract UserDao userDao();
    public abstract VoteDao voteDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "klimate_db"
                            )
                            // During active development swap this for .fallbackToDestructiveMigration()
                            // to avoid manual migrations on schema changes.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}