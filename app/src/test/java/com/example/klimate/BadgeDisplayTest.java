package com.example.klimate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BadgeDisplayTest {

    private static class BadgeRule {
        String id;
        long requiredPoints;
        long requiredStreakDays;

        BadgeRule(String id, long requiredPoints, long requiredStreakDays) {
            this.id = id;
            this.requiredPoints = requiredPoints;
            this.requiredStreakDays = requiredStreakDays;
        }
    }

    private boolean isBadgeEarned(BadgeRule badge,
                                  Set<String> earnedBadgeIds,
                                  long totalPoints,
                                  long streakDays) {
        String normalizedId = normalizeBadgeId(badge.id);

        if (earnedBadgeIds.contains(normalizedId)) {
            return true;
        }

        if (badge.requiredPoints > 0 && totalPoints >= badge.requiredPoints) {
            return true;
        }

        return badge.requiredStreakDays > 0 && streakDays >= badge.requiredStreakDays;
    }

    private String normalizeBadgeId(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.getDefault()).replaceAll("[^a-z0-9]", "");
    }

    @Test
    public void userWithEnoughPointsUnlocksPointBadges() {
        Set<String> earned = new HashSet<>();

        BadgeRule firstSteps = new BadgeRule("first_steps", 100, 0);
        BadgeRule recycler = new BadgeRule("recycler", 200, 0);
        BadgeRule ecoSprout = new BadgeRule("eco_sprout", 500, 0);

        assertTrue(isBadgeEarned(firstSteps, earned, 260, 0));
        assertTrue(isBadgeEarned(recycler, earned, 260, 0));
        assertFalse(isBadgeEarned(ecoSprout, earned, 260, 0));
    }

    @Test
    public void userWithEnoughStreakUnlocksStreakBadge() {
        Set<String> earned = new HashSet<>();

        BadgeRule streak7 = new BadgeRule("streak_7", 0, 7);
        BadgeRule streak30 = new BadgeRule("streak_30", 0, 30);

        assertTrue(isBadgeEarned(streak7, earned, 0, 8));
        assertFalse(isBadgeEarned(streak30, earned, 0, 8));
    }

    @Test
    public void firestoreBadgeDocAlsoUnlocksBadge() {
        Set<String> earned = new HashSet<>();
        earned.add(normalizeBadgeId("cat_bff"));

        BadgeRule catBff = new BadgeRule("cat_bff", 0, 0);

        assertTrue(isBadgeEarned(catBff, earned, 0, 0));
    }
}