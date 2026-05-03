/**
 * PointsManagerTest.java
 *
 * Unit tests for PointsManager's static bonus points calculation methods.
 * These tests do not require a Firebase instance — they only test the
 * pure calculation logic using the static helper methods.
 *
 * @author Haroon
 */
package com.example.klimate;

import org.junit.Test;
import static org.junit.Assert.*;

public class PointsManagerTest {

    /**
     * 5 upvotes, 0 downvotes.
     * Expected: (5 * 2) - (0 * 1) = 10
     */
    @Test
    public void testNetBonus_fiveUpvotesZeroDownvotes() {
        int result = PointsManager.calculateNetBonusPoints(5, 0);
        assertEquals(10, result);
    }

    /**
     * 2 upvotes, 3 downvotes.
     * Expected: (2 * 2) - (3 * 1) = 4 - 3 = 1
     */
    @Test
    public void testNetBonus_twoUpvotesThreeDownvotes() {
        int result = PointsManager.calculateNetBonusPoints(2, 3);
        assertEquals(1, result);
    }

    /**
     * 0 upvotes, 5 downvotes.
     * Expected: max(0, (0 * 2) - (5 * 1)) = max(0, -5) = 0
     * Result must never go negative — floored at 0.
     */
    @Test
    public void testNetBonus_zeroUpvotesFiveDownvotes_floorAtZero() {
        int result = PointsManager.calculateNetBonusPoints(0, 5);
        assertEquals(0, result);
    }

    /**
     * 3 upvotes, 3 downvotes.
     * Expected: (3 * 2) - (3 * 1) = 6 - 3 = 3
     */
    @Test
    public void testNetBonus_threeUpvotesThreeDownvotes() {
        int result = PointsManager.calculateNetBonusPoints(3, 3);
        assertEquals(3, result);
    }

    /**
     * Negative upvote input — defensive case.
     * Expected: floored at 0, not a negative number.
     */
    @Test
    public void testNetBonus_negativeInputs_floorAtZero() {
        int result = PointsManager.calculateNetBonusPoints(-1, -1);
        assertEquals(0, result);
    }

    /**
     * Zero upvotes and zero downvotes.
     * Expected: 0
     */
    @Test
    public void testNetBonus_zeroAndZero() {
        int result = PointsManager.calculateNetBonusPoints(0, 0);
        assertEquals(0, result);
    }

    /**
     * Large number of upvotes, no downvotes.
     * Expected: 100 * 2 = 200
     */
    @Test
    public void testNetBonus_largeUpvoteCount() {
        int result = PointsManager.calculateNetBonusPoints(100, 0);
        assertEquals(200, result);
    }

    /**
     * Basic calculateBonusPoints (upvotes only) — still used in some paths.
     * Expected: 3 * 2 = 6
     */
    @Test
    public void testLegacyBonusPoints_threeUpvotes() {
        int result = PointsManager.calculateBonusPointsStatic(3);
        assertEquals(6, result);
    }

    /**
     * Legacy bonus with negative input — defensive.
     * Expected: 0
     */
    @Test
    public void testLegacyBonusPoints_negativeInput_returnsZero() {
        int result = PointsManager.calculateBonusPointsStatic(-5);
        assertEquals(0, result);
    }
}