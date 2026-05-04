package com.example.klimate;

import com.example.klimate.model.ActivityLog;
import com.example.klimate.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for the ActivityLog and User model classes.
 */
@RunWith(JUnit4.class)
public class ModelTest {

    // ─────────────────────────────────────────────────────────────────────────
    // ActivityLog tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void activityLog_defaultConstructor_initialValues() {
        ActivityLog log = new ActivityLog();
        assertNull(log.getLogId());
        assertNull(log.getUserId());
        assertNull(log.getActivityType());
        assertNull(log.getStatus());
        assertEquals(0, log.getPoints());
        assertEquals(0, log.getBonusPoints());
        assertEquals(0, log.getVoteCount());
        assertEquals(0.0, log.getCo2SavedKg(), 0.0001);
    }

    @Test
    public void activityLog_getTotalPoints_sumsBaseAndBonus() {
        ActivityLog log = new ActivityLog();
        log.setPoints(50);
        log.setBonusPoints(10);
        assertEquals(60, log.getTotalPoints());
    }

    @Test
    public void activityLog_getTotalPoints_zeroBonusReturnsBase() {
        ActivityLog log = new ActivityLog();
        log.setPoints(25);
        log.setBonusPoints(0);
        assertEquals(25, log.getTotalPoints());
    }

    @Test
    public void activityLog_getTotalPoints_zeroPointsReturnsBonusOnly() {
        ActivityLog log = new ActivityLog();
        log.setPoints(0);
        log.setBonusPoints(15);
        assertEquals(15, log.getTotalPoints());
    }

    @Test
    public void activityLog_getTotalPoints_bothZeroReturnsZero() {
        ActivityLog log = new ActivityLog();
        log.setPoints(0);
        log.setBonusPoints(0);
        assertEquals(0, log.getTotalPoints());
    }

    @Test
    public void activityLog_settersAndGetters_roundTrip() {
        ActivityLog log = new ActivityLog();
        log.setLogId("log123");
        log.setUserId("user456");
        log.setActivityType("Cycling");
        log.setStatus("quick");
        log.setPoints(30);
        log.setBonusPoints(4);
        log.setVoteCount(2);
        log.setCo2SavedKg(1.245);
        log.setQuantity(5);

        assertEquals("log123", log.getLogId());
        assertEquals("user456", log.getUserId());
        assertEquals("Cycling", log.getActivityType());
        assertEquals("quick", log.getStatus());
        assertEquals(30, log.getPoints());
        assertEquals(4, log.getBonusPoints());
        assertEquals(2, log.getVoteCount());
        assertEquals(1.245, log.getCo2SavedKg(), 0.0001);
        assertEquals(5, log.getQuantity());
    }

    @Test
    public void activityLog_setStatus_pendingVerification() {
        ActivityLog log = new ActivityLog();
        log.setStatus("pending_verification");
        assertEquals("pending_verification", log.getStatus());
    }

    @Test
    public void activityLog_setStatus_verified() {
        ActivityLog log = new ActivityLog();
        log.setStatus("verified");
        assertEquals("verified", log.getStatus());
    }

    @Test
    public void activityLog_setCo2_negativeAllowed() {
        ActivityLog log = new ActivityLog();
        log.setCo2SavedKg(-1.0);
        assertEquals(-1.0, log.getCo2SavedKg(), 0.0001);
    }

    @Test
    public void activityLog_setProofUrl() {
        ActivityLog log = new ActivityLog();
        log.setProofUrl("https://firebase.example.com/proof.jpg");
        assertEquals("https://firebase.example.com/proof.jpg", log.getProofUrl());
    }

    @Test
    public void activityLog_setProofUrl_null() {
        ActivityLog log = new ActivityLog();
        log.setProofUrl(null);
        assertNull(log.getProofUrl());
    }

    @Test
    public void activityLog_highBonus_totalReflectsIt() {
        ActivityLog log = new ActivityLog();
        log.setPoints(10);
        log.setBonusPoints(200);
        assertEquals(210, log.getTotalPoints());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void user_defaultConstructor_nullFields() {
        User user = new User();
        assertNull(user.getUid());
        assertNull(user.getDisplayName());
        assertNull(user.getEmail());
        assertEquals(0, user.getTotalPoints());
        assertEquals(0, user.getStreakDays());
        assertEquals(0.0, user.getCo2SavedKg(), 0.0001);
    }

    @Test
    public void user_getUniversity_defaultsToLums_whenNull() {
        User user = new User();
        // university is null via default ctor → should return "LUMS"
        assertEquals("LUMS", user.getUniversity());
    }

    @Test
    public void user_getUniversity_returnsSetValue() {
        User user = new User();
        user.setUniversity("FAST");
        assertEquals("FAST", user.getUniversity());
    }

    @Test
    public void user_setTotalPoints_returnsCorrectValue() {
        User user = new User();
        user.setTotalPoints(1500);
        assertEquals(1500, user.getTotalPoints());
    }

    @Test
    public void user_setStreakDays_returnsCorrectValue() {
        User user = new User();
        user.setStreakDays(14);
        assertEquals(14, user.getStreakDays());
    }

    @Test
    public void user_setCo2SavedKg_returnsCorrectValue() {
        User user = new User();
        user.setCo2SavedKg(3.75);
        assertEquals(3.75, user.getCo2SavedKg(), 0.0001);
    }

    @Test
    public void user_setRole_student() {
        User user = new User();
        user.setRole("student");
        assertEquals("student", user.getRole());
    }

    @Test
    public void user_setRole_staff() {
        User user = new User();
        user.setRole("staff");
        assertEquals("staff", user.getRole());
    }

    @Test
    public void user_setUid_returnsCorrectValue() {
        User user = new User();
        user.setUid("uid_abc123");
        assertEquals("uid_abc123", user.getUid());
    }

    @Test
    public void user_setEmail_returnsCorrectValue() {
        User user = new User();
        user.setEmail("test@lums.edu.pk");
        assertEquals("test@lums.edu.pk", user.getEmail());
    }

    @Test
    public void user_setDisplayName_returnsCorrectValue() {
        User user = new User();
        user.setDisplayName("Ali Khan");
        assertEquals("Ali Khan", user.getDisplayName());
    }

    @Test
    public void user_zeroPoints_atStart() {
        User user = new User();
        assertEquals(0, user.getTotalPoints());
    }

    @Test
    public void user_zeroStreak_atStart() {
        User user = new User();
        assertEquals(0, user.getStreakDays());
    }
}