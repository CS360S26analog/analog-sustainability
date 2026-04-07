/**
 * HomeFragment.java
 *
 * The main dashboard screen of the Klimate app.
 * Displays the current user's greeting, streak, CO2 saved, and points
 * by observing LiveData from DashboardViewModel.
 *
 * Role in design: Part of the View layer (MVVM pattern).
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

import java.util.Locale;

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

        tvUserName = view.findViewById(R.id.tv_user_name);
        tvStreakHeader = view.findViewById(R.id.tv_streak_header);
        tvStreakDays = view.findViewById(R.id.tv_streak_days);
        tvCo2Saved = view.findViewById(R.id.tv_co2_saved);
        tvPoints = view.findViewById(R.id.tv_points);

        TextView btnCycling = view.findViewById(R.id.btn_log_cycling);
        TextView btnRecycling = view.findViewById(R.id.btn_log_recycling);

        btnCycling.setOnClickListener(v -> navigateToLog());
        btnRecycling.setOnClickListener(v -> navigateToLog());

        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                String displayName = user.getDisplayName();
                if (displayName != null && !displayName.trim().isEmpty()) {
                    String firstName = displayName.trim().split("\\s+")[0];
                    tvUserName.setText(firstName + "!");
                }

                tvStreakHeader.setText(String.valueOf(user.getStreakDays()));
                tvStreakDays.setText(user.getStreakDays() + " days");
                tvPoints.setText(String.valueOf(user.getTotalPoints()));
            }
        });

        viewModel.getCo2LiveData().observe(getViewLifecycleOwner(), co2 -> {
            if (co2 != null) {
                tvCo2Saved.setText(String.format(Locale.getDefault(), "%.1f kg", co2));
            }
        });

        viewModel.loadUserData();
        viewModel.loadActivityLogs();

        return view;
    }

    private void navigateToLog() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToLog();
        }
    }
}