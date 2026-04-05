/**
 * PointsManagerTest.java
 *
 * Unit tests for PointsManager.calculateBonusPoints().
 * Tests cover zero votes, single vote, multiple votes, and negative
 * input (defensive case). No Firebase or Android dependencies needed
 * since calculateBonusPointsStatic() is pure logic with no Firestore calls.
 *
 * Role in design: Verifies the Model layer points calculation in
 * isolation, without any Firestore calls.
 *
 * @author Haroon
 */
package com.example.klimate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PointsManagerTest {

    /**
     * Zero votes should earn zero bonus points.
     */
    @Test
    public void testZeroVotes_returnsZero() {
        assertEquals(0, PointsManager.calculateBonusPointsStatic(0));
    }

    /**
     * One upvote should earn 2 bonus points.
     */
    @Test
    public void testOneVote_returnsTwo() {
        assertEquals(2, PointsManager.calculateBonusPointsStatic(1));
    }

    /**
     * Five upvotes should earn 10 bonus points.
     */
    @Test
    public void testFiveVotes_returnsTen() {
        assertEquals(10, PointsManager.calculateBonusPointsStatic(5));
    }

    /**
     * Large vote count should scale correctly (20 votes → 40 pts).
     */
    @Test
    public void testLargeVoteCount_scalesCorrectly() {
        assertEquals(40, PointsManager.calculateBonusPointsStatic(20));
    }

    /**
     * Negative input should return 0, not a negative number.
     */
    @Test
    public void testNegativeInput_returnsZero() {
        assertEquals(0, PointsManager.calculateBonusPointsStatic(-1));
        assertEquals(0, PointsManager.calculateBonusPointsStatic(-100));
    }

    /**
     * Result should always be non-negative regardless of input.
     */
    @Test
    public void testResultIsNeverNegative() {
        assertTrue(PointsManager.calculateBonusPointsStatic(-999) >= 0);
    }
}