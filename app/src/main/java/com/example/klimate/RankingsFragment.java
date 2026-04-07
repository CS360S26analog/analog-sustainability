/**
 * RankingsFragment.java
 *
 * Displays the leaderboard screen and provides access
 * to the community validation feed.
 *
 * Role in design: Part of the UI layer. Reached from the
 * Rankings tab in the bottom navigation.
 *
 * Outstanding issues: leaderboard data is currently static.
 * @author Karar
 */

package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class RankingsFragment extends Fragment {

    /**
     * Inflates the rankings screen and wires the validation feed entry card.
     *
     * @param inflater the LayoutInflater used to inflate views
     * @param container the parent view group
     * @param savedInstanceState previously saved fragment state
     * @return the root view for the rankings screen
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rankings, container, false);

        View openValidationCard = view.findViewById(R.id.card_open_validation_feed);
        openValidationCard.setOnClickListener(v ->
                requireActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ValidationFeedFragment())
                        .addToBackStack(null)
                        .commit()
        );

        return view;
    }
}