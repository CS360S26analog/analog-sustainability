/**
 * AuthUiTest.java
 *
 * UI/intent tests for the Login and Register screens.
 * Tests cover field validation, error message display, and
 * navigation between screens. These are Espresso instrumented tests
 * that run on an emulator or device.
 *
 * Note: Tests that require actual Firebase Auth calls (successful login,
 * successful registration) are not included here as they depend on live
 * network connectivity and test account setup.
 *
 * Outstanding issues: Firebase-dependent tests (actual sign-in flow)
 * require a dedicated test Firebase project — not set up for half checkpoint.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuth;
@RunWith(AndroidJUnit4.class)
public class AuthUiTest {

    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule =
            new ActivityScenarioRule<>(LoginActivity.class);

    /**
     * Login screen should display email and password fields on launch.
     */
    @Test
    public void loginScreen_emailAndPasswordFieldsVisible() {
        onView(withId(R.id.et_email)).check(matches(isDisplayed()));
        onView(withId(R.id.et_password)).check(matches(isDisplayed()));
    }

    /**
     * Login screen should display the login button on launch.
     */
    @Test
    public void loginScreen_loginButtonVisible() {
        onView(withId(R.id.btn_login)).check(matches(isDisplayed()));
    }

    /**
     * Tapping login with empty fields should keep the user on the login screen.
     * (Firebase will reject empty credentials — user should not navigate away.)
     */
    @Test
    public void loginScreen_emptyFields_staysOnLoginScreen() {
        onView(withId(R.id.btn_login)).perform(click());
        onView(withId(R.id.et_email)).check(matches(isDisplayed()));
    }

    /**
     * Tapping "Don't have an account? Register" should open RegisterActivity.
     */
    @Test
    public void loginScreen_tapRegisterLink_opensRegisterScreen() {
        onView(withId(R.id.tv_go_register)).perform(click());
        onView(withId(R.id.btn_register)).check(matches(isDisplayed()));
    }

    /**
     * Register screen should show name, email, and password fields.
     */
    @Test
    public void registerScreen_allFieldsVisible() {
        onView(withId(R.id.tv_go_register)).perform(click());
        onView(withId(R.id.et_name)).check(matches(isDisplayed()));
        onView(withId(R.id.et_email)).check(matches(isDisplayed()));
        onView(withId(R.id.et_password)).check(matches(isDisplayed()));
    }

    /**
     * Tapping register with empty fields should keep the user on the
     * register screen — the form should not navigate away.
     */
    @Test
    public void registerScreen_emptyFields_staysOnRegisterScreen() {
        onView(withId(R.id.tv_go_register)).perform(click());
        onView(withId(R.id.btn_register)).perform(click());
        onView(withId(R.id.et_name)).check(matches(isDisplayed()));
    }

    /**
     * Tapping "Already have an account?" on register screen should
     * return to the login screen.
     */
    @Test
    public void registerScreen_tapLoginLink_returnsToLoginScreen() {
        // Sign out first so the login screen is visible
        FirebaseAuth.getInstance().signOut();
        onView(withId(R.id.tv_go_register)).perform(click());
        onView(withId(R.id.btn_register)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_go_login)).perform(click());
        onView(withId(R.id.btn_login)).check(matches(isDisplayed()));
    }
}