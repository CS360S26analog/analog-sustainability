/**
 * LumsAlternatives.java
 *
 * Provides hardcoded sustainable alternatives available at common LUMS campus
 * locations. EcoPicksFragment reads this static data and lets students choose
 * an item that can pre-fill LogFragment with the matching activity type.
 *
 * Role in design: Model/helper data source for the Eco Picks feature.
 *
 * Outstanding issues: Locations and items are currently hardcoded for the demo.
 *
 * @author Izza
 */
package com.example.klimate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LumsAlternatives {

    private LumsAlternatives() {
        // Utility class; do not instantiate.
    }

    /**
     * Represents one sustainable action available at a campus location.
     */
    public static class EcoItem {
        private final String title;
        private final String description;
        private final String activityType;

        /**
         * Creates one Eco Pick item.
         *
         * @param title short title shown to the user
         * @param description explanation of the sustainable action
         * @param activityType activity type used to pre-fill LogFragment
         */
        public EcoItem(String title, String description, String activityType) {
            this.title = title;
            this.description = description;
            this.activityType = activityType;
        }

        /**
         * Returns the item title.
         *
         * @return item title
         */
        public String getTitle() {
            return title;
        }

        /**
         * Returns the item description.
         *
         * @return item description
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns the activity type connected to this item.
         *
         * @return activity type for LogFragment
         */
        public String getActivityType() {
            return activityType;
        }
    }

    /**
     * Represents a LUMS location that has one or more sustainable alternatives.
     */
    public static class EcoLocation {
        private final String locationName;
        private final String block;
        private final List<EcoItem> items;

        /**
         * Creates one location card for Eco Picks.
         *
         * @param locationName display name of the campus location
         * @param block short area or block label
         * @param items sustainable actions available at this location
         */
        public EcoLocation(String locationName, String block, List<EcoItem> items) {
            this.locationName = locationName;
            this.block = block;
            this.items = items;
        }

        /**
         * Returns the campus location name.
         *
         * @return location name
         */
        public String getLocationName() {
            return locationName;
        }

        /**
         * Returns the block or campus area.
         *
         * @return block label
         */
        public String getBlock() {
            return block;
        }

        /**
         * Returns the sustainable alternatives available at this location.
         *
         * @return immutable list of EcoItem objects
         */
        public List<EcoItem> getItems() {
            return Collections.unmodifiableList(items);
        }
    }

    /**
     * Returns the hardcoded Eco Picks catalogue for LUMS.
     *
     * @return list of EcoLocation objects
     */
    public static List<EcoLocation> getLocations() {
        List<EcoLocation> locations = new ArrayList<>();

        List<EcoItem> cStoreItems = new ArrayList<>();
        cStoreItems.add(new EcoItem(
                "Bring your own bag",
                "Skip a plastic bag when buying snacks or supplies.",
                "Reusable bag"
        ));
        locations.add(new EcoLocation("C-Store", "PDC", cStoreItems));

        List<EcoItem> cafeteriaItems = new ArrayList<>();
        cafeteriaItems.add(new EcoItem(
                "Choose a plant-based meal",
                "Pick a lower-carbon meal option at the cafeteria.",
                "Plant-based meal"
        ));
        locations.add(new EcoLocation("Main Cafeteria", "PDC", cafeteriaItems));

        List<EcoItem> foodCourtItems = new ArrayList<>();
        foodCourtItems.add(new EcoItem(
                "Recycle your cardboard cup",
                "Use the correct recycling bin after finishing your drink.",
                "Recycling"
        ));
        locations.add(new EcoLocation("Food Court", "Sector D", foodCourtItems));

        List<EcoItem> campusItems = new ArrayList<>();
        campusItems.add(new EcoItem(
                "Walk the academic block",
                "Choose walking instead of a short car or bike ride.",
                "Walked"
        ));
        locations.add(new EcoLocation("Campus-wide Transport", "Campus", campusItems));

        return locations;
    }
}