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

/**
 * ValidationFeedUiTest.java
 *
 * UI tests for validation feed navigation and empty state behavior.
 *
 * Role in design: Part of the androidTest suite for Karar's
 * validation feed feature.
 *
 * Outstanding issues: Firestore-backed feed item tests can be added
 * later when shared test data is available.
 * @author Karar
 */
@RunWith(AndroidJUnit4.class)
public class ValidationFeedUiTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityScenarioRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void openingValidationFeedFromRankingsShowsFeedScreen() {
        onView(withId(R.id.nav_rankings)).perform(click());
        onView(withId(R.id.card_open_validation_feed)).perform(click());

        onView(withId(R.id.text_validation_feed_title))
                .check(matches(isDisplayed()));

        onView(withText("Validation Feed"))
                .check(matches(isDisplayed()));

        onView(withId(R.id.card_empty_validation_state))
                .check(matches(isDisplayed()));
    }

    @Test
    public void tappingBackOnValidationFeedReturnsToRankings() {
        onView(withId(R.id.nav_rankings)).perform(click());
        onView(withId(R.id.card_open_validation_feed)).perform(click());
        onView(withId(R.id.btn_validation_back)).perform(click());

        onView(withText("Leader Board"))
                .check(matches(isDisplayed()));
    }
}