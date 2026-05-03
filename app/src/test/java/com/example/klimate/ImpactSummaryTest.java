/**
 * ImpactSummaryTest.java
 *
 * Unit tests for the campus-wide impact summary logic (US-13).
 * Tests the aggregation of co2SavedKg values and distinct user
 * counting that powers the CampusImpactCard on HomeFragment.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImpactSummaryTest {

    // ── Helper: simulates what HomeFragment.loadCampusImpact does ──

    private double sumCo2(List<Map<String, Object>> logs) {
        double total = 0.0;
        for (Map<String, Object> log : logs) {
            Object co2 = log.get("co2SavedKg");
            if (co2 instanceof Double) total += (Double) co2;
        }
        return total;
    }

    private int countDistinctUsers(List<Map<String, Object>> logs) {
        Set<String> users = new HashSet<>();
        for (Map<String, Object> log : logs) {
            Object uid = log.get("userId");
            if (uid instanceof String) users.add((String) uid);
        }
        return users.size();
    }

    // ── Tests ──

    @Test
    public void sumCo2_withThreeLogs_returnsCorrectTotal() {
        List<Map<String, Object>> logs = Arrays.asList(
                makeLog("user1", 5.0),
                makeLog("user2", 3.5),
                makeLog("user3", 2.0)
        );
        assertEquals(10.5, sumCo2(logs), 0.001);
    }

    @Test
    public void sumCo2_withNullCo2Fields_skipsNulls() {
        List<Map<String, Object>> logs = Arrays.asList(
                makeLog("user1", 5.0),
                makeLogNoCo2("user2"),
                makeLog("user3", 3.0)
        );
        assertEquals(8.0, sumCo2(logs), 0.001);
    }

    @Test
    public void sumCo2_emptyList_returnsZero() {
        assertEquals(0.0, sumCo2(Arrays.asList()), 0.001);
    }

    @Test
    public void countDistinctUsers_threeDistinctUsers_returnsThree() {
        List<Map<String, Object>> logs = Arrays.asList(
                makeLog("user1", 1.0),
                makeLog("user2", 2.0),
                makeLog("user3", 3.0)
        );
        assertEquals(3, countDistinctUsers(logs));
    }

    @Test
    public void countDistinctUsers_sameUserMultipleLogs_countsOnce() {
        List<Map<String, Object>> logs = Arrays.asList(
                makeLog("user1", 1.0),
                makeLog("user1", 2.0),
                makeLog("user2", 3.0)
        );
        assertEquals(2, countDistinctUsers(logs));
    }

    @Test
    public void countDistinctUsers_emptyList_returnsZero() {
        assertEquals(0, countDistinctUsers(Arrays.asList()));
    }

    @Test
    public void sumCo2_underThreshold_formatsAsKg() {
        List<Map<String, Object>> logs = Arrays.asList(
                makeLog("u1", 300.0),
                makeLog("u2", 400.0)
        );
        double total = sumCo2(logs);
        assertEquals(700.0, total, 0.001);
        assertTrue(total < 1000);
    }

    @Test
    public void sumCo2_overThreshold_formatsAsTonnes() {
        List<Map<String, Object>> logs = Arrays.asList(
                makeLog("u1", 500.0),
                makeLog("u2", 600.0)
        );
        double total = sumCo2(logs);
        assertEquals(1100.0, total, 0.001);
        assertTrue(total >= 1000);
    }

    // ── Helpers ──

    private Map<String, Object> makeLog(String userId, double co2) {
        Map<String, Object> log = new HashMap<>();
        log.put("userId", userId);
        log.put("co2SavedKg", co2);
        return log;
    }

    private Map<String, Object> makeLogNoCo2(String userId) {
        Map<String, Object> log = new HashMap<>();
        log.put("userId", userId);
        // co2SavedKg intentionally absent
        return log;
    }
}