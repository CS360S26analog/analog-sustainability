/**
 * Vote.java
 *
 * Model class representing a community validation vote on a pending
 * sustainability activity log. Stores which log was voted on, who
 * cast the vote, whether it was an upvote or downvote, and when the
 * vote happened. Mapped to the votes Firestore collection.
 *
 * Role in design: Part of the Model layer. Written by
 * ValidationFeedFragment and read when checking whether a user has
 * already voted on a submission.
 *
 * Outstanding issues: none for the half checkpoint.
 * @author Karar
 */

package com.example.klimate.model;

import com.google.firebase.Timestamp;

public class Vote {

    private String voteId;
    private String logId;
    private String voterId;
    private boolean isUpvote;
    private Timestamp timestamp;

    /**
     * Required empty constructor for Firebase Firestore.
     */
    public Vote() {
    }

    /**
     * Creates a vote with all fields.
     *
     * @param voteId the Firestore document ID for this vote
     * @param logId the ID of the activity log being voted on
     * @param voterId the UID of the student casting the vote
     * @param isUpvote true if the vote is an upvote false if downvote
     * @param timestamp when the vote was cast
     */
    public Vote(String voteId, String logId, String voterId, boolean isUpvote, Timestamp timestamp) {
        this.voteId = voteId;
        this.logId = logId;
        this.voterId = voterId;
        this.isUpvote = isUpvote;
        this.timestamp = timestamp;
    }

    /**
     * Returns the Firestore vote document ID.
     *
     * @return the vote ID
     */
    public String getVoteId() {
        return voteId;
    }

    /**
     * Sets the Firestore vote document ID.
     *
     * @param voteId the vote ID to store
     */
    public void setVoteId(String voteId) {
        this.voteId = voteId;
    }

    /**
     * Returns the ID of the activity log this vote belongs to.
     *
     * @return the activity log ID
     */
    public String getLogId() {
        return logId;
    }

    /**
     * Sets the ID of the activity log this vote belongs to.
     *
     * @param logId the activity log ID
     */
    public void setLogId(String logId) {
        this.logId = logId;
    }

    /**
     * Returns the UID of the student who cast this vote.
     *
     * @return the voter UID
     */
    public String getVoterId() {
        return voterId;
    }

    /**
     * Sets the UID of the student who cast this vote.
     *
     * @param voterId the voter UID
     */
    public void setVoterId(String voterId) {
        this.voterId = voterId;
    }

    /**
     * Returns whether this vote is an upvote.
     *
     * @return true if upvote false if downvote
     */
    public boolean isUpvote() {
        return isUpvote;
    }

    /**
     * Sets whether this vote is an upvote.
     *
     * @param upvote true for upvote false for downvote
     */
    public void setUpvote(boolean upvote) {
        isUpvote = upvote;
    }

    /**
     * Returns when the vote was cast.
     *
     * @return the vote timestamp
     */
    public Timestamp getTimestamp() {
        return timestamp;
    }

    /**
     * Sets when the vote was cast.
     *
     * @param timestamp the vote timestamp
     */
    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}