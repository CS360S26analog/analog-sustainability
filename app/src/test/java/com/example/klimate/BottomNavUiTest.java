package com.example.klimate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class BottomNavUiTest {

    private String readProjectFile(String pathFromAppModule) throws Exception {
        File fileFromAppModule = new File(pathFromAppModule);

        if (fileFromAppModule.exists()) {
            return new String(Files.readAllBytes(fileFromAppModule.toPath()), StandardCharsets.UTF_8);
        }

        File fileFromProjectRoot = new File("app/" + pathFromAppModule);

        if (fileFromProjectRoot.exists()) {
            return new String(Files.readAllBytes(fileFromProjectRoot.toPath()), StandardCharsets.UTF_8);
        }

        throw new IllegalStateException("Could not find file: " + pathFromAppModule);
    }

    @Test
    public void rankingsNoLongerContainsValidationFeedEntry() throws Exception {
        String rankingsXml = readProjectFile("src/main/res/layout/fragment_rankings.xml");
        String rankingsJava = readProjectFile("src/main/java/com/example/klimate/RankingsFragment.java");

        assertFalse(rankingsXml.contains("card_open_validation_feed"));
        assertFalse(rankingsJava.contains("new ValidationFeedFragment()"));
    }

    @Test
    public void friendsContainsPendingReviewsEntry() throws Exception {
        String friendsXml = readProjectFile("src/main/res/layout/fragment_friends.xml");
        String friendsJava = readProjectFile("src/main/java/com/example/klimate/FriendsFragment.java");

        assertTrue(friendsXml.contains("card_pending_reviews"));
        assertTrue(friendsJava.contains("new ValidationFeedFragment()"));
    }
}