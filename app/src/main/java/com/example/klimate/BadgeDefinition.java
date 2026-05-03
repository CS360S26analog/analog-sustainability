package com.example.klimate;

public class BadgeDefinition {
    public final String id;
    public final String name;
    public final String emoji;
    public final int thresholdPoints;
    public final String description;

    public BadgeDefinition(String id, String name, String emoji, int thresholdPoints, String description) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.thresholdPoints = thresholdPoints;
        this.description = description;
    }

    public static final BadgeDefinition[] BADGE_CATALOGUE = {
            new BadgeDefinition("first_steps", "First Steps", "🌱", 100, "Earn 100 points."),
            new BadgeDefinition("recycler", "Recycler", "♻️", 200, "Earn 200 points."),
            new BadgeDefinition("eco_sprout", "Eco Sprout", "🌿", 500, "Earn 500 points."),
            new BadgeDefinition("green_guardian", "Green Guardian", "🛡️", 1000, "Earn 1000 points."),
            new BadgeDefinition("climate_champion", "Climate Champion", "🏆", 2500, "Earn 2500 points.")
    };

    public static BadgeDefinition findById(String id) {
        for (BadgeDefinition badge : BADGE_CATALOGUE) {
            if (badge.id.equals(id)) {
                return badge;
            }
        }
        return null;
    }
}