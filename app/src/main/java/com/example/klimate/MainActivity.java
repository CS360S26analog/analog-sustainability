/**
 * MainActivity.java
 *
 * The primary host activity of the Klimate app. Manages the bottom
 * navigation bar and switches between the five main fragments:
 * HomeFragment, LogFragment, RankingsFragment, FriendsFragment,
 * and ProfileFragment.
 *
 * Role in design: Part of the Navigation layer. Acts as the container
 * for all main screen fragments. Uses Android's FragmentManager to
 * replace the fragment container on each tab selection.
 *
 * Outstanding issues: none.
 *
 * @author Team Analog
 */
package com.example.klimate;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_log) {
                fragment = new LogFragment();
            } else if (id == R.id.nav_rankings) {
                fragment = new RankingsFragment();
            } else if (id == R.id.nav_friends) {
                fragment = new FriendsFragment();
            } else if (id == R.id.nav_profile) {
                fragment = new ProfileFragment();
            } else {
                return false;
            }
            loadFragment(fragment);
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit();
    }

    public void navigateToLog() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_log);
    }
}
