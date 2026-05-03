/**
 * ActivityLog.java
 *
 * Model class representing a single sustainability activity log entry.
 * Stores activity type, submission status (quick/verified), points earned,
 * proof URL, and vote count. Mapped to the "activity_logs" Firestore collection.
 *
 * Role in design: Part of the Model layer (MVC). Written by LogFragment,
 * read by dashboard/statistics features, and later used by validation/voting.
 *
 * Outstanding issues: proof upload flow is handled separately in the verified
 * logging feature path.
 *
 * @author Izza
 */
package com.example.klimate.model;

import com.google.firebase.Timestamp;

public class ActivityLog {

    private String logId;

    private double co2SavedKg;
    private String userId;
    private String activityType;
    private String status; // "quick", "pending_verification", or "verified"
    private int points;
    private int bonusPoints;
    private String proofUrl;
    private int voteCount;
    private int quantity;
    private Timestamp timestamp;

    /**
     * Required empty constructor for Firestore.
     */
    public ActivityLog() {
    }

    /**
     * Constructs a new activity log with the main required fields.
     *
     * @param userId       UID of the user submitting the log
     * @param activityType activity category name
     * @param status       log status such as "quick" or "pending_verification"
     * @param points       base points for this activity
     * @param timestamp    submission timestamp
     */
    public ActivityLog(String userId, String activityType, String status, int points, Timestamp timestamp) {
        this.userId = userId;
        this.activityType = activityType;
        this.status = status;
        this.points = points;
        this.timestamp = timestamp;
        this.bonusPoints = 0;
        this.voteCount = 0;
        this.proofUrl = null;
    }

    /**
     * Returns the Firestore document ID of this log.
     *
     * @return log ID
     */
    public String getLogId() {
        return logId;
    }

    /**
     * Sets the Firestore document ID of this log.
     *
     * @param logId auto-generated Firestore document ID
     */
    public void setLogId(String logId) {
        this.logId = logId;
    }

    /**
     * Returns the UID of the user who submitted the log.
     *
     * @return user UID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user UID for this log.
     *
     * @param userId Firebase Auth UID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the logged activity type.
     *
     * @return activity type name
     */
    public String getActivityType() {
        return activityType;
    }

    /**
     * Sets the activity type.
     *
     * @param activityType activity category name
     */
    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    /**
     * Returns the current log status.
     *
     * @return status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the current log status.
     *
     * @param status "quick", "pending_verification", or "verified"
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the base points assigned to this activity.
     *
     * @return base points
     */
    public int getPoints() {
        return points;
    }

    /**
     * Sets the base points for this activity.
     *
     * @param points base points value
     */
    public void setPoints(int points) {
        this.points = points;
    }

    /**
     * Returns additional points earned from votes.
     *
     * @return bonus points
     */
    public int getBonusPoints() {
        return bonusPoints;
    }

    /**
     * Sets additional bonus points.
     *
     * @param bonusPoints vote-based bonus points
     */
    public void setBonusPoints(int bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    /**
     * Returns the proof image URL if present.
     *
     * @return Firebase Storage download URL or null
     */
    public String getProofUrl() {
        return proofUrl;
    }

    /**
     * Sets the proof image URL.
     *
     * @param proofUrl Firebase Storage download URL
     */
    public void setProofUrl(String proofUrl) {
        this.proofUrl = proofUrl;
    }

    /**
     * Returns the total number of upvotes on this log.
     *
     * @return vote count
     */
    public int getVoteCount() {
        return voteCount;
    }

    /**
     * Sets the current vote count.
     *
     * @param voteCount number of upvotes
     */
    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }

    /**
     * Returns when the log was submitted.
     *
     * @return Firestore timestamp
     */
    public Timestamp getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the submission timestamp.
     *
     * @param timestamp submission time
     */
    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Calculates total points including vote bonus.
     *
     * @return base points plus bonus points
     */
    public int getTotalPoints() {
        return points + bonusPoints;
    }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getCo2SavedKg()
    {
        /**
         * Calculates co2 saved ad returns.
         *
         * @return co2 saved
         */
        return co2SavedKg;
    }

    public void setCo2SavedKg(double co2SavedKg)
    {
        /**
         * Calculates total points including vote bonus.
         *
         * @return base points plus bonus points
         */
        this.co2SavedKg = co2SavedKg;
    }

}