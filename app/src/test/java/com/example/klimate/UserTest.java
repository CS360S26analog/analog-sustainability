/**
 * UserTest.java
 *
 * Unit tests for the User model class.
 * Tests cover constructor initialisation, getter/setter correctness,
 * and default zero values for stats fields.
 * No Firebase or Android dependencies needed — pure Java logic only.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import com.example.klimate.model.User;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserTest {

    /**
     * New user created via full constructor should have zero stats.
     */
    @Test
    public void testNewUser_statsInitialisedToZero() {
        User user = new User("uid123", "Maya", "maya@lums.edu.pk", null);
        assertEquals(0, user.getTotalPoints());
        assertEquals(0, user.getStreakDays());
        assertEquals(0.0, user.getCo2SavedKg(), 0.001);
    }

    /**
     * getDisplayName should return the name passed to the constructor.
     */
    @Test
    public void testGetDisplayName_returnsConstructorValue() {
        User user = new User("uid123", "Maya", "maya@lums.edu.pk", null);
        assertEquals("Maya", user.getDisplayName());
    }

    /**
     * getEmail should return the email passed to the constructor.
     */
    @Test
    public void testGetEmail_returnsConstructorValue() {
        User user = new User("uid123", "Maya", "maya@lums.edu.pk", null);
        assertEquals("maya@lums.edu.pk", user.getEmail());
    }

    /**
     * getUid should return the UID passed to the constructor.
     */
    @Test
    public void testGetUid_returnsConstructorValue() {
        User user = new User("uid123", "Maya", "maya@lums.edu.pk", null);
        assertEquals("uid123", user.getUid());
    }

    /**
     * setTotalPoints then getTotalPoints should return the updated value.
     */
    @Test
    public void testSetTotalPoints_updatesCorrectly() {
        User user = new User("uid123", "Maya", "maya@lums.edu.pk", null);
        user.setTotalPoints(500);
        assertEquals(500, user.getTotalPoints());
    }

    /**
     * setStreakDays then getStreakDays should return the updated value.
     */
    @Test
    public void testSetStreakDays_updatesCorrectly() {
        User user = new User("uid123", "Maya", "maya@lums.edu.pk", null);
        user.setStreakDays(14);
        assertEquals(14, user.getStreakDays());
    }

    /**
     * setCo2SavedKg then getCo2SavedKg should return the updated value.
     */
    @Test
    public void testSetCo2SavedKg_updatesCorrectly() {
        User user = new User("uid123", "Maya", "maya@lums.edu.pk", null);
        user.setCo2SavedKg(2.4);
        assertEquals(2.4, user.getCo2SavedKg(), 0.001);
    }

    /**
     * setDisplayName should overwrite the name set in the constructor.
     */
    @Test
    public void testSetDisplayName_overwritesConstructorValue() {
        User user = new User("uid123", "Maya", "maya@lums.edu.pk", null);
        user.setDisplayName("Maya P.");
        assertEquals("Maya P.", user.getDisplayName());
    }

    /**
     * No-arg constructor should not throw and all fields should be null/zero.
     */
    @Test
    public void testNoArgConstructor_doesNotThrow() {
        User user = new User();
        assertNotNull(user);
        assertEquals(0, user.getTotalPoints());
        assertEquals(0, user.getStreakDays());
    }
}
