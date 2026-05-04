package com.example.klimate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for CarbonCalculator.
 * Tests CO2 savings calculations for all supported activity types.
 */
@RunWith(JUnit4.class)
public class CarbonCalculatorTest {

    private static final double DELTA = 0.0001;

    // ── Walked ────────────────────────────────────────────────────────────────

    @Test
    public void walked_1km_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Walked", 1.0);
        assertEquals(0.249, result, DELTA);
    }

    @Test
    public void walked_10km_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Walked", 10.0);
        assertEquals(2.49, result, DELTA);
    }

    @Test
    public void walked_0km_returnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("Walked", 0.0);
        assertEquals(0.0, result, DELTA);
    }

    // ── Cycling ───────────────────────────────────────────────────────────────

    @Test
    public void cycling_1km_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", 1.0);
        assertEquals(0.249, result, DELTA);
    }

    @Test
    public void cycling_5km_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", 5.0);
        assertEquals(1.245, result, DELTA);
    }

    @Test
    public void cycling_largeDistance_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", 100.0);
        assertEquals(24.9, result, DELTA);
    }

    // ── Public Transit ────────────────────────────────────────────────────────

    @Test
    public void publicTransit_1km_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Public Transit", 1.0);
        assertEquals(0.153, result, DELTA);
    }

    @Test
    public void publicTransit_20km_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Public Transit", 20.0);
        assertEquals(3.06, result, DELTA);
    }

    // ── Plant-based meal ──────────────────────────────────────────────────────

    @Test
    public void plantBasedMeal_1meal_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Plant-based meal", 1.0);
        assertEquals(3.200, result, DELTA);
    }

    @Test
    public void plantBasedMeal_3meals_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Plant-based meal", 3.0);
        assertEquals(9.600, result, DELTA);
    }

    // ── Reusable cup ──────────────────────────────────────────────────────────

    @Test
    public void reusableCup_1use_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Reusable cup", 1.0);
        assertEquals(0.017, result, DELTA);
    }

    @Test
    public void reusableCup_5uses_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Reusable cup", 5.0);
        assertEquals(0.085, result, DELTA);
    }

    // ── Recycling ─────────────────────────────────────────────────────────────

    @Test
    public void recycling_1kg_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Recycling", 1.0);
        assertEquals(0.350, result, DELTA);
    }

    @Test
    public void recycling_10kg_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Recycling", 10.0);
        assertEquals(3.50, result, DELTA);
    }

    // ── Composting ────────────────────────────────────────────────────────────

    @Test
    public void composting_1kg_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Composting", 1.0);
        assertEquals(0.350, result, DELTA);
    }

    @Test
    public void composting_5kg_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Composting", 5.0);
        assertEquals(1.750, result, DELTA);
    }

    // ── Energy saving ─────────────────────────────────────────────────────────

    @Test
    public void energySaving_1kwh_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Energy saving", 1.0);
        assertEquals(0.363, result, DELTA);
    }

    @Test
    public void energySaving_10kwh_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Energy saving", 10.0);
        assertEquals(3.63, result, DELTA);
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    public void nullActivityType_returnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg(null, 5.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void negativeQuantity_returnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", -1.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void zeroQuantity_returnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("Recycling", 0.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void unknownActivityType_returnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("Flying a Kite", 5.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void emptyActivityType_returnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("", 5.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void fractionalQuantity_cycling_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", 0.5);
        assertEquals(0.1245, result, DELTA);
    }

    @Test
    public void veryLargeQuantity_returnsCorrectCo2() {
        double result = CarbonCalculator.calculateCo2SavedKg("Walked", 1000.0);
        assertEquals(249.0, result, DELTA);
    }

    @Test
    public void cyclingAndWalked_sameCo2PerKm() {
        double cycling = CarbonCalculator.calculateCo2SavedKg("Cycling", 1.0);
        double walked  = CarbonCalculator.calculateCo2SavedKg("Walked", 1.0);
        assertEquals(cycling, walked, DELTA);
    }

    @Test
    public void recyclingAndComposting_sameCo2PerKg() {
        double recycling  = CarbonCalculator.calculateCo2SavedKg("Recycling", 1.0);
        double composting = CarbonCalculator.calculateCo2SavedKg("Composting", 1.0);
        assertEquals(recycling, composting, DELTA);
    }

    @Test
    public void plantBasedMeal_highestCo2PerUnit() {
        double plantMeal = CarbonCalculator.calculateCo2SavedKg("Plant-based meal", 1.0);
        double cycling   = CarbonCalculator.calculateCo2SavedKg("Cycling", 1.0);
        assertTrue("Plant-based meal should save more CO2 per unit than cycling 1km",
                plantMeal > cycling);
    }

    @Test
    public void caseMatters_wrongCase_returnsZero() {
        // Activity types are case-sensitive in the map
        double result = CarbonCalculator.calculateCo2SavedKg("cycling", 5.0);
        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void caseMatters_correctCase_returnsNonZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", 5.0);
        assertTrue(result > 0.0);
    }
}