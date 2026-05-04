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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ValidationFeedFragment extends Fragment {

    private static final int DOWNVOTE_FLAG_THRESHOLD = 5;

    private FirebaseFirestore db;
    private LinearLayout validationFeedContainer;
    private View emptyStateCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_validation_feed, container, false);

        db = FirebaseFirestore.getInstance();

        validationFeedContainer = view.findViewById(R.id.validation_feed_container);
        emptyStateCard = view.findViewById(R.id.card_empty_validation_state);

        loadPendingLogs(inflater);

        return view;
    }

    private void loadPendingLogs(@NonNull LayoutInflater inflater) {
        db.collection("activity_logs")
                .whereEqualTo("status", "pending_verification")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;

                    validationFeedContainer.removeAllViews();

                    if (querySnapshot.isEmpty()) {
                        emptyStateCard.setVisibility(View.VISIBLE);
                        return;
                    }

                    emptyStateCard.setVisibility(View.GONE);

                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
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
                    if (!isAdded()) return;

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

        View btnUpvote = cardView.findViewById(R.id.btn_upvote);
        View btnDownvote = cardView.findViewById(R.id.btn_downvote);
        View btnReportLog = cardView.findViewById(R.id.btn_report_log);

        FrameLayout proofImageContainer = cardView.findViewById(R.id.proof_image_container);
        TextView textProofOverlay = cardView.findViewById(R.id.text_proof_overlay);
        ImageView imageProof = cardView.findViewById(R.id.image_proof);

        String documentId = document.getId();
        cardView.setTag(documentId);

        String logId = document.getString("logId");
        if (TextUtils.isEmpty(logId)) {
            logId = documentId;
        }

        String userId = document.getString("userId");
        String activityType = document.getString("activityType");

        String proofUrl = document.getString("proofUrl");
        if (TextUtils.isEmpty(proofUrl)) {
            proofUrl = document.getString("proofImageUrl");
        }
        if (TextUtils.isEmpty(proofUrl)) {
            proofUrl = document.getString("imageUrl");
        }
        if (TextUtils.isEmpty(proofUrl)) {
            proofUrl = document.getString("photoUrl");
        }

        Timestamp timestamp = document.getTimestamp("timestamp");

        if (textName != null) {
            textName.setText("Student");
        }

        if (textInitials != null) {
            textInitials.setText("ST");
        }

        if (textActivity != null) {
            textActivity.setText(activityType != null ? activityType : "Unknown activity");
        }

        if (textTimestamp != null) {
            textTimestamp.setText(formatTimestamp(timestamp));
        }

        if (textUpvoteCount != null) {
            textUpvoteCount.setText("Loading votes...");
        }

        bindProofPreview(proofUrl, textProofStatus, proofImageContainer, textProofOverlay, imageProof);

        if (textName != null && textInitials != null) {
            loadSubmitterName(userId, textName, textInitials);
        }

        final String finalLogId = logId;
        final String finalDocumentId = documentId;

        if (textUpvoteCount != null && btnUpvote != null && btnDownvote != null) {
            refreshVoteState(
                    finalLogId,
                    finalDocumentId,
                    textUpvoteCount,
                    btnUpvote,
                    btnDownvote
            );

            btnUpvote.setOnClickListener(v ->
                    castVote(
                            finalLogId,
                            finalDocumentId,
                            true,
                            textUpvoteCount,
                            btnUpvote,
                            btnDownvote
                    )
            );

            btnDownvote.setOnClickListener(v ->
                    castVote(
                            finalLogId,
                            finalDocumentId,
                            false,
                            textUpvoteCount,
                            btnUpvote,
                            btnDownvote
                    )
            );
        }

        if (btnReportLog != null) {
            btnReportLog.setOnClickListener(v ->
                    reportLog(finalLogId, finalDocumentId)
            );
        }
    }

    private void bindProofPreview(@Nullable String proofUrl,
                                  @Nullable TextView textProofStatus,
                                  @Nullable FrameLayout proofImageContainer,
                                  @Nullable TextView textProofOverlay,
                                  @Nullable ImageView imageProof) {
        if (TextUtils.isEmpty(proofUrl)) {
            if (textProofStatus != null) {
                textProofStatus.setText("No proof uploaded");
            }

            if (proofImageContainer != null) {
                proofImageContainer.setVisibility(View.GONE);
            }

            if (imageProof != null) {
                imageProof.setImageDrawable(null);
                imageProof.setVisibility(View.GONE);
            }

            if (textProofOverlay != null) {
                textProofOverlay.setVisibility(View.GONE);
            }

            return;
        }

        if (textProofStatus != null) {
            textProofStatus.setText("Proof attached");
        }

        if (proofImageContainer != null) {
            proofImageContainer.setVisibility(View.VISIBLE);
        }

        if (textProofOverlay != null) {
            textProofOverlay.setVisibility(View.GONE);
        }

        if (imageProof != null) {
            imageProof.setVisibility(View.VISIBLE);
            imageProof.setScaleType(ImageView.ScaleType.CENTER_CROP);

            Glide.with(imageProof.getContext())
                    .load(proofUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imageProof);
        }
    }

    private void castVote(@NonNull String logId,
                          @NonNull String activityLogDocumentId,
                          boolean isUpvote,
                          @NonNull TextView textUpvoteCount,
                          @NonNull View btnUpvote,
                          @NonNull View btnDownvote) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String voterId = currentUser.getUid();
        String voteId = activityLogDocumentId + "_" + voterId;

        setVoteButtonsEnabled(btnUpvote, btnDownvote, false);

        db.collection("votes")
                .document(voteId)
                .get()
                .addOnSuccessListener(existingVote -> {
                    if (!isAdded()) return;

                    if (existingVote.exists()) {
                        Toast.makeText(
                                requireContext(),
                                "You already voted on this log",
                                Toast.LENGTH_SHORT
                        ).show();

                        refreshVoteState(
                                logId,
                                activityLogDocumentId,
                                textUpvoteCount,
                                btnUpvote,
                                btnDownvote
                        );
                        return;
                    }

                    Map<String, Object> voteData = new HashMap<>();
                    voteData.put("voteId", voteId);
                    voteData.put("logId", logId);
                    voteData.put("activityLogDocumentId", activityLogDocumentId);
                    voteData.put("voterId", voterId);
                    voteData.put("isUpvote", isUpvote);
                    voteData.put("timestamp", Timestamp.now());

                    db.collection("votes")
                            .document(voteId)
                            .set(voteData)
                            .addOnSuccessListener(unused -> {
                                if (!isAdded()) return;

                                Toast.makeText(
                                        requireContext(),
                                        isUpvote ? "Upvote submitted" : "Downvote submitted",
                                        Toast.LENGTH_SHORT
                                ).show();

                                refreshVoteState(
                                        logId,
                                        activityLogDocumentId,
                                        textUpvoteCount,
                                        btnUpvote,
                                        btnDownvote
                                );
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;

                                Toast.makeText(
                                        requireContext(),
                                        "Could not submit vote",
                                        Toast.LENGTH_SHORT
                                ).show();

                                setVoteButtonsEnabled(btnUpvote, btnDownvote, true);
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;

                    Toast.makeText(
                            requireContext(),
                            "Could not check vote status",
                            Toast.LENGTH_SHORT
                    ).show();

                    setVoteButtonsEnabled(btnUpvote, btnDownvote, true);
                });
    }

    private void refreshVoteState(@NonNull String logId,
                                  @NonNull String activityLogDocumentId,
                                  @NonNull TextView textUpvoteCount,
                                  @NonNull View btnUpvote,
                                  @NonNull View btnDownvote) {
        db.collection("votes")
                .whereEqualTo("logId", logId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;

                    int upvoteCount = 0;
                    int downvoteCount = 0;
                    Boolean currentUserVote = null;

                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    String currentUserId = currentUser != null ? currentUser.getUid() : null;

                    for (QueryDocumentSnapshot voteDocument : querySnapshot) {
                        Boolean isUpvote = readVoteIsUpvote(voteDocument);

                        if (Boolean.TRUE.equals(isUpvote)) {
                            upvoteCount++;
                        } else if (Boolean.FALSE.equals(isUpvote)) {
                            downvoteCount++;
                        }

                        String voterId = voteDocument.getString("voterId");
                        if (currentUserId != null && currentUserId.equals(voterId)) {
                            currentUserVote = isUpvote;
                        }
                    }

                    textUpvoteCount.setText(formatVoteCount(upvoteCount, downvoteCount));

                    final boolean shouldFlagLog = downvoteCount >= DOWNVOTE_FLAG_THRESHOLD;

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("upvoteCount", upvoteCount);
                    updates.put("downvoteCount", downvoteCount);
                    updates.put("voteCount", upvoteCount - downvoteCount);

                    if (shouldFlagLog) {
                        updates.put("status", "flagged");
                        updates.put("flagged", true);
                        updates.put("flagReason", "5_downvotes");
                        updates.put("flaggedAt", Timestamp.now());
                    }

                    db.collection("activity_logs")
                            .document(activityLogDocumentId)
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                if (!isAdded()) return;

                                if (shouldFlagLog) {
                                    Toast.makeText(
                                            requireContext(),
                                            "Log flagged for staff review",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    removeCardFromFeed(activityLogDocumentId);
                                }
                            });

                    if (currentUser == null || currentUserVote != null) {
                        setVoteButtonsEnabled(btnUpvote, btnDownvote, false);
                    } else {
                        setVoteButtonsEnabled(btnUpvote, btnDownvote, true);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;

                    textUpvoteCount.setText("0 upvotes · 0 downvotes");
                    setVoteButtonsEnabled(btnUpvote, btnDownvote, true);
                });
    }

    private void reportLog(@NonNull String logId,
                           @NonNull String activityLogDocumentId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String reporterId = currentUser.getUid();
        String reportId = activityLogDocumentId + "_" + reporterId;

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("reportId", reportId);
        reportData.put("logId", logId);
        reportData.put("activityLogDocumentId", activityLogDocumentId);
        reportData.put("reporterId", reporterId);
        reportData.put("status", "open");
        reportData.put("reason", "manual_report");
        reportData.put("timestamp", Timestamp.now());

        db.collection("reports")
                .document(reportId)
                .get()
                .addOnSuccessListener(existingReport -> {
                    if (!isAdded()) return;

                    if (existingReport.exists()) {
                        Toast.makeText(
                                requireContext(),
                                "You already reported this log",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    db.collection("reports")
                            .document(reportId)
                            .set(reportData)
                            .addOnSuccessListener(unused -> flagLogForStaffReview(activityLogDocumentId))
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;

                                Toast.makeText(
                                        requireContext(),
                                        "Could not submit report",
                                        Toast.LENGTH_SHORT
                                ).show();
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;

                    Toast.makeText(
                            requireContext(),
                            "Could not check report status",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void flagLogForStaffReview(@NonNull String activityLogDocumentId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "flagged");
        updates.put("flagged", true);
        updates.put("flagReason", "manual_report");
        updates.put("flaggedAt", Timestamp.now());
        updates.put("reportCount", FieldValue.increment(1));

        db.collection("activity_logs")
                .document(activityLogDocumentId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) return;

                    Toast.makeText(
                            requireContext(),
                            "Log reported for staff review",
                            Toast.LENGTH_SHORT
                    ).show();

                    removeCardFromFeed(activityLogDocumentId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;

                    Toast.makeText(
                            requireContext(),
                            "Report saved, but log was not flagged",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    @Nullable
    private Boolean readVoteIsUpvote(@NonNull DocumentSnapshot voteDocument) {
        Boolean isUpvote = voteDocument.getBoolean("isUpvote");

        if (isUpvote != null) {
            return isUpvote;
        }

        return voteDocument.getBoolean("upvote");
    }

    private void removeCardFromFeed(@NonNull String activityLogDocumentId) {
        if (validationFeedContainer == null) return;

        for (int i = 0; i < validationFeedContainer.getChildCount(); i++) {
            View child = validationFeedContainer.getChildAt(i);
            Object tag = child.getTag();

            if (activityLogDocumentId.equals(tag)) {
                validationFeedContainer.removeViewAt(i);
                break;
            }
        }

        if (validationFeedContainer.getChildCount() == 0 && emptyStateCard != null) {
            emptyStateCard.setVisibility(View.VISIBLE);
        }
    }

    private void loadSubmitterName(@Nullable String userId,
                                   @NonNull TextView textName,
                                   @NonNull TextView textInitials) {
        if (TextUtils.isEmpty(userId)) return;

        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(userDocument -> {
                    if (!isAdded()) return;

                    String displayName = userDocument.getString("displayName");

                    if (!TextUtils.isEmpty(displayName)) {
                        textName.setText(displayName);
                        textInitials.setText(makeInitials(displayName));
                    }
                });
    }

    private void setVoteButtonsEnabled(@NonNull View btnUpvote,
                                       @NonNull View btnDownvote,
                                       boolean enabled) {
        btnUpvote.setEnabled(enabled);
        btnDownvote.setEnabled(enabled);

        btnUpvote.setAlpha(enabled ? 1.0f : 0.5f);
        btnDownvote.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private String formatTimestamp(@Nullable Timestamp timestamp) {
        if (timestamp == null) {
            return "Just now";
        }

        SimpleDateFormat formatter = new SimpleDateFormat(
                "dd MMM · h:mm a",
                Locale.getDefault()
        );

        return formatter.format(timestamp.toDate());
    }

    private String formatVoteCount(int upvoteCount, int downvoteCount) {
        String upvoteText = upvoteCount == 1
                ? "1 upvote"
                : upvoteCount + " upvotes";

        String downvoteText = downvoteCount == 1
                ? "1 downvote"
                : downvoteCount + " downvotes";

        return upvoteText + " · " + downvoteText;
    }

    private String makeInitials(@NonNull String name) {
        String trimmedName = name.trim();

        if (trimmedName.isEmpty()) {
            return "ST";
        }

        String[] parts = trimmedName.split("\\s+");

        if (parts.length == 1) {
            String first = parts[0];
            return first.substring(0, Math.min(2, first.length()))
                    .toUpperCase(Locale.getDefault());
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