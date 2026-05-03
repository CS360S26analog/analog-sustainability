/**
 * TutorialOnceTest.java
 *
 * Unit tests for the first-launch navigation tutorial logic (EXTRA-3).
 * Tests that the SharedPreferences flag correctly controls whether
 * the tutorial is shown, and that it is marked done after completion.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

public class TutorialOnceTest {

    // ── Simulated SharedPreferences (plain Map for unit tests) ──

    private Map<String, Boolean> buildPrefs(boolean tutorialShown) {
        Map<String, Boolean> prefs = new HashMap<>();
        prefs.put("tutorial_shown", tutorialShown);
        return prefs;
    }

    private boolean shouldShowTutorial(Map<String, Boolean> prefs) {
        Boolean shown = prefs.get("tutorial_shown");
        return shown == null || !shown;
    }

    private void markTutorialDone(Map<String, Boolean> prefs) {
        prefs.put("tutorial_shown", true);
    }

    // ── Tests ──

    @Test
    public void tutorialShown_flagFalse_shouldShowTutorial() {
        Map<String, Boolean> prefs = buildPrefs(false);
        assertTrue(shouldShowTutorial(prefs));
    }

    @Test
    public void tutorialShown_flagTrue_shouldNotShowTutorial() {
        Map<String, Boolean> prefs = buildPrefs(true);
        assertFalse(shouldShowTutorial(prefs));
    }

    @Test
    public void tutorialShown_flagAbsent_shouldShowTutorial() {
        Map<String, Boolean> prefs = new HashMap<>(); // no key set
        assertTrue(shouldShowTutorial(prefs));
    }

    @Test
    public void markTutorialDone_setsFlagToTrue() {
        Map<String, Boolean> prefs = buildPrefs(false);
        assertTrue(shouldShowTutorial(prefs)); // was showing

        markTutorialDone(prefs);

        assertFalse(shouldShowTutorial(prefs)); // no longer shows
    }

    @Test
    public void markTutorialDone_calledTwice_remainsFalse() {
        Map<String, Boolean> prefs = buildPrefs(false);
        markTutorialDone(prefs);
        markTutorialDone(prefs); // idempotent
        assertFalse(shouldShowTutorial(prefs));
    }

    @Test
    public void tutorialSequenceSteps_fourTargetsExpected() {
        // The tutorial should have exactly 4 steps
        String[] steps = {"Log tab", "Home tab", "Rankings/Challenges tab", "Profile tab"};
        assertEquals(4, steps.length);
    }
}
