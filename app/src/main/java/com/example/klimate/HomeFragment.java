/**
 * HomeFragment.java
 *
 * The main dashboard screen of the Klimate app.
 * Displays the current user's greeting, streak, CO2 saved, and points
 * by observing LiveData from DashboardViewModel.
 * All stats are loaded from Firestore in real time — no hardcoded values.
 *
 * Role in design: Part of the View layer (MVVM pattern).
 * Observes DashboardViewModel and updates UI elements when data changes.
 *
 * Outstanding issues: Time period filter (weekly/monthly) not yet
 * connected to UI — currently shows all-time stats.
 *
 * @author Maryam Ali
 */
package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class HomeFragment extends Fragment {

    private DashboardViewModel viewModel;

    private TextView tvUserName;
    private TextView tvStreakHeader;
    private TextView tvStreakDays;
    private TextView tvCo2Saved;
    private TextView tvPoints;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Connect Java variables to the XML views by their IDs
        tvUserName     = view.findViewById(R.id.tv_user_name);
        tvStreakHeader = view.findViewById(R.id.tv_streak_header);
        tvStreakDays   = view.findViewById(R.id.tv_streak_days);
        tvCo2Saved     = view.findViewById(R.id.tv_co2_saved);
        tvPoints       = view.findViewById(R.id.tv_points);

        // Set up the Log it buttons to navigate to the Log screen
        TextView btnCycling   = view.findViewById(R.id.btn_log_cycling);
        TextView btnRecycling = view.findViewById(R.id.btn_log_recycling);

        btnCycling.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToLog();
            }
        });

        btnRecycling.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToLog();
            }
        });

        // Create the ViewModel
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        // Observe user profile data (name, streak, points)
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                tvUserName.setText(user.getDisplayName() + "!");
                tvStreakHeader.setText(String.valueOf(user.getStreakDays()));
                tvStreakDays.setText(user.getStreakDays() + " days");
                tvPoints.setText(String.valueOf(user.getTotalPoints()));
            }
        });

        // Observe CO2 savings data
        viewModel.getCo2LiveData().observe(getViewLifecycleOwner(), co2 -> {
            if (co2 != null) {
                tvCo2Saved.setText(String.format("%.1f kg", co2));
            }
        });

        // Kick off the data loads from Firestore
        viewModel.loadUserData();
        viewModel.loadActivityLogs();

        return view;
    }
}