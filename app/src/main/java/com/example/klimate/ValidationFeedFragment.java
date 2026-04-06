package com.example.klimate;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.klimate.model.Vote;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Locale;

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
 * @author Karar
 */
public class ValidationFeedFragment extends Fragment {

    private FirebaseFirestore db;
    private LinearLayout validationFeedContainer;
    private View emptyStateCard;

    /**
     * Inflates the validation feed layout and loads pending submissions.
     *
     * @param inflater the LayoutInflater used to inflate views
     * @param container the parent view group
     * @param savedInstanceState previously saved fragment state
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
        TextView btnUpvote = cardView.findViewById(R.id.btn_upvote);
        TextView btnDownvote = cardView.findViewById(R.id.btn_downvote);

        String logId = document.getString("logId");
        if (TextUtils.isEmpty(logId)) {
            logId = document.getId();
        }

        String userId = document.getString("userId");
        String activityType = document.getString("activityType");
        String proofUrl = document.getString("proofUrl");
        Timestamp timestamp = document.getTimestamp("timestamp");

        textName.setText("Student");
        textInitials.setText("ST");
        textActivity.setText(activityType != null ? activityType : "Unknown activity");
        textTimestamp.setText(formatTimestamp(timestamp));

        if (TextUtils.isEmpty(proofUrl)) {
            textProofStatus.setText("No proof uploaded");
        } else {
            textProofStatus.setText("Proof attached");
        }

        loadSubmitterName(userId, textName, textInitials);

        final String finalLogId = logId;
        btnUpvote.setOnClickListener(v -> castVote(finalLogId, true, btnUpvote, btnDownvote));
        btnDownvote.setOnClickListener(v -> castVote(finalLogId, false, btnUpvote, btnDownvote));
    }

    private void loadSubmitterName(@Nullable String userId,
                                   @NonNull TextView textName,
                                   @NonNull TextView textInitials) {
        if (TextUtils.isEmpty(userId)) {
            return;
        }

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
                          boolean isUpvote,
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
                        return;
                    }

                    String voteId = db.collection("votes").document().getId();
                    Vote vote = new Vote(
                            voteId,
                            logId,
                            voterId,
                            isUpvote,
                            Timestamp.now()
                    );

                    db.collection("votes")
                            .document(voteId)
                            .set(vote)
                            .addOnSuccessListener(unused -> {
                                btnUpvote.setEnabled(false);
                                btnDownvote.setEnabled(false);

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
        if (timestamp == null) {
            return "Just now";
        }

        SimpleDateFormat formatter = new SimpleDateFormat("dd MMM  h:mm a", Locale.getDefault());
        return formatter.format(timestamp.toDate());
    }

    private String makeInitials(@NonNull String name) {
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            return "ST";
        }

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
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}