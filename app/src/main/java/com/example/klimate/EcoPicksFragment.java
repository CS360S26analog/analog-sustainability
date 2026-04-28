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
        float x;
        float y;

        EcoSpot(String title, String activityType, float x, float y, String[] tips) {
            this.title = title;
            this.activityType = activityType;
            this.x = x;
            this.y = y;
            this.tips = tips;
        }
    }

    private final EcoSpot[] spots = {

            new EcoSpot("Parking", "Public Transit", 0.24f, 0.13f, new String[]{
                    "Use designated parking efficiently. 🚗",
                    "Carpool when possible. 🌱"
            }),

            new EcoSpot("Campus Gates", "Public Transit", 0.08f, 0.88f, new String[]{
                    "Coordinate carpools for daily commutes. 🚗",
                    "Walk or cycle for short distances. 🚲",
                    "Waiting for pickup? Turn off the engine. 🌬️"
            }),

            new EcoSpot("Campus Gates", "Public Transit", 0.47f, 0.88f, new String[]{
                    "Coordinate carpools for daily commutes. 🚗",
                    "Walk or cycle for short distances. 🚲",
                    "Waiting for pickup? Turn off the engine. 🌬️"
            }),

            new EcoSpot("Male Hostels", "Energy saving", 0.16f, 0.30f, new String[]{
                    "Set AC to 26°C for efficiency. ❄️",
                    "Wash laundry in full loads. 🧺",
                    "Switch off lights before leaving. 💡"
            }),

            new EcoSpot("PDC / Eateries", "Reusable cup", 0.25f, 0.56f, new String[]{
                    "Bring your own mug for chai or coffee. ☕",
                    "Refill bottles instead of buying new ones. 🚰",
                    "Take only what you will finish. 🥘"
            }),

            new EcoSpot("Academic Block", "Energy saving", 0.49f, 0.5f, new String[]{
                    "Switch off lights if you're last out. 💡",
                    "Use stairs over elevators. 🚶",
                    "Print less, print double-sided. 📄"
            }),

            new EcoSpot("SOE / Library", "Paper saving", 0.64f, 0.63f, new String[]{
                    "Use digital notes where possible. 📱",
                    "Print only final versions. 📄",
                    "Use task lighting smartly. 💡"
            }),

            new EcoSpot("SSE", "Energy saving", 0.10f, 0.53f, new String[]{
                    "Turn off lab equipment before leaving. 💡",
                    "Use stairs where possible. 🚶",
                    "Reduce unnecessary printing. 📄"
            }),

            new EcoSpot("Mosque", "Water saving", 0.71f, 0.41f, new String[]{
                    "Use water mindfully during wudu. 🚰",
                    "Turn taps off fully. 💧",
                    "Help keep the area clean. 🌿"
            }),

            new EcoSpot("Sports Complex", "Reusable bottle", 0.55f, 0.22f, new String[]{
                    "Carry a reusable flask. 💧",
                    "Switch off lights after play. 🏀",
                    "Respect green spaces. 🌿"
            }),

            new EcoSpot("Female Hostels", "Energy saving", 0.86f, 0.59f, new String[]{
                    "Set AC to 26°C for efficiency. ❄️",
                    "Wash laundry in full loads. 🧺",
                    "Switch off lights before leaving. 💡"
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
            if (mapContainer.getChildCount() > 1) {
                mapContainer.removeViews(1, mapContainer.getChildCount() - 1);
            }

            int mapW = mapContainer.getWidth();
            int mapH = mapContainer.getHeight();

            for (EcoSpot spot : spots) {
                TextView marker = createMarker();

                int markerSize = dp(34);
                int left = (int) (mapW * spot.x) - markerSize / 2;
                int top = (int) (mapH * spot.y) - markerSize / 2;

                FrameLayout.LayoutParams params =
                        new FrameLayout.LayoutParams(markerSize, markerSize);

                params.leftMargin = left;
                params.topMargin = top;

                marker.setLayoutParams(params);
                marker.setOnClickListener(v -> showStickyNote(spot));

                mapContainer.addView(marker);
            }
        });
    }

    private TextView createMarker() {
        TextView marker = new TextView(requireContext());
        marker.setText("📍");
        marker.setTextSize(24);
        marker.setGravity(Gravity.CENTER);
        marker.setBackgroundResource(R.drawable.bg_activity_card_selected);
        marker.setElevation(dp(4));
        marker.setClickable(true);
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