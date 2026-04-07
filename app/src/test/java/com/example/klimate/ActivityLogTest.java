/**
 * ActivityLogTest.java
 *
 * Unit tests for the ActivityLog model class.
 * Tests cover constructor initialisation, status field values,
 * points calculation via getTotalPoints(), and getter/setter correctness.
 * No Firebase or Android dependencies needed — pure Java logic only.
 *
 * @author Izza
 */
package com.example.klimate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.klimate.model.ActivityLog;
import com.google.firebase.Timestamp;

import org.junit.Test;

public class ActivityLogTest {

    @Test
    public void constructor_setsMainFieldsCorrectly() {
        Timestamp now = Timestamp.now();
        ActivityLog log = new ActivityLog("user123", "Cycling", "quick", 15, now);

        assertEquals("user123", log.getUserId());
        assertEquals("Cycling", log.getActivityType());
        assertEquals("quick", log.getStatus());
        assertEquals(15, log.getPoints());
        assertEquals(0, log.getBonusPoints());
        assertEquals(0, log.getVoteCount());
        assertNull(log.getProofUrl());
        assertEquals(now, log.getTimestamp());
    }

    @Test
    public void getTotalPoints_returnsBasePlusBonus() {
        ActivityLog log = new ActivityLog();
        log.setPoints(10);
        log.setBonusPoints(4);

        assertEquals(14, log.getTotalPoints());
    }

    @Test
    public void settersAndGetters_workCorrectly() {
        Timestamp now = Timestamp.now();
        ActivityLog log = new ActivityLog();

        log.setLogId("log001");
        log.setUserId("user001");
        log.setActivityType("Recycling");
        log.setStatus("pending_verification");
        log.setPoints(8);
        log.setBonusPoints(2);
        log.setProofUrl("https://example.com/proof.jpg");
        log.setVoteCount(3);
        log.setTimestamp(now);

        assertEquals("log001", log.getLogId());
        assertEquals("user001", log.getUserId());
        assertEquals("Recycling", log.getActivityType());
        assertEquals("pending_verification", log.getStatus());
        assertEquals(8, log.getPoints());
        assertEquals(2, log.getBonusPoints());
        assertEquals("https://example.com/proof.jpg", log.getProofUrl());
        assertEquals(3, log.getVoteCount());
        assertEquals(now, log.getTimestamp());
    }
}