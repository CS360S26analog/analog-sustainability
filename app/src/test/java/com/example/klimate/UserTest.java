/**
 * UserTest.java
 *
 * Unit tests for the User model class.
 * Tests cover constructor initialisation, getter/setter correctness,
 * and default zero values for stats fields.
 * No Firebase or Android dependencies needed — pure Java logic only.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import com.example.klimate.model.User;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserTest {

    // Helper — builds a standard test user with the current 6-arg constructor
    private User makeUser() {
        return new User("uid123", "Maya", "maya@lums.edu.pk", "LUMS", null, "student");
    }

    @Test
    public void testNewUser_statsInitialisedToZero() {
        User user = makeUser();
        assertEquals(0, user.getTotalPoints());
        assertEquals(0, user.getStreakDays());
        assertEquals(0.0, user.getCo2SavedKg(), 0.001);
    }

    @Test
    public void testGetDisplayName_returnsConstructorValue() {
        assertEquals("Maya", makeUser().getDisplayName());
    }

    @Test
    public void testGetEmail_returnsConstructorValue() {
        assertEquals("maya@lums.edu.pk", makeUser().getEmail());
    }

    @Test
    public void testGetUid_returnsConstructorValue() {
        assertEquals("uid123", makeUser().getUid());
    }

    @Test
    public void testGetUniversity_returnsConstructorValue() {
        assertEquals("LUMS", makeUser().getUniversity());
    }

    @Test
    public void testGetRole_returnsConstructorValue() {
        assertEquals("student", makeUser().getRole());
    }

    @Test
    public void testGetRole_staffUser_returnsStaff() {
        User staff = new User("s1", "Prof", "prof@lums.edu.pk", "LUMS", null, "staff");
        assertEquals("staff", staff.getRole());
    }

    @Test
    public void testSetTotalPoints_updatesCorrectly() {
        User user = makeUser();
        user.setTotalPoints(500);
        assertEquals(500, user.getTotalPoints());
    }

    @Test
    public void testSetStreakDays_updatesCorrectly() {
        User user = makeUser();
        user.setStreakDays(14);
        assertEquals(14, user.getStreakDays());
    }

    @Test
    public void testSetCo2SavedKg_updatesCorrectly() {
        User user = makeUser();
        user.setCo2SavedKg(2.4);
        assertEquals(2.4, user.getCo2SavedKg(), 0.001);
    }

    @Test
    public void testSetDisplayName_overwritesConstructorValue() {
        User user = makeUser();
        user.setDisplayName("Maya P.");
        assertEquals("Maya P.", user.getDisplayName());
    }

    @Test
    public void testNoArgConstructor_doesNotThrow() {
        User user = new User();
        assertNotNull(user);
        assertEquals(0, user.getTotalPoints());
        assertEquals(0, user.getStreakDays());
    }

    @Test
    public void testUniversity_defaultsToLums_whenNull() {
        User user = new User();
        // getUniversity() returns "LUMS" as default when field is null
        assertEquals("LUMS", user.getUniversity());
    }
}