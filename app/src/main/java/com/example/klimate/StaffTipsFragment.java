/**
 * StaffTipsFragment.java
 *
 * Staff-only screen for publishing and managing sustainability tips.
 * Tips written here appear in the student feed under the Tips filter tab.
 * Access is gated by users/{uid}/role == "staff" check.
 *
 * Role in design: Part of the View layer (MVVM). Writes to and reads from
 * the Firestore "tips" collection. Also reviews pending EcoMap tip
 * suggestions from the "eco_map_notes" collection.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StaffTipsFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private EditText etTitle, etBody;
    private Spinner spinnerCategory;
    private LinearLayout llTipsList;

    // The tip being edited — null when creating a new tip
    private String editingTipId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_staff_tips, container, false);

        db = FirebaseFirestore.getInstance();
        forceGreenHeaderColor();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        etTitle = view.findViewById(R.id.et_tip_title);
        etBody = view.findViewById(R.id.et_tip_body);
        spinnerCategory = view.findViewById(R.id.spinner_tip_category);
        llTipsList = view.findViewById(R.id.ll_tips_list);
        TextView btnPost = view.findViewById(R.id.btn_post_tip);

        // Set up category spinner
        List<String> categories = Arrays.asList(
                "Recycling", "Transport", "Food", "Energy", "Other"
        );
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                categories
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        // Check staff role before showing anything
        checkStaffRoleAndLoad(view, btnPost);

        btnPost.setOnClickListener(v -> postOrUpdateTip());

        return view;
    }

    private void forceGreenHeaderColor() {
        if (getActivity() == null) return;

        Window window = getActivity().getWindow();
        window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.color_green_header));
    }

    /**
     * Verifies that the current user has role == "staff" in Firestore.
     * Shows an access-denied message if not. Loads tips if yes.
     */
    private void checkStaffRoleAndLoad(View view, TextView btnPost) {
        if (currentUser == null) {
            showAccessDenied(view);
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if (!"staff".equals(role)) {
                        showAccessDenied(view);
                    } else {
                        loadPublishedTips();
                    }
                })
                .addOnFailureListener(e -> showAccessDenied(view));
    }

    /**
     * Hides the form and shows an access-denied message.
     */
    private void showAccessDenied(View view) {
        View formCard = view.findViewById(R.id.et_tip_title);
        if (formCard != null) formCard.setVisibility(View.GONE);

        if (llTipsList != null) {
            llTipsList.removeAllViews();
            TextView tv = new TextView(requireContext());
            tv.setText("⛔  Staff access only.\nYour account does not have staff permissions.");
            tv.setTextSize(15f);
            tv.setPadding(0, 32, 0, 0);
            llTipsList.addView(tv);
        }
    }

    /**
     * Posts a new tip or updates an existing one depending on editingTipId.
     * Validates that title and body are not empty before writing to Firestore.
     */
    private void postOrUpdateTip() {
        String title = etTitle.getText().toString().trim();
        String body = etBody.getText().toString().trim();
        String category = spinnerCategory.getSelectedItem().toString();

        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }
        if (body.isEmpty()) {
            etBody.setError("Body text is required");
            return;
        }

        Map<String, Object> tipData = new HashMap<>();
        tipData.put("title", title);
        tipData.put("body", body);
        tipData.put("category", category);
        tipData.put("authorUid", currentUser.getUid());
        tipData.put("timestamp", Timestamp.now());
        tipData.put("views", 0);

        if (editingTipId != null) {
            // Update existing tip
            db.collection("tips")
                    .document(editingTipId)
                    .update(tipData)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(getContext(), "Tip updated ✅", Toast.LENGTH_SHORT).show();
                        clearForm();
                        loadPublishedTips();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        } else {
            // Create new tip
            db.collection("tips")
                    .add(tipData)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(getContext(), "Tip published 🌿", Toast.LENGTH_SHORT).show();
                        clearForm();
                        loadPublishedTips();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        }
    }

    /**
     * Loads pending EcoMap suggestions first, then loads all tips authored
     * by the current staff user and renders them as cards in the tips list.
     */
    private void loadPublishedTips() {
        if (currentUser == null || llTipsList == null) return;

        llTipsList.removeAllViews();
        loadPendingEcoMapSuggestions();
    }

    /**
     * Loads student-submitted EcoMap suggestions that are waiting for staff review.
     */
    private void loadPendingEcoMapSuggestions() {
        db.collection("eco_map_notes")
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    if (!isAdded() || getContext() == null) return;

                    addSectionHeading("Pending EcoMap Suggestions");

                    if (querySnapshots.isEmpty()) {
                        addSmallMessage("No EcoMap suggestions pending review.");
                    } else {
                        java.util.List<QueryDocumentSnapshot> notes = new java.util.ArrayList<>();

                        for (QueryDocumentSnapshot doc : querySnapshots) {
                            notes.add(doc);
                        }

                        notes.sort((a, b) -> {
                            Timestamp ta = a.getTimestamp("submittedAt");
                            Timestamp tb = b.getTimestamp("submittedAt");

                            if (ta == null && tb == null) return 0;
                            if (ta == null) return 1;
                            if (tb == null) return -1;

                            return tb.compareTo(ta);
                        });

                        for (QueryDocumentSnapshot doc : notes) {
                            addEcoMapSuggestionCard(doc);
                        }
                    }

                    loadOwnTipsBelowSuggestions();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;

                    addSectionHeading("Pending EcoMap Suggestions");
                    addSmallMessage("Could not load EcoMap suggestions: " + e.getMessage());

                    loadOwnTipsBelowSuggestions();
                });
    }

    /**
     * Loads the staff user's own published tips below the review queue.
     */
    private void loadOwnTipsBelowSuggestions() {
        db.collection("tips")
                .whereEqualTo("authorUid", currentUser.getUid())
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    if (!isAdded() || getContext() == null) return;

                    addSectionHeading("Your Published Tips");

                    if (querySnapshots.isEmpty()) {
                        TextView empty = new TextView(requireContext());
                        empty.setText("No tips published yet. Write your first one above!");
                        empty.setTextSize(13f);
                        empty.setTextColor(0xFF888888);
                        llTipsList.addView(empty);
                        return;
                    }

                    java.util.List<QueryDocumentSnapshot> tips = new java.util.ArrayList<>();

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        tips.add(doc);
                    }

                    tips.sort((a, b) -> {
                        Timestamp ta = a.getTimestamp("timestamp");
                        Timestamp tb = b.getTimestamp("timestamp");

                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;

                        return tb.compareTo(ta);
                    });

                    for (QueryDocumentSnapshot doc : tips) {
                        addTipCard(doc);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;

                    addSectionHeading("Your Published Tips");

                    TextView err = new TextView(requireContext());
                    err.setText("Could not load tips: " + e.getMessage());
                    err.setTextSize(13f);
                    err.setTextColor(0xFF888888);
                    llTipsList.addView(err);
                });
    }

    /**
     * Renders one pending EcoMap suggestion card with approve/reject controls.
     *
     * @param doc the Firestore document for this EcoMap suggestion
     */
    private void addEcoMapSuggestionCard(QueryDocumentSnapshot doc) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.bg_card_outline);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);

        String noteId = doc.getId();
        String spotTitle = doc.getString("spotTitle");
        String activityType = doc.getString("activityType");
        String noteText = doc.getString("noteText");
        String submittedBy = doc.getString("submittedBy");

        TextView title = new TextView(requireContext());
        title.setText("📍 " + safeText(spotTitle, "EcoMap Spot"));
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));
        card.addView(title);

        TextView meta = new TextView(requireContext());
        meta.setText(safeText(activityType, "Sustainability") + "  ·  Student suggestion");
        meta.setTextSize(13f);
        meta.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green_header));
        meta.setPadding(0, dp(4), 0, dp(8));
        card.addView(meta);

        TextView body = new TextView(requireContext());
        body.setText(safeText(noteText, ""));
        body.setTextSize(14f);
        body.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_secondary));
        body.setPadding(0, 0, 0, dp(12));
        card.addView(body);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);

        TextView btnApprove = createReviewButton("Approve");
        TextView btnReject = createReviewButton("Reject");

        btnApprove.setOnClickListener(v ->
                approveEcoMapSuggestion(noteId, spotTitle, activityType, noteText, submittedBy)
        );

        btnReject.setOnClickListener(v ->
                rejectEcoMapSuggestion(noteId)
        );

        buttonRow.addView(btnApprove);
        buttonRow.addView(btnReject);
        card.addView(buttonRow);

        llTipsList.addView(card);
    }

    /**
     * Approves an EcoMap suggestion by publishing it to tips and marking
     * the original eco_map_notes document as approved.
     */
    private void approveEcoMapSuggestion(String noteId,
                                         String spotTitle,
                                         String activityType,
                                         String noteText,
                                         String submittedBy) {
        if (currentUser == null) return;

        Map<String, Object> tipData = new HashMap<>();
        tipData.put("title", safeText(spotTitle, "EcoMap") + " Tip");
        tipData.put("body", safeText(noteText, ""));
        tipData.put("category", safeText(activityType, "Other"));
        tipData.put("authorUid", currentUser.getUid());
        tipData.put("timestamp", Timestamp.now());
        tipData.put("views", 0);
        tipData.put("source", "eco_map_note");
        tipData.put("originalNoteId", noteId);
        tipData.put("submittedBy", submittedBy);

        db.collection("tips")
                .add(tipData)
                .addOnSuccessListener(ref ->
                        db.collection("eco_map_notes")
                                .document(noteId)
                                .update(
                                        "status", "approved",
                                        "reviewedBy", currentUser.getUid(),
                                        "reviewedAt", Timestamp.now(),
                                        "publishedTipId", ref.getId()
                                )
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(getContext(),
                                            "Suggestion approved and published ✅",
                                            Toast.LENGTH_SHORT).show();
                                    loadPublishedTips();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(),
                                                "Published, but review status was not updated.",
                                                Toast.LENGTH_LONG).show()
                                )
                )
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Could not approve suggestion: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Rejects an EcoMap suggestion after confirmation.
     */
    private void rejectEcoMapSuggestion(String noteId) {
        if (currentUser == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Reject Suggestion")
                .setMessage("Are you sure you want to reject this EcoMap suggestion?")
                .setPositiveButton("Reject", (dialog, which) ->
                        db.collection("eco_map_notes")
                                .document(noteId)
                                .update(
                                        "status", "rejected",
                                        "reviewedBy", currentUser.getUid(),
                                        "reviewedAt", Timestamp.now()
                                )
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(getContext(),
                                            "Suggestion rejected.",
                                            Toast.LENGTH_SHORT).show();
                                    loadPublishedTips();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(),
                                                "Could not reject suggestion: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show()
                                )
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Inflates a tip card and binds the title, body, category, date,
     * and Edit / Delete button actions.
     *
     * @param doc the Firestore document for this tip
     */
    private void addTipCard(QueryDocumentSnapshot doc) {
        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_tip_card, llTipsList, false);

        String tipId    = doc.getId();
        String title    = doc.getString("title");
        String body     = doc.getString("body");
        String category = doc.getString("category");
        Timestamp ts    = doc.getTimestamp("timestamp");

        TextView tvTitle = card.findViewById(R.id.tv_tip_title);
        TextView tvMeta  = card.findViewById(R.id.tv_tip_meta);
        TextView tvBody  = card.findViewById(R.id.tv_tip_body);
        TextView btnEdit = card.findViewById(R.id.btn_edit_tip);
        TextView btnDel  = card.findViewById(R.id.btn_delete_tip);

        tvTitle.setText(title != null ? title : "");
        tvBody.setText(body != null ? body : "");

        String dateStr = "";
        if (ts != null) {
            dateStr = new SimpleDateFormat("dd MMM", Locale.getDefault())
                    .format(ts.toDate());
        }
        tvMeta.setText((category != null ? category : "") + "  ·  " + dateStr);

        // Edit: pre-fill form with this tip's data
        btnEdit.setOnClickListener(v -> {
            etTitle.setText(title);
            etBody.setText(body);

            // Select correct category in spinner
            if (spinnerCategory.getAdapter() != null) {
                for (int i = 0; i < spinnerCategory.getAdapter().getCount(); i++) {
                    if (spinnerCategory.getAdapter().getItem(i).toString().equals(category)) {
                        spinnerCategory.setSelection(i);
                        break;
                    }
                }
            }

            editingTipId = tipId;
            TextView btnPost = requireView().findViewById(R.id.btn_post_tip);
            btnPost.setText("Update Tip");

            // Scroll to top so user sees the pre-filled form
            View scrollView = requireView();
            if (scrollView instanceof androidx.core.widget.NestedScrollView) {
                ((androidx.core.widget.NestedScrollView) scrollView).smoothScrollTo(0, 0);
            }
        });

        // Delete: confirm then remove from Firestore
        btnDel.setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Tip")
                        .setMessage("Are you sure you want to delete \"" + title + "\"?")
                        .setPositiveButton("Delete", (dialog, which) ->
                                db.collection("tips").document(tipId)
                                        .delete()
                                        .addOnSuccessListener(unused -> {
                                            Toast.makeText(getContext(),
                                                    "Tip deleted", Toast.LENGTH_SHORT).show();
                                            loadPublishedTips();
                                        })
                        )
                        .setNegativeButton("Cancel", null)
                        .show()
        );

        llTipsList.addView(card);
    }

    /**
     * Clears the form fields and resets the editing state.
     */
    private void clearForm() {
        etTitle.setText("");
        etBody.setText("");
        spinnerCategory.setSelection(0);
        editingTipId = null;
        TextView btnPost = requireView().findViewById(R.id.btn_post_tip);
        if (btnPost != null) btnPost.setText("Post Tip");
    }

    private void addSectionHeading(String text) {
        TextView heading = new TextView(requireContext());
        heading.setText(text);
        heading.setTextSize(17f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_primary));
        heading.setPadding(0, dp(18), 0, dp(10));
        llTipsList.addView(heading);
    }

    private void addSmallMessage(String text) {
        TextView message = new TextView(requireContext());
        message.setText(text);
        message.setTextSize(13f);
        message.setTextColor(0xFF888888);
        message.setPadding(0, 0, 0, dp(12));
        llTipsList.addView(message);
    }

    private TextView createReviewButton(String text) {
        TextView button = new TextView(requireContext());
        button.setText(text);
        button.setTextSize(13f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_green_header));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setClickable(true);
        button.setFocusable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);

        return button;
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}