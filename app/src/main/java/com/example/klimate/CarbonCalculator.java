package com.example.klimate;

import java.util.HashMap;
import java.util.Map;
/**
 * CarbonCalculator.java
 *
 * Utility class for calculating estimated co2 saved from sustainability activities.
 *
 * @author Karar
 */

public class CarbonCalculator
{
    private static final Map<String, Double> CO2_PER_UNIT_KG = new HashMap<>();

    static
    {
        // source epa average passenger car emits about 400 g co2 per mile
        // assumption 1 km not driven saves 400 divided by 1 point 609 grams
        // result 0 point 249 kg co2 saved per km
        CO2_PER_UNIT_KG.put("Walked", 0.249);
        CO2_PER_UNIT_KG.put("Cycling", 0.249);

        // source epa car factor is 0 point 249 kg per km
        // source uk government local bus factor is about 0 point 0965 kg co2e per passenger km
        // assumption public transit replaces same distance by car
        // result 0 point 249 minus 0 point 0965 equals 0 point 1525 kg co2e saved per km
        CO2_PER_UNIT_KG.put("Public Transit", 0.153);

        // source cop24 menu analysis says meat meal average is 4 point 1 kg co2e
        // source same analysis says plant based meal average is 0 point 90 kg co2e
        // assumption one plant based meal replaces one meat based meal
        // result 4 point 1 minus 0 point 90 equals 3 point 2 kg co2e saved per meal
        CO2_PER_UNIT_KG.put("Plant-based meal", 3.200);

        // source scottish government assessment says reusable cup is 4 point 6 kg co2e over 500 uses
        // source recoup summary says disposable cup packaging is about 4 percent of 0 point 64 kg co2e hot latte
        // assumption avoided disposable cup packaging is about 0 point 026 kg co2e per use
        // assumption reusable cup impact per use is 4 point 6 divided by 500 equals 0 point 009 kg co2e
        // result 0 point 026 minus 0 point 009 equals about 0 point 017 kg co2e saved per use
        CO2_PER_UNIT_KG.put("Reusable cup", 0.017);

        // source epa warm compares recycling against landfilling
        // source iclei protocol based on epa warm gives mixed recyclables reduction around 0 point 35 mtco2e per ton
        // assumption quantity is kg of mixed recyclables
        // result 0 point 35 kg co2e saved per kg
        CO2_PER_UNIT_KG.put("Recycling", 0.350);

        // source epa warm food waste landfilling is about 0 point 50 mtco2e per short ton
        // source epa warm food waste composting is about 0 point 15 mtco2e per short ton
        // assumption quantity is kg of food waste composted instead of landfilled
        // result difference is about 0 point 35 kg co2e per kg
        CO2_PER_UNIT_KG.put("Composting", 0.350);

        // source university of michigan says us electricity emits about 0 point 8 lb co2e per kwh
        // assumption quantity is kwh saved
        // result 0 point 8 lb equals about 0 point 363 kg co2e saved per kwh
        CO2_PER_UNIT_KG.put("Energy saving", 0.363);
    }

    public static double calculateCo2SavedKg(String activityType, double quantity)
    {
        if (activityType == null || quantity <= 0)
        {
            return 0.0;
        }

        Double co2PerUnit = CO2_PER_UNIT_KG.get(activityType);

        if (co2PerUnit == null)
        {
            return 0.0;
        }

        return co2PerUnit * quantity;
    }
}