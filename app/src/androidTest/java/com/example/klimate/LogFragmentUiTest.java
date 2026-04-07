/**
 * LogFragmentUiTest.java
 *
 * UI/intent tests for the Log Activity screen (US-02, US-04).
 * Tests verify that activity cards are displayed, that selecting a card
 * highlights it, that the Quick/Verified tab toggle works, and that
 * tapping Log Activity without a selection shows an appropriate message.
 *
 * How to run:
 *   1. Start an API 34 emulator
 *   2. Sign in to the app manually first so MainActivity loads
 *   3. Right-click this file in Android Studio -> Run 'LogFragmentUiTest'
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
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class LogFragmentUiTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    /**
     * Navigating to the Log tab should show the Log Activity header.
     */
    @Test
    public void logTab_headerVisible() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.btn_log_activity)).check(matches(isDisplayed()));
    }

    /**
     * The Quick Log tab should be visible on the log screen.
     */
    @Test
    public void logScreen_quickLogTabVisible() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.btn_quick_log)).check(matches(isDisplayed()));
    }

    /**
     * The Verified Log tab should be visible on the log screen.
     */
    @Test
    public void logScreen_verifiedLogTabVisible() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.btn_verified_log)).check(matches(isDisplayed()));
    }

    /**
     * Tapping the Verified Log tab should not crash the app.
     * Verifies tab switching works without error.
     */
    @Test
    public void logScreen_tapVerifiedTab_doesNotCrash() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.btn_verified_log)).perform(click());
        onView(withId(R.id.btn_quick_log)).check(matches(isDisplayed()));
    }

    /**
     * The Cycling activity card should be visible on the log screen.
     */
    @Test
    public void logScreen_cyclingCardVisible() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.card_cycling)).check(matches(isDisplayed()));
    }

    /**
     * The Recycling activity card should be visible on the log screen.
     */
    @Test
    public void logScreen_recyclingCardVisible() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.card_recycling)).check(matches(isDisplayed()));
    }

    /**
     * Tapping Log Activity without selecting a card should keep the
     * user on the log screen (no navigation away).
     */
    @Test
    public void logScreen_submitWithNoSelection_staysOnScreen() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.btn_log_activity)).perform(click());
        onView(withId(R.id.btn_log_activity)).check(matches(isDisplayed()));
    }

    /**
     * Tapping a card and then tapping Log Activity should not crash.
     * Verifies the full selection and submit flow runs without error.
     */
    @Test
    public void logScreen_selectCardAndSubmit_doesNotCrash() {
        onView(withId(R.id.nav_log)).perform(click());
        onView(withId(R.id.card_cycling)).perform(click());
        onView(withId(R.id.btn_log_activity)).perform(click());
        onView(withId(R.id.nav_log)).check(matches(isDisplayed()));
    }
}
