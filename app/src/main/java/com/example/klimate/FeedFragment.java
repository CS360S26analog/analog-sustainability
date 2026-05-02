/**
 * FeedFragment.java
 *
 * Community feed screen with a filter chip bar at the top.
 * Three tabs: Feed (recent activity logs with display names fetched
 * from Firestore), Tips (staff-published sustainability tips),
 * and Challenges (active campus challenges via ChallengesFragment).
 *
 * Role in design: Part of the View layer. Uses Observer pattern via
 * Firestore queries. Each activity log card does a secondary lookup
 * to resolve the userId to a displayName for a personalised feed.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class FeedFragment extends Fragment {

    private TextView chipFeed, chipTips, chipChallenges;
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
        chipTips       = view.findViewById(R.id.chip_tips);
        chipChallenges = view.findViewById(R.id.chip_challenges);
        contentContainer = view.findViewById(R.id.feed_content_container);

        chipFeed.setOnClickListener(v -> selectChip(0));
        chipTips.setOnClickListener(v -> selectChip(1));
        chipChallenges.setOnClickListener(v -> selectChip(2));

        selectChip(0);
        return view;
    }

    /**
     * Switches the active filter chip and refreshes content.
     * 0 = Feed, 1 = Tips, 2 = Challenges
     *
     * @param index the selected chip index
     */
    private void selectChip(int index) {
        chipFeed.setBackgroundResource(R.drawable.bg_card_outline);
        chipFeed.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        chipTips.setBackgroundResource(R.drawable.bg_card_outline);
        chipTips.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        chipChallenges.setBackgroundResource(R.drawable.bg_card_outline);
        chipChallenges.setTextColor(requireContext().getColor(R.color.color_text_secondary));

        TextView selected = index == 0 ? chipFeed : index == 1 ? chipTips : chipChallenges;
        selected.setBackgroundResource(R.drawable.bg_header_green);
        selected.setTextColor(requireContext().getColor(android.R.color.white));

        contentContainer.removeAllViews();
        switch (index) {
            case 0: loadFeedContent(); break;
            case 1: loadTipsContent(); break;
            case 2: loadChallengesContent(); break;
        }
    }

    // ─────────────────────────────────────────────────
    // FEED TAB — activity logs with resolved names
    // ─────────────────────────────────────────────────

    /**
     * Loads recent activity logs from all users. For each log, does a
     * secondary Firestore lookup to resolve userId → displayName so
     * the feed shows personalised entries rather than raw UIDs.
     */
    private void loadFeedContent() {
        LinearLayout list = buildScrollList();
        list.addView(buildLoadingLabel("Loading community feed…"));

        db.collection("activity_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snapshots -> {
                    list.removeAllViews();

                    if (snapshots.isEmpty()) {
                        list.addView(buildEmptyLabel(
                                "No activity logs yet.\nBe the first to log! 🌿"));
                        return;
                    }

                    // Collect all docs into a list so we can render in order
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) docs.add(doc);

                    // Use AtomicInteger to track async completion
                    AtomicInteger remaining = new AtomicInteger(docs.size());

                    // Pre-create card views in order (null = placeholder)
                    View[] cardSlots = new View[docs.size()];

                    for (int i = 0; i < docs.size(); i++) {
                        final int idx = i;
                        QueryDocumentSnapshot doc = docs.get(i);

                        String userId      = doc.getString("userId");
                        String activityType = doc.getString("activityType");
                        String status      = doc.getString("status");
                        Long points        = doc.getLong("points");
                        Long bonusPoints   = doc.getLong("bonusPoints");
                        Long voteCount     = doc.getLong("voteCount");
                        Timestamp ts       = doc.getTimestamp("timestamp");

                        if (userId == null) {
                            cardSlots[idx] = buildActivityCard(
                                    "Someone", activityType, status,
                                    points, bonusPoints, voteCount, ts);
                            if (remaining.decrementAndGet() == 0)
                                renderCards(list, cardSlots);
                            continue;
                        }

                        // Secondary lookup: resolve userId → displayName
                        db.collection("users").document(userId).get()
                                .addOnSuccessListener(userDoc -> {
                                    String name = userDoc.getString("displayName");
                                    String firstName = resolveFirstName(name, userId);
                                    cardSlots[idx] = buildActivityCard(
                                            firstName, activityType, status,
                                            points, bonusPoints, voteCount, ts);
                                    if (remaining.decrementAndGet() == 0)
                                        renderCards(list, cardSlots);
                                })
                                .addOnFailureListener(e -> {
                                    cardSlots[idx] = buildActivityCard(
                                            "A student", activityType, status,
                                            points, bonusPoints, voteCount, ts);
                                    if (remaining.decrementAndGet() == 0)
                                        renderCards(list, cardSlots);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    list.removeAllViews();
                    list.addView(buildEmptyLabel("Could not load feed."));
                });
    }

    /**
     * Renders card views into the list in the original order once all
     * async name lookups have completed.
     */
    private void renderCards(LinearLayout list, View[] cardSlots) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            list.removeAllViews();
            for (View card : cardSlots) {
                if (card != null) list.addView(card);
            }
        });
    }

    /**
     * Extracts the first name from a display name string,
     * or falls back to a shortened UID if name is missing.
     */
    private String resolveFirstName(String displayName, String userId) {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName.trim().split("\\s+")[0];
        }
        return userId != null && userId.length() >= 6
                ? "User " + userId.substring(0, 4) : "A student";
    }

    /**
     * Builds a rich activity log card showing the user's name, activity
     * emoji, points badge, time-ago label, and verification status.
     */
    private View buildActivityCard(String firstName, String activityType,
                                   String status, Long points, Long bonusPoints,
                                   Long voteCount, Timestamp ts) {
        CardView card = new CardView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(10));
        card.setLayoutParams(lp);
        card.setRadius(dpToPx(14));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(requireContext().getColor(android.R.color.white));

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        // ── Top row: emoji + name + time-ago ──
        LinearLayout topRow = new LinearLayout(requireContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Big activity emoji circle
        TextView tvEmoji = new TextView(requireContext());
        tvEmoji.setText(getActivityEmoji(activityType));
        tvEmoji.setTextSize(22f);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(
                dpToPx(44), dpToPx(44));
        emojiLp.setMarginEnd(dpToPx(12));
        tvEmoji.setLayoutParams(emojiLp);
        tvEmoji.setGravity(android.view.Gravity.CENTER);
        tvEmoji.setBackground(requireContext().getDrawable(R.drawable.bg_streak_number));

        // Name + activity
        LinearLayout nameBlock = new LinearLayout(requireContext());
        nameBlock.setOrientation(LinearLayout.VERTICAL);
        nameBlock.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(requireContext());
        tvName.setText(firstName + " logged");
        tvName.setTextSize(12f);
        tvName.setTextColor(requireContext().getColor(R.color.color_text_secondary));

        TextView tvActivity = new TextView(requireContext());
        tvActivity.setText(activityType != null ? activityType : "Sustainable activity");
        tvActivity.setTextSize(15f);
        tvActivity.setTypeface(null, android.graphics.Typeface.BOLD);
        tvActivity.setTextColor(requireContext().getColor(R.color.color_text_primary));

        nameBlock.addView(tvName);
        nameBlock.addView(tvActivity);

        // Time-ago label
        TextView tvTime = new TextView(requireContext());
        tvTime.setText(getTimeAgo(ts));
        tvTime.setTextSize(11f);
        tvTime.setTextColor(requireContext().getColor(R.color.color_text_secondary));

        topRow.addView(tvEmoji);
        topRow.addView(nameBlock);
        topRow.addView(tvTime);
        inner.addView(topRow);

        // ── Bottom row: points badge + status badge + vote count ──
        LinearLayout bottomRow = new LinearLayout(requireContext());
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.topMargin = dpToPx(10);
        bottomRow.setLayoutParams(bottomLp);

        // Points badge
        long totalPts = (points != null ? points : 0)
                + (bonusPoints != null ? bonusPoints : 0);
        TextView tvPoints = buildBadge("⭐ +" + totalPts + " pts",
                R.color.color_amber, android.R.color.white);
        bottomRow.addView(tvPoints);

        // Status badge
        if ("pending_verification".equals(status)) {
            TextView tvStatus = buildBadge("📋 Pending review",
                    R.color.color_green_header, android.R.color.white);
            LinearLayout.LayoutParams sp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.setMarginStart(dpToPx(6));
            tvStatus.setLayoutParams(sp);
            bottomRow.addView(tvStatus);
        } else if ("verified".equals(status)) {
            TextView tvStatus = buildBadge("✅ Verified",
                    R.color.color_green_header, android.R.color.white);
            LinearLayout.LayoutParams sp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.setMarginStart(dpToPx(6));
            tvStatus.setLayoutParams(sp);
            bottomRow.addView(tvStatus);
        }

        // Vote count (only show if > 0)
        if (voteCount != null && voteCount > 0) {
            TextView tvVotes = buildBadge("👍 " + voteCount,
                    R.color.color_green_header, android.R.color.white);
            LinearLayout.LayoutParams vp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            vp.setMarginStart(dpToPx(6));
            tvVotes.setLayoutParams(vp);
            bottomRow.addView(tvVotes);
        }

        inner.addView(bottomRow);
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
                .addOnFailureListener(e ->
                        list.addView(buildEmptyLabel("Could not load tips.")));
    }

    // ─────────────────────────────────────────────────
    // CHALLENGES TAB
    // ─────────────────────────────────────────────────

    private void loadChallengesContent() {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.feed_content_container, new ChallengesFragment())
                .commit();
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

    private View buildTipCard(String title, String body,
                              String category, Timestamp ts) {
        CardView card = new CardView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(10));
        card.setLayoutParams(lp);
        card.setRadius(dpToPx(14));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(requireContext().getColor(android.R.color.white));

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        // Category + date row
        LinearLayout metaRow = new LinearLayout(requireContext());
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        if (category != null) {
            TextView tvCat = buildBadge("💡 " + category,
                    R.color.color_green_header, android.R.color.white);
            metaRow.addView(tvCat);
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

    private TextView buildBadge(String text, int bgColorRes, int textColorRes) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setTextColor(requireContext().getColor(textColorRes));
        tv.setBackground(requireContext().getDrawable(R.drawable.bg_header_green));
        tv.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
        // Tint background to the requested colour
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

    /**
     * Returns an emoji for the given activity type string.
     */
    private String getActivityEmoji(String activityType) {
        if (activityType == null) return "🌿";
        switch (activityType) {
            case "Cycling":          return "🚲";
            case "Public Transit":   return "🚌";
            case "Recycling":        return "♻️";
            case "Plant-based meal": return "🥗";
            case "Reusable cup":     return "☕";
            case "Composting":       return "🍂";
            case "Walked":           return "🚶";
            case "Energy saving":    return "💡";
            default:                 return "🌿";
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext()
                .getResources().getDisplayMetrics().density);
    }
}