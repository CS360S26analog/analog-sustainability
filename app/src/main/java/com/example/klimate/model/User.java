/**
 * User.java
 *
 * Model class representing a registered Klimate app user.
 * Stores authentication identity, display name, and aggregated sustainability
 * statistics (points, streak, CO2 saved). Mapped to the "users" Firestore
 * collection where each document ID equals the Firebase Auth UID.
 *
 * Role in design: Part of the Model layer (MVC). Written by RegisterActivity
 * on first registration and read by HomeFragment / ProfileFragment to display
 * live stats. DashboardViewModel updates totalPoints, streakDays, and
 * co2SavedKg whenever new activity logs are processed.
 *
 * Outstanding issues: streakDays reset logic (midnight check) not yet
 * implemented — currently only increments.
 *
 * @author Maryam Waseem
 */
package com.example.klimate.model;

import com.google.firebase.Timestamp;

public class User {

    private String uid;
    private String displayName;
    private String email;
    private int totalPoints;
    private int streakDays;
    private double co2SavedKg;
    private Timestamp createdAt;

    /**
     * Required no-argument constructor for Firestore deserialization.
     */
    public User() {}

    /**
     * Constructs a new User with all fields specified.
     *
     * @param uid         Firebase Auth UID, also used as the Firestore document ID
     * @param displayName the user's chosen display name
     * @param email       the campus email address used at registration
     * @param createdAt   timestamp of account creation
     */
    public User(String uid, String displayName, String email, Timestamp createdAt) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.totalPoints = 0;
        this.streakDays = 0;
        this.co2SavedKg = 0.0;
        this.createdAt = createdAt;
    }

    /**
     * Returns the Firebase Auth UID for this user.
     *
     * @return the UID string
     */
    public String getUid() { return uid; }

    /**
     * Sets the Firebase Auth UID.
     *
     * @param uid the UID string
     */
    public void setUid(String uid) { this.uid = uid; }

    /**
     * Returns the user's display name.
     *
     * @return display name string
     */
    public String getDisplayName() { return displayName; }

    /**
     * Sets the user's display name.
     *
     * @param displayName the new display name
     */
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * Returns the campus email address associated with this account.
     *
     * @return email string
     */
    public String getEmail() { return email; }

    /**
     * Sets the email address.
     *
     * @param email the campus email address
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * Returns the user's current total points balance.
     *
     * @return total points as an integer
     */
    public int getTotalPoints() { return totalPoints; }

    /**
     * Sets the total points balance.
     *
     * @param totalPoints the new points total
     */
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

    /**
     * Returns the user's current consecutive logging streak in days.
     *
     * @return streak length in days
     */
    public int getStreakDays() { return streakDays; }

    /**
     * Sets the streak day count.
     *
     * @param streakDays the new streak count
     */
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }

    /**
     * Returns the cumulative CO2 saved in kilograms based on logged activities.
     *
     * @return CO2 saved in kg as a double
     */
    public double getCo2SavedKg() { return co2SavedKg; }

    /**
     * Sets the cumulative CO2 saved value.
     *
     * @param co2SavedKg total CO2 saved in kilograms
     */
    public void setCo2SavedKg(double co2SavedKg) { this.co2SavedKg = co2SavedKg; }

    /**
     * Returns the Firestore Timestamp when this account was created.
     *
     * @return account creation timestamp
     */
    public Timestamp getCreatedAt() { return createdAt; }

    /**
     * Sets the account creation timestamp.
     *
     * @param createdAt the creation timestamp
     */
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
