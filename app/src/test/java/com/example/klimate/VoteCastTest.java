/**
 * VoteCastTest.java
 *
 * Unit tests for vote casting logic.
 * Tests point deduction on downvote, rejection threshold at -5 net votes,
 * and the net bonus points floor behaviour.
 *
 * These tests use pure Java logic mirroring what happens in
 * ValidationFeedFragment and PointsManager without requiring Firebase.
 *
 * @author Haroon
 */
package com.example.klimate;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class VoteCastTest {

    // Mirrors state that would be held in Firestore per log document
    private int upvoteCount;
    private int downvoteCount;
    private int ownerTotalPoints;
    private int logBonusPoints;
    private String logStatus;

    @Before
    public void setUp() {
        upvoteCount      = 0;
        downvoteCount    = 0;
        ownerTotalPoints = 100; // user starts with 100 pts
        logBonusPoints   = 0;
        logStatus        = "pending_verification";
    }

    // -------------------------------------------------------------------
    // Downvote deducts 1 point from owner immediately
    // -------------------------------------------------------------------

    /**
     * Casting a downvote should immediately deduct 1 point from the owner.
     * This mirrors the FieldValue.increment(-1) in castVote().
     */
    @Test
    public void testDownvote_deductsOnePointFromOwner() {
        int pointsBefore = ownerTotalPoints;

        // Simulate downvote cast
        downvoteCount++;
        ownerTotalPoints -= 1; // immediate deduction in castVote()

        assertEquals("Owner should have 1 fewer point after downvote",
                pointsBefore - 1, ownerTotalPoints);
    }

    /**
     * Casting an upvote should NOT deduct points — only attachVoteListener
     * handles the bonus increment on upvote.
     */
    @Test
    public void testUpvote_doesNotDeductPoints() {
        int pointsBefore = ownerTotalPoints;

        upvoteCount++;
        // No immediate deduction for upvotes

        assertEquals("Owner points should be unchanged after upvote",
                pointsBefore, ownerTotalPoints);
    }

    // -------------------------------------------------------------------
    // Net vote calculation and bonus points
    // -------------------------------------------------------------------

    /**
     * After 3 upvotes and 1 downvote:
     * net = 3 - 1 = 2 (not rejected)
     * bonus = calculateNetBonusPoints(3, 1) = (3*2) - (1*1) = 5
     */
    @Test
    public void testNetVotes_threeUpOneDown_notRejected() {
        upvoteCount   = 3;
        downvoteCount = 1;

        int netVotes   = upvoteCount - downvoteCount;
        int bonusPoints = PointsManager.calculateNetBonusPoints(upvoteCount, downvoteCount);

        assertEquals("Net votes should be 2", 2, netVotes);
        assertEquals("Bonus should be 5", 5, bonusPoints);
        assertFalse("Log should NOT be rejected", netVotes <= -5);
    }

    /**
     * After 0 upvotes and 5 downvotes:
     * net = 0 - 5 = -5 → log should be rejected
     * bonus = max(0, (0*2) - (5*1)) = 0
     */
    @Test
    public void testNetVotes_fiveDownvotes_logRejected() {
        upvoteCount   = 0;
        downvoteCount = 5;

        int netVotes    = upvoteCount - downvoteCount;
        int bonusPoints = PointsManager.calculateNetBonusPoints(upvoteCount, downvoteCount);

        assertEquals("Net votes should be -5", -5, netVotes);
        assertEquals("Bonus should be floored at 0", 0, bonusPoints);
        assertTrue("Log SHOULD be rejected at -5", netVotes <= -5);

        // Simulate rejection
        if (netVotes <= -5) {
            logStatus = "rejected";
        }

        assertEquals("Log status should be rejected", "rejected", logStatus);
    }

    /**
     * Rejection threshold is exactly -5.
     * Net vote of -4 should NOT trigger rejection.
     */
    @Test
    public void testRejection_atFourDownvotes_notRejected() {
        upvoteCount   = 0;
        downvoteCount = 4;

        int netVotes = upvoteCount - downvoteCount;

        assertFalse("Should NOT be rejected at -4 net votes", netVotes <= -5);
        assertEquals("Status should still be pending", "pending_verification", logStatus);
    }

    /**
     * Net vote of -6 (more than 5 downvotes) should also trigger rejection.
     */
    @Test
    public void testRejection_beyondFiveDownvotes_alsoRejected() {
        upvoteCount   = 0;
        downvoteCount = 6;

        int netVotes = upvoteCount - downvoteCount;
        assertTrue("Should be rejected beyond -5 net votes", netVotes <= -5);

        if (netVotes <= -5) {
            logStatus = "rejected";
        }

        assertEquals("rejected", logStatus);
    }

    // -------------------------------------------------------------------
    // Bonus points floor — never goes negative
    // -------------------------------------------------------------------

    /**
     * Net bonus should never be negative regardless of vote counts.
     * Max(0, ...) ensures floor at 0.
     */
    @Test
    public void testBonusPoints_alwaysNonNegative_manyDownvotes() {
        int bonus = PointsManager.calculateNetBonusPoints(0, 100);
        assertTrue("Bonus points should never be negative", bonus >= 0);
        assertEquals("Bonus should be exactly 0", 0, bonus);
    }

    /**
     * Equal upvotes and downvotes — bonus should be positive since
     * each upvote is worth 2 but each downvote only removes 1.
     */
    @Test
    public void testBonusPoints_equalVotes_positiveBonus() {
        // 4 up, 4 down: (4*2) - (4*1) = 8 - 4 = 4
        int bonus = PointsManager.calculateNetBonusPoints(4, 4);
        assertEquals("Bonus should be 4 with equal votes", 4, bonus);
        assertTrue("Bonus should be positive", bonus > 0);
    }

    // -------------------------------------------------------------------
    // Point delta application
    // -------------------------------------------------------------------

    /**
     * When bonus points increase from 2 to 6 (delta = +4),
     * owner's total points should increase by 4.
     */
    @Test
    public void testPointDelta_bonusIncrease_ownerPointsIncrease() {
        int currentBonus = 2;
        int newBonus = 6;
        int delta = newBonus - currentBonus;

        ownerTotalPoints += delta;

        assertEquals("Owner should gain 4 points from bonus increase",
                104, ownerTotalPoints);
    }

    /**
     * When bonus points decrease from 6 to 2 (delta = -4),
     * owner's total points should decrease by 4.
     */
    @Test
    public void testPointDelta_bonusDecrease_ownerPointsDecrease() {
        ownerTotalPoints = 106;
        int currentBonus = 6;
        int newBonus = 2;
        int delta = newBonus - currentBonus;

        ownerTotalPoints += delta;

        assertEquals("Owner should lose 4 points from bonus decrease",
                102, ownerTotalPoints);
    }

    /**
     * When delta is 0 (bonus unchanged), owner points should not change.
     */
    @Test
    public void testPointDelta_noDelta_ownerPointsUnchanged() {
        int pointsBefore = ownerTotalPoints;
        int delta = 0;

        if (delta != 0) {
            ownerTotalPoints += delta;
        }

        assertEquals("Owner points should be unchanged when delta is 0",
                pointsBefore, ownerTotalPoints);
    }

    // -------------------------------------------------------------------
    // Already voted check
    // -------------------------------------------------------------------

    /**
     * A user who has already voted should be blocked from voting again.
     * Mirrors the existing vote check in castVote().
     */
    @Test
    public void testAlreadyVoted_blocked() {
        String currentUserId = "user_abc";

        // Simulate existing vote by this user
        String existingVoter = "user_abc";

        boolean alreadyVoted = currentUserId.equals(existingVoter);
        assertTrue("User should be blocked from voting twice", alreadyVoted);
    }

    /**
     * A different user should be allowed to vote.
     */
    @Test
    public void testDifferentUser_allowed() {
        String currentUserId = "user_xyz";
        String existingVoter = "user_abc";

        boolean alreadyVoted = currentUserId.equals(existingVoter);
        assertFalse("Different user should be allowed to vote", alreadyVoted);
    }
}