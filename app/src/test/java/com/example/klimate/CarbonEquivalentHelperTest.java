package com.example.klimate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CarbonEquivalentHelperTest {

    private static final double DELTA = 0.0001;

    @Test
    public void zeroCo2ReturnsZeroTrees() {
        assertEquals(0.0, CarbonEquivalentHelper.treesEquivalent(0), DELTA);
    }

    @Test
    public void negativeCo2ReturnsZeroTrees() {
        assertEquals(0.0, CarbonEquivalentHelper.treesEquivalent(-5), DELTA);
    }

    @Test
    public void twentyOneKgEqualsOneTreePerYear() {
        assertEquals(1.0, CarbonEquivalentHelper.treesEquivalent(21.0), DELTA);
    }

    @Test
    public void eighteenKgEqualsHundredKmDrivingAvoided() {
        assertEquals(100.0, CarbonEquivalentHelper.kmDrivingEquivalent(18.0), DELTA);
    }

    @Test
    public void twoHundredFiftyFiveKgEqualsOneLahoreKarachiFlight() {
        assertEquals(1.0, CarbonEquivalentHelper.flightEquivalent(255.0), DELTA);
    }

    @Test
    public void zeroCo2BuildsEmptyEquivalentString() {
        assertEquals("", CarbonEquivalentHelper.buildEquivalentString(0));
    }

    @Test
    public void smallCo2BuildsReadableLowImpactString() {
        String result = CarbonEquivalentHelper.buildEquivalentString(0.09);

        assertTrue(result.contains("< 0.1 trees"));
        assertTrue(result.contains("< 1 km driving avoided"));
    }

    @Test
    public void normalCo2BuildsTreeAndDrivingString() {
        String result = CarbonEquivalentHelper.buildEquivalentString(21.0);

        assertTrue(result.contains("1.0 trees"));
        assertTrue(result.contains("117 km driving avoided"));
    }

    @Test
    public void largeCo2UsesRoundedTreeAndThousandsDrivingFormat() {
        String result = CarbonEquivalentHelper.buildEquivalentString(210.0);

        assertTrue(result.contains("10 trees"));
        assertTrue(result.contains("1,000 km driving avoided"));
    }
}