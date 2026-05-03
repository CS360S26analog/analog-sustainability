/**
 * StaffLogsFragment.java
 *
 * Staff-only screen showing ALL activity logs across all students.
 * Staff can edit the description/notes on any log or delete it entirely.
 * Staff cannot create new logs — this is admin management only.
 *
 * Role in design: Part of the View layer (staff section).
 * Uses Firestore query on activity_logs collection (no userId filter).
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class StaffLogsFragment extends Fragment {

    private FirebaseFirestore db;
    private LinearLayout logsList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Build layout programmatically
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(requireContext().getColor(R.color.color_cream_bg));

        // Header
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundResource(R.drawable.bg_header_green);
        int hp = dpToPx(22);
        header.setPadding(hp, dpToPx(24), hp, dpToPx(20));

        TextView tvSub = new TextView(requireContext());
        tvSub.setText("Staff Panel");
        tvSub.setTextColor(0xCCFFFFFF);
        tvSub.setTextSize(13f);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Activity Logs");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(24f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText("Edit or delete any student activity log");
        tvDesc.setTextColor(0xCCFFFFFF);
        tvDesc.setTextSize(13f);
        tvDesc.setPadding(0, dpToPx(2), 0, 0);

        header.addView(tvSub);
        header.addView(tvTitle);
        header.addView(tvDesc);
        root.addView(header);

        // Scrollable list
        NestedScrollView scroll = new NestedScrollView(requireContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        logsList = new LinearLayout(requireContext());
        logsList.setOrientation(LinearLayout.VERTICAL);
        int lp = dpToPx(16);
        logsList.setPadding(lp, lp, lp, lp);

        TextView loading = new TextView(requireContext());
        loading.setText("Loading all activity logs…");
        loading.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        loading.setTextSize(14f);
        logsList.addView(loading);

        scroll.addView(logsList);
        root.addView(scroll);

        db = FirebaseFirestore.getInstance();
        loadAllLogs();

        return root;
    }

    /**
     * Loads all activity logs from Firestore (no userId filter).
     * Ordered by timestamp descending so most recent appear first.
     */
    private void loadAllLogs() {
        db.collection("activity_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshots -> {
                    logsList.removeAllViews();
                    if (snapshots.isEmpty()) {
                        TextView empty = new TextView(requireContext());
                        empty.setText("No activity logs found.");
                        empty.setTextColor(requireContext().getColor(R.color.color_text_secondary));
                        empty.setTextSize(14f);
                        logsList.addView(empty);
                        return;
                    }
                    for (QueryDocumentSnapshot doc : snapshots) {
                        // For each log, fetch the owner's display name
                        String userId = doc.getString("userId");
                        if (userId != null) {
                            db.collection("users").document(userId).get()
                                    .addOnSuccessListener(userDoc -> {
                                        String name = userDoc.getString("displayName");
                                        String firstName = (name != null && !name.trim().isEmpty())
                                                ? name.trim().split("\\s+")[0] : "Student";
                                        if (isAdded()) addLogCard(doc, firstName);
                                    })
                                    .addOnFailureListener(e -> {
                                        if (isAdded()) addLogCard(doc, "Student");
                                    });
                        } else {
                            addLogCard(doc, "Student");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    logsList.removeAllViews();
                    TextView err = new TextView(requireContext());
                    err.setText("Could not load logs.");
                    err.setTextColor(requireContext().getColor(R.color.color_text_secondary));
                    logsList.addView(err);
                });
    }

    /**
     * Builds and adds a log card with Edit and Delete buttons.
     *
     * @param doc       the Firestore document for this activity log
     * @param ownerName resolved first name of the log owner
     */
    private void addLogCard(QueryDocumentSnapshot doc, String ownerName) {
        String docId       = doc.getId();
        String activityType = doc.getString("activityType");
        String status      = doc.getString("status");
        Long points        = doc.getLong("points");
        Timestamp ts       = doc.getTimestamp("timestamp");

        CardView card = new CardView(requireContext());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, 0, dpToPx(10));
        card.setLayoutParams(clp);
        card.setRadius(dpToPx(12));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(0xFFFFFFFF);

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        // Activity + owner
        TextView tvActivity = new TextView(requireContext());
        tvActivity.setText(getEmoji(activityType) + "  " + (activityType != null ? activityType : "Activity"));
        tvActivity.setTextSize(15f);
        tvActivity.setTypeface(null, android.graphics.Typeface.BOLD);
        tvActivity.setTextColor(requireContext().getColor(R.color.color_text_primary));

        // Meta: owner, date, points, status
        String dateStr = ts != null
                ? new SimpleDateFormat("dd MMM yyyy  h:mm a", Locale.getDefault()).format(ts.toDate())
                : "";
        String statusLabel = status != null ? " · " + status : "";

        TextView tvMeta = new TextView(requireContext());
        tvMeta.setText(ownerName + "  ·  " + dateStr + "  ·  +" + (points != null ? points : 0) + " pts" + statusLabel);
        tvMeta.setTextSize(12f);
        tvMeta.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dpToPx(3);
        metaLp.bottomMargin = dpToPx(8);
        tvMeta.setLayoutParams(metaLp);

        // Action buttons row
        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView btnEdit = buildActionBtn("Edit", R.color.color_green_header);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        editLp.setMarginEnd(dpToPx(8));
        btnEdit.setLayoutParams(editLp);

        TextView btnDelete = buildActionBtn("Delete", android.R.color.holo_red_dark);

        btnEdit.setOnClickListener(v -> showEditDialog(docId, activityType, status));
        btnDelete.setOnClickListener(v -> showDeleteDialog(docId, activityType, card));

        btnRow.addView(btnEdit);
        btnRow.addView(btnDelete);

        inner.addView(tvActivity);
        inner.addView(tvMeta);
        inner.addView(btnRow);
        card.addView(inner);
        logsList.addView(card);
    }

    /**
     * Shows a dialog letting staff edit the status of a log.
     * Options: pending_verification, verified, rejected, flagged.
     */
    private void showEditDialog(String docId, String currentActivity, String currentStatus) {
        String[] statusOptions = {"pending_verification", "verified", "rejected", "flagged", "quick"};
        int current = 0;
        for (int i = 0; i < statusOptions.length; i++) {
            if (statusOptions[i].equals(currentStatus)) { current = i; break; }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit: " + (currentActivity != null ? currentActivity : "Activity"))
                .setMessage("Change verification status:")
                .setSingleChoiceItems(statusOptions, current, null)
                .setPositiveButton("Save", (dialog, which) -> {
                    android.app.Dialog d = (android.app.Dialog) dialog;
                    int selected = ((android.app.AlertDialog) d)
                            .getListView().getCheckedItemPosition();
                    String newStatus = statusOptions[selected];

                    Map<String, Object> update = new HashMap<>();
                    update.put("status", newStatus);

                    db.collection("activity_logs").document(docId)
                            .update(update)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(getContext(),
                                        "Status updated to: " + newStatus,
                                        Toast.LENGTH_SHORT).show();
                                loadAllLogs();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(),
                                            "Update failed: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Shows a confirmation dialog before deleting a log permanently.
     *
     * @param docId        the Firestore document ID of the log
     * @param activityType the activity name for the confirmation message
     * @param card         the card view to remove from the list on success
     */
    private void showDeleteDialog(String docId, String activityType, CardView card) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Log?")
                .setMessage("Permanently delete this \"" + (activityType != null ? activityType : "activity") + "\" log? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) ->
                        db.collection("activity_logs").document(docId)
                                .delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(getContext(),
                                            "Log deleted", Toast.LENGTH_SHORT).show();
                                    logsList.removeView(card);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(),
                                                "Delete failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView buildActionBtn(String label, int colorRes) {
        TextView btn = new TextView(requireContext());
        btn.setText(label);
        btn.setTextSize(12f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setPadding(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5));
        btn.setBackground(requireContext().getDrawable(R.drawable.bg_header_green));
        btn.getBackground().setTint(requireContext().getColor(colorRes));
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
    }

    private String getEmoji(String activityType) {
        if (activityType == null) return "🌿";
        switch (activityType) {
            case "Cycling": return "🚲";
            case "Public Transit": return "🚌";
            case "Recycling": return "♻️";
            case "Plant-based meal": return "🥗";
            case "Reusable cup": return "☕";
            case "Composting": return "🍂";
            case "Walked": return "🚶";
            case "Energy saving": return "💡";
            default: return "🌿";
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
