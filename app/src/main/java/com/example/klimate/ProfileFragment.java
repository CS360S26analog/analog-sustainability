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
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import com.example.klimate.local.ActivityLogDao;
import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.example.klimate.local.UserEntity;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ProfileFragment extends Fragment {

    private FirebaseFirestore db;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private FirebaseAuth auth;

    private long currentTotalPoints = 0;
    private long currentStreakDays = 0;

    private GridLayout badgesContainer;
    private ListenerRegistration profileListener;
    private ListenerRegistration badgesListener;

    private final Set<String> earnedBadgeIds = new HashSet<>();
    private final Map<String, Timestamp> earnedDates = new HashMap<>();

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

    private static final Reward[] REWARDS = {
            new Reward("☕", "Free Coffee", 500),
            new Reward("🚲", "Bike Rental Voucher", 1200),
            new Reward("♻️", "Eco Tote Bag", 800),
            new Reward("🌍", "Plant a Tree", 1000),
            new Reward("🍃", "Campus Meal Discount", 1500),
    };

    private static final BadgeDefinition[] BADGE_CATALOGUE = {
            new BadgeDefinition(
                    "first_steps",
                    "🌱",
                    "First Steps",
                    "Awarded after reaching 100 sustainability points.",
                    "Reach 100 points to unlock this badge.",
                    R.drawable.bg_badge_green,
                    100,
                    0
            ),
            new BadgeDefinition(
                    "recycler",
                    "♻️",
                    "Recycler",
                    "Awarded after reaching 200 sustainability points.",
                    "Reach 200 points to unlock this badge.",
                    R.drawable.bg_badge_green,
                    200,
                    0
            ),
            new BadgeDefinition(
                    "eco_sprout",
                    "🍃",
                    "Eco Sprout",
                    "Awarded after reaching 500 sustainability points.",
                    "Reach 500 points to unlock this badge.",
                    R.drawable.bg_badge_amber,
                    500,
                    0
            ),
            new BadgeDefinition(
                    "green_guardian",
                    "🛡️",
                    "Green Guardian",
                    "Awarded after reaching 1000 sustainability points.",
                    "Reach 1000 points to unlock this badge.",
                    R.drawable.bg_badge_blue,
                    1000,
                    0
            ),
            new BadgeDefinition(
                    "climate_champion",
                    "🏆",
                    "Climate Champion",
                    "Awarded after reaching 2500 sustainability points.",
                    "Reach 2500 points to unlock this badge.",
                    R.drawable.bg_badge_amber,
                    2500,
                    0
            ),
            new BadgeDefinition(
                    "streak_7",
                    "🔥",
                    "7 Day Streak",
                    "Awarded after logging for 7 days in a row.",
                    "Build a 7 day streak to unlock this badge.",
                    R.drawable.bg_badge_green,
                    0,
                    7
            ),
            new BadgeDefinition(
                    "streak_30",
                    "🌟",
                    "30 Day Streak",
                    "Awarded after logging for 30 days in a row.",
                    "Build a 30 day streak to unlock this badge.",
                    R.drawable.bg_badge_blue,
                    0,
                    30
            ),
            new BadgeDefinition(
                    "cat_bff",
                    "🐱",
                    "Cat BFF",
                    "Awarded after keeping Mimi happy for 14 consecutive days.",
                    "Keep Mimi happy for 14 days to unlock this badge.",
                    R.drawable.bg_badge_amber,
                    0,
                    0
            )
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return view;

        String uid = currentUser.getUid();

        TextView tvName = view.findViewById(R.id.tv_display_name);
        TextView tvPointsBadge = view.findViewById(R.id.tv_points_badge);
        TextView tvCo2 = view.findViewById(R.id.tv_co2_value);
        TextView tvStreak = view.findViewById(R.id.tv_streak_value);
        TextView tvAvatar = view.findViewById(R.id.tv_avatar_initials);
        badgesContainer = view.findViewById(R.id.badges_container);

        executor.execute(() -> {
       UserEntity cached = AppDatabase.getInstance(requireContext())
                                      .userDao().getUserSync(uid);
       if (cached == null || !isAdded()) return;
       requireActivity().runOnUiThread(() -> {
           if (tvName != null && !cached.displayName.isEmpty())
               tvName.setText(cached.displayName);
           if (tvAvatar != null && !cached.displayName.isEmpty())
               tvAvatar.setText(getInitials(cached.displayName));
           if (tvPointsBadge != null)
               tvPointsBadge.setText("• " + cached.totalPoints + " pts");
           if (tvCo2 != null)
               tvCo2.setText(String.format(Locale.getDefault(), "%.1f", cached.co2SavedKg));
           if (tvStreak != null)
               tvStreak.setText(String.valueOf(cached.streakDays));
           currentTotalPoints = cached.totalPoints;
           currentStreakDays  = cached.streakDays;
           renderBadges(); // render badges immediately from cached points/streak
       });
   });

        profileListener = db.collection("users").document(uid)
                .addSnapshotListener((doc, e) -> {
                    if (!isAdded() || getContext() == null) return;
                    if (e != null || doc == null || !doc.exists()) return;

                    String displayName = doc.getString("displayName");
                    Long totalPointsValue = doc.getLong("totalPoints");
                    Long streakDaysValue = doc.getLong("streakDays");
                    Double co2SavedValue = doc.getDouble("co2SavedKg");

                    currentTotalPoints = totalPointsValue != null ? totalPointsValue : 0;
                    currentStreakDays = streakDaysValue != null ? streakDaysValue : 0;
                    double co2Saved = co2SavedValue != null ? co2SavedValue : 0.0;

                    if (tvName != null && !TextUtils.isEmpty(displayName)) {
                        tvName.setText(displayName);
                    }

                    if (tvAvatar != null && !TextUtils.isEmpty(displayName)) {
                        tvAvatar.setText(getInitials(displayName));
                    }

                    if (tvPointsBadge != null) {
                        tvPointsBadge.setText("• " + currentTotalPoints + " pts");
                    }

                    if (tvCo2 != null) {
                        tvCo2.setText(String.format(Locale.getDefault(), "%.1f", co2Saved));
                    }

                    if (tvStreak != null) {
                        tvStreak.setText(String.valueOf(currentStreakDays));
                    }
                    executor.execute(() -> {
                       UserEntity u = AppDatabase.getInstance(requireContext()).userDao().getUserSync(uid);
                       if (u != null) {
                           u.totalPoints = (int) currentTotalPoints;
                           u.streakDays  = (int) currentStreakDays;
                           u.co2SavedKg  = co2Saved;   // (the local variable from the snapshot)
                           AppDatabase.getInstance(requireContext()).userDao().update(u);
                       }
                   });

                    renderBadges();
                });

        loadBadges(uid);
        wireSettingsRows(view);
        wireLogout(view);

        // Only show rewards for student accounts (not staff)
        db.collection("users").document(uid).get()
                .addOnSuccessListener(roleDoc -> {
                    String role = roleDoc.getString("role");
                    boolean isStaffUser = "staff".equals(role);

                    if (isStaffUser) {
                        // Hide the entire rewards section for staff
                        hideRewardsSectionForStaff(view);
                    } else {
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

    private void wireSettingsRows(View view) {
        int[] rowIds = {
                R.id.row_account,
                R.id.row_privacy,
                R.id.row_notifications,
                R.id.row_help
        };

        String[] rowLabels = {
                "Account Settings",
                "Privacy",
                "Notifications",
                "Help & Support"
        };

        for (int i = 0; i < rowIds.length; i++) {
            final String label = rowLabels[i];
            LinearLayout row = view.findViewById(rowIds[i]);

            if (row != null) {
                row.setOnClickListener(v ->
                        Toast.makeText(getContext(), label + " — coming soon!", Toast.LENGTH_SHORT).show()
                );
            }
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

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(56)
            );
            row.setLayoutParams(rowParams);
            row.setPadding(dp16, 0, dp16, 0);
            row.setClickable(true);
            row.setFocusable(true);

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

            final Reward finalReward = reward;
            final TextView finalTvCost = tvCost;
            final LinearLayout finalRow = row;

            row.setOnClickListener(v -> showRedeemDialog(uid, finalReward, finalRow, finalTvCost, root));

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

    private void showRedeemDialog(String uid,
                                  Reward reward,
                                  LinearLayout row,
                                  TextView tvCost,
                                  View rootView) {
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

                    Map<String, Object> redemptionData = new HashMap<>();
                    redemptionData.put("uid", uid);
                    redemptionData.put("rewardId", reward.name.toLowerCase(Locale.getDefault()).replace(" ", "_"));
                    redemptionData.put("rewardName", reward.name);
                    redemptionData.put("cost", reward.pointsCost);
                    redemptionData.put("redeemedAt", Timestamp.now());

                    db.collection("rewards_redemptions")
                            .add(redemptionData)
                            .addOnSuccessListener(docRef ->
                                    db.collection("users")
                                            .document(uid)
                                            .update("totalPoints", FieldValue.increment(-reward.pointsCost))
                                            .addOnSuccessListener(unused -> {
                                                row.setAlpha(0.45f);
                                                row.setClickable(false);
                                                tvCost.setText("Redeemed");
                                                tvCost.setTextColor(ContextCompat.getColor(
                                                        requireContext(),
                                                        R.color.color_text_secondary
                                                ));

                                                Snackbar.make(
                                                        rootView,
                                                        reward.emoji + " Reward redeemed! Enjoy your " + reward.name + ".",
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

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
    }

    /**
     * Hides the rewards section entirely for staff accounts.
     * Staff do not participate in the points/rewards system.
     * Finds and hides the rewards header label and the rewards container.
     */
    /**
     * Hides the rewards section entirely for staff accounts.
     * Staff do not participate in the points/rewards system.
     */
    private void hideRewardsSectionForStaff(View view) {
        View rewardsContainer = view.findViewById(R.id.rewards_container);
        if (rewardsContainer == null) return;

        // Walk up the view hierarchy to hide the entire rewards card/section
        View parent = (View) rewardsContainer.getParent();
        while (parent != null) {
            if (parent instanceof androidx.cardview.widget.CardView) {
                parent.setVisibility(View.GONE);
                return;
            }
            // Stop at the ScrollView level — don't go further up
            if (parent instanceof androidx.core.widget.NestedScrollView) {
                break;
            }
            View grandParent = (parent.getParent() instanceof View)
                    ? (View) parent.getParent() : null;
            parent = grandParent;
        }

        // Fallback — just hide the container itself
        rewardsContainer.setVisibility(View.GONE);
    }
}