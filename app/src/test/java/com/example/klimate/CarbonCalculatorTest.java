package com.example.klimate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CarbonCalculatorTest {

    private static final double DELTA = 0.0001;

    @Test
    public void cyclingCalculatesCo2FromQuantity() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", 10);

        assertEquals(2.49, result, DELTA);
    }

    @Test
    public void walkingCalculatesCo2FromQuantity() {
        double result = CarbonCalculator.calculateCo2SavedKg("Walked", 5);

        assertEquals(1.245, result, DELTA);
    }

    @Test
    public void publicTransitCalculatesCo2FromQuantity() {
        double result = CarbonCalculator.calculateCo2SavedKg("Public Transit", 10);

        assertEquals(1.53, result, DELTA);
    }

    @Test
    public void plantBasedMealCalculatesCo2FromMeals() {
        double result = CarbonCalculator.calculateCo2SavedKg("Plant-based meal", 2);

        assertEquals(6.4, result, DELTA);
    }

    @Test
    public void reusableCupCalculatesSmallCo2Saving() {
        double result = CarbonCalculator.calculateCo2SavedKg("Reusable cup", 3);

        assertEquals(0.051, result, DELTA);
    }

    @Test
    public void recyclingCalculatesCo2FromKg() {
        double result = CarbonCalculator.calculateCo2SavedKg("Recycling", 4);

        assertEquals(1.4, result, DELTA);
    }

    @Test
    public void compostingCalculatesCo2FromKg() {
        double result = CarbonCalculator.calculateCo2SavedKg("Composting", 4);

        assertEquals(1.4, result, DELTA);
    }

    @Test
    public void energySavingCalculatesCo2FromKwh() {
        double result = CarbonCalculator.calculateCo2SavedKg("Energy saving", 5);

        assertEquals(1.815, result, DELTA);
    }

    @Test
    public void unknownActivityReturnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("Unknown Activity", 10);

        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void nullActivityReturnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg(null, 10);

        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void zeroQuantityReturnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", 0);

        assertEquals(0.0, result, DELTA);
    }

    @Test
    public void negativeQuantityReturnsZero() {
        double result = CarbonCalculator.calculateCo2SavedKg("Cycling", -3);

        assertEquals(0.0, result, DELTA);
    }
}