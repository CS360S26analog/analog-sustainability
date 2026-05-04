package com.example.klimate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
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
public class ProfileLogoutIntentTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();

        FirebaseAuth.getInstance().signOut();

        // Prevent the TapTarget tutorial overlay from blocking bottom-nav/profile clicks.
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("tutorial_shown", true)
                .putBoolean("staff_tutorial_shown", true)
                .apply();

        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();
    }

    @Test
    public void studentProfileLogout_opensLoginActivity() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_profile)).perform(click());
            onView(withId(R.id.row_logout)).perform(scrollTo(), click());

            intended(hasComponent(LoginActivity.class.getName()));
        }
    }
}
