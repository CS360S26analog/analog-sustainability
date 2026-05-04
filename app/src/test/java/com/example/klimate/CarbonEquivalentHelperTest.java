package com.example.klimate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for CarbonEquivalentHelper.
 * Verifies CO2 equivalence conversions (trees, driving km, flights).
 */
@RunWith(JUnit4.class)
public class CarbonEquivalentHelperTest {

    private static final double DELTA = 0.001;

    // ── treesEquivalent ───────────────────────────────────────────────────────

    @Test
    public void treesEquivalent_21kg_returns1Tree() {
        double result = CarbonEquivalentHelper.treesEquivalent(21.0);
        assertEquals(1.0, result, DELTA);
    }

    @Test
    public void treesEquivalent_42kg_returns2Trees() {
        double result = CarbonEquivalentHelper.treesEquivalent(42.0);
        assertEquals(2.0, result, DELTA);
    }

    @Test
    public void treesEquivalent_10point5kg_returnsHalfTree() {
        double result = CarbonEquivalentHelper.treesEquivalent(10.5);
        assertEquals(0.5, result, DELTA);
    }

    @Test
    public void treesEquivalent_zeroKg_returnsZero() {
        double result = CarbonEquivalentHelper.treesEquivalent(0.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void treesEquivalent_negativeKg_returnsZero() {
        double result = CarbonEquivalentHelper.treesEquivalent(-5.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void treesEquivalent_210kg_returns10Trees() {
        double result = CarbonEquivalentHelper.treesEquivalent(210.0);
        assertEquals(10.0, result, DELTA);
    }

    // ── kmDrivingEquivalent ───────────────────────────────────────────────────

    @Test
    public void kmDriving_0point18kg_returns1km() {
        double result = CarbonEquivalentHelper.kmDrivingEquivalent(0.18);
        assertEquals(1.0, result, DELTA);
    }

    @Test
    public void kmDriving_1point8kg_returns10km() {
        double result = CarbonEquivalentHelper.kmDrivingEquivalent(1.8);
        assertEquals(10.0, result, DELTA);
    }

    @Test
    public void kmDriving_18kg_returns100km() {
        double result = CarbonEquivalentHelper.kmDrivingEquivalent(18.0);
        assertEquals(100.0, result, DELTA);
    }

    @Test
    public void kmDriving_zeroKg_returnsZero() {
        double result = CarbonEquivalentHelper.kmDrivingEquivalent(0.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void kmDriving_negativeKg_returnsZero() {
        double result = CarbonEquivalentHelper.kmDrivingEquivalent(-10.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void kmDriving_largeKg_returnsLargeKm() {
        double result = CarbonEquivalentHelper.kmDrivingEquivalent(180.0);
        assertEquals(1000.0, result, DELTA);
    }

    // ── flightEquivalent ──────────────────────────────────────────────────────

    @Test
    public void flight_255kg_returnsOneFlight() {
        double result = CarbonEquivalentHelper.flightEquivalent(255.0);
        assertEquals(1.0, result, DELTA);
    }

    @Test
    public void flight_127point5kg_returnsHalfFlight() {
        double result = CarbonEquivalentHelper.flightEquivalent(127.5);
        assertEquals(0.5, result, DELTA);
    }

    @Test
    public void flight_510kg_returnsTwoFlights() {
        double result = CarbonEquivalentHelper.flightEquivalent(510.0);
        assertEquals(2.0, result, DELTA);
    }

    @Test
    public void flight_zeroKg_returnsZero() {
        double result = CarbonEquivalentHelper.flightEquivalent(0.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void flight_negativeKg_returnsZero() {
        double result = CarbonEquivalentHelper.flightEquivalent(-100.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void flight_smallAmount_returnsSmallFraction() {
        double result = CarbonEquivalentHelper.flightEquivalent(25.5);
        assertEquals(0.1, result, DELTA);
    }

    // ── buildEquivalentString ─────────────────────────────────────────────────

    @Test
    public void buildEquivalentString_zeroKg_returnsEmpty() {
        String result = CarbonEquivalentHelper.buildEquivalentString(0.0);
        assertEquals("", result);
    }

    @Test
    public void buildEquivalentString_negativeKg_returnsEmpty() {
        String result = CarbonEquivalentHelper.buildEquivalentString(-5.0);
        assertEquals("", result);
    }

    @Test
    public void buildEquivalentString_positiveKg_containsTree() {
        String result = CarbonEquivalentHelper.buildEquivalentString(21.0);
        assertTrue("Should contain tree emoji", result.contains("🌳"));
        assertFalse("Should not be empty", result.isEmpty());
    }

    @Test
    public void buildEquivalentString_positiveKg_containsKmDriving() {
        String result = CarbonEquivalentHelper.buildEquivalentString(21.0);
        assertTrue("Should mention km driving", result.contains("km driving avoided"));
    }

    @Test
    public void buildEquivalentString_smallKg_showsLessThanPoint1Trees() {
        // 1 kg => trees = 1/21 ≈ 0.048, which is < 0.1
        String result = CarbonEquivalentHelper.buildEquivalentString(1.0);
        assertTrue("Should show < 0.1 trees for small amount",
                result.contains("< 0.1 trees"));
    }

    @Test
    public void buildEquivalentString_210kg_shows10Trees() {
        // 210 kg => 10.0 trees (exactly 10, no decimal)
        String result = CarbonEquivalentHelper.buildEquivalentString(210.0);
        assertTrue("Should show 10 trees", result.contains("10 trees"));
    }

    @Test
    public void buildEquivalentString_contains_middot_separator() {
        String result = CarbonEquivalentHelper.buildEquivalentString(21.0);
        assertTrue("Should contain separator", result.contains("·"));
    }

    @Test
    public void buildEquivalentString_startsWithApprox() {
        String result = CarbonEquivalentHelper.buildEquivalentString(5.0);
        assertTrue("Should start with ≈", result.startsWith("≈"));
    }

    @Test
    public void buildEquivalentString_smallKmDriving_showsLessThan1km() {
        // 0.1 kg => 0.1/0.18 ≈ 0.556 km, which is < 1
        String result = CarbonEquivalentHelper.buildEquivalentString(0.1);
        assertTrue("Should show < 1 km for tiny amount",
                result.contains("< 1 km driving avoided"));
    }

    @Test
    public void buildEquivalentString_largeKm_formatsWithComma() {
        // 1000 km threshold: need kgCo2 = 1000 * 0.18 = 180 kg → km = 1000
        // The format is "%.0f,000 km driving avoided" applied to km/1000
        String result = CarbonEquivalentHelper.buildEquivalentString(200.0);
        // 200/0.18 ≈ 1111 km → >= 1000, so uses the large format
        assertTrue("Large km should be formatted in thousands",
                result.contains("km driving avoided"));
    }

    // ── Proportionality checks ────────────────────────────────────────────────

    @Test
    public void treesEquivalent_isProportional() {
        double single = CarbonEquivalentHelper.treesEquivalent(21.0);
        double double_ = CarbonEquivalentHelper.treesEquivalent(42.0);
        assertEquals(double_, 2 * single, DELTA);
    }

    @Test
    public void kmDrivingEquivalent_isProportional() {
        double single = CarbonEquivalentHelper.kmDrivingEquivalent(1.0);
        double triple = CarbonEquivalentHelper.kmDrivingEquivalent(3.0);
        assertEquals(triple, 3 * single, DELTA);
    }

    @Test
    public void flightEquivalent_isProportional() {
        double single = CarbonEquivalentHelper.flightEquivalent(255.0);
        double double_ = CarbonEquivalentHelper.flightEquivalent(510.0);
        assertEquals(double_, 2 * single, DELTA);
    }
}