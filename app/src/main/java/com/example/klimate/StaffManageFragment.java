/**
 * StaffManageFragment.java
 *
 * Allows staff users to create and manage campus sustainability challenges.
 * Staff can publish a challenge with name, description, start/end date,
 * target contribution count, and allowed activity types.
 *
 * Firestore write:
 * challenges/{challengeId}
 * {name, description, startDate, endDate, target, allowedActivities[], active, createdBy}
 *
 * Role in design: View layer for US-12 Staff challenge creation.
 *
 * Outstanding issues: Edit currently pre-fills the form and republishes as a new challenge;
 * full in-place editing can be added later if required.
 *
 * @author Izza
 */
package com.example.klimate;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StaffManageFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private EditText etChallengeName;
    private EditText etChallengeDescription;
    private EditText etStartDate;
    private EditText etEndDate;
    private EditText etTarget;

    private CheckBox cbCycling;
    private CheckBox cbTransit;
    private CheckBox cbRecycling;
    private CheckBox cbPlantBased;
    private CheckBox cbReusable;
    private CheckBox cbComposting;
    private CheckBox cbWalked;
    private CheckBox cbEnergy;

    private TextView tvActivityError;
    private TextView btnPublishChallenge;
    private LinearLayout llStaffActiveChallenges;
    private TextView tvStaffEmptyChallenges;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_staff_manage, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        bindViews(view);
        setupDatePickers();

        btnPublishChallenge.setOnClickListener(v -> publishChallenge());

        loadActiveChallenges();

        return view;
    }

    /**
     * Binds XML views to Java fields.
     *
     * @param view root layout view
     */
    private void bindViews(View view) {
        etChallengeName = view.findViewById(R.id.et_challenge_name);
        etChallengeDescription = view.findViewById(R.id.et_challenge_description);
        etStartDate = view.findViewById(R.id.et_start_date);
        etEndDate = view.findViewById(R.id.et_end_date);
        etTarget = view.findViewById(R.id.et_target);

        cbCycling = view.findViewById(R.id.cb_cycling);
        cbTransit = view.findViewById(R.id.cb_transit);
        cbRecycling = view.findViewById(R.id.cb_recycling);
        cbPlantBased = view.findViewById(R.id.cb_plantbased);
        cbReusable = view.findViewById(R.id.cb_reusable);
        cbComposting = view.findViewById(R.id.cb_composting);
        cbWalked = view.findViewById(R.id.cb_walked);
        cbEnergy = view.findViewById(R.id.cb_energy);

        tvActivityError = view.findViewById(R.id.tv_activity_error);
        btnPublishChallenge = view.findViewById(R.id.btn_publish_challenge);
        llStaffActiveChallenges = view.findViewById(R.id.ll_staff_active_challenges);
        tvStaffEmptyChallenges = view.findViewById(R.id.tv_staff_empty_challenges);
    }

    /**
     * Makes the start and end date fields open calendar pickers.
     * The picker blocks past dates. End date is also blocked from being before start date.
     */
    private void setupDatePickers() {
        etStartDate.setFocusable(false);
        etStartDate.setCursorVisible(false);
        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate, null));

        etEndDate.setFocusable(false);
        etEndDate.setCursorVisible(false);
        etEndDate.setOnClickListener(v -> {
            Date startDate = parseDateSafely(etStartDate.getText().toString().trim());
            showDatePicker(etEndDate, startDate);
        });
    }

    /**
     * Shows a DatePickerDialog with a minimum selectable date.
     *
     * @param targetInput date input field to update
     * @param minimumDate optional minimum date, used for end date after start date is selected
     */
    private void showDatePicker(EditText targetInput, @Nullable Date minimumDate) {
        Calendar today = Calendar.getInstance();
        resetCalendarToStartOfDay(today);

        Calendar minimumCalendar = Calendar.getInstance();
        minimumCalendar.setTime(today.getTime());

        if (minimumDate != null && minimumDate.after(minimumCalendar.getTime())) {
            minimumCalendar.setTime(minimumDate);
            resetCalendarToStartOfDay(minimumCalendar);
        }

        Calendar initialCalendar = Calendar.getInstance();
        Date existingDate = parseDateSafely(targetInput.getText().toString().trim());

        if (existingDate != null && !existingDate.before(minimumCalendar.getTime())) {
            initialCalendar.setTime(existingDate);
        } else {
            initialCalendar.setTime(minimumCalendar.getTime());
        }

        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(year, month, dayOfMonth);
                    resetCalendarToStartOfDay(selectedCalendar);

                    targetInput.setText(dateFormat.format(selectedCalendar.getTime()));

                    if (targetInput == etStartDate) {
                        Date currentEndDate = parseDateSafely(etEndDate.getText().toString().trim());
                        if (currentEndDate != null && currentEndDate.before(selectedCalendar.getTime())) {
                            etEndDate.setText("");
                            etEndDate.setError("Pick an end date after the start date");
                        }
                    }
                },
                initialCalendar.get(Calendar.YEAR),
                initialCalendar.get(Calendar.MONTH),
                initialCalendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.getDatePicker().setMinDate(minimumCalendar.getTimeInMillis());
        dialog.show();
    }

    /**
     * Validates the form and publishes a new active challenge to Firestore.
     */
    private void publishChallenge() {
        if (currentUser == null) {
            Toast.makeText(getContext(), "Please log in first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etChallengeName.getText().toString().trim();
        String description = etChallengeDescription.getText().toString().trim();
        String startDateText = etStartDate.getText().toString().trim();
        String endDateText = etEndDate.getText().toString().trim();
        String targetText = etTarget.getText().toString().trim();

        if (name.isEmpty()) {
            etChallengeName.setError("Enter a challenge name");
            return;
        }

        if (description.isEmpty()) {
            etChallengeDescription.setError("Enter a description");
            return;
        }

        if (startDateText.isEmpty()) {
            etStartDate.setError("Pick a start date");
            return;
        }

        if (endDateText.isEmpty()) {
            etEndDate.setError("Pick an end date");
            return;
        }

        if (targetText.isEmpty()) {
            etTarget.setError("Enter a target");
            return;
        }

        int target;
        try {
            target = Integer.parseInt(targetText);
            if (target <= 0) {
                etTarget.setError("Target must be greater than 0");
                return;
            }
        } catch (NumberFormatException e) {
            etTarget.setError("Enter a valid number");
            return;
        }

        Date startDate = parseDate(startDateText, etStartDate);
        if (startDate == null) return;

        Date endDate = parseDate(endDateText, etEndDate);
        if (endDate == null) return;

        Date today = getTodayStart();

        if (startDate.before(today)) {
            etStartDate.setError("Start date cannot be in the past");
            return;
        }

        if (endDate.before(today)) {
            etEndDate.setError("End date cannot be in the past");
            return;
        }

        if (endDate.before(startDate)) {
            etEndDate.setError("End date must be after start date");
            return;
        }

        ArrayList<String> allowedActivities = getSelectedActivities();
        if (allowedActivities.isEmpty()) {
            tvActivityError.setVisibility(View.VISIBLE);
            return;
        }

        tvActivityError.setVisibility(View.GONE);

        Map<String, Object> challengeData = new HashMap<>();
        challengeData.put("name", name);
        challengeData.put("description", description);
        challengeData.put("startDate", new Timestamp(startDate));
        challengeData.put("endDate", new Timestamp(endDate));
        challengeData.put("target", target);
        challengeData.put("allowedActivities", allowedActivities);
        challengeData.put("active", true);
        challengeData.put("createdBy", currentUser.getUid());
        challengeData.put("participantCount", 0);
        challengeData.put("createdAt", Timestamp.now());

        btnPublishChallenge.setEnabled(false);
        btnPublishChallenge.setText("Publishing...");

        db.collection("challenges")
                .add(challengeData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "Challenge published!", Toast.LENGTH_SHORT).show();
                    clearForm();
                    loadActiveChallenges();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Could not publish: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                )
                .addOnCompleteListener(task -> {
                    btnPublishChallenge.setEnabled(true);
                    btnPublishChallenge.setText("Publish Challenge");
                });
    }

    /**
     * Parses a date string in yyyy-MM-dd format.
     *
     * @param dateText date text from input
     * @param input input field used for showing errors
     * @return parsed Date, or null if invalid
     */
    @Nullable
    private Date parseDate(String dateText, EditText input) {
        try {
            dateFormat.setLenient(false);
            return dateFormat.parse(dateText);
        } catch (ParseException e) {
            input.setError("Use format YYYY-MM-DD");
            return null;
        }
    }

    /**
     * Parses a date without showing field errors.
     *
     * @param dateText date text
     * @return parsed Date, or null if empty/invalid
     */
    @Nullable
    private Date parseDateSafely(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }

        try {
            dateFormat.setLenient(false);
            return dateFormat.parse(dateText);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Returns today's date at midnight.
     *
     * @return today at 00:00:00
     */
    private Date getTodayStart() {
        Calendar calendar = Calendar.getInstance();
        resetCalendarToStartOfDay(calendar);
        return calendar.getTime();
    }

    /**
     * Resets a Calendar object to the beginning of its day.
     *
     * @param calendar calendar to reset
     */
    private void resetCalendarToStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    /**
     * Returns all selected allowed activity types.
     *
     * @return selected activity names
     */
    private ArrayList<String> getSelectedActivities() {
        ArrayList<String> selected = new ArrayList<>();

        addIfChecked(selected, cbCycling, "Cycling");
        addIfChecked(selected, cbTransit, "Public Transit");
        addIfChecked(selected, cbRecycling, "Recycling");
        addIfChecked(selected, cbPlantBased, "Plant-based meal");
        addIfChecked(selected, cbReusable, "Reusable cup");
        addIfChecked(selected, cbComposting, "Composting");
        addIfChecked(selected, cbWalked, "Walked");
        addIfChecked(selected, cbEnergy, "Energy saving");

        return selected;
    }

    /**
     * Adds an activity to the selected list if its checkbox is checked.
     *
     * @param selected list being built
     * @param checkBox checkbox to inspect
     * @param value activity value to add
     */
    private void addIfChecked(ArrayList<String> selected, CheckBox checkBox, String value) {
        if (checkBox.isChecked()) {
            selected.add(value);
        }
    }

    /**
     * Loads active challenges created in Firestore and displays them for staff.
     */
    private void loadActiveChallenges() {
        db.collection("challenges")
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    llStaffActiveChallenges.removeAllViews();

                    if (snapshots.isEmpty()) {
                        tvStaffEmptyChallenges.setVisibility(View.VISIBLE);
                        return;
                    }

                    tvStaffEmptyChallenges.setVisibility(View.GONE);

                    for (QueryDocumentSnapshot doc : snapshots) {
                        addStaffChallengeRow(doc);
                    }
                })
                .addOnFailureListener(e -> {
                    tvStaffEmptyChallenges.setText("Could not load challenges.");
                    tvStaffEmptyChallenges.setVisibility(View.VISIBLE);
                });
    }

    /**
     * Adds one active challenge row to the staff list.
     *
     * @param doc challenge document
     */
    private void addStaffChallengeRow(QueryDocumentSnapshot doc) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.bg_card_outline);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);

        String name = doc.getString("name");
        String description = doc.getString("description");
        Long target = doc.getLong("target");

        TextView tvName = new TextView(requireContext());
        tvName.setText(name != null ? name : "Unnamed Challenge");
        tvName.setTextColor(requireContext().getColor(R.color.color_text_primary));
        tvName.setTextSize(16);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvName);

        TextView tvDescription = new TextView(requireContext());
        tvDescription.setText(description != null ? description : "");
        tvDescription.setTextColor(requireContext().getColor(R.color.color_text_secondary));
        tvDescription.setTextSize(13);
        tvDescription.setPadding(0, dp(4), 0, dp(6));
        card.addView(tvDescription);

        TextView tvMeta = new TextView(requireContext());
        tvMeta.setText("Target: " + (target != null ? target : 0) + " contributions");
        tvMeta.setTextColor(requireContext().getColor(R.color.color_green_header));
        tvMeta.setTextSize(13);
        tvMeta.setPadding(0, 0, 0, dp(10));
        card.addView(tvMeta);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnEdit = makeSmallButton("Edit");
        TextView btnEnd = makeSmallButton("End");

        btnEdit.setOnClickListener(v -> prefillFormForEdit(doc));
        btnEnd.setOnClickListener(v -> endChallenge(doc.getId()));

        buttonRow.addView(btnEdit);
        buttonRow.addView(btnEnd);

        card.addView(buttonRow);
        llStaffActiveChallenges.addView(card);
    }

    /**
     * Creates a small action button for active challenge rows.
     *
     * @param text button text
     * @return configured TextView button
     */
    private TextView makeSmallButton(String text) {
        TextView button = new TextView(requireContext());
        button.setText(text);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTextColor(requireContext().getColor(R.color.white));
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setBackgroundResource(R.drawable.bg_card_amber);
        button.setPadding(dp(14), 0, dp(14), 0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(40),
                1
        );
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);

        button.setClickable(true);
        button.setFocusable(true);

        return button;
    }

    /**
     * Prefills the challenge creation form using an existing challenge.
     *
     * @param doc challenge document to copy into the form
     */
    private void prefillFormForEdit(QueryDocumentSnapshot doc) {
        etChallengeName.setText(doc.getString("name"));
        etChallengeDescription.setText(doc.getString("description"));

        Long target = doc.getLong("target");
        etTarget.setText(target != null ? String.valueOf(target) : "");

        Timestamp start = doc.getTimestamp("startDate");
        Timestamp end = doc.getTimestamp("endDate");

        if (start != null) {
            etStartDate.setText(dateFormat.format(start.toDate()));
        }

        if (end != null) {
            etEndDate.setText(dateFormat.format(end.toDate()));
        }

        clearActivityChecks();

        Object allowedObj = doc.get("allowedActivities");
        if (allowedObj instanceof List) {
            List<?> allowed = (List<?>) allowedObj;
            cbCycling.setChecked(allowed.contains("Cycling"));
            cbTransit.setChecked(allowed.contains("Public Transit"));
            cbRecycling.setChecked(allowed.contains("Recycling"));
            cbPlantBased.setChecked(allowed.contains("Plant-based meal"));
            cbReusable.setChecked(allowed.contains("Reusable cup"));
            cbComposting.setChecked(allowed.contains("Composting"));
            cbWalked.setChecked(allowed.contains("Walked"));
            cbEnergy.setChecked(allowed.contains("Energy saving"));
        }

        Toast.makeText(getContext(),
                "Form filled. Publish to create an updated copy.",
                Toast.LENGTH_LONG).show();
    }

    /**
     * Ends an active challenge by setting active=false.
     *
     * @param challengeId Firestore challenge document ID
     */
    private void endChallenge(String challengeId) {
        db.collection("challenges")
                .document(challengeId)
                .update("active", false)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), "Challenge ended.", Toast.LENGTH_SHORT).show();
                    loadActiveChallenges();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Could not end challenge: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Clears the create challenge form after a successful publish.
     */
    private void clearForm() {
        etChallengeName.setText("");
        etChallengeDescription.setText("");
        etStartDate.setText("");
        etEndDate.setText("");
        etTarget.setText("");
        clearActivityChecks();
        tvActivityError.setVisibility(View.GONE);
    }

    /**
     * Unchecks all activity checkboxes.
     */
    private void clearActivityChecks() {
        cbCycling.setChecked(false);
        cbTransit.setChecked(false);
        cbRecycling.setChecked(false);
        cbPlantBased.setChecked(false);
        cbReusable.setChecked(false);
        cbComposting.setChecked(false);
        cbWalked.setChecked(false);
        cbEnergy.setChecked(false);
    }

    /**
     * Converts dp to pixels.
     *
     * @param value dp value
     * @return pixel value
     */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}