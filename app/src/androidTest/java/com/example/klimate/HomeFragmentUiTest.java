/**
 * HomeFragmentUiTest.java
 *
 * Intent/UI tests for the HomeFragment dashboard screen.
 * Verifies that the dashboard loads correctly, displays the
 * expected stat cards, and that the Log it buttons navigate
 * to the Log screen as required by US-07 and US-08.
 *
 * Role in design: Part of the androidTest suite for the dashboard
 * feature. Tests the View layer (HomeFragment) using Espresso.
 * Complements DashboardViewModelTest which tests logic in isolation.
 *
 * Outstanding issues: Stats cards show 0 values during tests since
 * no authenticated user is present — Firestore data tests require
 * a test user account and are deferred to a later sprint.
 *
 * @author Maryam Ali
 */
package com.example.klimate;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.klimate.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class HomeFragmentUiTest {

    /**
     * Launches MainActivity before each test and closes it after.
     * All dashboard tests start from the main activity since
     * HomeFragment is the default screen loaded on launch.
     */
    @Rule
    public ActivityScenarioRule<MainActivity> activityScenarioRule =
            new ActivityScenarioRule<>(MainActivity.class);

    /**
     * Verifies that the home dashboard screen loads successfully
     * and the streak stat card is visible to the user.
     * Satisfies US-07: dashboard must be visible on app launch.
     *
     * Note: ID updated from tv_streak_days to tv_bottom_streak_days
     * to match the actual stat card ID in fragment_home.xml.
     */
    @Test
    public void dashboardLoadsAndShowsStreakCard() {
        onView(withId(R.id.tv_bottom_streak_days))
                .check(matches(isDisplayed()));
    }

    /**
     * Verifies that the CO2 saved stat card is visible on the dashboard.
     * Satisfies US-08: estimated carbon reduction must be displayed
     * with clear units on the dashboard screen.
     */
    @Test
    public void dashboardShowsCo2SavedCard() {
        onView(withId(R.id.tv_co2_saved))
                .check(matches(isDisplayed()));
    }

    /**
     * Verifies that the points stat card is visible on the dashboard.
     * Satisfies US-07: dashboard must show the user's points balance.
     *
     * Note: ID updated from tv_points to tv_points_value
     * to match the actual stat card ID in fragment_home.xml.
     */
    @Test
    public void dashboardShowsPointsCard() {
        onView(withId(R.id.tv_points_value))
                .check(matches(isDisplayed()));
    }

    /**
     * Verifies that tapping the first activity card Log now button
     * navigates the user to the Log Activity screen.
     * Satisfies US-07: dashboard action cards must navigate to logging.
     *
     * Note: ID updated from btn_log_cycling to btn_recent_1 to match
     * the generic activity card button ID in fragment_home.xml.
     */
    @Test
    public void tappingCyclingLogItNavigatesToLogScreen() {
        onView(withId(R.id.btn_recent_1)).perform(click());
        onView(withId(R.id.nav_log)).check(matches(isDisplayed()));
    }

    /**
     * Verifies that tapping the second activity card Log now button
     * navigates the user to the Log Activity screen.
     * Satisfies US-07: dashboard action cards must navigate to logging.
     *
     * Note: ID updated from btn_log_recycling to btn_recent_2 to match
     * the generic activity card button ID in fragment_home.xml.
     */
    @Test
    public void tappingRecyclingLogItNavigatesToLogScreen() {
        onView(withId(R.id.btn_recent_2)).perform(click());
        onView(withId(R.id.nav_log)).check(matches(isDisplayed()));
    }
}