package com.example.klimate.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Local cache row for FeedFragment content.
 *
 * type = "news" for news articles
 * type = "tip"  for staff tips
 */
@Entity(tableName = "feed_cache")
public class FeedCacheEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String type;

    public String title;
    public String summary;
    public String url;
    public String source;
    public String category;

    public long timestampMillis;
    public long cachedAtMillis;

    public FeedCacheEntity(@NonNull String id,
                           @NonNull String type,
                           String title,
                           String summary,
                           String url,
                           String source,
                           String category,
                           long timestampMillis,
                           long cachedAtMillis) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.url = url;
        this.source = source;
        this.category = category;
        this.timestampMillis = timestampMillis;
        this.cachedAtMillis = cachedAtMillis;
    }
}
