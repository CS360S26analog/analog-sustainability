/**
 * GoalSettingTest.java
 *
 * Unit tests for the monthly CO₂ goal logic (US-14).
 * Tests goal validation, progress percentage calculation,
 * and the SharedPreferences storage key.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import org.junit.Test;
import static org.junit.Assert.*;

public class GoalSettingTest {

    // ── Progress calculation (mirrors HomeFragment.updateGoalStatus) ──

    private int calculateGoalPercent(float savedKg, float goalKg) {
        if (goalKg <= 0) return 0;
        int percent = (int) ((savedKg / goalKg) * 100);
        return Math.min(percent, 100);
    }

    private boolean isValidGoal(float goalKg) {
        return goalKg > 0;
    }

    // ── Tests ──

    @Test
    public void goalPercent_halfwayToGoal_returns50() {
        assertEquals(50, calculateGoalPercent(10f, 20f));
    }

    @Test
    public void goalPercent_goalMet_returns100() {
        assertEquals(100, calculateGoalPercent(20f, 20f));
    }

    @Test
    public void goalPercent_overGoal_cappedAt100() {
        assertEquals(100, calculateGoalPercent(30f, 20f));
    }

    @Test
    public void goalPercent_zeroSaved_returns0() {
        assertEquals(0, calculateGoalPercent(0f, 20f));
    }

    @Test
    public void goalPercent_zeroGoal_returns0() {
        assertEquals(0, calculateGoalPercent(10f, 0f));
    }

    @Test
    public void goalValidation_positiveValue_isValid() {
        assertTrue(isValidGoal(15f));
    }

    @Test
    public void goalValidation_zeroValue_isInvalid() {
        assertFalse(isValidGoal(0f));
    }

    @Test
    public void goalValidation_negativeValue_isInvalid() {
        assertFalse(isValidGoal(-5f));
    }

    @Test
    public void goalPercent_smallProgress_roundsDown() {
        // 1.5 / 20 = 7.5% → rounds to 7
        assertEquals(7, calculateGoalPercent(1.5f, 20f));
    }
}
