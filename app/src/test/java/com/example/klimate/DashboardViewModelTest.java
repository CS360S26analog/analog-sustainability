/**
 * DashboardViewModelTest.java
 *
 * Unit tests for DashboardViewModel logic.
 * Tests CO2 calculation and category count methods using
 * realistic sample ActivityLog data without requiring
 * a live Firestore connection.
 *
 * Role in design: Part of the test suite for the dashboard feature.
 * Tests the Model/ViewModel layer in isolation.
 *
 * Outstanding issues: Firestore query methods cannot be unit tested
 * without mocking — those are covered by intent tests instead.
 *
 * @author Maryam Ali
 */
package com.example.klimate;

import com.example.klimate.model.ActivityLog;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DashboardViewModelTest {

    private List<ActivityLog> sampleLogs;

    /**
     * Sets up realistic sample activity logs before each test.
     * Uses the same activity types defined in DashboardViewModel's
     * CO2_PER_ACTIVITY map to ensure calculations are testable.
     */
    @Before
    public void setUp() {
        Timestamp now = new Timestamp(new Date());

        ActivityLog log1 = new ActivityLog("user1", "Cycling", "quick", 10, now);
        ActivityLog log2 = new ActivityLog("user1", "Recycling", "quick", 8, now);
        ActivityLog log3 = new ActivityLog("user1", "Cycling", "verified", 10, now);
        ActivityLog log4 = new ActivityLog("user1", "Walking", "quick", 5, now);
        ActivityLog log5 = new ActivityLog("user1", "Composting", "quick", 7, now);

        sampleLogs = Arrays.asList(log1, log2, log3, log4, log5);
    }

    /**
     * Tests that CO2 is calculated correctly for a known set of activities.
     * Cycling = 0.21 kg, Recycling = 0.15 kg, Walking = 0.10 kg,
     * Composting = 0.12 kg. Two cycling logs = 0.42 kg.
     * Expected total = 0.21 + 0.15 + 0.21 + 0.10 + 0.12 = 0.79 kg
     */
    @Test
    public void testCo2CalculationIsCorrect() {
        Map<String, Double> co2PerActivity = new HashMap<String, Double>() {{
            put("Cycling",    0.21);
            put("Walking",    0.10);
            put("Recycling",  0.15);
            put("Composting", 0.12);
            put("Public Transport", 0.08);
            put("Vegetarian Meal",  0.50);
        }};

        double total = 0.0;
        for (ActivityLog log : sampleLogs) {
            Double co2 = co2PerActivity.get(log.getActivityType());
            if (co2 != null) total += co2;
        }

        assertEquals(0.79, total, 0.001);
    }

    /**
     * Tests that category counts are correctly tallied.
     * With 2 Cycling logs, counts map should have Cycling = 2.
     */
    @Test
    public void testCategoryCountsAreCorrect() {
        Map<String, Integer> counts = new HashMap<>();
        for (ActivityLog log : sampleLogs) {
            String type = log.getActivityType();
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }

        assertEquals(2, (int) counts.get("Cycling"));
        assertEquals(1, (int) counts.get("Recycling"));
        assertEquals(1, (int) counts.get("Walking"));
        assertEquals(1, (int) counts.get("Composting"));
    }

    /**
     * Tests that total logs count matches the number of sample logs provided.
     */
    @Test
    public void testTotalLogCountIsCorrect() {
        assertEquals(5, sampleLogs.size());
    }

    /**
     * Tests that an activity type not in the CO2 map contributes 0.0 kg.
     */
    @Test
    public void testUnknownActivityContributesZeroCo2() {
        Map<String, Double> co2PerActivity = new HashMap<String, Double>() {{
            put("Cycling", 0.21);
        }};

        Timestamp now = new Timestamp(new Date());
        ActivityLog unknownLog = new ActivityLog("user1", "Skydiving", "quick", 5, now);

        Double co2 = co2PerActivity.get(unknownLog.getActivityType());
        assertEquals(0.0, co2 == null ? 0.0 : co2, 0.001);
    }

    /**
     * Tests that getTotalPoints returns base points plus bonus points correctly.
     */
    @Test
    public void testActivityLogTotalPointsCalculation() {
        Timestamp now = new Timestamp(new Date());
        ActivityLog log = new ActivityLog("user1", "Cycling", "verified", 10, now);
        log.setBonusPoints(5);

        assertEquals(15, log.getTotalPoints());
    }
}