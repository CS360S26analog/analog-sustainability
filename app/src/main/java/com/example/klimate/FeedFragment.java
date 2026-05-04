/**
 * FeedFragment.java
 *
 * Community feed screen with four filter chips: Verified, News, Tips,
 * and Challenges.
 *
 * Verified tab    embeds ValidationFeedFragment as a child fragment,
 *                  reusing all its voting, proof-image, and upvote logic.
 * News tab        reads news_articles where approved == true, ordered
 *                  by publishedAt descending. Tapping a card opens the
 *                  article URL in the device browser.
 * Tips tab        reads staff-published tips from the tips collection.
 * Challenges tab  embeds ChallengesFragment via childFragmentManager.
 *
 * Role in design: Part of the View layer. Uses child-fragment embedding
 * for the Verified and Challenges tabs so that their own fragment logic
 * (voting, team joining, etc.) works correctly without duplication.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
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

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeedFragment extends Fragment {

    private TextView chipFeed, chipNews, chipExplore, chipTeams;
    private ViewGroup contentContainer;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_feed, container, false);
        db = FirebaseFirestore.getInstance();

        chipFeed       = view.findViewById(R.id.chip_feed);
        chipNews       = view.findViewById(R.id.chip_news);
        chipExplore    = view.findViewById(R.id.chip_explore);
        chipTeams    = view.findViewById(R.id.chip_teams);


        contentContainer = view.findViewById(R.id.feed_content_container);

        chipFeed.setOnClickListener(v       -> selectChip(0));
        chipNews.setOnClickListener(v       -> selectChip(1));
        chipExplore.setOnClickListener(v       -> selectChip(2));
        chipTeams.setOnClickListener(v -> selectChip(3));

        selectChip(0);
        return view;
    }

    /**
     * Switches the active filter chip and refreshes content.
     * Clears any child fragments before swapping so that embedded
     * fragments (Verified / Challenges) are properly torn down.
     *
     * 0 = Verified, 1 = News, 2 = Tips, 3 = Challenges
     *
     * @param index the selected chip index
     */
    private void selectChip(int index) {
        // Style — reset all, then highlight selected
        TextView[] chips = { chipFeed, chipNews, chipExplore, chipTeams};
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.bg_card_outline);
            chip.setTextColor(requireContext().getColor(R.color.color_text_secondary));
            chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        chips[index].setBackgroundResource(R.drawable.bg_button_amber);
        chips[index].setTextColor(requireContext().getColor(android.R.color.white));
        chips[index].setTypeface(null, android.graphics.Typeface.BOLD);

        // Remove any previously embedded child fragment before switching
        Fragment existing = getChildFragmentManager().findFragmentById(R.id.feed_content_container);
        if (existing != null) {
            getChildFragmentManager().beginTransaction().remove(existing).commitNow();
        }
        contentContainer.removeAllViews();

        switch (index) {
            case 0: loadVerifiedContent(); break;
            case 1: loadNewsContent();     break;
            case 2: loadExploreContent();  break; // Index 2 is now Explore
            case 3: loadMyTeamsContent();    break;
        }
    }

    // ─────────────────────────────────────────────────
    // VERIFIED TAB — delegates entirely to ValidationFeedFragment
    // ─────────────────────────────────────────────────

    /**
     * Embeds ValidationFeedFragment into the content container so that
     * all voting, proof-image loading, and upvote logic is handled there
     * without any duplication.
     */
    private void loadVerifiedContent() {
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.replace(R.id.feed_content_container, new ValidationFeedFragment());
        ft.commit();
    }

    // ─────────────────────────────────────────────────
    // NEWS TAB
    // ─────────────────────────────────────────────────

    /**
     * Loads approved news articles from Firestore, ordered by publishedAt
     * descending. Each card is tappable and opens the article URL in the
     * device's default browser via an Intent.
     *
     * Articles are pre-imported into Firestore (no scraping in the app).
     * Only documents where approved == true are shown.
     */
    private void loadNewsContent() {
        LinearLayout list = buildScrollList();
        list.addView(buildLoadingLabel("Loading news…"));

        db.collection("news_articles")
                .whereEqualTo("approved", true)
                .orderBy("publishedAt", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .addOnSuccessListener(snapshots -> {
                    list.removeAllViews();

                    if (snapshots.isEmpty()) {
                        list.addView(buildEmptyLabel(
                                "No news articles yet.\nCheck back soon! 📰"));
                        return;
                    }

                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.addView(buildNewsCard(
                                doc.getString("title"),
                                doc.getString("summary"),
                                doc.getString("url"),
                                doc.getString("source"),
                                doc.getString("category"),
                                doc.getTimestamp("publishedAt")));
                    }
                })
                .addOnFailureListener(e -> {
                    list.removeAllViews();
                    list.addView(buildEmptyLabel("Could not load news."));
                });
    }

    private void loadMyTeamsContent() {
        ChallengesFragment fragment = new ChallengesFragment();

        // Create a bundle to tell the fragment to show "My Teams" by default
        Bundle args = new Bundle();
        args.putString("mode", "teams"); // Triggers fragment_challenges_my_teams.xml
        fragment.setArguments(args);

        getChildFragmentManager().beginTransaction()
                .replace(R.id.feed_content_container, fragment).commit();
    }

    /**
     * Builds a tappable news article card. Tapping opens the article URL
     * in the device's default browser. If the URL is missing the card is
     * still shown but is not tappable.
     *
     * @param title       article headline
     * @param summary     short article summary
     * @param url         full article URL
     * @param source      publisher name e.g. "Dawn", "BBC"
     * @param category    topic category e.g. "Climate"
     * @param publishedAt Firestore Timestamp of publication date
     */
    private View buildNewsCard(String title, String summary, String url,
                               String source, String category,
                               Timestamp publishedAt) {
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

        // Meta row: category badge + source + date
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

        if (publishedAt != null) {
            TextView tvDate = new TextView(requireContext());
            tvDate.setText("  ·  " + new SimpleDateFormat(
                    "dd MMM", Locale.getDefault()).format(publishedAt.toDate()));
            tvDate.setTextSize(11f);
            tvDate.setTextColor(requireContext().getColor(R.color.color_text_secondary));
            metaRow.addView(tvDate);
        }

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(title != null ? title : "Untitled article");
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(requireContext().getColor(R.color.color_text_primary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin    = dpToPx(8);
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
    // TIPS TAB
    // ─────────────────────────────────────────────────

    /**
     * Loads staff-published sustainability tips from the 'tips' collection.
     */
    private void loadTipsContent() {
        LinearLayout list = buildScrollList();
        list.addView(buildLoadingLabel("Loading tips…"));

        db.collection("tips")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    list.removeAllViews();
                    if (snapshots.isEmpty()) {
                        list.addView(buildEmptyLabel(
                                "No tips published yet.\nCheck back soon! 💡"));
                        return;
                    }
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.addView(buildTipCard(
                                doc.getString("title"),
                                doc.getString("body"),
                                doc.getString("category"),
                                doc.getTimestamp("timestamp")));
                    }
                })
                .addOnFailureListener(e -> {
                    list.removeAllViews();
                    list.addView(buildEmptyLabel("Could not load tips."));
                });
    }

    private View buildTipCard(String title, String body,
                              String category, Timestamp ts) {
        CardView card = new CardView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dpToPx(280), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(16), 0, 0, dpToPx(10)); // Margin start only for spacing
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

        if (category != null) {
            metaRow.addView(buildBadge( category,
                    R.color.color_green_header, android.R.color.white));
        }

        if (ts != null) {
            TextView tvDate = new TextView(requireContext());
            tvDate.setText("  ·  " + new SimpleDateFormat(
                    "dd MMM", Locale.getDefault()).format(ts.toDate()));
            tvDate.setTextSize(11f);
            tvDate.setTextColor(requireContext().getColor(R.color.color_text_secondary));
            metaRow.addView(tvDate);
        }

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(title != null ? title : "Tip");
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(requireContext().getColor(R.color.color_text_primary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin    = dpToPx(8);
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
    // CHALLENGES TAB — embeds ChallengesFragment
    // ─────────────────────────────────────────────────

    private void loadExploreContent() {
        // 1. Parent vertical layout: tips row on top, challenges below.
        LinearLayout parent = new LinearLayout(requireContext());
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Horizontal, endlessly extendable tips rail.
        HorizontalScrollView tipScroll = new HorizontalScrollView(requireContext());
        LinearLayout.LayoutParams tipScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tipScroll.setLayoutParams(tipScrollParams);
        tipScroll.setPadding(0, dpToPx(16), dpToPx(16), dpToPx(16));
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

        // 3. Challenges fill the remaining vertical space.
        FrameLayout challengeFrame = new FrameLayout(requireContext());
        challengeFrame.setId(View.generateViewId());
        LinearLayout.LayoutParams challengeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        challengeFrame.setLayoutParams(challengeParams);
        parent.addView(challengeFrame);

        contentContainer.addView(parent);

        // 4. Load all tips once, shuffle once, then repeat that same shuffled cycle.
        TextView loading = buildLoadingLabel("Loading tips…");
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        loadingParams.setMargins(dpToPx(16), 0, 0, 0);
        loading.setLayoutParams(loadingParams);
        tipRow.addView(loading);

        final boolean[] isAppendingTips = {false};

        db.collection("tips")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    tipRow.removeAllViews();
                    List<DocumentSnapshot> tipDocs = new ArrayList<>(snapshots.getDocuments());

                    if (tipDocs.isEmpty()) {
                        tipRow.addView(buildEmptyHorizontalLabel(
                                "No tips published yet. Check back soon! 💡"));
                        return;
                    }

                    // Shuffle once, like a music playlist shuffle.
                    // Example: if Firestore order is 1,2,3 and this becomes 1,3,2,
                    // the rail repeats as 1,3,2,1,3,2,1,3,2...
                    List<DocumentSnapshot> shuffledTipCycle = new ArrayList<>(tipDocs);
                    java.util.Collections.shuffle(shuffledTipCycle);

                    // Add two copies of the same cycle so the rail feels full immediately.
                    appendTipCycle(tipRow, shuffledTipCycle);
                    appendTipCycle(tipRow, shuffledTipCycle);

                    tipScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                        if (isAppendingTips[0] || shuffledTipCycle.isEmpty()) return;

                        int distanceToEnd = tipRow.getWidth() - (scrollX + tipScroll.getWidth());

                        // Conventional lazy-load threshold: start adding before the user hits the edge.
                        if (distanceToEnd < dpToPx(420)) {
                            isAppendingTips[0] = true;
                            appendTipCycle(tipRow, shuffledTipCycle);
                            tipRow.post(() -> isAppendingTips[0] = false);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    tipRow.removeAllViews();
                    tipRow.addView(buildEmptyHorizontalLabel("Could not load tips."));
                });

        // 5. Load ChallengesFragment into the inner frame.
        ChallengesFragment fragment = new ChallengesFragment();
        Bundle args = new Bundle();
        args.putString("mode", "browse");
        fragment.setArguments(args);

        getChildFragmentManager()
                .beginTransaction()
                .replace(challengeFrame.getId(), fragment)
                .commit();
    }

    /**
     * Adds one copy of the already-shuffled tips cycle to the horizontal rail.
     * Important: this method does NOT reshuffle. The order is chosen once in
     * loadExploreContent(), then repeated forever like a shuffled playlist.
     */
    private void appendTipCycle(LinearLayout tipRow, List<DocumentSnapshot> shuffledTipCycle) {
        for (DocumentSnapshot doc : shuffledTipCycle) {
            tipRow.addView(buildTipCard(
                    doc.getString("title"),
                    doc.getString("body"),
                    doc.getString("category"),
                    doc.getTimestamp("timestamp")
            ));
        }
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

    // ─────────────────────────────────────────────────
    // VIEW BUILDERS
    // ─────────────────────────────────────────────────

    /**
     * Creates a NestedScrollView wrapping a padded vertical LinearLayout,
     * adds it to the content container, and returns the inner list so
     * callers can append cards to it directly.
     */
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

    private TextView buildLoadingLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        tv.setPadding(0, dpToPx(12), 0, 0);
        return tv;
    }

    // ─────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────

    /**
     * Returns a human-friendly "time ago" string for a Firestore Timestamp.
     * e.g. "just now", "5 min ago", "2 hrs ago", "3 days ago"
     */
    private String getTimeAgo(Timestamp ts) {
        if (ts == null) return "";
        long diffMs = new Date().getTime() - ts.toDate().getTime();
        long mins  = diffMs / 60000;
        long hours = mins / 60;
        long days  = hours / 24;

        if (mins < 2)   return "just now";
        if (mins < 60)  return mins + " min ago";
        if (hours < 24) return hours + " hr" + (hours == 1 ? "" : "s") + " ago";
        return days + " day" + (days == 1 ? "" : "s") + " ago";
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext()
                .getResources().getDisplayMetrics().density);
    }
}