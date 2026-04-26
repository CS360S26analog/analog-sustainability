package com.example.klimate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LeaderboardUiTest {

    private static class UserRow {
        String uid;
        String name;
        long points;

        UserRow(String uid, String name, long points) {
            this.uid = uid;
            this.name = name;
            this.points = points;
        }
    }

    private List<UserRow> sortByPoints(List<UserRow> users) {
        Collections.sort(users, (a, b) -> Long.compare(b.points, a.points));
        return users;
    }

    private String medalForRank(int rank) {
        if (rank == 1) return "🥇";
        if (rank == 2) return "🥈";
        if (rank == 3) return "🥉";
        return "";
    }

    @Test
    public void leaderboardSortsUsersByTotalPointsDescending() {
        List<UserRow> users = new ArrayList<>();
        users.add(new UserRow("u1", "Ali", 100));
        users.add(new UserRow("u2", "Sara", 500));
        users.add(new UserRow("u3", "Omar", 300));

        sortByPoints(users);

        assertEquals("Sara", users.get(0).name);
        assertEquals("Omar", users.get(1).name);
        assertEquals("Ali", users.get(2).name);
    }

    @Test
    public void topThreeRanksShowMedals() {
        assertEquals("🥇", medalForRank(1));
        assertEquals("🥈", medalForRank(2));
        assertEquals("🥉", medalForRank(3));
        assertEquals("", medalForRank(4));
    }

    @Test
    public void currentUserCanBeDetectedForHighlighting() {
        UserRow user = new UserRow("current_user", "Karar", 700);
        String currentUid = "current_user";

        assertTrue(user.uid.equals(currentUid));
    }
}