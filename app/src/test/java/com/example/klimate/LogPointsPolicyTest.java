package com.example.klimate;

import org.junit.Test;

import static org.junit.Assert.*;

public class LogPointsPolicyTest {

    @Test
    public void quickLog_shouldNotAwardPoints() {
        assertFalse(LogPointsPolicy.shouldAwardPoints("quick"));
    }

    @Test
    public void verifiedLog_shouldAwardPoints() {
        assertTrue(LogPointsPolicy.shouldAwardPoints("pending_verification"));
    }

    @Test
    public void quickLog_shouldStoreZeroPointsEvenIfCalculatedPointsExist() {
        int storedPoints = LogPointsPolicy.pointsForStoredLog("quick", 30);

        assertEquals(0, storedPoints);
    }

    @Test
    public void verifiedLog_shouldStoreCalculatedPoints() {
        int storedPoints = LogPointsPolicy.pointsForStoredLog("pending_verification", 30);

        assertEquals(30, storedPoints);
    }

    @Test
    public void verifiedLog_shouldNotStoreNegativePoints() {
        int storedPoints = LogPointsPolicy.pointsForStoredLog("pending_verification", -10);

        assertEquals(0, storedPoints);
    }

    @Test
    public void quickLog_successMessageShouldNotMentionPoints() {
        String message = LogPointsPolicy.buildSuccessMessage("quick", "Cycling");

        assertEquals("Cycling logged ✅", message);
        assertFalse(message.contains("pts"));
        assertFalse(message.contains("points"));
    }

    @Test
    public void verifiedLog_successMessageShouldMentionProof() {
        String message = LogPointsPolicy.buildSuccessMessage("pending_verification", "Cycling");

        assertEquals("Verified log submitted with proof", message);
    }
}