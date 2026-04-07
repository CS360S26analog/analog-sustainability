/**
 * BottomNavUiTest.java
 *
 * UI/intent tests for bottom navigation between all five tabs.
 * Tests verify that each tab loads its correct screen and that
 * switching between tabs does not crash the app.
 *
 * These tests are distinct from AuthUiTest which only covers the
 * login and register screens before the user is authenticated.
 * These tests run inside MainActivity after the user is signed in.
 *
 * How to run:
 *   1. Start an API 34 emulator
 *   2. Sign in to the app manually first so MainActivity loads on launch
 *   3. Right-click this file -> Run 'BottomNavUiTest'
 *      OR run: ./gradlew connectedAndroidTest
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class BottomNavUiTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    /**
     * Home tab should load by default when MainActivity opens.
     */
    @Test
    public void bottomNav_homeLoadsByDefault() {
        onView(withId(R.id.nav_home)).check(matches(isDisplayed()));
    }

    /**
     * Tapping the Log tab should load the log screen.
     */
    @Test
    public void bottomNav_tapLog_loadsLogScreen() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.btn_log_activity)).check(matches(isDisplayed()));
    }

    /**
     * Tapping the Rankings tab should load the rankings screen.
     */
    @Test
    public void bottomNav_tapRankings_loadsRankingsScreen() {
        onView(withId(R.id.nav_rankings)).perform(click());
        onView(withId(R.id.card_open_validation_feed)).check(matches(isDisplayed()));
    }

    /**
     * Tapping the Profile tab should load the profile screen.
     */
    @Test
    public void bottomNav_tapProfile_loadsProfileScreen() {
        onView(withId(R.id.nav_profile)).perform(click());
        onView(withId(R.id.row_account)).check(matches(isDisplayed()));
    }

    /**
     * Tapping the Friends tab should load the friends screen.
     */
    @Test
    public void bottomNav_tapFriends_loadsFriendsScreen() {
        onView(withId(R.id.nav_friends)).perform(click());
        onView(withId(R.id.btn_add_friend)).check(matches(isDisplayed()));
    }

    /**
     * Switching between multiple tabs in sequence should not crash.
     */
    @Test
    public void bottomNav_switchAllTabs_doesNotCrash() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.nav_rankings)).perform(click());
        onView(withId(R.id.nav_profile)).perform(click());
        onView(withId(R.id.nav_friends)).perform(click());
        onView(withId(R.id.nav_home)).perform(click());
        onView(withId(R.id.nav_home)).check(matches(isDisplayed()));
    }

    /**
     * Tapping Rankings and then opening validation feed should navigate correctly.
     */
    @Test
    public void bottomNav_rankingsToValidationFeed_navigates() {
        onView(withId(R.id.nav_rankings)).perform(click());
        onView(withId(R.id.card_open_validation_feed)).perform(click());
        onView(withId(R.id.btn_validation_back)).check(matches(isDisplayed()));
    }
}
