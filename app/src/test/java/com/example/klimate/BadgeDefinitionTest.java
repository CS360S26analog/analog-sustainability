package com.example.klimate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for BadgeDefinition — catalogue integrity and lookup logic.
 */
@RunWith(JUnit4.class)
public class BadgeDefinitionTest {

    // ── BADGE_CATALOGUE ───────────────────────────────────────────────────────

    @Test
    public void badgeCatalogue_notNull() {
        assertNotNull(BadgeDefinition.BADGE_CATALOGUE);
    }

    @Test
    public void badgeCatalogue_hasExpected5Badges() {
        assertEquals(5, BadgeDefinition.BADGE_CATALOGUE.length);
    }

    @Test
    public void badgeCatalogue_firstBadgeIs_firstSteps() {
        assertEquals("first_steps", BadgeDefinition.BADGE_CATALOGUE[0].id);
    }

    @Test
    public void badgeCatalogue_lastBadgeIs_climateChampion() {
        int last = BadgeDefinition.BADGE_CATALOGUE.length - 1;
        assertEquals("climate_champion", BadgeDefinition.BADGE_CATALOGUE[last].id);
    }

    @Test
    public void badgeCatalogue_pointsAreAscending() {
        for (int i = 1; i < BadgeDefinition.BADGE_CATALOGUE.length; i++) {
            assertTrue("Badge thresholds should be ascending",
                    BadgeDefinition.BADGE_CATALOGUE[i].thresholdPoints
                            > BadgeDefinition.BADGE_CATALOGUE[i - 1].thresholdPoints);
        }
    }

    @Test
    public void badgeCatalogue_noNullIds() {
        for (BadgeDefinition badge : BadgeDefinition.BADGE_CATALOGUE) {
            assertNotNull("Badge id should not be null", badge.id);
            assertFalse("Badge id should not be empty", badge.id.isEmpty());
        }
    }

    @Test
    public void badgeCatalogue_noNullNames() {
        for (BadgeDefinition badge : BadgeDefinition.BADGE_CATALOGUE) {
            assertNotNull("Badge name should not be null", badge.name);
        }
    }

    @Test
    public void badgeCatalogue_noNullEmojis() {
        for (BadgeDefinition badge : BadgeDefinition.BADGE_CATALOGUE) {
            assertNotNull("Badge emoji should not be null", badge.emoji);
            assertFalse("Badge emoji should not be empty", badge.emoji.isEmpty());
        }
    }

    @Test
    public void badgeCatalogue_noNullDescriptions() {
        for (BadgeDefinition badge : BadgeDefinition.BADGE_CATALOGUE) {
            assertNotNull("Badge description should not be null", badge.description);
        }
    }

    @Test
    public void badgeCatalogue_allThresholdsPositive() {
        for (BadgeDefinition badge : BadgeDefinition.BADGE_CATALOGUE) {
            assertTrue("Threshold points must be positive", badge.thresholdPoints > 0);
        }
    }

    @Test
    public void badgeCatalogue_firstSteps_threshold100() {
        BadgeDefinition firstSteps = BadgeDefinition.BADGE_CATALOGUE[0];
        assertEquals(100, firstSteps.thresholdPoints);
    }

    @Test
    public void badgeCatalogue_climateChampion_threshold2500() {
        BadgeDefinition last = BadgeDefinition.BADGE_CATALOGUE[BadgeDefinition.BADGE_CATALOGUE.length - 1];
        assertEquals(2500, last.thresholdPoints);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    public void findById_firstSteps_returnsCorrectBadge() {
        BadgeDefinition badge = BadgeDefinition.findById("first_steps");
        assertNotNull(badge);
        assertEquals("first_steps", badge.id);
        assertEquals("First Steps", badge.name);
    }

    @Test
    public void findById_recycler_returnsCorrectBadge() {
        BadgeDefinition badge = BadgeDefinition.findById("recycler");
        assertNotNull(badge);
        assertEquals(200, badge.thresholdPoints);
    }

    @Test
    public void findById_ecoSprout_returnsCorrectBadge() {
        BadgeDefinition badge = BadgeDefinition.findById("eco_sprout");
        assertNotNull(badge);
        assertEquals(500, badge.thresholdPoints);
    }

    @Test
    public void findById_greenGuardian_returnsCorrectBadge() {
        BadgeDefinition badge = BadgeDefinition.findById("green_guardian");
        assertNotNull(badge);
        assertEquals(1000, badge.thresholdPoints);
    }

    @Test
    public void findById_climateChampion_returnsCorrectBadge() {
        BadgeDefinition badge = BadgeDefinition.findById("climate_champion");
        assertNotNull(badge);
        assertEquals(2500, badge.thresholdPoints);
    }

    @Test
    public void findById_unknownId_returnsNull() {
        BadgeDefinition badge = BadgeDefinition.findById("nonexistent_badge");
        assertNull(badge);
    }

    @Test
    public void findById_null_returnsNull() {
        BadgeDefinition badge = BadgeDefinition.findById(null);
        assertNull(badge);
    }

    @Test
    public void findById_emptyString_returnsNull() {
        BadgeDefinition badge = BadgeDefinition.findById("");
        assertNull(badge);
    }

    @Test
    public void findById_wrongCase_returnsNull() {
        BadgeDefinition badge = BadgeDefinition.findById("First_Steps");
        assertNull("IDs are case-sensitive", badge);
    }

    @Test
    public void findById_allCatalogueIdsFound() {
        for (BadgeDefinition expected : BadgeDefinition.BADGE_CATALOGUE) {
            BadgeDefinition found = BadgeDefinition.findById(expected.id);
            assertNotNull("Should find badge with id: " + expected.id, found);
            assertEquals(expected.id, found.id);
        }
    }

    // ── Constructor / field access ────────────────────────────────────────────

    @Test
    public void constructor_setsAllFields() {
        BadgeDefinition badge = new BadgeDefinition(
                "test_id", "Test Badge", "🏅", 300, "Earn 300 points.");
        assertEquals("test_id", badge.id);
        assertEquals("Test Badge", badge.name);
        assertEquals("🏅", badge.emoji);
        assertEquals(300, badge.thresholdPoints);
        assertEquals("Earn 300 points.", badge.description);
    }
}