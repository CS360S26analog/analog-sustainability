package com.example.klimate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BottomNavUiTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FirebaseAuth.getInstance().signOut();

        // Prevent TapTarget tutorial overlays from blocking bottom-nav clicks.
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("tutorial_shown", true)
                .putBoolean("staff_tutorial_shown", true)
                .apply();
    }

    @Test
    public void bottomNav_homeLoadsByDefault() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.tv_greeting_message)).check(matches(isDisplayed()));
            onView(withId(R.id.nav_home)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void bottomNav_tapLog_loadsLogScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_log)).perform(click());
            onView(withId(R.id.btn_log_activity)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void bottomNav_tapRankings_loadsRankingsScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_rankings)).perform(click());
            onView(withId(R.id.leaderboard_container)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void bottomNav_tapProfile_loadsProfileScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_profile)).perform(click());
            onView(withId(R.id.tv_display_name)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void bottomNav_tapCommunity_loadsFeedScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // MainPagerAdapter position 1 is FeedFragment now. The menu ID is still nav_friends.
            onView(withId(R.id.nav_friends)).perform(click());

            onView(withId(R.id.chip_feed)).check(matches(isDisplayed()));
            onView(withId(R.id.chip_news)).check(matches(isDisplayed()));
            onView(withId(R.id.chip_explore)).check(matches(isDisplayed()));
            onView(withId(R.id.chip_teams)).check(matches(isDisplayed()));
            onView(withId(R.id.feed_content_container)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void bottomNav_switchAllTabs_doesNotCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_log)).perform(click());
            onView(withId(R.id.nav_rankings)).perform(click());
            onView(withId(R.id.nav_profile)).perform(click());
            onView(withId(R.id.nav_friends)).perform(click());
            onView(withId(R.id.nav_home)).perform(click());

            onView(withId(R.id.tv_greeting_message)).check(matches(isDisplayed()));
        }
    }
}
