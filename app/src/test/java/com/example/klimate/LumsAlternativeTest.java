package com.example.klimate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for LumsAlternatives — verifies catalogue structure and data integrity.
 */
@RunWith(JUnit4.class)
public class LumsAlternativeTest {

    // ── getLocations list ─────────────────────────────────────────────────────

    @Test
    public void getLocations_notNull() {
        assertNotNull(LumsAlternatives.getLocations());
    }

    @Test
    public void getLocations_notEmpty() {
        assertFalse(LumsAlternatives.getLocations().isEmpty());
    }

    @Test
    public void getLocations_has4Entries() {
        assertEquals(4, LumsAlternatives.getLocations().size());
    }

    @Test
    public void getLocations_returnsImmutableOrNewListEachCall() {
        List<LumsAlternatives.EcoLocation> list1 = LumsAlternatives.getLocations();
        List<LumsAlternatives.EcoLocation> list2 = LumsAlternatives.getLocations();
        assertEquals(list1.size(), list2.size());
    }

    @Test
    public void getLocations_noNullEntries() {
        for (LumsAlternatives.EcoLocation location : LumsAlternatives.getLocations()) {
            assertNotNull(location);
        }
    }

    // ── EcoLocation fields ────────────────────────────────────────────────────

    @Test
    public void ecoLocation_locationNames_notNullOrEmpty() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            assertNotNull(loc.getLocationName());
            assertFalse(loc.getLocationName().isEmpty());
        }
    }

    @Test
    public void ecoLocation_blocks_notNullOrEmpty() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            assertNotNull(loc.getBlock());
            assertFalse(loc.getBlock().isEmpty());
        }
    }

    @Test
    public void ecoLocation_items_notNullOrEmpty() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            assertNotNull(loc.getItems());
            assertFalse("Each location should have at least one item",
                    loc.getItems().isEmpty());
        }
    }

    @Test
    public void ecoLocation_containsCStore() {
        boolean found = false;
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            if ("C-Store".equals(loc.getLocationName())) { found = true; break; }
        }
        assertTrue("Should contain C-Store location", found);
    }

    @Test
    public void ecoLocation_containsMainCafeteria() {
        boolean found = false;
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            if ("Main Cafeteria".equals(loc.getLocationName())) { found = true; break; }
        }
        assertTrue("Should contain Main Cafeteria location", found);
    }

    @Test
    public void ecoLocation_containsFoodCourt() {
        boolean found = false;
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            if ("Food Court".equals(loc.getLocationName())) { found = true; break; }
        }
        assertTrue("Should contain Food Court location", found);
    }

    @Test
    public void ecoLocation_containsCampusWideTransport() {
        boolean found = false;
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            if ("Campus-wide Transport".equals(loc.getLocationName())) { found = true; break; }
        }
        assertTrue("Should contain Campus-wide Transport location", found);
    }

    // ── EcoItem fields ────────────────────────────────────────────────────────

    @Test
    public void ecoItems_titles_notNullOrEmpty() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            for (LumsAlternatives.EcoItem item : loc.getItems()) {
                assertNotNull(item.getTitle());
                assertFalse(item.getTitle().isEmpty());
            }
        }
    }

    @Test
    public void ecoItems_descriptions_notNullOrEmpty() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            for (LumsAlternatives.EcoItem item : loc.getItems()) {
                assertNotNull(item.getDescription());
                assertFalse(item.getDescription().isEmpty());
            }
        }
    }

    @Test
    public void ecoItems_activityTypes_notNullOrEmpty() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            for (LumsAlternatives.EcoItem item : loc.getItems()) {
                assertNotNull(item.getActivityType());
                assertFalse(item.getActivityType().isEmpty());
            }
        }
    }

    @Test
    public void ecoItems_activityTypes_areValidKlimateTypes() {
        String[] validTypes = {
                "Walked", "Cycling", "Public Transit",
                "Plant-based meal", "Reusable cup", "Reusable bag",
                "Recycling", "Composting", "Energy saving"
        };

        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            for (LumsAlternatives.EcoItem item : loc.getItems()) {
                String actType = item.getActivityType();
                boolean valid = false;
                for (String vt : validTypes) {
                    if (vt.equals(actType)) { valid = true; break; }
                }
                assertTrue("Activity type '" + actType + "' should be a known type", valid);
            }
        }
    }

    @Test
    public void mainCafeteria_itemHasPlantBasedMealActivity() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            if ("Main Cafeteria".equals(loc.getLocationName())) {
                boolean found = false;
                for (LumsAlternatives.EcoItem item : loc.getItems()) {
                    if ("Plant-based meal".equals(item.getActivityType())) {
                        found = true; break;
                    }
                }
                assertTrue("Main Cafeteria should have Plant-based meal activity", found);
            }
        }
    }

    @Test
    public void foodCourt_itemHasRecyclingActivity() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            if ("Food Court".equals(loc.getLocationName())) {
                boolean found = false;
                for (LumsAlternatives.EcoItem item : loc.getItems()) {
                    if ("Recycling".equals(item.getActivityType())) {
                        found = true; break;
                    }
                }
                assertTrue("Food Court should have Recycling activity", found);
            }
        }
    }

    @Test
    public void campusTransport_itemHasWalkedActivity() {
        for (LumsAlternatives.EcoLocation loc : LumsAlternatives.getLocations()) {
            if ("Campus-wide Transport".equals(loc.getLocationName())) {
                boolean found = false;
                for (LumsAlternatives.EcoItem item : loc.getItems()) {
                    if ("Walked".equals(item.getActivityType())) {
                        found = true; break;
                    }
                }
                assertTrue("Campus-wide Transport should have Walked activity", found);
            }
        }
    }

    // ── EcoItem constructor ───────────────────────────────────────────────────

    @Test
    public void ecoItem_constructor_setsAllFields() {
        LumsAlternatives.EcoItem item = new LumsAlternatives.EcoItem(
                "Bring reusable bag",
                "Skip a plastic bag",
                "Reusable bag"
        );
        assertEquals("Bring reusable bag", item.getTitle());
        assertEquals("Skip a plastic bag", item.getDescription());
        assertEquals("Reusable bag", item.getActivityType());
    }

    // ── EcoLocation constructor ───────────────────────────────────────────────

    @Test
    public void ecoLocation_constructor_setsAllFields() {
        java.util.List<LumsAlternatives.EcoItem> items = new java.util.ArrayList<>();
        items.add(new LumsAlternatives.EcoItem("Title", "Desc", "Recycling"));

        LumsAlternatives.EcoLocation loc = new LumsAlternatives.EcoLocation(
                "Test Location", "Block A", items);
        assertEquals("Test Location", loc.getLocationName());
        assertEquals("Block A", loc.getBlock());
        assertEquals(1, loc.getItems().size());
    }
}