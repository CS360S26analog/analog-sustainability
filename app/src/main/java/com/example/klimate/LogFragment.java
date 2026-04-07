/**
 * LogFragment.java
 *
 * Allows the user to select a sustainability activity and submit it
 * as either a quick log or a pending verification log.
 * For verified logs, the user can attach a proof photo which is uploaded
 * to Firebase Storage and saved to Firestore under the proofUrl field.
 *
 * Role in design: Part of the UI/Controller layer. Collects user input,
 * uploads proof media when needed, and writes activity log documents.
 *
 * Outstanding issues: this version supports gallery image picking only.
 * Camera capture can be added later if needed.
 *
 * @author Izza
 * @author Haroon
 */
package com.example.klimate;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class LogFragment extends Fragment {

    private LinearLayout selectedCard = null;
    private String selectedActivityName = null;
    private String selectedStatus = "quick";
    private Uri selectedProofUri = null;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private View proofSection;
    private ImageView imageProofPreview;
    private TextView textProofStatus;
    private TextView btnSelectProof;
    private TextView btnLogActivity;
    private View quickTab;
    private View verifiedTab;

    private final int[] cardIds = {
            R.id.card_cycling, R.id.card_transit, R.id.card_recycling,
            R.id.card_plantbased, R.id.card_reusable, R.id.card_composting,
            R.id.card_walked, R.id.card_energy
    };

    private final String[] activityNames = {
            "Cycling", "Public Transit", "Recycling", "Plant-based meal",
            "Reusable cup", "Composting", "Walked", "Energy saving"
    };

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }

                selectedProofUri = uri;

                if (imageProofPreview != null) {
                    imageProofPreview.setImageURI(uri);
                    imageProofPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }

                if (textProofStatus != null) {
                    textProofStatus.setText("Photo selected");
                }

                if (btnSelectProof != null) {
                    btnSelectProof.setText("Choose Another Photo");
                }
            });

    /**
     * Inflates the logging screen, initializes Firebase,
     * and wires activity selection, proof selection, and submission.
     *
     * @param inflater the LayoutInflater used to inflate the fragment layout
     * @param container the parent view group
     * @param savedInstanceState previously saved fragment state
     * @return the root view for the logging screen
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        ImageView btnHistory = view.findViewById(R.id.btn_history);
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> {
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new HistoryFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }

        proofSection = view.findViewById(R.id.card_verified_proof_section);
        imageProofPreview = view.findViewById(R.id.image_verified_proof_preview);
        textProofStatus = view.findViewById(R.id.text_verified_proof_status);
        btnSelectProof = view.findViewById(R.id.btn_select_verified_proof);
        btnLogActivity = view.findViewById(R.id.btn_log_activity);
        quickTab = view.findViewById(R.id.btn_quick_log);
        verifiedTab = view.findViewById(R.id.btn_verified_log);

        LinearLayout[] cards = new LinearLayout[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            final int index = i;
            cards[i] = view.findViewById(cardIds[i]);
            cards[i].setOnClickListener(v -> selectCard(cards, (LinearLayout) v, activityNames[index]));
        }

        if (quickTab != null && verifiedTab != null) {
            quickTab.setOnClickListener(v -> {
                selectedStatus = "quick";
                setActiveTab(quickTab, verifiedTab);
                updateProofSection();
            });

            verifiedTab.setOnClickListener(v -> {
                selectedStatus = "pending_verification";
                setActiveTab(verifiedTab, quickTab);
                updateProofSection();
            });

            setActiveTab(quickTab, verifiedTab);
        }

        updateProofSection();

        if (btnSelectProof != null) {
            btnSelectProof.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        }

        if (btnLogActivity != null) {
            btnLogActivity.setOnClickListener(v -> submitLog(cards));
        }

        return view;
    }

    /**
     * Marks one tab as visually active and the other as inactive,
     * animating the text colour on both so the switch feels smooth.
     *
     * @param active the tab that should appear selected
     * @param inactive the tab that should appear unselected
     */
    private void setActiveTab(View active, View inactive) {
        active.setBackgroundResource(R.drawable.bg_toggle_selected);
        inactive.setBackground(null);

        int white = requireContext().getColor(android.R.color.white);
        int secondary = requireContext().getColor(R.color.color_text_secondary);

        animateTextColor((TextView) active, secondary, white);
        animateTextColor((TextView) inactive, white, secondary);
    }

    /**
     * Smoothly animates a TextView text colour.
     *
     * @param tv the text view to animate
     * @param fromColor the starting color
     * @param toColor the ending color
     */
    private void animateTextColor(TextView tv, int fromColor, int toColor) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        animator.setDuration(200);
        animator.addUpdateListener(anim -> tv.setTextColor((int) anim.getAnimatedValue()));
        animator.start();
    }

    private void updateProofSection() {
        if (proofSection == null) {
            return;
        }

        boolean isVerified = "pending_verification".equals(selectedStatus);
        proofSection.setVisibility(isVerified ? View.VISIBLE : View.GONE);

        if (!isVerified) {
            return;
        }

        if (selectedProofUri == null) {
            imageProofPreview.setImageResource(android.R.drawable.ic_menu_gallery);
            imageProofPreview.setScaleType(ImageView.ScaleType.CENTER);
            textProofStatus.setText("Required for verified logs");
            btnSelectProof.setText("Choose Photo");
        } else {
            imageProofPreview.setImageURI(selectedProofUri);
            imageProofPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            textProofStatus.setText("Photo selected");
            btnSelectProof.setText("Choose Another Photo");
        }
    }

    private void selectCard(LinearLayout[] cards, LinearLayout card, String name) {
        deselectAll(cards);
        card.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card_selected));
        selectedCard = card;
        selectedActivityName = name;
    }

    private void deselectAll(LinearLayout[] cards) {
        for (LinearLayout c : cards) {
            c.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card));
        }
    }

    private void submitLog(LinearLayout[] cards) {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to submit a log", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCard == null || selectedActivityName == null) {
            Toast.makeText(getContext(), "Please select an activity first", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("pending_verification".equals(selectedStatus) && selectedProofUri == null) {
            Toast.makeText(getContext(), "Please attach a proof photo for verified logs", Toast.LENGTH_SHORT).show();
            return;
        }

        setLogButtonEnabled(false);

        int basePoints = getBasePoints(selectedActivityName);
        DocumentReference docRef = db.collection("activity_logs").document();

        if ("pending_verification".equals(selectedStatus)) {
            uploadProofAndSaveLog(docRef, currentUser.getUid(), basePoints, cards);
        } else {
            saveLogDocument(docRef, currentUser.getUid(), basePoints, null, cards);
        }
    }

    private void uploadProofAndSaveLog(@NonNull DocumentReference docRef,
                                       @NonNull String userId,
                                       int basePoints,
                                       @NonNull LinearLayout[] cards) {
        if (selectedProofUri == null) {
            setLogButtonEnabled(true);
            Toast.makeText(getContext(), "Please choose a proof photo first", Toast.LENGTH_SHORT).show();
            return;
        }

        StorageReference proofRef = storage.getReference()
                .child("proofs")
                .child(userId)
                .child(docRef.getId() + ".jpg");

        proofRef.putFile(selectedProofUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return proofRef.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri ->
                        saveLogDocument(docRef, userId, basePoints, downloadUri.toString(), cards)
                )
                .addOnFailureListener(e -> {
                    setLogButtonEnabled(true);
                    Toast.makeText(
                            getContext(),
                            "Failed to upload proof photo: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void saveLogDocument(@NonNull DocumentReference docRef,
                                 @NonNull String userId,
                                 int basePoints,
                                 @Nullable String proofUrl,
                                 @NonNull LinearLayout[] cards) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("logId", docRef.getId());
        logData.put("userId", userId);
        logData.put("activityType", selectedActivityName);
        logData.put("status", selectedStatus);
        logData.put("points", basePoints);
        logData.put("bonusPoints", 0);
        logData.put("proofUrl", proofUrl);
        logData.put("voteCount", 0);
        logData.put("timestamp", Timestamp.now());

        docRef.set(logData)
                .addOnSuccessListener(unused -> {
                    new PointsManager().awardBasePoints(userId, basePoints);

                    String message;
                    if ("pending_verification".equals(selectedStatus)) {
                        message = "Verified log submitted with proof";
                    } else {
                        message = "Quick activity logged successfully ✅";
                    }

                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    resetForm(cards);
                    setLogButtonEnabled(true);
                })
                .addOnFailureListener(e -> {
                    setLogButtonEnabled(true);
                    Toast.makeText(
                            getContext(),
                            "Failed to save log: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void resetForm(@NonNull LinearLayout[] cards) {
        deselectAll(cards);
        selectedCard = null;
        selectedActivityName = null;
        selectedProofUri = null;
        selectedStatus = "quick";

        if (quickTab != null && verifiedTab != null) {
            setActiveTab(quickTab, verifiedTab);
        }

        updateProofSection();
    }

    private void setLogButtonEnabled(boolean enabled) {
        if (btnLogActivity == null) {
            return;
        }

        btnLogActivity.setEnabled(enabled);
        btnLogActivity.setAlpha(enabled ? 1f : 0.6f);
    }

    private int getBasePoints(String activityType) {
        switch (activityType) {
            case "Cycling":
                return 30;
            case "Public Transit":
                return 20;
            case "Recycling":
                return 15;
            case "Plant-based meal":
                return 25;
            case "Reusable cup":
                return 10;
            case "Composting":
                return 20;
            case "Walked":
                return 20;
            case "Energy saving":
                return 10;
            default:
                return 0;
        }
    }
}