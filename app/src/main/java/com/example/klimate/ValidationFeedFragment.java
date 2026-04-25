/**
 * ValidationFeedFragment.java
 *
 * Screen for browsing pending verified activity submissions
 * that are awaiting community review and voting.
 *
 * Role in design: Part of the UI layer. Accessible from the
 * Rankings screen for the half checkpoint.
 *
 * Outstanding issues: actual remote proof image loading can be
 * added later if the project includes an image loading library.
 *
 * @author Karar
 * @author Haroon
 */

package com.example.klimate;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ValidationFeedFragment extends Fragment {

    private FirebaseFirestore db;
    private LinearLayout validationFeedContainer;
    private View emptyStateCard;

    /**
     * Inflates the validation feed screen, initializes Firebase,
     * loads pending verified activity submissions, and wires the
     * back button for returning to the previous screen.
     *
     * @param inflater the LayoutInflater used to inflate the fragment layout
     * @param container the parent view group for this fragment
     * @param savedInstanceState previously saved fragment state if available
     * @return the root view for the validation feed screen
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_validation_feed, container, false);

        db = FirebaseFirestore.getInstance();
        validationFeedContainer = view.findViewById(R.id.validation_feed_container);
        emptyStateCard = view.findViewById(R.id.card_empty_validation_state);

        TextView backButton = view.findViewById(R.id.btn_validation_back);
        backButton.setOnClickListener(v -> requireActivity()
                .getSupportFragmentManager()
                .popBackStack());

        loadPendingLogs(inflater);

        return view;
    }

    private void loadPendingLogs(@NonNull LayoutInflater inflater) {
        db.collection("activity_logs")
                .whereEqualTo("status", "pending_verification")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded() || getContext() == null) return;
                    validationFeedContainer.removeAllViews();

                    if (queryDocumentSnapshots.isEmpty()) {
                        emptyStateCard.setVisibility(View.VISIBLE);
                        return;
                    }

                    emptyStateCard.setVisibility(View.GONE);

                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        View cardView = inflater.inflate(
                                R.layout.item_validation_submission,
                                validationFeedContainer,
                                false
                        );

                        bindLogCard(cardView, document);

                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        params.bottomMargin = dpToPx(12);
                        cardView.setLayoutParams(params);

                        validationFeedContainer.addView(cardView);
                    }
                })
                .addOnFailureListener(e -> {
                    emptyStateCard.setVisibility(View.VISIBLE);
                    Toast.makeText(
                            requireContext(),
                            "Failed to load validation feed",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void bindLogCard(@NonNull View cardView, @NonNull DocumentSnapshot document) {

        TextView textInitials = cardView.findViewById(R.id.text_submitter_initials);
        TextView textName = cardView.findViewById(R.id.text_submitter_name);
        TextView textActivity = cardView.findViewById(R.id.text_activity_type);
        TextView textTimestamp = cardView.findViewById(R.id.text_timestamp);
        TextView textProofStatus = cardView.findViewById(R.id.text_proof_status);
        TextView textUpvoteCount = cardView.findViewById(R.id.text_upvote_count);
        TextView btnUpvote = cardView.findViewById(R.id.btn_upvote);
        TextView btnDownvote = cardView.findViewById(R.id.btn_downvote);

        FrameLayout proofImageContainer = cardView.findViewById(R.id.proof_image_container);
        TextView textProofOverlay = cardView.findViewById(R.id.text_proof_overlay);
        ImageView imageProof = cardView.findViewById(R.id.image_proof);

        String logId = document.getString("logId");
        if (TextUtils.isEmpty(logId)) {
            logId = document.getId();
        }

        String documentId = document.getId();
        String userId = document.getString("userId");
        String activityType = document.getString("activityType");
        String proofUrl = document.getString("proofUrl");
        Timestamp timestamp = document.getTimestamp("timestamp");

        textName.setText("Student");
        textInitials.setText("ST");
        textActivity.setText(activityType != null ? activityType : "Unknown activity");
        textTimestamp.setText(formatTimestamp(timestamp));
        textUpvoteCount.setText("Loading...");

        if (TextUtils.isEmpty(proofUrl)) {
            textProofStatus.setText("No proof uploaded");
            proofImageContainer.setVisibility(View.GONE);
        } else {
            textProofStatus.setText("Proof attached");
            proofImageContainer.setVisibility(View.VISIBLE);
            imageProof.setVisibility(View.VISIBLE);
            textProofOverlay.setVisibility(View.GONE);

            Glide.with(cardView.getContext())
                    .asBitmap()
                    .load(proofUrl)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                                    @Nullable Transition<? super Bitmap> transition) {

                            imageProof.setImageBitmap(resource);

                            int imgWidth = resource.getWidth();
                            int imgHeight = resource.getHeight();

                            float aspectRatio = (float) imgHeight / imgWidth;

                            // Get container width (more accurate than full screen)
                            int containerWidth = cardView.getWidth();
                            if (containerWidth == 0) {
                                containerWidth = cardView.getResources()
                                        .getDisplayMetrics().widthPixels;
                            }

                            float maxRatio = 5f / 4f;  // Instagram portrait cap
                            float minRatio = 1f;       // square baseline

                            ViewGroup.LayoutParams params = imageProof.getLayoutParams();

                            if (aspectRatio > maxRatio) {
                                // Too tall → crop to 4:5
                                params.height = (int) (containerWidth * maxRatio);
                            } else if (aspectRatio < minRatio) {
                                // Too wide → keep square-ish
                                params.height = (int) (containerWidth * minRatio);
                            } else {
                                // Normal → preserve ratio
                                params.height = (int) (containerWidth * aspectRatio);
                            }

                            imageProof.setLayoutParams(params);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // No-op
                        }
                    });
        }

        loadSubmitterName(userId, textName, textInitials);

        final String finalLogId = logId;
        final String finalDocumentId = documentId;

        refreshVoteState(finalLogId, finalDocumentId, textUpvoteCount, btnUpvote, btnDownvote);

        btnUpvote.setOnClickListener(v -> castVote(
                finalLogId, finalDocumentId, true,
                textUpvoteCount, btnUpvote, btnDownvote
        ));

        btnDownvote.setOnClickListener(v -> castVote(
                finalLogId, finalDocumentId, false,
                textUpvoteCount, btnUpvote, btnDownvote
        ));
    }

    private void refreshVoteState(@NonNull String logId,
                                  @NonNull String activityLogDocumentId,
                                  @NonNull TextView textUpvoteCount,
                                  @NonNull TextView btnUpvote,
                                  @NonNull TextView btnDownvote) {
        db.collection("votes")
                .whereEqualTo("logId", logId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int upvoteCount = 0;

                    for (QueryDocumentSnapshot voteDocument : querySnapshot) {
                        Boolean isUpvote = readVoteIsUpvote(voteDocument);
                        if (Boolean.TRUE.equals(isUpvote)) {
                            upvoteCount++;
                        }
                    }

                    textUpvoteCount.setText(formatUpvoteCount(upvoteCount));

                    db.collection("activity_logs")
                            .document(activityLogDocumentId)
                            .update("voteCount", upvoteCount);

                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser == null) {
                        btnUpvote.setEnabled(false);
                        btnDownvote.setEnabled(false);
                        return;
                    }

                    String currentUserId = currentUser.getUid();
                    boolean alreadyVoted = false;

                    for (QueryDocumentSnapshot voteDocument : querySnapshot) {
                        String voterId = voteDocument.getString("voterId");
                        if (currentUserId.equals(voterId)) {
                            alreadyVoted = true;
                            break;
                        }
                    }

                    btnUpvote.setEnabled(!alreadyVoted);
                    btnDownvote.setEnabled(!alreadyVoted);
                })
                .addOnFailureListener(e -> {
                    textUpvoteCount.setText("0 upvotes");
                    btnUpvote.setEnabled(true);
                    btnDownvote.setEnabled(true);
                });
    }

    @Nullable
    private Boolean readVoteIsUpvote(@NonNull DocumentSnapshot voteDocument) {
        Boolean isUpvote = voteDocument.getBoolean("isUpvote");
        if (isUpvote != null) return isUpvote;
        return voteDocument.getBoolean("upvote");
    }

    private void loadSubmitterName(@Nullable String userId,
                                   @NonNull TextView textName,
                                   @NonNull TextView textInitials) {
        if (TextUtils.isEmpty(userId)) return;

        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(userDocument -> {
                    String displayName = userDocument.getString("displayName");
                    if (!TextUtils.isEmpty(displayName)) {
                        textName.setText(displayName);
                        textInitials.setText(makeInitials(displayName));
                    }
                });
    }

    private void castVote(@Nullable String logId,
                          @NonNull String activityLogDocumentId,
                          boolean isUpvote,
                          @NonNull TextView textUpvoteCount,
                          @NonNull TextView btnUpvote,
                          @NonNull TextView btnDownvote) {

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(logId)) {
            Toast.makeText(requireContext(), "Invalid log entry", Toast.LENGTH_SHORT).show();
            return;
        }

        String voterId = currentUser.getUid();

        db.collection("votes")
                .whereEqualTo("logId", logId)
                .whereEqualTo("voterId", voterId)
                .get()
                .addOnSuccessListener(existingVotes -> {
                    if (!existingVotes.isEmpty()) {
                        Toast.makeText(
                                requireContext(),
                                "You already voted on this submission",
                                Toast.LENGTH_SHORT
                        ).show();

                        refreshVoteState(
                                logId, activityLogDocumentId,
                                textUpvoteCount, btnUpvote, btnDownvote
                        );
                        return;
                    }

                    String voteId = db.collection("votes").document().getId();

                    Map<String, Object> voteData = new HashMap<>();
                    voteData.put("voteId", voteId);
                    voteData.put("logId", logId);
                    voteData.put("voterId", voterId);
                    voteData.put("isUpvote", isUpvote);
                    voteData.put("timestamp", Timestamp.now());

                    db.collection("votes")
                            .document(voteId)
                            .set(voteData)
                            .addOnSuccessListener(unused -> {

                                // Fetch the log to get the owner, then attach the vote listener
                                // which handles net bonus recalculation and rejection at -5
                                db.collection("activity_logs")
                                        .document(activityLogDocumentId)
                                        .get()
                                        .addOnSuccessListener(logDoc -> {
                                            if (!logDoc.exists()) return;

                                            String logOwnerId = logDoc.getString("userId");

                                            if (logOwnerId != null) {
                                                // Immediately deduct 1 point for a downvote
                                                // The vote listener handles bonus recalculation
                                                // but the base -1 per downvote is applied here
                                                if (!isUpvote) {
                                                    db.collection("users")
                                                            .document(logOwnerId)
                                                            .update("totalPoints",
                                                                    FieldValue.increment(-1));
                                                }

                                                // Attach listener to recalculate net bonus points
                                                // and handle rejection if net votes reach -5
                                                new PointsManager().attachVoteListener(
                                                        activityLogDocumentId,
                                                        logOwnerId
                                                );
                                            }
                                        });

                                refreshVoteState(
                                        logId, activityLogDocumentId,
                                        textUpvoteCount, btnUpvote, btnDownvote
                                );

                                Toast.makeText(
                                        requireContext(),
                                        isUpvote ? "Upvote submitted" : "Downvote submitted",
                                        Toast.LENGTH_SHORT
                                ).show();
                            })
                            .addOnFailureListener(e -> Toast.makeText(
                                    requireContext(),
                                    "Failed to submit vote",
                                    Toast.LENGTH_SHORT
                            ).show());
                })
                .addOnFailureListener(e -> Toast.makeText(
                        requireContext(),
                        "Failed to check existing vote",
                        Toast.LENGTH_SHORT
                ).show());
    }

    private String formatTimestamp(@Nullable Timestamp timestamp) {
        if (timestamp == null) return "Just now";
        SimpleDateFormat formatter = new SimpleDateFormat("dd MMM  h:mm a", Locale.getDefault());
        return formatter.format(timestamp.toDate());
    }

    private String formatUpvoteCount(int voteCount) {
        if (voteCount == 1) return "1 upvote";
        return voteCount + " upvotes";
    }

    private String makeInitials(@NonNull String name) {
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) return "ST";

        String[] parts = trimmedName.split("\\s+");

        if (parts.length == 1) {
            String first = parts[0];
            return first.substring(0, Math.min(2, first.length())).toUpperCase(Locale.getDefault());
        }

        String firstLetter = parts[0].substring(0, 1);
        String secondLetter = parts[1].substring(0, 1);
        return (firstLetter + secondLetter).toUpperCase(Locale.getDefault());
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}