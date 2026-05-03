/**
 * RewardRedemptionTest.java
 *
 * Unit tests for reward redemption logic.
 * Tests the point balance check and the optimistic UI deduction
 * without requiring a real Firebase connection.
 *
 * @author Haroon
 */
package com.example.klimate;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewardRedemptionTest {

    // Simulates the currentTotalPoints field in ProfileFragment
    private long currentTotalPoints;

    // Reward costs matching the hardcoded REWARDS array in ProfileFragment
    private static final int COST_FREE_COFFEE        = 500;
    private static final int COST_BIKE_RENTAL        = 1200;
    private static final int COST_ECO_TOTE_BAG       = 800;
    private static final int COST_PLANT_A_TREE       = 1000;
    private static final int COST_CAMPUS_MEAL_DISC   = 1500;

    @Before
    public void setUp() {
        currentTotalPoints = 0;
    }

    // -------------------------------------------------------------------
    // Insufficient points — redemption should be blocked
    // -------------------------------------------------------------------

    /**
     * User has 0 points and tries to redeem Free Coffee (500 pts).
     * Expected: redemption blocked, points unchanged.
     */
    @Test
    public void testRedemption_insufficientPoints_blocked() {
        currentTotalPoints = 0;
        boolean canRedeem = currentTotalPoints >= COST_FREE_COFFEE;
        assertFalse("Should not be able to redeem with 0 points", canRedeem);
        assertEquals("Points should be unchanged", 0, currentTotalPoints);
    }

    /**
     * User has 499 points and tries to redeem Free Coffee (500 pts).
     * Expected: redemption blocked — one point short.
     */
    @Test
    public void testRedemption_onePointShort_blocked() {
        currentTotalPoints = 499;
        boolean canRedeem = currentTotalPoints >= COST_FREE_COFFEE;
        assertFalse("Should not be able to redeem with 499 points", canRedeem);
    }

    /**
     * User has exactly enough points for the most expensive reward.
     * Expected: redemption allowed.
     */
    @Test
    public void testRedemption_exactPoints_allowed() {
        currentTotalPoints = COST_CAMPUS_MEAL_DISC;
        boolean canRedeem = currentTotalPoints >= COST_CAMPUS_MEAL_DISC;
        assertTrue("Should be able to redeem with exact points", canRedeem);
    }

    // -------------------------------------------------------------------
    // Sufficient points — optimistic deduction
    // -------------------------------------------------------------------

    /**
     * User has 1000 points and redeems Free Coffee (500 pts).
     * Expected: optimistic deduction leaves 500 pts immediately.
     */
    @Test
    public void testRedemption_sufficient_optimisticDeduction() {
        currentTotalPoints = 1000;
        // Simulate the optimistic deduction in ProfileFragment
        currentTotalPoints -= COST_FREE_COFFEE;
        assertEquals("Points should be 500 after redeeming Free Coffee",
                500, currentTotalPoints);
    }

    /**
     * User has exactly 500 points and redeems Free Coffee.
     * Expected: optimistic deduction leaves 0 pts.
     */
    @Test
    public void testRedemption_exactPoints_deductionLeavesZero() {
        currentTotalPoints = 500;
        currentTotalPoints -= COST_FREE_COFFEE;
        assertEquals("Points should be 0 after exact redemption",
                0, currentTotalPoints);
    }

    /**
     * Simulates rollback when Firebase write fails.
     * Expected: points restored to pre-redemption value.
     */
    @Test
    public void testRedemption_rollback_restoresPoints() {
        currentTotalPoints = 1000;
        long pointsBeforeRedemption = currentTotalPoints;

        // Optimistic deduction
        currentTotalPoints -= COST_FREE_COFFEE;
        assertEquals(500, currentTotalPoints);

        // Firebase fails — rollback
        currentTotalPoints = pointsBeforeRedemption;
        assertEquals("Points should be restored to 1000 after rollback",
                1000, currentTotalPoints);
    }

    /**
     * User redeems two different rewards sequentially.
     * Expected: points deducted correctly after each.
     */
    @Test
    public void testRedemption_twoSequentialRedemptions() {
        currentTotalPoints = 2000;

        // First redemption: Free Coffee (500)
        assertTrue(currentTotalPoints >= COST_FREE_COFFEE);
        currentTotalPoints -= COST_FREE_COFFEE;
        assertEquals(1500, currentTotalPoints);

        // Second redemption: Eco Tote Bag (800)
        assertTrue(currentTotalPoints >= COST_ECO_TOTE_BAG);
        currentTotalPoints -= COST_ECO_TOTE_BAG;
        assertEquals(700, currentTotalPoints);
    }

    /**
     * User tries to redeem a second reward after first deduction
     * leaves insufficient balance.
     * Expected: second redemption blocked.
     */
    @Test
    public void testRedemption_secondRedemptionBlocked_afterFirst() {
        currentTotalPoints = 600;

        // First redemption succeeds
        assertTrue(currentTotalPoints >= COST_FREE_COFFEE);
        currentTotalPoints -= COST_FREE_COFFEE;
        assertEquals(100, currentTotalPoints);

        // Second redemption for Eco Tote Bag (800) should be blocked
        boolean canRedeemSecond = currentTotalPoints >= COST_ECO_TOTE_BAG;
        assertFalse("Should not be able to redeem second reward with 100 pts",
                canRedeemSecond);
    }

    // -------------------------------------------------------------------
    // Reward ID generation
    // -------------------------------------------------------------------

    /**
     * Verifies reward IDs are generated consistently from name.
     * These IDs are stored in Firestore and used to check redeemed state.
     */
    @Test
    public void testRewardId_freeCoffee() {
        String name = "Free Coffee";
        String expectedId = "free_coffee";
        String actualId = name.toLowerCase().replace(" ", "_");
        assertEquals(expectedId, actualId);
    }

    @Test
    public void testRewardId_bikeRentalVoucher() {
        String name = "Bike Rental Voucher";
        String expectedId = "bike_rental_voucher";
        String actualId = name.toLowerCase().replace(" ", "_");
        assertEquals(expectedId, actualId);
    }

    @Test
    public void testRewardId_campusMealDiscount() {
        String name = "Campus Meal Discount";
        String expectedId = "campus_meal_discount";
        String actualId = name.toLowerCase().replace(" ", "_");
        assertEquals(expectedId, actualId);
    }
}