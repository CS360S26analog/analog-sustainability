/**
 * FeedFragment.java
 *
 * Community feed screen with four filter chips: Verified, News, Tips,
 * and My Teams.
 *
 * Room-first implementation:
 * - News and Tips render immediately from Room cache when available.
 * - Firestore refreshes in the background.
 * - Fresh Firestore data replaces the Room cache.
 *
 * Note:
 * - Verified is still delegated to ValidationFeedFragment.
 * - Challenges/My Teams are still delegated to ChallengesFragment.
 *   If those are slow, cache them inside ChallengesFragment separately.
 */
package com.example.klimate;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.klimate.local.AppDatabase;
import com.example.klimate.local.FeedCacheDao;
import com.example.klimate.local.FeedCacheEntity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FeedFragment extends Fragment {

    private static final String CACHE_TYPE_NEWS = "news";
    private static final String CACHE_TYPE_TIP  = "tip";

    private TextView chipFeed, chipNews, chipExplore, chipTeams;
    private ViewGroup contentContainer;

    private FirebaseFirestore db;
    private AppDatabase appDatabase;
    private FeedCacheDao feedCacheDao;

    private final Executor executor = Executors.newSingleThreadExecutor();

    // Incremented whenever the user switches chips. Prevents old async callbacks
    // from rendering into the wrong tab after fast switching.
    private int contentVersion = 0;

    // In-memory copies make repeated tab switching instant within this fragment instance.
    private final List<FeedCacheEntity> memoryNewsCache = new ArrayList<>();
    private final List<FeedCacheEntity> memoryTipCycle  = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_feed, container, false);

        db = FirebaseFirestore.getInstance();
        appDatabase = AppDatabase.getInstance(requireContext());
        feedCacheDao = appDatabase.feedCacheDao();

        chipFeed    = view.findViewById(R.id.chip_feed);
        chipNews    = view.findViewById(R.id.chip_news);
        chipExplore = view.findViewById(R.id.chip_explore);
        chipTeams   = view.findViewById(R.id.chip_teams);

        contentContainer = view.findViewById(R.id.feed_content_container);

        chipFeed.setOnClickListener(v -> selectChip(0));
        chipNews.setOnClickListener(v -> selectChip(1));
        chipExplore.setOnClickListener(v -> selectChip(2));
        chipTeams.setOnClickListener(v -> selectChip(3));

        selectChip(0);
        return view;
    }

    /**
     * 0 = Verified, 1 = News, 2 = Explore/Tips + Challenges, 3 = My Teams
     */
    private void selectChip(int index) {
        contentVersion++;
        int version = contentVersion;

        TextView[] chips = { chipFeed, chipNews, chipExplore, chipTeams };
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.bg_card_outline);
            chip.setTextColor(requireContext().getColor(R.color.color_text_secondary));
            chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        chips[index].setBackgroundResource(R.drawable.bg_button_amber);
        chips[index].setTextColor(requireContext().getColor(android.R.color.white));
        chips[index].setTypeface(null, android.graphics.Typeface.BOLD);

        Fragment existing = getChildFragmentManager().findFragmentById(R.id.feed_content_container);
        if (existing != null) {
            getChildFragmentManager().beginTransaction().remove(existing).commitNow();
        }

        contentContainer.removeAllViews();

        switch (index) {
            case 0: loadVerifiedContent(); break;
            case 1: loadNewsContent(version); break;
            case 2: loadExploreContent(version); break;
            case 3: loadMyTeamsContent(); break;
        }
    }

    // ─────────────────────────────────────────────────
    // VERIFIED TAB
    // ─────────────────────────────────────────────────

    private void loadVerifiedContent() {
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.replace(R.id.feed_content_container, new ValidationFeedFragment());
        ft.commit();
    }

    // ─────────────────────────────────────────────────
    // NEWS TAB — ROOM FIRST, FIRESTORE REFRESH SECOND
    // ─────────────────────────────────────────────────

    private void loadNewsContent(int version) {
        LinearLayout list = buildScrollList();

        if (!memoryNewsCache.isEmpty()) {
            renderNewsList(list, memoryNewsCache);
        } else {
            list.addView(buildLoadingLabel("Loading news…"));
        }

        // 1. Room first: instant cached render.
        executor.execute(() -> {
            List<FeedCacheEntity> cached = feedCacheDao.getItemsByType(CACHE_TYPE_NEWS);

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (!isCurrent(version)) return;

                if (!cached.isEmpty()) {
                    memoryNewsCache.clear();
                    memoryNewsCache.addAll(cached);
                    renderNewsList(list, cached);
                } else if (memoryNewsCache.isEmpty()) {
                    list.removeAllViews();
                    list.addView(buildLoadingLabel("Loading news…"));
                }
            });
        });

        // 2. Firestore second: refresh cache in background.
        db.collection("news_article")
                .whereEqualTo("approved", true)
                .orderBy("publishedAt", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<FeedCacheEntity> freshItems = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        freshItems.add(mapNewsDocumentToCacheEntity(doc));
                    }

                    executor.execute(() ->
                            feedCacheDao.replaceType(CACHE_TYPE_NEWS, freshItems)
                    );

                    if (!isAdded() || !isCurrent(version)) return;

                    memoryNewsCache.clear();
                    memoryNewsCache.addAll(freshItems);

                    if (freshItems.isEmpty()) {
                        list.removeAllViews();
                        list.addView(buildEmptyLabel("No news articles yet.\nCheck back soon! 📰"));
                    } else {
                        renderNewsList(list, freshItems);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || !isCurrent(version)) return;

                    // If we had cached data, keep it on screen. Only show error when cache is empty.
                    if (memoryNewsCache.isEmpty()) {
                        list.removeAllViews();
                        list.addView(buildEmptyLabel("Could not load news."));
                    }
                });
    }

    private void renderNewsList(@NonNull LinearLayout list,
                                @NonNull List<FeedCacheEntity> items) {
        list.removeAllViews();

        if (items.isEmpty()) {
            list.addView(buildEmptyLabel("No news articles yet.\nCheck back soon! 📰"));
            return;
        }

        for (FeedCacheEntity item : items) {
            list.addView(buildNewsCard(
                    item.title,
                    item.summary,
                    item.url,
                    item.source,
                    item.category,
                    item.timestampMillis
            ));
        }
    }

    private FeedCacheEntity mapNewsDocumentToCacheEntity(@NonNull DocumentSnapshot doc) {
        Timestamp publishedAt = doc.getTimestamp("publishedAt");
        long publishedAtMillis = publishedAt != null
                ? publishedAt.toDate().getTime()
                : System.currentTimeMillis();

        return new FeedCacheEntity(
                CACHE_TYPE_NEWS + "_" + doc.getId(),
                CACHE_TYPE_NEWS,
                nullToEmpty(doc.getString("title")),
                nullToEmpty(doc.getString("summary")),
                nullToEmpty(doc.getString("url")),
                nullToEmpty(doc.getString("source")),
                nullToEmpty(doc.getString("category")),
                publishedAtMillis,
                System.currentTimeMillis()
        );
    }

    private View buildNewsCard(String title, String summary, String url,
                               String source, String category,
                               long publishedAtMillis) {
        CardView card = new CardView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(10));
        card.setLayoutParams(lp);
        card.setRadius(dpToPx(14));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(requireContext().getColor(android.R.color.white));

        if (url != null && !url.isEmpty()) {
            card.setClickable(true);
            card.setFocusable(true);
            card.setForeground(requireContext()
                    .obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground})
                    .getDrawable(0));
            card.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            });
        }

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout metaRow = new LinearLayout(requireContext());
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        if (category != null && !category.isEmpty()) {
            metaRow.addView(buildBadge("📰 " + category,
                    R.color.color_green_header, android.R.color.white));
        }

        if (source != null && !source.isEmpty()) {
            TextView tvSource = new TextView(requireContext());
            tvSource.setText("  ·  " + source);
            tvSource.setTextSize(11f);
            tvSource.setTextColor(requireContext().getColor(R.color.color_text_secondary));
            metaRow.addView(tvSource);
        }

        if (publishedAtMillis > 0) {
            TextView tvDate = new TextView(requireContext());
            tvDate.setText("  ·  " + new SimpleDateFormat(
                    "dd MMM", Locale.getDefault()).format(new Date(publishedAtMillis)));
            tvDate.setTextSize(11f);
            tvDate.setTextColor(requireContext().getColor(R.color.color_text_secondary));
            metaRow.addView(tvDate);
        }

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(title != null && !title.isEmpty() ? title : "Untitled article");
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(requireContext().getColor(R.color.color_text_primary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dpToPx(8);
        titleLp.bottomMargin = dpToPx(4);
        tvTitle.setLayoutParams(titleLp);

        TextView tvSummary = new TextView(requireContext());
        tvSummary.setText(summary != null ? summary : "");
        tvSummary.setTextSize(13f);
        tvSummary.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        tvSummary.setLineSpacing(dpToPx(2), 1f);

        inner.addView(metaRow);
        inner.addView(tvTitle);
        inner.addView(tvSummary);

        if (url != null && !url.isEmpty()) {
            TextView tvReadMore = new TextView(requireContext());
            tvReadMore.setText("Read article →");
            tvReadMore.setTextSize(12f);
            tvReadMore.setTypeface(null, android.graphics.Typeface.BOLD);
            tvReadMore.setTextColor(requireContext().getColor(R.color.color_green_text));
            LinearLayout.LayoutParams readLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            readLp.topMargin = dpToPx(8);
            tvReadMore.setLayoutParams(readLp);
            inner.addView(tvReadMore);
        }

        card.addView(inner);
        return card;
    }

    // ─────────────────────────────────────────────────
    // MY TEAMS TAB
    // ─────────────────────────────────────────────────

    private void loadMyTeamsContent() {
        ChallengesFragment fragment = new ChallengesFragment();

        Bundle args = new Bundle();
        args.putString("mode", "teams");
        fragment.setArguments(args);

        getChildFragmentManager().beginTransaction()
                .replace(R.id.feed_content_container, fragment)
                .commit();
    }

    // ─────────────────────────────────────────────────
    // TIPS + CHALLENGES TAB — TIPS ARE ROOM FIRST
    // ─────────────────────────────────────────────────

    private void loadExploreContent(int version) {
        LinearLayout parent = new LinearLayout(requireContext());
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        contentContainer.addView(parent);

        parent.addView(buildSectionLabel("Tips", "Swipe through quick sustainability ideas"));

        HorizontalScrollView tipScroll = new HorizontalScrollView(requireContext());
        LinearLayout.LayoutParams tipScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tipScroll.setLayoutParams(tipScrollParams);
        tipScroll.setPadding(0, dpToPx(8), dpToPx(16), dpToPx(14));
        tipScroll.setClipToPadding(false);
        tipScroll.setHorizontalScrollBarEnabled(false);
        tipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout tipRow = new LinearLayout(requireContext());
        tipRow.setOrientation(LinearLayout.HORIZONTAL);
        tipRow.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        tipScroll.addView(tipRow);
        parent.addView(tipScroll);

        parent.addView(buildSectionLabel("Challenges", "Join a challenge and track your progress"));

        FrameLayout challengeFrame = new FrameLayout(requireContext());
        challengeFrame.setId(View.generateViewId());

        LinearLayout.LayoutParams challengeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        challengeParams.topMargin = dpToPx(4);
        challengeFrame.setLayoutParams(challengeParams);
        parent.addView(challengeFrame);

        final boolean[] isAppendingTips = {false};

        if (!memoryTipCycle.isEmpty()) {
            renderInfiniteTipRail(tipScroll, tipRow, memoryTipCycle, isAppendingTips);
        } else {
            TextView loading = buildLoadingLabel("Loading tips…");
            LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            loadingParams.setMargins(dpToPx(16), 0, 0, 0);
            loading.setLayoutParams(loadingParams);
            tipRow.addView(loading);
        }

        // 1. Room first: show cached tips instantly.
        executor.execute(() -> {
            List<FeedCacheEntity> cachedTips = feedCacheDao.getItemsByType(CACHE_TYPE_TIP);

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (!isCurrent(version)) return;

                if (!cachedTips.isEmpty() && memoryTipCycle.isEmpty()) {
                    memoryTipCycle.clear();
                    memoryTipCycle.addAll(cachedTips);
                    Collections.shuffle(memoryTipCycle);
                    renderInfiniteTipRail(tipScroll, tipRow, memoryTipCycle, isAppendingTips);
                } else if (cachedTips.isEmpty() && memoryTipCycle.isEmpty()) {
                    tipRow.removeAllViews();
                    tipRow.addView(buildEmptyHorizontalLabel(
                            "No tips published yet. Check back soon! 💡"));
                }
            });
        });

        // 2. Firestore second: refresh Room and visible UI.
        db.collection("tips")
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<FeedCacheEntity> freshTips = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        freshTips.add(mapTipDocumentToCacheEntity(doc));
                    }

                    executor.execute(() ->
                            feedCacheDao.replaceType(CACHE_TYPE_TIP, freshTips)
                    );

                    if (!isAdded() || !isCurrent(version)) return;

                    memoryTipCycle.clear();
                    memoryTipCycle.addAll(freshTips);
                    Collections.shuffle(memoryTipCycle);

                    if (memoryTipCycle.isEmpty()) {
                        tipRow.removeAllViews();
                        tipRow.addView(buildEmptyHorizontalLabel(
                                "No tips published yet. Check back soon! 💡"));
                    } else {
                        renderInfiniteTipRail(tipScroll, tipRow, memoryTipCycle, isAppendingTips);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || !isCurrent(version)) return;

                    if (memoryTipCycle.isEmpty()) {
                        tipRow.removeAllViews();
                        tipRow.addView(buildEmptyHorizontalLabel("Could not load tips."));
                    }
                });

        ChallengesFragment fragment = new ChallengesFragment();
        Bundle args = new Bundle();
        args.putString("mode", "browse");
        fragment.setArguments(args);

        getChildFragmentManager()
                .beginTransaction()
                .replace(challengeFrame.getId(), fragment)
                .commit();
    }

    private FeedCacheEntity mapTipDocumentToCacheEntity(@NonNull DocumentSnapshot doc) {
        Timestamp timestamp = doc.getTimestamp("timestamp");
        long timestampMillis = timestamp != null
                ? timestamp.toDate().getTime()
                : System.currentTimeMillis();

        return new FeedCacheEntity(
                CACHE_TYPE_TIP + "_" + doc.getId(),
                CACHE_TYPE_TIP,
                nullToEmpty(doc.getString("title")),
                nullToEmpty(doc.getString("body")),
                "",
                "",
                nullToEmpty(doc.getString("category")),
                timestampMillis,
                System.currentTimeMillis()
        );
    }

    private void renderInfiniteTipRail(@NonNull HorizontalScrollView tipScroll,
                                       @NonNull LinearLayout tipRow,
                                       @NonNull List<FeedCacheEntity> tipCycle,
                                       @NonNull boolean[] isAppendingTips) {
        tipRow.removeAllViews();

        appendTipCycle(tipRow, tipCycle);
        appendTipCycle(tipRow, tipCycle);

        tipScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (isAppendingTips[0] || tipCycle.isEmpty()) return;

            int distanceToEnd = tipRow.getWidth() - (scrollX + tipScroll.getWidth());

            if (distanceToEnd < dpToPx(420)) {
                isAppendingTips[0] = true;
                appendTipCycle(tipRow, tipCycle);
                tipRow.post(() -> isAppendingTips[0] = false);
            }
        });
    }

    private void appendTipCycle(@NonNull LinearLayout tipRow,
                                @NonNull List<FeedCacheEntity> tipCycle) {
        for (FeedCacheEntity tip : tipCycle) {
            tipRow.addView(buildTipCard(
                    tip.title,
                    tip.summary,
                    tip.category,
                    tip.timestampMillis
            ));
        }
    }

    private View buildTipCard(String title, String body,
                              String category, long timestampMillis) {
        CardView card = new CardView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dpToPx(280), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(16), 0, 0, dpToPx(10));
        card.setLayoutParams(lp);
        card.setRadius(dpToPx(14));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(requireContext().getColor(android.R.color.white));

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout metaRow = new LinearLayout(requireContext());
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        if (category != null && !category.isEmpty()) {
            metaRow.addView(buildBadge(category,
                    R.color.color_green_header, android.R.color.white));
        }

        if (timestampMillis > 0) {
            TextView tvDate = new TextView(requireContext());
            tvDate.setText("  ·  " + new SimpleDateFormat(
                    "dd MMM", Locale.getDefault()).format(new Date(timestampMillis)));
            tvDate.setTextSize(11f);
            tvDate.setTextColor(requireContext().getColor(R.color.color_text_secondary));
            metaRow.addView(tvDate);
        }

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(title != null && !title.isEmpty() ? title : "Tip");
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(requireContext().getColor(R.color.color_text_primary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dpToPx(8);
        titleLp.bottomMargin = dpToPx(4);
        tvTitle.setLayoutParams(titleLp);

        TextView tvBody = new TextView(requireContext());
        tvBody.setText(body != null ? body : "");
        tvBody.setTextSize(13f);
        tvBody.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        tvBody.setLineSpacing(dpToPx(2), 1f);

        inner.addView(metaRow);
        inner.addView(tvTitle);
        inner.addView(tvBody);
        card.addView(inner);
        return card;
    }

    // ─────────────────────────────────────────────────
    // VIEW BUILDERS
    // ─────────────────────────────────────────────────

    private LinearLayout buildScrollList() {
        NestedScrollView scroll = new NestedScrollView(requireContext());
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(16);
        list.setPadding(pad, pad, pad, pad);

        scroll.addView(list);
        contentContainer.addView(scroll);
        return list;
    }

    private TextView buildBadge(String text, int bgColorRes, int textColorRes) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setTextColor(requireContext().getColor(textColorRes));
        tv.setBackground(requireContext().getDrawable(R.drawable.bg_card_green));
        tv.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
        tv.getBackground().setTint(requireContext().getColor(bgColorRes));
        return tv;
    }

    private TextView buildEmptyLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        tv.setPadding(0, dpToPx(16), 0, 0);
        return tv;
    }

    private TextView buildEmptyHorizontalLabel(String text) {
        TextView tv = buildEmptyLabel(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(16), 0, dpToPx(16), 0);
        tv.setLayoutParams(params);
        return tv;
    }

    private TextView buildLoadingLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        tv.setPadding(0, dpToPx(12), 0, 0);
        return tv;
    }

    private View buildSectionLabel(String title, String subtitle) {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.setMargins(dpToPx(16), dpToPx(16), dpToPx(16), 0);
        wrapper.setLayoutParams(wrapperParams);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(title);
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(requireContext().getColor(R.color.color_text_primary));

        TextView tvSubtitle = new TextView(requireContext());
        tvSubtitle.setText(subtitle);
        tvSubtitle.setTextSize(12f);
        tvSubtitle.setTextColor(requireContext().getColor(R.color.color_text_secondary));

        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dpToPx(2);
        tvSubtitle.setLayoutParams(subtitleParams);

        wrapper.addView(tvTitle);
        wrapper.addView(tvSubtitle);

        return wrapper;
    }

    // ─────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────

    private boolean isCurrent(int version) {
        return version == contentVersion;
    }

    private String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext()
                .getResources().getDisplayMetrics().density);
    }
}
