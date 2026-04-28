/**
 * EcoPicksFragment.java
 *
 * Displays hardcoded sustainable alternatives around LUMS. Each item opens
 * LogFragment with the matching activity type pre-selected so students can
 * quickly log campus-specific sustainable choices.
 *
 * Role in design: View layer for EXTRA-2 Eco Picks @ LUMS.
 *
 * Outstanding issues: Eco Picks data is currently hardcoded through
 * LumsAlternatives.java for the final demo.
 *
 * @author Izza
 */
package com.example.klimate;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class EcoPicksFragment extends Fragment {

    private LinearLayout llEcoLocations;
    private TextView tvEcoEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_eco_picks, container, false);

        llEcoLocations = view.findViewById(R.id.ll_eco_locations);
        tvEcoEmpty = view.findViewById(R.id.tv_eco_empty);

        renderEcoPicks();

        return view;
    }

    /**
     * Renders all Eco Pick location cards from LumsAlternatives.
     */
    private void renderEcoPicks() {
        List<LumsAlternatives.EcoLocation> locations = LumsAlternatives.getLocations();

        llEcoLocations.removeAllViews();

        if (locations.isEmpty()) {
            tvEcoEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvEcoEmpty.setVisibility(View.GONE);

        for (LumsAlternatives.EcoLocation location : locations) {
            llEcoLocations.addView(createLocationCard(location));
        }
    }

    /**
     * Creates a location card containing all sustainable actions for that place.
     *
     * @param location campus location data
     * @return fully configured card view
     */
    private View createLocationCard(LumsAlternatives.EcoLocation location) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_outline);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);

        TextView tvLocation = new TextView(requireContext());
        tvLocation.setText(location.getLocationName());
        tvLocation.setTextColor(requireContext().getColor(R.color.color_text_primary));
        tvLocation.setTextSize(18);
        tvLocation.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(tvLocation);

        TextView tvBlock = new TextView(requireContext());
        tvBlock.setText(location.getBlock());
        tvBlock.setTextColor(requireContext().getColor(R.color.color_green_header));
        tvBlock.setTextSize(13);
        tvBlock.setPadding(0, dp(2), 0, dp(8));
        card.addView(tvBlock);

        for (LumsAlternatives.EcoItem item : location.getItems()) {
            card.addView(createEcoItemRow(item));
        }

        return card;
    }

    /**
     * Creates one tappable Eco Pick row.
     *
     * @param item sustainable action data
     * @return row view that opens LogFragment when tapped
     */
    private View createEcoItemRow(LumsAlternatives.EcoItem item) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundResource(R.drawable.bg_activity_card);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(rowParams);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(item.getTitle());
        tvTitle.setTextColor(requireContext().getColor(R.color.color_text_primary));
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(tvTitle);

        TextView tvDescription = new TextView(requireContext());
        tvDescription.setText(item.getDescription());
        tvDescription.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        tvDescription.setTextSize(13);
        tvDescription.setPadding(0, dp(3), 0, dp(6));
        row.addView(tvDescription);

        TextView tvAction = new TextView(requireContext());
        tvAction.setText("Log as: " + item.getActivityType());
        tvAction.setTextColor(requireContext().getColor(R.color.color_green_header));
        tvAction.setTextSize(13);
        tvAction.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(tvAction);

        row.setOnClickListener(v -> openLogWithActivity(item.getActivityType()));

        return row;
    }

    /**
     * Opens the Log tab with the selected activity pre-filled.
     *
     * @param activityType activity type to pre-select in LogFragment
     */
    private void openLogWithActivity(String activityType) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToLog(activityType, false, false);
        }
    }

    /**
     * Converts density-independent pixels to raw pixels.
     *
     * @param value dp value
     * @return pixel value
     */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}