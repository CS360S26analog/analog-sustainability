package com.example.klimate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class StaffProfileLogoutIntentTest {

    @Before
    public void setUp() {
        FirebaseAuth.getInstance().signOut();
        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();
    }

    @Test
    public void staffProfileLogout_opensLoginActivity() {
        try (FragmentScenario<StaffProfileFragment> scenario =
                     FragmentScenario.launchInContainer(StaffProfileFragment.class)) {

            onView(withId(R.id.row_staff_logout)).perform(scrollTo(), click());

            intended(hasComponent(LoginActivity.class.getName()));
        }
    }
}
