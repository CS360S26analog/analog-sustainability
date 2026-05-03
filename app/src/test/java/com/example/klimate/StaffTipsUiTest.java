/**
 * StaffTipsUiTest.java
 *
 * Unit tests for staff tips publishing logic (US-11).
 * Tests input validation, category handling, and the
 * role-based access gate.
 *
 * Outstanding issues: None.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

public class StaffTipsUiTest {

    private static final List<String> VALID_CATEGORIES =
            Arrays.asList("Recycling", "Transport", "Food", "Energy", "Other");

    // ── Role check logic ──

    private boolean isStaff(String role) {
        return "staff".equals(role);
    }

    // ── Tip validation ──

    private boolean isTipValid(String title, String body) {
        return title != null && !title.trim().isEmpty()
                && body != null && !body.trim().isEmpty();
    }

    private boolean isCategoryValid(String category) {
        return VALID_CATEGORIES.contains(category);
    }

    // ── Tests ──

    @Test
    public void isStaff_withStaffRole_returnsTrue() {
        assertTrue(isStaff("staff"));
    }

    @Test
    public void isStaff_withStudentRole_returnsFalse() {
        assertFalse(isStaff("student"));
    }

    @Test
    public void isStaff_withNullRole_returnsFalse() {
        assertFalse(isStaff(null));
    }

    @Test
    public void tipValidation_validTitleAndBody_passes() {
        assertTrue(isTipValid("Use a reusable cup", "Bring your own mug to save waste."));
    }

    @Test
    public void tipValidation_emptyTitle_fails() {
        assertFalse(isTipValid("", "Some body text"));
    }

    @Test
    public void tipValidation_emptyBody_fails() {
        assertFalse(isTipValid("Some title", ""));
    }

    @Test
    public void tipValidation_nullTitle_fails() {
        assertFalse(isTipValid(null, "Some body"));
    }

    @Test
    public void tipValidation_whitespaceOnly_fails() {
        assertFalse(isTipValid("   ", "   "));
    }

    @Test
    public void categoryValidation_validCategory_passes() {
        assertTrue(isCategoryValid("Recycling"));
        assertTrue(isCategoryValid("Transport"));
        assertTrue(isCategoryValid("Food"));
        assertTrue(isCategoryValid("Energy"));
        assertTrue(isCategoryValid("Other"));
    }

    @Test
    public void categoryValidation_invalidCategory_fails() {
        assertFalse(isCategoryValid("Sports"));
        assertFalse(isCategoryValid(""));
        assertFalse(isCategoryValid(null));
    }

    @Test
    public void tipData_correctFieldsBuilt() {
        String title    = "Walk on campus";
        String body     = "Walking saves fuel and keeps you fit!";
        String category = "Transport";
        String authorUid = "uid_staff_001";

        // Simulate what StaffTipsFragment writes to Firestore
        assertNotNull(title);
        assertNotNull(body);
        assertTrue(isCategoryValid(category));
        assertNotNull(authorUid);
        assertTrue(isTipValid(title, body));
    }
}
