/**
 * EcoPicksFragment.java
 *
 * Displays an interactive LUMS EcoMap. Students can tap campus map markers
 * to view sticky-note sustainability tips, log suggested activities, and
 * submit their own eco tips for staff review.
 *
 * Role in design: View layer for EXTRA-2 EcoMap / Eco Picks @ LUMS.
 * Hardcoded tips are used as default tips. Staff-approved student suggestions
 * from Firestore are added into the same swipeable sticky-note carousel.
 *
 * @author Izza
 */
package com.example.klimate;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

    private static class CarouselTip {
        String text;
        boolean fromCommunity;

        CarouselTip(String text, boolean fromCommunity) {
            this.text = text;
            this.fromCommunity = fromCommunity;
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

        View btnHomeChip = view.findViewById(R.id.btn_home_chip);
        if (btnHomeChip != null) {
            btnHomeChip.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).setBottomNavVisible(true);
                    ((MainActivity) getActivity()).navigateToHome();
                }
            });
        }

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
                marker.setOnClickListener(v -> showSwipeableStickyNote(spot));

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

    private void showSwipeableStickyNote(EcoSpot spot) {
        ArrayList<CarouselTip> tips = new ArrayList<>();
        for (String defaultTip : spot.tips) {
            tips.add(new CarouselTip(defaultTip, false));
        }

        final int[] currentIndex = {0};

        LinearLayout rootLayout = new LinearLayout(requireContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(18), dp(18), dp(18), dp(14));
        rootLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        TextView title = new TextView(requireContext());
        title.setText(spot.title);
        title.setTextColor(requireContext().getColor(R.color.color_text_primary));
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("casual", Typeface.BOLD));
        title.setPadding(0, 0, 0, dp(12));
        rootLayout.addView(title);

        LinearLayout carouselRow = new LinearLayout(requireContext());
        carouselRow.setOrientation(LinearLayout.HORIZONTAL);
        carouselRow.setGravity(Gravity.CENTER);
        carouselRow.setPadding(0, 0, 0, dp(12));

        TextView btnPrev = createArrowButton("‹");

        LinearLayout noteLayout = new LinearLayout(requireContext());
        noteLayout.setOrientation(LinearLayout.VERTICAL);
        noteLayout.setGravity(Gravity.CENTER);
        noteLayout.setBackgroundResource(R.drawable.bg_sticky_note);
        noteLayout.setPadding(dp(24), dp(24), dp(24), dp(24));

        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                dp(240),
                dp(150)
        );
        noteParams.setMargins(dp(10), 0, dp(10), 0);
        noteLayout.setLayoutParams(noteParams);

        TextView tipText = new TextView(requireContext());
        tipText.setTextColor(requireContext().getColor(R.color.color_text_primary));
        tipText.setTextSize(18);
        tipText.setTypeface(Typeface.create("casual", Typeface.NORMAL));
        tipText.setGravity(Gravity.CENTER);
        tipText.setLineSpacing(dp(2), 1.0f);
        noteLayout.addView(tipText);

        TextView btnNext = createArrowButton("›");

        carouselRow.addView(btnPrev);
        carouselRow.addView(noteLayout);
        carouselRow.addView(btnNext);
        rootLayout.addView(carouselRow);

        LinearLayout buttons = new LinearLayout(requireContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        TextView btnLog = createStickyButton("Log this");
        TextView btnSuggest = createStickyButton("Suggest Tip");
        TextView btnClose = createStickyButton("Close");

        buttons.addView(btnLog);
        buttons.addView(btnSuggest);
        buttons.addView(btnClose);
        rootLayout.addView(buttons);

        Runnable updateTip = () -> {
            if (!tips.isEmpty()) {
                tipText.setText(tips.get(currentIndex[0]).text);
            }
        };

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(rootLayout)
                .create();

        btnPrev.setOnClickListener(v -> {
            currentIndex[0] = (currentIndex[0] - 1 + tips.size()) % tips.size();
            updateTip.run();
        });

        btnNext.setOnClickListener(v -> {
            currentIndex[0] = (currentIndex[0] + 1) % tips.size();
            updateTip.run();
        });

        btnLog.setOnClickListener(v -> {
            dialog.dismiss();
            openLogWithActivity(spot.activityType);
        });

        btnSuggest.setOnClickListener(v -> {
            dialog.dismiss();
            showSubmitNoteDialog(spot);
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                WindowManager.LayoutParams params = window.getAttributes();
                params.dimAmount = 0.72f;
                window.setAttributes(params);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.setBackgroundBlurRadius(dp(18));
                }
            }
        });

        dialog.show();
        updateTip.run();

        loadApprovedTipsForSpot(spot, tips, new TextView(requireContext()), new TextView(requireContext()), tipText, currentIndex);
    }

    private TextView createArrowButton(String text) {
        TextView button = new TextView(requireContext());
        button.setText(text);
        button.setTextSize(42);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(requireContext().getColor(R.color.color_green_header));
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(120));
        button.setLayoutParams(params);

        return button;
    }

    private void loadApprovedTipsForSpot(EcoSpot spot,
                                         ArrayList<CarouselTip> tips,
                                         TextView counter,
                                         TextView sourceLabel,
                                         TextView tipText,
                                         int[] currentIndex) {
        FirebaseFirestore.getInstance()
                .collection("eco_map_notes")
                .whereEqualTo("spotTitle", spot.title)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    if (!isAdded() || getContext() == null) return;

                    ArrayList<QueryDocumentSnapshot> approvedDocs = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        String status = doc.getString("status");
                        String noteText = doc.getString("noteText");

                        if ("approved".equals(status) && noteText != null && !noteText.trim().isEmpty()) {
                            approvedDocs.add(doc);
                        }
                    }

                    approvedDocs.sort((a, b) -> {
                        Timestamp ta = a.getTimestamp("reviewedAt");
                        Timestamp tb = b.getTimestamp("reviewedAt");

                        if (ta == null) ta = a.getTimestamp("submittedAt");
                        if (tb == null) tb = b.getTimestamp("submittedAt");

                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;

                        return tb.compareTo(ta);
                    });

                    for (QueryDocumentSnapshot doc : approvedDocs) {
                        String noteText = doc.getString("noteText");
                        tips.add(new CarouselTip(noteText.trim(), true));
                    }

                    if (!approvedDocs.isEmpty()) {
                        counter.setText((currentIndex[0] + 1) + " / " + tips.size());
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Could not load approved tips.", Toast.LENGTH_SHORT).show()
                );
    }

    private void showSubmitNoteDialog(EcoSpot spot) {
        LinearLayout dialogLayout = new LinearLayout(requireContext());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(22), dp(20), dp(22), dp(16));
        dialogLayout.setBackgroundResource(R.drawable.bg_sticky_note);

        TextView title = new TextView(requireContext());
        title.setText("Suggest a tip");
        title.setTextColor(requireContext().getColor(R.color.color_text_primary));
        title.setTextSize(22);
        title.setTypeface(Typeface.create("casual", Typeface.BOLD));
        dialogLayout.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("For " + spot.title + "\nStaff will review it before publishing.");
        subtitle.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        dialogLayout.addView(subtitle);

        EditText input = new EditText(requireContext());
        input.setHint("Example: Add a reminder to bring reusable mugs here...");
        input.setMinLines(4);
        input.setGravity(Gravity.TOP);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackgroundResource(R.drawable.bg_card_outline);
        input.setTextColor(requireContext().getColor(R.color.color_text_primary));
        input.setHintTextColor(requireContext().getColor(R.color.color_text_secondary));
        input.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        dialogLayout.addView(input);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        buttonRow.setPadding(0, dp(18), 0, 0);

        TextView btnCancel = createStickyButton("Cancel");
        TextView btnSubmit = createStickyButton("Submit");

        buttonRow.addView(btnCancel);
        buttonRow.addView(btnSubmit);
        dialogLayout.addView(buttonRow);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogLayout)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String note = input.getText().toString().trim();

            if (note.isEmpty()) {
                Toast.makeText(getContext(), "Tip cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            submitEcoMapNoteForReview(spot, note);
            dialog.dismiss();
        });

        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                WindowManager.LayoutParams params = window.getAttributes();
                params.dimAmount = 0.72f;
                window.setAttributes(params);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.setBackgroundBlurRadius(dp(18));
                }
            }
        });

        dialog.show();
    }

    private void submitEcoMapNoteForReview(EcoSpot spot, String noteText) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "Please log in before suggesting a tip.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference noteRef = db.collection("eco_map_notes").document();

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("noteId", noteRef.getId());
        noteData.put("spotTitle", spot.title);
        noteData.put("activityType", spot.activityType);
        noteData.put("noteText", noteText);
        noteData.put("submittedBy", currentUser.getUid());
        noteData.put("submittedAt", Timestamp.now());
        noteData.put("status", "pending");
        noteData.put("reviewedBy", null);
        noteData.put("reviewedAt", null);
        noteData.put("source", "eco_map");

        noteRef.set(noteData)
                .addOnSuccessListener(unused ->
                        Toast.makeText(
                                getContext(),
                                "Tip submitted for staff review ✅",
                                Toast.LENGTH_LONG
                        ).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(
                                getContext(),
                                "Could not submit tip: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void openLogWithActivity(String activityType) {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setBottomNavVisible(true);
            mainActivity.navigateToLog();
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

    private TextView createStickyButton(String text) {
        TextView button = new TextView(requireContext());
        button.setText(text);
        button.setTextColor(requireContext().getColor(R.color.color_green_header));
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);

        return button;
    }
}