package com.example.klimate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuthUiTest {

    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule =
            new ActivityScenarioRule<>(LoginActivity.class);

    @Before
    public void setUp() {
        FirebaseAuth.getInstance().signOut();
    }

    @Test
    public void loginScreen_emailAndPasswordFieldsVisible() {
        onView(withId(R.id.et_email)).check(matches(isDisplayed()));
        onView(withId(R.id.et_password)).check(matches(isDisplayed()));
    }

    @Test
    public void loginScreen_loginButtonVisible() {
        onView(withId(R.id.btn_login)).check(matches(isDisplayed()));
    }

    @Test
    public void loginScreen_emptyFields_staysOnLoginScreen() {
        onView(withId(R.id.btn_login)).perform(scrollTo(), click());
        onView(withId(R.id.et_email)).check(matches(isDisplayed()));
    }

    @Test
    public void loginScreen_tapRegisterLink_opensRegisterScreen() {
        onView(withId(R.id.tv_go_register)).perform(scrollTo(), click());
        onView(withId(R.id.btn_register)).check(matches(isDisplayed()));
    }

    @Test
    public void registerScreen_allFieldsVisible() {
        onView(withId(R.id.tv_go_register)).perform(scrollTo(), click());

        onView(withId(R.id.et_name)).check(matches(isDisplayed()));
        onView(withId(R.id.et_email)).check(matches(isDisplayed()));
        onView(withId(R.id.et_password)).check(matches(isDisplayed()));
    }

    @Test
    public void registerScreen_emptyFields_staysOnRegisterScreen() {
        onView(withId(R.id.tv_go_register)).perform(scrollTo(), click());
        onView(withId(R.id.btn_register)).perform(scrollTo(), closeSoftKeyboard(), click());

        onView(withId(R.id.et_name)).check(matches(isDisplayed()));
    }

    @Test
    public void registerScreen_tapLoginLink_returnsToLoginScreen() {
        onView(withId(R.id.tv_go_register)).perform(scrollTo(), click());
        onView(withId(R.id.btn_register)).check(matches(isDisplayed()));

        onView(withId(R.id.tv_go_login)).perform(scrollTo(), click());
        onView(withId(R.id.btn_login)).check(matches(isDisplayed()));
    }
}