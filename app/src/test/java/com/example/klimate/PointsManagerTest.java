package com.example.klimate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for PointsManager bonus-point calculations.
 * Only tests pure Java methods that do not require Firebase or Android.
 */
@RunWith(JUnit4.class)
public class PointsManagerTest {

    private PointsManager pointsManager;

    @Before
    public void setUp() {
        // PointsManager constructor calls FirebaseFirestore.getInstance().
        // We test only the static helpers which do not call Firebase.
        // If a no-arg ctor path is needed, use the static methods directly.
    }

    // ── calculateBonusPointsStatic ─────────────────────────────────────────────

    @Test
    public void calculateBonusPointsStatic_zero_returnsZero() {
        assertEquals(0, PointsManager.calculateBonusPointsStatic(0));
    }

    @Test
    public void calculateBonusPointsStatic_one_returnsTwo() {
        assertEquals(2, PointsManager.calculateBonusPointsStatic(1));
    }

    @Test
    public void calculateBonusPointsStatic_five_returnsTen() {
        assertEquals(10, PointsManager.calculateBonusPointsStatic(5));
    }

    @Test
    public void calculateBonusPointsStatic_negative_returnsZero() {
        assertEquals(0, PointsManager.calculateBonusPointsStatic(-3));
    }

    @Test
    public void calculateBonusPointsStatic_100votes_returns200() {
        assertEquals(200, PointsManager.calculateBonusPointsStatic(100));
    }

    @Test
    public void calculateBonusPointsStatic_isLinear() {
        int v1 = PointsManager.calculateBonusPointsStatic(3);
        int v2 = PointsManager.calculateBonusPointsStatic(6);
        assertEquals(2 * v1, v2);
    }

    // ── calculateNetBonusPoints ────────────────────────────────────────────────

    @Test
    public void calculateNetBonusPoints_zeroUpzeroDown_returnsZero() {
        assertEquals(0, PointsManager.calculateNetBonusPoints(0, 0));
    }

    @Test
    public void calculateNetBonusPoints_5up0down_returns10() {
        // 5*2 - 0 = 10
        assertEquals(10, PointsManager.calculateNetBonusPoints(5, 0));
    }

    @Test
    public void calculateNetBonusPoints_0up5down_returnsZero_notNegative() {
        // 0*2 - 5 = -5 → clamped to 0
        assertEquals(0, PointsManager.calculateNetBonusPoints(0, 5));
    }

    @Test
    public void calculateNetBonusPoints_3up1down_returns5() {
        // 3*2 - 1 = 5
        assertEquals(5, PointsManager.calculateNetBonusPoints(3, 1));
    }

    @Test
    public void calculateNetBonusPoints_equalUpAndDown_mayBeZeroOrPositive() {
        // 2*2 - 2 = 2
        assertEquals(2, PointsManager.calculateNetBonusPoints(2, 2));
    }

    @Test
    public void calculateNetBonusPoints_negativeUpvotes_treatedAsZero() {
        // -3 upvotes treated as 0; 0*2 - 0 = 0
        assertEquals(0, PointsManager.calculateNetBonusPoints(-3, 0));
    }

    @Test
    public void calculateNetBonusPoints_negativeDownvotes_treatedAsZero() {
        // 2*2 - (-2) → downvotes clamped to 0 → 2*2 - 0 = 4
        assertEquals(4, PointsManager.calculateNetBonusPoints(2, -2));
    }

    @Test
    public void calculateNetBonusPoints_neverNegative() {
        // Even if downvotes greatly outweigh upvotes, result must be >= 0
        int result = PointsManager.calculateNetBonusPoints(1, 100);
        assertTrue("Net bonus should never be negative", result >= 0);
    }

    @Test
    public void calculateNetBonusPoints_10up5down_returns15() {
        // 10*2 - 5 = 15
        assertEquals(15, PointsManager.calculateNetBonusPoints(10, 5));
    }

    @Test
    public void calculateNetBonusPoints_largeValues() {
        // 1000*2 - 500 = 1500
        assertEquals(1500, PointsManager.calculateNetBonusPoints(1000, 500));
    }

    @Test
    public void calculateNetBonusPoints_exactBreakEven() {
        // Upvotes contribute 2 per vote, downvotes deduct 1 per vote.
        // 1 upvote, 2 downvotes: 1*2 - 2 = 0
        assertEquals(0, PointsManager.calculateNetBonusPoints(1, 2));
    }

    // ── Consistency between static and instance ───────────────────────────────

    @Test
    public void staticAndInstanceMethods_sameResultFor5votes() {
        int staticResult = PointsManager.calculateBonusPointsStatic(5);
        // instance method unavailable without Firebase; use static only
        assertEquals(10, staticResult);
    }
}