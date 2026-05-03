/**
 * StaffTipsFragment.java
 *
 * Staff-only screen for publishing and managing sustainability tips.
 * Tips written here appear in the student feed under the Tips filter tab.
 * Access is gated by users/{uid}/role == "staff" check.
 *
 * Role in design: Part of the View layer (MVVM). Writes to and reads from
 * the Firestore "tips" collection. Uses Observer pattern via Firestore
 * snapshot listener for the published tips list.
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
import com.google.firebase.firestore.Query;
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
     * Loads all tips authored by the current staff user and renders them
     * as cards in the tips list.
     */
    private void loadPublishedTips() {
        if (currentUser == null || llTipsList == null) return;

        llTipsList.removeAllViews();

        db.collection("tips")
                .whereEqualTo("authorUid", currentUser.getUid())
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    if (!isAdded() || getContext() == null) return;

                    llTipsList.removeAllViews();

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

                    llTipsList.removeAllViews();

                    TextView err = new TextView(requireContext());
                    err.setText("Could not load tips: " + e.getMessage());
                    err.setTextSize(13f);
                    err.setTextColor(0xFF888888);
                    llTipsList.addView(err);
                });
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
}
