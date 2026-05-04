package com.example.klimate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.anything;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI tests for ValidationFeedFragment.
 *
 * This test opens the fragment directly through MainActivity because
 * the validation feed is now part of staff navigation, not the old
 * Rankings-card flow.
 */
@RunWith(AndroidJUnit4.class)
public class ValidationFeedUiTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityScenarioRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void validationFeedFragment_loadsContainer() {
        activityScenarioRule.getScenario().onActivity(activity ->
                activity.loadFragment(new ValidationFeedFragment())
        );

        onView(withId(R.id.validation_feed_container))
                .check(matches(anything()));
    }

    @Test
    public void validationFeedFragment_hasEmptyStateViewInLayout() {
        activityScenarioRule.getScenario().onActivity(activity ->
                activity.loadFragment(new ValidationFeedFragment())
        );

        onView(withId(R.id.card_empty_validation_state))
                .check(matches(anything()));
    }
}