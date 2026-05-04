package com.example.klimate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewParent;

import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.auth.FirebaseAuth;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class LogFragmentUiTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FirebaseAuth.getInstance().signOut();

        // Prevent TapTarget tutorial overlays from blocking clicks.
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("tutorial_shown", true)
                .putBoolean("staff_tutorial_shown", true)
                .apply();
    }

    @Test
    public void logTab_headerAndTabsVisible() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_log)).perform(click());

            onView(withId(R.id.btn_log_activity)).check(matches(isDisplayed()));
            onView(withId(R.id.btn_quick_log)).check(matches(isDisplayed()));
            onView(withId(R.id.btn_verified_log)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void logScreen_activityCardsExistInLayout() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_log)).perform(click());

            // These cards may not all be fully on-screen at once inside NestedScrollView.
            // Check actual layout visibility instead of screen visibility.
            onView(withId(R.id.card_cycling)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.card_transit)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.card_recycling)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.card_plantbased)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.card_reusable)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.card_composting)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.card_walked)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.card_energy)).check(matches(withEffectiveVisibility(VISIBLE)));
        }
    }

    @Test
    public void logScreen_verifiedTabShowsProofSection() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_log)).perform(click());
            onView(withId(R.id.btn_verified_log)).perform(click());

            onView(withId(R.id.card_verified_proof_section))
                    .check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.btn_select_verified_proof))
                    .check(matches(withEffectiveVisibility(VISIBLE)));
        }
    }

    @Test
    public void logScreen_quickTabHidesProofSectionAgain() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_log)).perform(click());
            onView(withId(R.id.btn_verified_log)).perform(click());
            onView(withId(R.id.btn_quick_log)).perform(click());

            onView(withId(R.id.card_verified_proof_section))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    @Test
    public void logScreen_submitWithNoSelection_staysOnScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_log)).perform(click());
            onView(withId(R.id.btn_log_activity)).perform(nestedScrollTo(), click());
            onView(withId(R.id.btn_log_activity)).check(matches(withEffectiveVisibility(VISIBLE)));
        }
    }

    @Test
    public void logScreen_selectCardAndSubmitWhenSignedOut_doesNotNavigateAway() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_log)).perform(click());

            // card_cycling is the first card, so no Espresso scrollTo() is needed.
            // Espresso scrollTo() is brittle here because LogFragment uses NestedScrollView.
            onView(withId(R.id.card_cycling)).perform(click());

            // Selecting a quantifiable card inserts a row slider, so the button may move.
            // Use a small NestedScrollView-aware action before clicking.
            onView(withId(R.id.btn_log_activity)).perform(nestedScrollTo(), click());

            // Signed-out users get a Toast and remain on the Log screen.
            onView(withId(R.id.btn_log_activity)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.card_cycling)).check(matches(withEffectiveVisibility(VISIBLE)));
        }
    }

    private static ViewAction nestedScrollTo() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return withEffectiveVisibility(VISIBLE);
            }

            @Override
            public String getDescription() {
                return "scroll to view inside NestedScrollView if needed";
            }

            @Override
            public void perform(UiController uiController, View view) {
                NestedScrollView scrollView = findParentNestedScrollView(view);

                if (scrollView != null) {
                    int[] scrollLocation = new int[2];
                    int[] viewLocation = new int[2];

                    scrollView.getLocationOnScreen(scrollLocation);
                    view.getLocationOnScreen(viewLocation);

                    int targetY = scrollView.getScrollY()
                            + viewLocation[1]
                            - scrollLocation[1]
                            - 32;

                    scrollView.smoothScrollTo(0, Math.max(targetY, 0));
                    uiController.loopMainThreadForAtLeast(300);
                }

                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    private static NestedScrollView findParentNestedScrollView(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof NestedScrollView) {
                return (NestedScrollView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
}
