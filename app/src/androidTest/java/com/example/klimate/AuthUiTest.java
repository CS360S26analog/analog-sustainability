package com.example.klimate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AuthUiTest {

    @Before
    public void setUp() {
        FirebaseAuth.getInstance().signOut();
    }

    @Test
    public void loginScreen_emailPasswordAndLoginButtonVisible() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.et_email)).check(matches(isDisplayed()));
            onView(withId(R.id.et_password)).check(matches(isDisplayed()));
            onView(withId(R.id.btn_login)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void loginScreen_emptyFields_staysOnLoginScreen() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.btn_login)).perform(click());
            onView(withId(R.id.et_email)).check(matches(isDisplayed()));
            onView(withId(R.id.et_password)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void loginScreen_tapRegisterLink_opensRegisterScreen() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.tv_go_register)).perform(click());
            onView(withId(R.id.btn_register)).check(matches(withEffectiveVisibility(VISIBLE)));
        }
    }

    @Test
    public void registerScreen_requiredStudentAndStaffFieldsExist() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.tv_go_register)).perform(click());

            onView(withId(R.id.et_name)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.et_email)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.et_password)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.et_staff_code)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.tv_selected_university)).check(matches(withEffectiveVisibility(VISIBLE)));
        }
    }

    @Test
    public void registerScreen_emptyFields_staysOnRegisterScreen() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.tv_go_register)).perform(click());
            onView(withId(R.id.btn_register)).perform(scrollTo(), click());

            onView(withId(R.id.et_name)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.btn_register)).check(matches(withEffectiveVisibility(VISIBLE)));
        }
    }

    @Test
    public void registerScreen_tapLoginLink_returnsToLoginScreen() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.tv_go_register)).perform(click());
            onView(withId(R.id.btn_register)).check(matches(withEffectiveVisibility(VISIBLE)));

            onView(withId(R.id.tv_go_login)).perform(click());

            onView(withId(R.id.btn_login)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.et_email)).check(matches(withEffectiveVisibility(VISIBLE)));
        }
    }
}
