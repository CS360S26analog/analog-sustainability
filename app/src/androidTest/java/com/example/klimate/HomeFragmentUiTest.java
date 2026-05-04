package com.example.klimate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
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
public class HomeFragmentUiTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FirebaseAuth.getInstance().signOut();
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("tutorial_shown", true)
                .putBoolean("staff_tutorial_shown", true)
                .apply();
    }

    @Test
    public void dashboardLoadsAndShowsCoreStats() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.tv_greeting_message)).check(matches(isDisplayed()));
            onView(withId(R.id.tv_bottom_streak_days)).check(matches(isDisplayed()));
            onView(withId(R.id.tv_co2_saved)).check(matches(isDisplayed()));
            onView(withId(R.id.tv_points_value)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void tappingSeeMoreLogsNavigatesToLogScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.tv_see_more_logs)).perform(scrollTo(), click());
            onView(withId(R.id.btn_log_activity)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void tappingEcoPicksWidgetLoadsEcoPicksScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.card_ecopicks_widget)).perform(scrollTo(), click());
            onView(withId(R.id.map_container)).check(matches(isDisplayed()));
        }
    }
}
