/**
 * CarbonEquivalentHelper.java
 *
 * Utility class for converting kg of CO₂ saved into relatable
 * real-world equivalents. Used by HomeFragment and ProfileFragment
 * to display human-readable impact figures.
 *
 * Role in design: Stateless helper (no design pattern needed).
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

public class CarbonEquivalentHelper {

    // 1 tree absorbs approx 21 kg CO₂ per year
    private static final double KG_PER_TREE_PER_YEAR = 21.0;

    // Average car emits approx 0.18 kg CO₂ per km
    private static final double KG_PER_KM_DRIVING = 0.18;

    // A Lahore↔Karachi flight emits approx 255 kg CO₂ per passenger
    private static final double KG_PER_LHE_KHI_FLIGHT = 255.0;

    /**
     * Returns how many trees would absorb this much CO₂ in a year.
     *
     * @param kgCo2 kg of CO₂ saved
     * @return equivalent number of trees (can be fractional)
     */
    public static double treesEquivalent(double kgCo2) {
        if (kgCo2 <= 0) return 0;
        return kgCo2 / KG_PER_TREE_PER_YEAR;
    }

    /**
     * Returns how many km of car driving this CO₂ saving represents.
     *
     * @param kgCo2 kg of CO₂ saved
     * @return equivalent km of driving avoided
     */
    public static double kmDrivingEquivalent(double kgCo2) {
        if (kgCo2 <= 0) return 0;
        return kgCo2 / KG_PER_KM_DRIVING;
    }

    /**
     * Returns what fraction of a Lahore↔Karachi flight this represents.
     *
     * @param kgCo2 kg of CO₂ saved
     * @return fraction of a flight (e.g. 0.5 = half a flight)
     */
    public static double flightEquivalent(double kgCo2) {
        if (kgCo2 <= 0) return 0;
        return kgCo2 / KG_PER_LHE_KHI_FLIGHT;
    }

    /**
     * Builds a short display string with the most meaningful equivalent
     * for the given CO₂ amount. Automatically picks the right unit
     * so the number is never awkwardly tiny or huge.
     *
     * @param kgCo2 kg of CO₂ saved
     * @return formatted string e.g. "≈ 0.9 trees 🌳 · ≈ 102 km driving avoided"
     */
    public static String buildEquivalentString(double kgCo2) {
        if (kgCo2 <= 0) return "";

        double trees = treesEquivalent(kgCo2);
        double km    = kmDrivingEquivalent(kgCo2);

        String treeStr;
        if (trees < 0.1) {
            treeStr = "< 0.1 trees 🌳";
        } else if (trees >= 10) {
            treeStr = String.format("%.0f trees 🌳", trees);
        } else {
            treeStr = String.format("%.1f trees 🌳", trees);
        }

        String kmStr;
        if (km < 1) {
            kmStr = "< 1 km driving avoided";
        } else if (km >= 1000) {
            kmStr = String.format("%.0f,000 km driving avoided", km / 1000);
        } else {
            kmStr = String.format("%.0f km driving avoided", km);
        }

        return "≈ " + treeStr + "  ·  ≈ " + kmStr;
    }
}