/**
 * EcoPicksFragment.java
 *
 * Displays an interactive LUMS EcoMap. Students can tap campus map markers
 * to view sticky-note sustainability tips, log suggested activities, and
 * submit their own eco notes for staff review.
 *
 * Role in design: View layer for EXTRA-2 EcoMap / Eco Picks @ LUMS.
 *
 * Outstanding issues: submitted notes are currently shown as confirmation only;
 * Firestore staff-review storage can be added next.
 *
 * @author Izza
 */
package com.example.klimate;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class EcoPicksFragment extends Fragment {

    private FrameLayout mapContainer;

    private static class EcoSpot {
        String title;
        String activityType;
        String[] tips;
        int leftDp;
        int topDp;

        EcoSpot(String title, String activityType, int leftDp, int topDp, String[] tips) {
            this.title = title;
            this.activityType = activityType;
            this.leftDp = leftDp;
            this.topDp = topDp;
            this.tips = tips;
        }
    }

    private final EcoSpot[] spots = {
            new EcoSpot("Academic Blocks", "Energy saving", 195, 280, new String[]{
                    "If you’re the last one in the lab, switch off the lights and AV equipment. 💡",
                    "Stairs > elevators. Get those steps in! 🚶",
                    "Printing a draft? Go double-sided or do not print at all. 📄"
            }),
            new EcoSpot("PDC / Eateries", "Reusable cup", 95, 245, new String[]{
                    "Do not buy a bottle. Refill your glass or bottle for free. 🚰",
                    "Bring your own mug for chai or coffee. ☕",
                    "Take only what you will finish to reduce food waste. 🥘"
            }),
            new EcoSpot("Hostels", "Energy saving", 55, 175, new String[]{
                    "Set AC to 26°C for energy efficiency. ❄️",
                    "Wait for a full laundry load before starting the machine. 🧺",
                    "Switch off extra lights before leaving your room. 💡"
            }),
            new EcoSpot("Library / SOE", "Paper saving", 265, 295, new String[]{
                    "Use digital notes when possible. 📱",
                    "Use desk lamps instead of unnecessary overhead lighting. 🛋️",
                    "Print only final versions, not every draft. 📄"
            }),
            new EcoSpot("Sports Complex", "Reusable bottle", 205, 115, new String[]{
                    "Carry a reusable flask for water. 💧",
                    "Switch off court lights immediately after play. 🏀",
                    "Stay on paths to protect green spaces. 🌿"
            }),
            new EcoSpot("Parking / Gates", "Public Transit", 45, 390, new String[]{
                    "Carpool from Gulberg, DHA, or nearby areas. 🚗",
                    "Waiting for pickup? Turn off the engine. 🌬️",
                    "Walk or cycle when the distance is short. 🚲"
            }),
            new EcoSpot("Mosque", "Water saving", 300, 205, new String[]{
                    "Use water mindfully during wudu. 🚰",
                    "Turn taps off properly after use. 💧",
                    "Help keep the area clean and low-waste. 🌿"
            })
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_eco_picks, container, false);

        mapContainer = view.findViewById(R.id.map_container);
        addMapMarkers();

        return view;
    }

    private void addMapMarkers() {
        mapContainer.post(() -> {
            for (EcoSpot spot : spots) {
                TextView marker = createMarker();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(38), dp(38));
                params.leftMargin = dp(spot.leftDp);
                params.topMargin = dp(spot.topDp);
                marker.setLayoutParams(params);
                marker.setOnClickListener(v -> showStickyNote(spot));
                mapContainer.addView(marker);
            }
        });
    }

    private TextView createMarker() {
        TextView marker = new TextView(requireContext());
        marker.setText("📍");
        marker.setTextSize(28);
        marker.setGravity(Gravity.CENTER);
        marker.setBackgroundResource(R.drawable.bg_activity_card_selected);
        marker.setElevation(dp(4));
        return marker;
    }

    private void showStickyNote(EcoSpot spot) {
        StringBuilder message = new StringBuilder();

        for (String tip : spot.tips) {
            message.append("• ").append(tip).append("\n\n");
        }

        message.append("Suggested log: ").append(spot.activityType);

        new AlertDialog.Builder(requireContext())
                .setTitle("📌 " + spot.title)
                .setMessage(message.toString())
                .setPositiveButton("Log this activity", (dialog, which) -> openLogWithActivity(spot.activityType))
                .setNegativeButton("Submit note", (dialog, which) -> showSubmitNoteDialog(spot))
                .setNeutralButton("Close", null)
                .show();
    }

    private void showSubmitNoteDialog(EcoSpot spot) {
        EditText input = new EditText(requireContext());
        input.setHint("Write your eco tip here...");
        input.setMinLines(3);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setBackgroundResource(R.drawable.bg_card_outline);
        input.setTextColor(requireContext().getColor(R.color.color_text_primary));
        input.setHintTextColor(requireContext().getColor(R.color.color_text_secondary));
        input.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC));

        new AlertDialog.Builder(requireContext())
                .setTitle("Submit note for " + spot.title)
                .setView(input)
                .setPositiveButton("Submit", (dialog, which) -> {
                    String note = input.getText().toString().trim();

                    if (note.isEmpty()) {
                        Toast.makeText(getContext(), "Note cannot be empty", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Note submitted for staff review ✅", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openLogWithActivity(String activityType) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToLog();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisible(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisible(true);
        }
    }
}