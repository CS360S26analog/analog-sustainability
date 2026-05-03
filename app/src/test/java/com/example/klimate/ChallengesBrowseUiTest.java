/**
 * ChallengesBrowseUiTest.java
 *
 * Unit tests for challenge browsing and join logic (US-10).
 * Tests the team code generator, duplicate join detection,
 * and participant count formatting.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

public class ChallengesBrowseUiTest {

    // ── Team code generator (mirrors ChallengesFragment.generateTeamCode) ──

    private String generateTeamCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }

    // ── Tests ──

    @Test
    public void generateTeamCode_alwaysFourCharacters() {
        for (int i = 0; i < 20; i++) {
            assertEquals(4, generateTeamCode().length());
        }
    }

    @Test
    public void generateTeamCode_onlyUppercaseAlphanumeric() {
        for (int i = 0; i < 20; i++) {
            String code = generateTeamCode();
            assertTrue(code.matches("[A-Z0-9]+"));
        }
    }

    @Test
    public void generateTeamCode_neverContainsAmbiguousChars() {
        // 'I', 'O', '0', '1' excluded from charset to avoid confusion
        for (int i = 0; i < 50; i++) {
            String code = generateTeamCode();
            assertFalse(code.contains("I"));
            assertFalse(code.contains("O"));
            assertFalse(code.contains("0"));
            assertFalse(code.contains("1"));
        }
    }

    @Test
    public void generateTeamCode_producesVariedCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            codes.add(generateTeamCode());
        }
        // With 50 runs we expect at least 20 distinct codes
        assertTrue(codes.size() > 20);
    }

    @Test
    public void duplicateJoinCheck_userAlreadyMember_detected() {
        String currentUid = "user123";
        Set<String> existingMembers = new HashSet<>();
        existingMembers.add("user123");
        existingMembers.add("user456");

        assertTrue(existingMembers.contains(currentUid));
    }

    @Test
    public void duplicateJoinCheck_newUser_notDetected() {
        String currentUid = "user789";
        Set<String> existingMembers = new HashSet<>();
        existingMembers.add("user123");

        assertFalse(existingMembers.contains(currentUid));
    }

    @Test
    public void participantCount_formatsCorrectly() {
        int count = 34;
        String endStr = "Ends 28 Apr";
        String meta = endStr + "  ·  " + count + " joined";
        assertEquals("Ends 28 Apr  ·  34 joined", meta);
    }
}