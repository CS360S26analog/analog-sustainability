/**
 * ProfileFragment.java
 *
 * Displays the logged-in student's profile, including real-time points,
 * streak days, CO2 saved, badge grid, rewards, settings rows, and logout.
 *
 * Current sprint work:
 * - Shows earned and locked badges from the badge catalogue
 * - Reads earned badge IDs from users/{uid}/badges
 * - Also unlocks point and streak badges directly from user stats
 * - Earned badges show full colour
 * - Locked badges show dimmed with lock overlay
 * - Tapping a badge opens a detail dialog
 *
 * @author Haroon
 * @author Izza
 * @author Karar
 * @author Maryam W.
 * @author Maryam A.
 */
package com.example.klimate;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ProfileFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private long currentTotalPoints = 0;
    private long currentStreakDays = 0;

    private GridLayout badgesContainer;
    private ListenerRegistration profileListener;
    private ListenerRegistration badgesListener;
    private ListenerRegistration notificationsListener;

    private LinearLayout historyPreviewContainer;

    private boolean rewardsAllowed = false;
    private View currentRootView;
    private String currentUid;

    private final Set<String> earnedBadgeIds = new HashSet<>();
    private final Map<String, Timestamp> earnedDates = new HashMap<>();
    private final List<HistoryEntry> historyEntries = new ArrayList<>();

    private static class Reward {
        final String emoji;
        final String name;
        final int pointsCost;

        Reward(String emoji, String name, int pointsCost) {
            this.emoji = emoji;
            this.name = name;
            this.pointsCost = pointsCost;
        }
    }

    private static class BadgeDefinition {
        final String id;
        final String emoji;
        final String name;
        final String description;
        final String lockedHint;
        final int backgroundDrawable;
        final long requiredPoints;
        final long requiredStreakDays;

        BadgeDefinition(String id,
                        String emoji,
                        String name,
                        String description,
                        String lockedHint,
                        int backgroundDrawable,
                        long requiredPoints,
                        long requiredStreakDays) {
            this.id = id;
            this.emoji = emoji;
            this.name = name;
            this.description = description;
            this.lockedHint = lockedHint;
            this.backgroundDrawable = backgroundDrawable;
            this.requiredPoints = requiredPoints;
            this.requiredStreakDays = requiredStreakDays;
        }
    }

    private static class HistoryEntry {
        final String title;
        final String detail;
        final long timeMillis;

        HistoryEntry(String title, String detail, long timeMillis) {
            this.title = title;
            this.detail = detail;
            this.timeMillis = timeMillis;
        }
    }

    private static final Reward[] REWARDS = {
            new Reward("☕", "Free Coffee", 500),
            new Reward("🚲", "Bike Rental Voucher", 1200),
            new Reward("♻️", "Eco Tote Bag", 800),
            new Reward("🌍", "Plant a Tree", 1000),
            new Reward("🍃", "Campus Meal Discount", 1500),
    };

    private static final BadgeDefinition[] BADGE_CATALOGUE = {
            new BadgeDefinition("first_steps", "🌱", "First Steps", "Awarded after reaching 100 sustainability points.", "Reach 100 points to unlock this badge.", R.drawable.bg_badge_green, 100, 0),
            new BadgeDefinition("recycler", "♻️", "Recycler", "Awarded after reaching 200 sustainability points.", "Reach 200 points to unlock this badge.", R.drawable.bg_badge_green, 200, 0),
            new BadgeDefinition("eco_sprout", "🍃", "Eco Sprout", "Awarded after reaching 500 sustainability points.", "Reach 500 points to unlock this badge.", R.drawable.bg_badge_amber, 500, 0),
            new BadgeDefinition("green_guardian", "🛡️", "Green Guardian", "Awarded after reaching 1000 sustainability points.", "Reach 1000 points to unlock this badge.", R.drawable.bg_badge_blue, 1000, 0),
            new BadgeDefinition("climate_champion", "🏆", "Climate Champion", "Awarded after reaching 2500 sustainability points.", "Reach 2500 points to unlock this badge.", R.drawable.bg_badge_amber, 2500, 0),
            new BadgeDefinition("streak_7", "🔥", "7 Day Streak", "Awarded after logging for 7 days in a row.", "Build a 7 day streak to unlock this badge.", R.drawable.bg_badge_green, 0, 7),
            new BadgeDefinition("streak_30", "🌟", "30 Day Streak", "Awarded after logging for 30 days in a row.", "Build a 30 day streak to unlock this badge.", R.drawable.bg_badge_blue, 0, 30),
            new BadgeDefinition("cat_bff", "🐱", "Cat BFF", "Awarded after keeping Mimi happy for 14 consecutive days.", "Keep Mimi happy for 14 days to unlock this badge.", R.drawable.bg_badge_amber, 0, 0)
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        currentRootView = view;

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return view;

        String uid = currentUser.getUid();
        currentUid = uid;

        TextView tvName = view.findViewById(R.id.tv_display_name);
        TextView tvPointsBadge = view.findViewById(R.id.tv_points_badge);
        TextView tvAvatar = view.findViewById(R.id.tv_avatar_initials);
        ImageView ivAvatarPhoto = view.findViewById(R.id.iv_avatar_photo);
        View btnTopLeftAccount = view.findViewById(R.id.btn_top_left_account);
        View btnAvatarSettings = view.findViewById(R.id.btn_avatar_settings);
        View btnNotifications = view.findViewById(R.id.btn_notifications);
        View notificationDot = view.findViewById(R.id.view_notification_dot);
        View historyScrollView = view.findViewById(R.id.history_scroll_view);

        badgesContainer = view.findViewById(R.id.badges_container);
        historyPreviewContainer = view.findViewById(R.id.history_preview_container);

        if (historyScrollView != null) {
            historyScrollView.setOnTouchListener((v, event) -> {
                disallowParentScroll(v, event);
                return false;
            });
        }

        if (btnTopLeftAccount != null) {
            btnTopLeftAccount.setOnClickListener(v -> openAccountSettings());
        }

        if (btnAvatarSettings != null) {
            btnAvatarSettings.setOnClickListener(v -> openAccountSettings());
        }

        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v -> showNotifications(uid));
        }

        setupNotifications(uid, notificationDot);

        profileListener = db.collection("users").document(uid)
                .addSnapshotListener((doc, e) -> {
                    if (!isAdded() || getContext() == null) return;
                    if (e != null || doc == null || !doc.exists()) return;

                    String displayName = doc.getString("displayName");
                    Long totalPointsValue = doc.getLong("totalPoints");
                    Long streakDaysValue = doc.getLong("streakDays");
                    String profilePhotoBase64 = doc.getString("profilePhotoBase64");

                    currentTotalPoints = totalPointsValue != null ? totalPointsValue : 0;
                    currentStreakDays = streakDaysValue != null ? streakDaysValue : 0;

                    if (tvName != null && !TextUtils.isEmpty(displayName)) {
                        tvName.setText(displayName);
                    }

                    updateAvatar(tvAvatar, ivAvatarPhoto, displayName, profilePhotoBase64);

                    if (tvPointsBadge != null) {
                        tvPointsBadge.setText("• " + currentTotalPoints + " pts");
                    }

                    renderBadges();

                    if (rewardsAllowed && currentRootView != null && currentUid != null) {
                        populateRewards(currentRootView, currentUid);
                    }
                });

        loadBadges(uid);
        loadHistoryPreview(uid);
        wireSettingsRows(view, uid);
        wireLogout(view);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(roleDoc -> {
                    String role = roleDoc.getString("role");
                    boolean isStaffUser = "staff".equals(role);

                    if (isStaffUser) {
                        rewardsAllowed = false;
                        hideRewardsSectionForStaff(view);
                    } else {
                        rewardsAllowed = true;
                        populateRewards(view, uid);
                    }
                });

        return view;
    }

    private void loadBadges(String uid) {
        if (badgesContainer == null) return;

        badgesListener = db.collection("users")
                .document(uid)
                .collection("badges")
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded() || getContext() == null) return;

                    earnedBadgeIds.clear();
                    earnedDates.clear();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            String docId = normalizeBadgeId(doc.getId());
                            earnedBadgeIds.add(docId);

                            String badgeIdField = doc.getString("badgeId");
                            if (!TextUtils.isEmpty(badgeIdField)) {
                                earnedBadgeIds.add(normalizeBadgeId(badgeIdField));
                            }

                            Timestamp earnedAt = doc.getTimestamp("earnedAt");
                            if (earnedAt == null) {
                                earnedAt = doc.getTimestamp("timestamp");
                            }

                            earnedDates.put(docId, earnedAt);

                            if (!TextUtils.isEmpty(badgeIdField)) {
                                earnedDates.put(normalizeBadgeId(badgeIdField), earnedAt);
                            }
                        }
                    }

                    renderBadges();
                });
    }

    private void loadHistoryPreview(String uid) {
        historyEntries.clear();

        db.collection("activity_logs")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(logSnapshots -> {
                    for (QueryDocumentSnapshot doc : logSnapshots) {
                        String activityType = doc.getString("activityType");
                        Long points = doc.getLong("points");
                        Timestamp timestamp = doc.getTimestamp("timestamp");

                        String title = "Log: " + (!TextUtils.isEmpty(activityType) ? activityType : "Activity");
                        String detail = (points != null ? points : 0) + " pts";

                        if (timestamp != null) {
                            detail += " • " + formatShortDate(timestamp);
                        }

                        historyEntries.add(new HistoryEntry(title, detail, getTimeMillis(timestamp)));
                    }

                    loadPostHistory(uid);
                })
                .addOnFailureListener(e -> loadPostHistory(uid));
    }

    private void loadPostHistory(String uid) {
        db.collection("posts")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(postSnapshots -> {
                    for (QueryDocumentSnapshot doc : postSnapshots) {
                        String text = doc.getString("text");
                        String caption = doc.getString("caption");
                        String content = doc.getString("content");
                        Timestamp timestamp = doc.getTimestamp("timestamp");

                        String postText = !TextUtils.isEmpty(text) ? text : caption;

                        if (TextUtils.isEmpty(postText)) {
                            postText = content;
                        }

                        if (TextUtils.isEmpty(postText)) {
                            postText = "Post";
                        }

                        String title = "Post";
                        String detail = postText;

                        if (timestamp != null) {
                            detail += " • " + formatShortDate(timestamp);
                        }

                        historyEntries.add(new HistoryEntry(title, detail, getTimeMillis(timestamp)));
                    }

                    renderHistoryPreview();
                })
                .addOnFailureListener(e -> renderHistoryPreview());
    }

    private void renderHistoryPreview() {
        if (historyPreviewContainer == null || getContext() == null) return;

        historyPreviewContainer.removeAllViews();

        historyEntries.sort((a, b) -> Long.compare(b.timeMillis, a.timeMillis));

        if (historyEntries.isEmpty()) {
            TextView emptyView = createHistoryText("No history yet.", "Logs and posts will appear here.");
            historyPreviewContainer.addView(emptyView);
            return;
        }

        for (int i = 0; i < historyEntries.size(); i++) {
            HistoryEntry entry = historyEntries.get(i);
            historyPreviewContainer.addView(createHistoryText(entry.title, entry.detail));

            if (i < historyEntries.size() - 1) {
                historyPreviewContainer.addView(createDivider());
            }
        }
    }

    private TextView createHistoryText(String title, String detail) {
        TextView textView = new TextView(requireContext());
        textView.setText(title + "\n" + detail);
        textView.setTextSize(14);
        textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));
        textView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        return textView;
    }

    private View createDivider() {
        View divider = new View(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        params.setMarginStart(dpToPx(16));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_divider));
        return divider;
    }

    private void renderBadges() {
        if (badgesContainer == null || getContext() == null) return;

        badgesContainer.removeAllViews();
        badgesContainer.setColumnCount(4);

        for (BadgeDefinition badge : BADGE_CATALOGUE) {
            boolean earned = isBadgeEarned(badge);
            Timestamp earnedAt = earnedDates.get(normalizeBadgeId(badge.id));

            View badgeView = createBadgeView(badge, earned, earnedAt);
            badgesContainer.addView(badgeView);
        }
    }

    private boolean isBadgeEarned(BadgeDefinition badge) {
        String normalizedId = normalizeBadgeId(badge.id);

        if (earnedBadgeIds.contains(normalizedId)) {
            return true;
        }

        if (badge.requiredPoints > 0 && currentTotalPoints >= badge.requiredPoints) {
            return true;
        }

        if (badge.requiredStreakDays > 0 && currentStreakDays >= badge.requiredStreakDays) {
            return true;
        }

        return false;
    }

    private View createBadgeView(BadgeDefinition badge, boolean earned, Timestamp earnedAt) {
        LinearLayout outer = new LinearLayout(requireContext());
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER);
        outer.setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6));

        GridLayout.LayoutParams outerParams = new GridLayout.LayoutParams();
        outerParams.width = 0;
        outerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        outerParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        outerParams.setGravity(Gravity.FILL_HORIZONTAL);
        outerParams.setMargins(0, 0, 0, dpToPx(12));
        outer.setLayoutParams(outerParams);

        FrameLayout badgeCircle = new FrameLayout(requireContext());
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(dpToPx(62), dpToPx(62));
        badgeCircle.setLayoutParams(circleParams);
        badgeCircle.setAlpha(earned ? 1.0f : 0.35f);

        TextView emojiView = new TextView(requireContext());
        emojiView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        emojiView.setBackgroundResource(earned ? badge.backgroundDrawable : R.drawable.bg_badge_locked);
        emojiView.setGravity(Gravity.CENTER);
        emojiView.setText(badge.emoji);
        emojiView.setTextSize(24);
        badgeCircle.addView(emojiView);

        if (!earned) {
            TextView lockView = new TextView(requireContext());
            lockView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            lockView.setGravity(Gravity.CENTER);
            lockView.setText("🔒");
            lockView.setTextSize(20);
            badgeCircle.addView(lockView);
        }

        TextView nameView = new TextView(requireContext());
        nameView.setText(badge.name.toUpperCase(Locale.getDefault()));
        nameView.setTextSize(9);
        nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        nameView.setMaxLines(2);
        nameView.setTextColor(ContextCompat.getColor(
                requireContext(),
                earned ? R.color.color_green_text : R.color.color_text_secondary
        ));

        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(0, dpToPx(6), 0, 0);
        nameView.setLayoutParams(nameParams);

        outer.addView(badgeCircle);
        outer.addView(nameView);

        outer.setClickable(true);
        outer.setFocusable(true);
        outer.setOnClickListener(v -> showBadgeDetailDialog(badge, earned, earnedAt));

        return outer;
    }

    private void showBadgeDetailDialog(BadgeDefinition badge, boolean earned, Timestamp earnedAt) {
        if (getContext() == null) return;

        String message;

        if (earned) {
            message = badge.description;

            if (earnedAt != null) {
                SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                message += "\n\nEarned on: " + format.format(earnedAt.toDate());
            } else {
                message += "\n\nStatus: Earned";
            }
        } else {
            message = badge.lockedHint + "\n\nStatus: Locked";
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle((earned ? badge.emoji + " " : "🔒 ") + badge.name)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void wireSettingsRows(View view, String uid) {
        LinearLayout rowAccount = view.findViewById(R.id.row_account);
        LinearLayout rowNotifications = view.findViewById(R.id.row_notifications);
        LinearLayout rowHelp = view.findViewById(R.id.row_help);

        if (rowAccount != null) {
            rowAccount.setOnClickListener(v -> openAccountSettings());
        }

        if (rowNotifications != null) {
            rowNotifications.setOnClickListener(v -> showNotifications(uid));
        }

        if (rowHelp != null) {
            rowHelp.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Help & Support — coming soon!", Toast.LENGTH_SHORT).show()
            );
        }

        TextView tvViewAll = view.findViewById(R.id.tv_view_all);
        if (tvViewAll != null) {
            tvViewAll.setOnClickListener(v ->
                    Toast.makeText(getContext(), "All badges are shown below", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void wireLogout(View view) {
        LinearLayout rowLogout = view.findViewById(R.id.row_logout);

        if (rowLogout != null) {
            rowLogout.setOnClickListener(v -> {
                auth.signOut();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        }
    }

    private void populateRewards(View root, String uid) {
        LinearLayout container = root.findViewById(R.id.rewards_container);
        if (container == null) return;

        container.removeAllViews();

        int dp16 = dpToPx(16);
        int dp12 = dpToPx(12);

        for (int i = 0; i < REWARDS.length; i++) {
            Reward reward = REWARDS[i];
            boolean canRedeem = currentTotalPoints >= reward.pointsCost;

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(56)
            );
            row.setLayoutParams(rowParams);
            row.setPadding(dp16, 0, dp16, 0);
            row.setAlpha(canRedeem ? 1.0f : 0.35f);
            row.setClickable(canRedeem);
            row.setFocusable(canRedeem);

            TextView tvEmoji = new TextView(getContext());
            tvEmoji.setText(reward.emoji);
            tvEmoji.setTextSize(20);
            tvEmoji.setPadding(0, 0, dp12, 0);
            row.addView(tvEmoji);

            TextView tvName = new TextView(getContext());
            tvName.setText(reward.name);
            tvName.setTextSize(15);
            tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));

            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            tvName.setLayoutParams(nameParams);
            row.addView(tvName);

            TextView tvCost = new TextView(getContext());
            tvCost.setText(reward.pointsCost + " pts");
            tvCost.setTextSize(13);
            tvCost.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green_text));
            tvCost.setTypeface(null, Typeface.BOLD);
            row.addView(tvCost);

            if (canRedeem) {
                final Reward finalReward = reward;
                row.setOnClickListener(v -> showRedeemDialog(uid, finalReward, root));
            }

            container.addView(row);

            if (i < REWARDS.length - 1) {
                View divider = new View(getContext());
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                );
                divParams.setMarginStart(dp16);
                divider.setLayoutParams(divParams);
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_divider));
                container.addView(divider);
            }
        }
    }

    private void showRedeemDialog(String uid, Reward reward, View rootView) {
        if (getContext() == null) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Redeem " + reward.name + "?")
                .setMessage("This will cost " + reward.pointsCost + " pts.\nYou currently have " + currentTotalPoints + " pts.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Redeem", (dialog, which) -> {
                    if (currentTotalPoints < reward.pointsCost) {
                        Toast.makeText(
                                getContext(),
                                "Not enough points (need " + reward.pointsCost + ", have " + currentTotalPoints + ")",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    String rewardCode = generateRewardCode();

                    Map<String, Object> redemptionData = new HashMap<>();
                    redemptionData.put("uid", uid);
                    redemptionData.put("rewardId", reward.name.toLowerCase(Locale.getDefault()).replace(" ", "_"));
                    redemptionData.put("rewardName", reward.name);
                    redemptionData.put("cost", reward.pointsCost);
                    redemptionData.put("code", rewardCode);
                    redemptionData.put("status", "active");
                    redemptionData.put("redeemedAt", Timestamp.now());

                    db.collection("rewards_redemptions")
                            .add(redemptionData)
                            .addOnSuccessListener(docRef ->
                                    db.collection("users")
                                            .document(uid)
                                            .update("totalPoints", FieldValue.increment(-reward.pointsCost))
                                            .addOnSuccessListener(unused -> {
                                                showRewardCodeDialog(reward, rewardCode);

                                                Snackbar.make(
                                                        rootView,
                                                        reward.emoji + " Reward redeemed!",
                                                        Snackbar.LENGTH_LONG
                                                ).show();
                                            })
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(
                                                            getContext(),
                                                            "Failed to deduct points. Please try again.",
                                                            Toast.LENGTH_SHORT
                                                    ).show()
                                            )
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(
                                            getContext(),
                                            "Redemption failed. Please try again.",
                                            Toast.LENGTH_SHORT
                                    ).show()
                            );
                })
                .show();
    }

    private void showRewardCodeDialog(Reward reward, String rewardCode) {
        if (getContext() == null) return;

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dpToPx(24), dpToPx(20), dpToPx(24), dpToPx(20));

        TextView emoji = new TextView(requireContext());
        emoji.setText(reward.emoji);
        emoji.setTextSize(42);
        emoji.setGravity(Gravity.CENTER);
        card.addView(emoji);

        TextView title = new TextView(requireContext());
        title.setText(reward.name);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dpToPx(8), 0, dpToPx(8));
        card.addView(title);

        TextView code = new TextView(requireContext());
        code.setText(rewardCode);
        code.setTextSize(32);
        code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        code.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green_text));
        code.setGravity(Gravity.CENTER);
        code.setPadding(0, dpToPx(8), 0, dpToPx(8));
        card.addView(code);

        TextView message = new TextView(requireContext());
        message.setText("Show this code to redeem your reward.");
        message.setTextSize(14);
        message.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary));
        message.setGravity(Gravity.CENTER);
        card.addView(message);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reward Code")
                .setView(card)
                .setPositiveButton("Done", null)
                .show();
    }

    private String generateRewardCode() {
        int code = new Random().nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    private void openAccountSettings() {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new AccountSettingsFragment())
                .addToBackStack(null)
                .commit();
    }

    private void setupNotifications(String uid, View notificationDot) {
        if (notificationDot == null) return;

        notificationsListener = db.collection("users")
                .document(uid)
                .collection("notifications")
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded() || getContext() == null) return;

                    boolean hasNotifications = snapshots != null && !snapshots.isEmpty();
                    notificationDot.setVisibility(hasNotifications ? View.VISIBLE : View.GONE);
                });
    }

    private void showNotifications(String uid) {
        if (getContext() == null) return;

        db.collection("users")
                .document(uid)
                .collection("notifications")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || getContext() == null) return;

                    if (querySnapshot.isEmpty()) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Notifications")
                                .setMessage("No notifications yet.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    StringBuilder builder = new StringBuilder();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String title = doc.getString("title");
                        String message = doc.getString("message");
                        Timestamp timestamp = doc.getTimestamp("timestamp");

                        if (!TextUtils.isEmpty(title)) {
                            builder.append(title).append("\n");
                        }

                        if (!TextUtils.isEmpty(message)) {
                            builder.append(message).append("\n");
                        }

                        if (timestamp != null) {
                            SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault());
                            builder.append(format.format(timestamp.toDate())).append("\n");
                        }

                        builder.append("\n");
                    }

                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Notifications")
                            .setMessage(builder.toString().trim())
                            .setPositiveButton("OK", null)
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to load notifications.", Toast.LENGTH_SHORT).show()
                );
    }

    private void updateAvatar(TextView tvAvatar,
                              ImageView ivAvatarPhoto,
                              String displayName,
                              String profilePhotoBase64) {
        if (tvAvatar == null || ivAvatarPhoto == null) return;

        if (!TextUtils.isEmpty(profilePhotoBase64)) {
            try {
                byte[] imageBytes = Base64.decode(profilePhotoBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                if (bitmap != null) {
                    ivAvatarPhoto.setImageBitmap(bitmap);
                    ivAvatarPhoto.setVisibility(View.VISIBLE);
                    tvAvatar.setVisibility(View.GONE);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        ivAvatarPhoto.setVisibility(View.GONE);
        tvAvatar.setVisibility(View.VISIBLE);
        tvAvatar.setText(getInitials(displayName));
    }

    private String getInitials(String displayName) {
        if (TextUtils.isEmpty(displayName)) return "ST";

        String cleanName = displayName.trim();
        if (cleanName.isEmpty()) return "ST";

        String[] parts = cleanName.split("\\s+");

        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.getDefault());
        }

        if (cleanName.length() >= 2) {
            return cleanName.substring(0, 2).toUpperCase(Locale.getDefault());
        }

        return cleanName.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private String normalizeBadgeId(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.getDefault()).replaceAll("[^a-z0-9]", "");
    }

    private String formatShortDate(Timestamp timestamp) {
        if (timestamp == null) return "";
        SimpleDateFormat format = new SimpleDateFormat("MMM d", Locale.getDefault());
        return format.format(timestamp.toDate());
    }

    private long getTimeMillis(Timestamp timestamp) {
        if (timestamp == null) return 0;
        return timestamp.toDate().getTime();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void disallowParentScroll(View view, MotionEvent event) {
        ViewParent parent = view.getParent();

        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
            parent = parent.getParent();
        }

        if (event.getAction() == MotionEvent.ACTION_UP ||
                event.getAction() == MotionEvent.ACTION_CANCEL) {
            parent = view.getParent();

            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(false);
                parent = parent.getParent();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }

        if (badgesListener != null) {
            badgesListener.remove();
            badgesListener = null;
        }

        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }

        currentRootView = null;
    }

    private void hideRewardsSectionForStaff(View view) {
        View rewardsContainer = view.findViewById(R.id.rewards_container);
        if (rewardsContainer == null) return;

        View parent = (View) rewardsContainer.getParent();
        while (parent != null) {
            if (parent instanceof CardView) {
                parent.setVisibility(View.GONE);
                return;
            }

            if (parent instanceof androidx.core.widget.NestedScrollView) {
                break;
            }

            View grandParent = (parent.getParent() instanceof View)
                    ? (View) parent.getParent() : null;
            parent = grandParent;
        }

        rewardsContainer.setVisibility(View.GONE);
    }
}