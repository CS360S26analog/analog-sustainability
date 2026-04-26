package com.example.klimate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StaffReportsLogicTest {

    private boolean isFlagged(long downvotes) {
        return downvotes >= 5;
    }

    private long pointsAfterReject(long currentPoints, long logPoints, long bonusPoints) {
        long deduction = Math.max(0, logPoints + bonusPoints);
        return Math.max(0, currentPoints - deduction);
    }

    @Test
    public void logIsFlaggedAtFiveDownvotes() {
        assertFalse(isFlagged(4));
        assertTrue(isFlagged(5));
        assertTrue(isFlagged(8));
    }

    @Test
    public void rejectDeductsLogPointsAndBonusPoints() {
        long result = pointsAfterReject(500, 100, 20);

        assertEquals(380, result);
    }

    @Test
    public void rejectNeverMakesUserPointsNegative() {
        long result = pointsAfterReject(50, 100, 20);

        assertEquals(0, result);
    }
}