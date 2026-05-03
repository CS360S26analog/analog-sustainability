package com.example.klimate;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BadgeDefinitionTest {

    @Test
    public void catalogue_containsExpectedMilestoneBadges() {
        assertNotNull(BadgeDefinition.findById("first_steps"));
        assertNotNull(BadgeDefinition.findById("eco_sprout"));
        assertNotNull(BadgeDefinition.findById("climate_champion"));
    }

    @Test
    public void findById_returnsNullForUnknownBadge() {
        assertNull(BadgeDefinition.findById("not_a_badge"));
    }

    @Test
    public void badgeThresholds_areInIncreasingOrder() {
        int previous = 0;

        for (BadgeDefinition badge : BadgeDefinition.BADGE_CATALOGUE) {
            assertTrue(badge.thresholdPoints >= previous);
            previous = badge.thresholdPoints;
        }
    }
}