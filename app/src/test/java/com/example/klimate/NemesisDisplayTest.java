package com.example.klimate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class NemesisDisplayTest {

    private static class UserRow {
        String uid;
        long points;

        UserRow(String uid, long points) {
            this.uid = uid;
            this.points = points;
        }
    }

    private static class NemesisResult {
        UserRow above;
        UserRow below;
        long aboveGap;
        long belowGap;
    }

    private NemesisResult calculateNemesis(List<UserRow> users, String currentUid) {
        NemesisResult result = new NemesisResult();

        int currentIndex = -1;

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).uid.equals(currentUid)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return result;
        }

        UserRow currentUser = users.get(currentIndex);

        if (currentIndex > 0) {
            result.above = users.get(currentIndex - 1);
            result.aboveGap = Math.max(0, result.above.points - currentUser.points);
        }

        if (currentIndex < users.size() - 1) {
            result.below = users.get(currentIndex + 1);
            result.belowGap = Math.max(0, currentUser.points - result.below.points);
        }

        return result;
    }

    private List<UserRow> makeUsers() {
        List<UserRow> users = new ArrayList<>();
        users.add(new UserRow("u1", 1000));
        users.add(new UserRow("u2", 900));
        users.add(new UserRow("u3", 800));
        users.add(new UserRow("u4", 700));
        users.add(new UserRow("u5", 600));
        users.add(new UserRow("u6", 550));
        users.add(new UserRow("u7", 400));
        return users;
    }

    @Test
    public void middleUserShowsAboveAndBelowNemesis() {
        NemesisResult result = calculateNemesis(makeUsers(), "u5");

        assertEquals("u4", result.above.uid);
        assertEquals(100, result.aboveGap);

        assertEquals("u6", result.below.uid);
        assertEquals(50, result.belowGap);
    }

    @Test
    public void firstUserOnlyShowsBelowNemesis() {
        NemesisResult result = calculateNemesis(makeUsers(), "u1");

        assertNull(result.above);
        assertEquals("u2", result.below.uid);
        assertEquals(100, result.belowGap);
    }

    @Test
    public void lastUserOnlyShowsAboveNemesis() {
        NemesisResult result = calculateNemesis(makeUsers(), "u7");

        assertEquals("u6", result.above.uid);
        assertEquals(150, result.aboveGap);
        assertNull(result.below);
    }
}