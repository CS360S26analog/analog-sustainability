/**
 * ProfileFragment.java
 *
 * Displays the logged-in student's profile, including real-time points
 * balance read from Firestore, streak days, CO2 saved, university, and
 * a list of available rewards. Handles reward redemption with optimistic
 * UI updates.
 *
 * Performance improvements:
 * - Reads cached profile from SharedPreferences on open for instant display
 * - Loads user document and redeemed rewards in PARALLEL on first load
 * - Reward redemption updates UI optimistically before Firebase confirms
 * - Rolls back UI if Firebase write fails
 *
 * Role in design: Part of the View layer (MVC). Reads from the users/
 * Firestore collection using the current Firebase Auth UID.
 *
 * Outstanding issues: Rewards list is hardcoded — replace with Firestore
 * read in a future sprint. Challenges count is hardcoded.
 *
 * @author Haroon
 * @author Izza
 * @author Karar
 * @author Maryam W.
 * @author Maryam A.
 */
package com.example.klimate;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ProfileFragment extends Fragment {

    // -------------------------------------------------------------------
    // SharedPreferences cache keys
    // -------------------------------------------------------------------
    private static final String PREF_NAME        = "klimate_profile_cache";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_TOTAL_POINTS = "total_points";
    private static final String KEY_STREAK_DAYS  = "streak_days";
    private static final String KEY_CO2_SAVED    = "co2_saved";
    private static final String KEY_UNIVERSITY   = "university";

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SharedPreferences prefs;

    // Live points balance — kept in sync by snapshot listener
    // and updated optimistically during redemption
    private long currentTotalPoints = 0;

    // -------------------------------------------------------------------
    // Reward data class
    // -------------------------------------------------------------------
    private static class Reward {
        final String emoji;
        final String name;
        final int pointsCost;

        Reward(String emoji, String name, int pointsCost) {
            this.emoji      = emoji;
            this.name       = name;
            this.pointsCost = pointsCost;
        }

        /** Stable ID used to match against rewards_redemptions records */
        String getId() {
            return name.toLowerCase().replace(" ", "_");
        }
    }

    private static final Reward[] REWARDS = {
            new Reward("☕", "Free Coffee",          500),
            new Reward("🚲", "Bike Rental Voucher", 1200),
            new Reward("♻️", "Eco Tote Bag",         800),
            new Reward("🌍", "Plant a Tree",        1000),
            new Reward("🍃", "Campus Meal Discount", 1500),
    };

    // -------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        db    = FirebaseFirestore.getInstance();
        auth  = FirebaseAuth.getInstance();
        prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return view;

        String uid = currentUser.getUid();

        // Find all TextViews once here — passed into helpers so they
        // are never looked up more than once per fragment open
        TextView tvName        = view.findViewById(R.id.tv_display_name);
        TextView tvPointsBadge = view.findViewById(R.id.tv_points_badge);
        TextView tvCo2         = view.findViewById(R.id.tv_co2_value);
        TextView tvStreak      = view.findViewById(R.id.tv_streak_value);
        TextView tvAvatar      = view.findViewById(R.id.tv_avatar_initials);
        TextView tvUniversity  = view.findViewById(R.id.tv_university);

        // -------------------------------------------------------------------
        // STEP 1 — Show cached data instantly so screen is never blank
        // -------------------------------------------------------------------
        applyCachedProfile(tvName, tvPointsBadge, tvCo2, tvStreak, tvAvatar, tvUniversity);

        // -------------------------------------------------------------------
        // STEP 2 — Fire user doc fetch + redeemed rewards fetch IN PARALLEL
        //          Both queries start at the same time — no sequential waiting
        // -------------------------------------------------------------------
        Task<DocumentSnapshot> userTask = db.collection("users")
                .document(uid)
                .get();

        Task<QuerySnapshot> redeemedTask = db.collection("rewards_redemptions")
                .whereEqualTo("uid", uid)
                .get();

        LinearLayout rewardsContainer = view.findViewById(R.id.rewards_container);

        Tasks.whenAllComplete(userTask, redeemedTask)
                .addOnCompleteListener(combinedTask -> {
                    if (!isAdded()) return;

                    // --- Process user document ---
                    if (userTask.isSuccessful() && userTask.getResult() != null) {
                        DocumentSnapshot doc = userTask.getResult();
                        if (doc.exists()) {
                            String displayName = doc.getString("displayName");
                            long   totalPoints = doc.getLong("totalPoints")  != null ? doc.getLong("totalPoints")  : 0;
                            long   streakDays  = doc.getLong("streakDays")   != null ? doc.getLong("streakDays")   : 0;
                            double co2Saved    = doc.getDouble("co2SavedKg") != null ? doc.getDouble("co2SavedKg") : 0.0;

                            // University defaults to LUMS for old accounts that
                            // were created before the field existed
                            String university  = doc.getString("university");
                            if (university == null || university.trim().isEmpty()) {
                                university = "LUMS";
                            }

                            currentTotalPoints = totalPoints;

                            updateProfileUI(
                                    tvName, tvPointsBadge, tvCo2, tvStreak,
                                    tvAvatar, tvUniversity,
                                    displayName, totalPoints, streakDays,
                                    co2Saved, university
                            );

                            saveToCache(displayName, totalPoints, streakDays,
                                    co2Saved, university);
                        }
                    }

                    // --- Process redeemed rewards ---
                    Set<String> redeemedIds = new HashSet<>();
                    if (redeemedTask.isSuccessful() && redeemedTask.getResult() != null) {
                        for (DocumentSnapshot doc : redeemedTask.getResult().getDocuments()) {
                            String rewardId = doc.getString("rewardId");
                            if (rewardId != null) {
                                redeemedIds.add(rewardId);
                            }
                        }
                    }

                    if (rewardsContainer != null) {
                        rewardsContainer.removeAllViews();
                        populateRewards(view, rewardsContainer, uid, redeemedIds);
                    }
                });

        // -------------------------------------------------------------------
        // STEP 3 — Attach snapshot listener for real-time updates
        //          (e.g. points ticking up from votes while profile is open)
        // -------------------------------------------------------------------
        db.collection("users").document(uid)
                .addSnapshotListener((doc, e) -> {
                    if (!isAdded() || e != null || doc == null || !doc.exists()) return;

                    String displayName = doc.getString("displayName");
                    long   totalPoints = doc.getLong("totalPoints")  != null ? doc.getLong("totalPoints")  : 0;
                    long   streakDays  = doc.getLong("streakDays")   != null ? doc.getLong("streakDays")   : 0;
                    double co2Saved    = doc.getDouble("co2SavedKg") != null ? doc.getDouble("co2SavedKg") : 0.0;
                    String university  = doc.getString("university");
                    if (university == null || university.trim().isEmpty()) {
                        university = "LUMS";
                    }

                    currentTotalPoints = totalPoints;

                    updateProfileUI(
                            tvName, tvPointsBadge, tvCo2, tvStreak,
                            tvAvatar, tvUniversity,
                            displayName, totalPoints, streakDays,
                            co2Saved, university
                    );

                    saveToCache(displayName, totalPoints, streakDays,
                            co2Saved, university);
                });

        // Settings rows
        int[]    rowIds    = {R.id.row_account, R.id.row_privacy, R.id.row_notifications, R.id.row_help};
        String[] rowLabels = {"Account Settings", "Privacy", "Notifications", "Help & Support"};

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
                    Toast.makeText(getContext(), "All achievements — coming soon!", Toast.LENGTH_SHORT).show()
            );
        }

        LinearLayout rowLogout = view.findViewById(R.id.row_logout);
        if (rowLogout != null) {
            rowLogout.setOnClickListener(v -> {
                // Clear cache on logout so stale data never shows for next user
                prefs.edit().clear().apply();
                auth.signOut();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        }

        return view;
    }

    // -------------------------------------------------------------------
    // Cache helpers
    // -------------------------------------------------------------------

    /**
     * Reads cached profile data from SharedPreferences and applies it
     * to the UI immediately — before any Firebase call fires.
     * This ensures the screen is never blank on open.
     *
     * @param tvName        display name TextView
     * @param tvPointsBadge points badge TextView
     * @param tvCo2         CO2 saved TextView
     * @param tvStreak      streak days TextView
     * @param tvAvatar      avatar initials TextView
     * @param tvUniversity  university name TextView
     */
    private void applyCachedProfile(TextView tvName,
                                    TextView tvPointsBadge,
                                    TextView tvCo2,
                                    TextView tvStreak,
                                    TextView tvAvatar,
                                    TextView tvUniversity) {
        String cachedName       = prefs.getString(KEY_DISPLAY_NAME, null);
        long   cachedPoints     = prefs.getLong(KEY_TOTAL_POINTS, 0);
        long   cachedStreak     = prefs.getLong(KEY_STREAK_DAYS, 0);
        float  cachedCo2        = prefs.getFloat(KEY_CO2_SAVED, 0f);
        String cachedUniversity = prefs.getString(KEY_UNIVERSITY, "LUMS");

        // No cache yet — first ever open, nothing to show
        if (cachedName == null) return;

        currentTotalPoints = cachedPoints;

        updateProfileUI(
                tvName, tvPointsBadge, tvCo2, tvStreak,
                tvAvatar, tvUniversity,
                cachedName, cachedPoints, cachedStreak,
                (double) cachedCo2, cachedUniversity
        );
    }

    /**
     * Persists the latest profile values to SharedPreferences.
     * Called every time Firebase returns fresh data so the cache
     * stays current and the next open is always instant.
     *
     * @param displayName  the user's display name
     * @param totalPoints  current points balance
     * @param streakDays   current streak length
     * @param co2Saved     cumulative CO2 saved in kg
     * @param university   the user's university
     */
    private void saveToCache(String displayName,
                             long totalPoints,
                             long streakDays,
                             double co2Saved,
                             String university) {
        prefs.edit()
                .putString(KEY_DISPLAY_NAME, displayName != null ? displayName : "")
                .putLong(KEY_TOTAL_POINTS, totalPoints)
                .putLong(KEY_STREAK_DAYS, streakDays)
                .putFloat(KEY_CO2_SAVED, (float) co2Saved)
                .putString(KEY_UNIVERSITY, university != null ? university : "LUMS")
                .apply();
    }

    /**
     * Applies all profile values to the TextViews on screen.
     * Called from both the cache path (instant) and Firebase path (fresh).
     * University is passed as a plain String — no Firestore reference needed.
     *
     * @param tvName        display name TextView
     * @param tvPointsBadge points badge TextView
     * @param tvCo2         CO2 saved TextView
     * @param tvStreak      streak days TextView
     * @param tvAvatar      avatar initials TextView
     * @param tvUniversity  university name TextView
     * @param displayName   user's display name
     * @param totalPoints   current points balance
     * @param streakDays    current streak length
     * @param co2Saved      cumulative CO2 saved in kg
     * @param university    the user's university name
     */
    private void updateProfileUI(TextView tvName,
                                 TextView tvPointsBadge,
                                 TextView tvCo2,
                                 TextView tvStreak,
                                 TextView tvAvatar,
                                 TextView tvUniversity,
                                 String displayName,
                                 long totalPoints,
                                 long streakDays,
                                 double co2Saved,
                                 String university) {
        if (tvName != null && displayName != null) {
            tvName.setText(displayName);
        }

        if (tvAvatar != null && displayName != null && displayName.length() >= 2) {
            tvAvatar.setText(displayName.substring(0, 2).toUpperCase());
        }

        if (tvPointsBadge != null) {
            tvPointsBadge.setText("• " + totalPoints + " pts");
        }

        if (tvCo2 != null) {
            tvCo2.setText(String.format("%.1f", co2Saved));
        }

        if (tvStreak != null) {
            tvStreak.setText(String.valueOf(streakDays));
        }

        if (tvUniversity != null) {
            tvUniversity.setText("🏛 " + university);
        }
    }

    // -------------------------------------------------------------------
    // Rewards
    // -------------------------------------------------------------------

    /**
     * Populates the rewards container. Marks already-redeemed rewards
     * as greyed out using the redeemed IDs fetched in parallel with the
     * user document — so the state is correct from the first render.
     *
     * @param root         root view for Snackbar anchoring
     * @param container    the LinearLayout to add rows into
     * @param uid          current user UID
     * @param redeemedIds  set of rewardId strings already redeemed
     */
    private void populateRewards(View root,
                                 LinearLayout container,
                                 String uid,
                                 Set<String> redeemedIds) {
        if (getContext() == null) return;

        int dp16 = dpToPx(16);
        int dp12 = dpToPx(12);

        for (int i = 0; i < REWARDS.length; i++) {
            Reward reward = REWARDS[i];
            boolean alreadyRedeemed = redeemedIds.contains(reward.getId());

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56));
            row.setLayoutParams(rowParams);
            row.setPadding(dp16, 0, dp16, 0);
            row.setClickable(!alreadyRedeemed);
            row.setFocusable(!alreadyRedeemed);

            if (alreadyRedeemed) {
                row.setAlpha(0.45f);
            }

            TextView tvEmoji = new TextView(getContext());
            tvEmoji.setText(reward.emoji);
            tvEmoji.setTextSize(20);
            tvEmoji.setPadding(0, 0, dp12, 0);
            row.addView(tvEmoji);

            TextView tvName = new TextView(getContext());
            tvName.setText(reward.name);
            tvName.setTextSize(15);
            tvName.setTextColor(getResources().getColor(R.color.color_text_primary, null));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(nameParams);
            row.addView(tvName);

            TextView tvCost = new TextView(getContext());
            tvCost.setTextSize(13);
            tvCost.setTypeface(null, android.graphics.Typeface.BOLD);

            if (alreadyRedeemed) {
                tvCost.setText("Redeemed");
                tvCost.setTextColor(
                        getResources().getColor(R.color.color_text_secondary, null));
            } else {
                tvCost.setText(reward.pointsCost + " pts");
                tvCost.setTextColor(
                        getResources().getColor(R.color.color_green_text, null));

                final LinearLayout finalRow  = row;
                final TextView finalTvCost   = tvCost;

                row.setOnClickListener(v ->
                        showRedeemDialog(uid, reward, finalRow, finalTvCost, root)
                );
            }

            row.addView(tvCost);
            container.addView(row);

            if (i < REWARDS.length - 1) {
                View divider = new View(getContext());
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divParams.setMarginStart(dp16);
                divider.setLayoutParams(divParams);
                divider.setBackgroundColor(
                        getResources().getColor(R.color.color_divider, null));
                container.addView(divider);
            }
        }
    }

    // -------------------------------------------------------------------
    // Reward redemption — optimistic UI
    // -------------------------------------------------------------------

    /**
     * Shows a confirmation dialog for redeeming a reward.
     *
     * Applies an OPTIMISTIC UPDATE immediately on confirm:
     *   - Points badge decremented before Firebase responds
     *   - Row greyed out immediately
     *   - Cache updated immediately
     *
     * Firebase writes happen in the background. If either write fails,
     * rollbackRedemption() fully restores the previous UI state.
     *
     * @param uid          current user UID
     * @param reward       the Reward being redeemed
     * @param row          the row view to grey out on success
     * @param tvCost       the cost label to change to "Redeemed"
     * @param rootView     root view used to anchor the Snackbar
     */
    private void showRedeemDialog(String uid,
                                  Reward reward,
                                  LinearLayout row,
                                  TextView tvCost,
                                  View rootView) {
        if (getContext() == null) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Redeem " + reward.name + "?")
                .setMessage("This will cost " + reward.pointsCost
                        + " pts.\nYou currently have " + currentTotalPoints + " pts.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Redeem", (dialog, which) -> {

                    if (currentTotalPoints < reward.pointsCost) {
                        Toast.makeText(
                                getContext(),
                                "Not enough points (need " + reward.pointsCost
                                        + ", have " + currentTotalPoints + ")",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    // OPTIMISTIC UPDATE — apply immediately before Firebase confirms
                    long pointsBeforeRedemption = currentTotalPoints;
                    currentTotalPoints -= reward.pointsCost;

                    row.setAlpha(0.45f);
                    row.setClickable(false);
                    tvCost.setText("Redeemed");
                    tvCost.setTextColor(
                            getResources().getColor(R.color.color_text_secondary, null));

                    updatePointsBadgeInView(rootView, currentTotalPoints);

                    prefs.edit()
                            .putLong(KEY_TOTAL_POINTS, currentTotalPoints)
                            .apply();

                    // BACKGROUND — write to Firebase
                    Map<String, Object> redemptionData = new HashMap<>();
                    redemptionData.put("uid",        uid);
                    redemptionData.put("rewardId",   reward.getId());
                    redemptionData.put("rewardName", reward.name);
                    redemptionData.put("cost",       reward.pointsCost);
                    redemptionData.put("redeemedAt", Timestamp.now());

                    db.collection("rewards_redemptions")
                            .add(redemptionData)
                            .addOnSuccessListener(docRef ->
                                    db.collection("users")
                                            .document(uid)
                                            .update("totalPoints",
                                                    FieldValue.increment(-reward.pointsCost))
                                            .addOnSuccessListener(unused ->
                                                    Snackbar.make(
                                                            rootView,
                                                            reward.emoji + " Reward redeemed! Enjoy your "
                                                                    + reward.name + ".",
                                                            Snackbar.LENGTH_LONG
                                                    ).show()
                                            )
                                            .addOnFailureListener(e ->
                                                    rollbackRedemption(
                                                            row, tvCost, rootView,
                                                            reward, pointsBeforeRedemption
                                                    )
                                            )
                            )
                            .addOnFailureListener(e ->
                                    rollbackRedemption(
                                            row, tvCost, rootView,
                                            reward, pointsBeforeRedemption
                                    )
                            );
                })
                .show();
    }

    /**
     * Rolls back the optimistic UI update if either Firebase write fails.
     * Restores points balance, row appearance, points badge, and cache.
     *
     * @param row                    the reward row to restore
     * @param tvCost                 the cost label to restore
     * @param rootView               root view for Snackbar
     * @param reward                 the reward that failed to redeem
     * @param pointsBeforeRedemption points value before the optimistic deduction
     */
    private void rollbackRedemption(LinearLayout row,
                                    TextView tvCost,
                                    View rootView,
                                    Reward reward,
                                    long pointsBeforeRedemption) {
        if (!isAdded()) return;

        currentTotalPoints = pointsBeforeRedemption;

        row.setAlpha(1f);
        row.setClickable(true);
        tvCost.setText(reward.pointsCost + " pts");
        tvCost.setTextColor(getResources().getColor(R.color.color_green_text, null));

        updatePointsBadgeInView(rootView, currentTotalPoints);

        prefs.edit()
                .putLong(KEY_TOTAL_POINTS, currentTotalPoints)
                .apply();

        Snackbar.make(
                rootView,
                "Redemption failed. Please try again.",
                Snackbar.LENGTH_LONG
        ).show();
    }

    /**
     * Finds and updates the points badge TextView in the current view.
     *
     * @param rootView  the fragment root view
     * @param newPoints the updated points value to display
     */
    private void updatePointsBadgeInView(View rootView, long newPoints) {
        if (rootView == null) return;
        TextView tvPointsBadge = rootView.findViewById(R.id.tv_points_badge);
        if (tvPointsBadge != null) {
            tvPointsBadge.setText("• " + newPoints + " pts");
        }
    }

    /**
     * Converts dp units to pixels using the current display density.
     *
     * @param dp value in density-independent pixels
     * @return equivalent value in pixels
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}