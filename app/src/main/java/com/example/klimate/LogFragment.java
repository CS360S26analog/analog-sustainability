/**
 * LogFragment.java
 *
 * Allows the user to select a sustainability activity and submit it
 * as either a quick log or a pending verification log.
 * For verified logs, the user can attach a proof photo which is uploaded
 * to Firebase Storage and saved to Firestore under the proofUrl field.
 *
 * Quantifiable activities (Cycling, Walking, etc.) show a dynamic slider
 * beneath the selected activity row. Verified logs calculate points
 * live as the slider moves. Quick logs save the activity and CO₂ impact
 * without awarding points.
 *
 * Role in design: Part of the UI/Controller layer. Collects user input,
 * uploads proof media when needed, and writes activity log documents.
 *
 * Outstanding issues: none.
 *
 * @author Izza
 * @author Haroon
 */
package com.example.klimate;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LogFragment extends Fragment {

    private LinearLayout selectedCard = null;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private String selectedActivityName = null;
    private String selectedStatus = "quick";
    private Uri selectedProofUri = null;
    private Uri cameraImageUri = null;
    private int selectedQuantity = 1;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private View proofSection;
    private ImageView imageProofPreview;
    private TextView textProofStatus;
    private TextView btnSelectProof;
    private TextView btnTakeProof;
    private TextView btnLogActivity;
    private View quickTab;
    private View verifiedTab;
    private NestedScrollView logScrollView;
    private LinearLayout sliderSlotRow1;
    private LinearLayout sliderSlotRow2;
    private LinearLayout sliderSlotRow3;
    private LinearLayout sliderSlotRow4;

    private boolean shouldAutoOpenProofPicker = false;

    private final int[] cardIds = {
            R.id.card_cycling,
            R.id.card_transit,
            R.id.card_recycling,
            R.id.card_plantbased,
            R.id.card_reusable,
            R.id.card_composting,
            R.id.card_walked,
            R.id.card_energy
    };

    private final String[] activityNames = {
            "Cycling",
            "Public Transit",
            "Recycling",
            "Plant-based meal",
            "Reusable cup",
            "Composting",
            "Walked",
            "Energy saving"
    };

    // -------------------------------------------------------------------
    // Activity configuration — unit, slider max, pts per unit
    // -------------------------------------------------------------------

    private String getUnit(String activityType) {
        switch (activityType) {
            case "Cycling":
            case "Walked":
            case "Public Transit":
                return "km";
            case "Plant-based meal":
                return "meal";
            case "Reusable cup":
                return "cup";
            case "Recycling":
                return "item";
            default:
                return "";
        }
    }

    private int getMaxQuantity(String activityType) {
        switch (activityType) {
            case "Cycling":          return 20;
            case "Walked":           return 15;
            case "Public Transit":   return 50;
            case "Plant-based meal": return 3;
            case "Reusable cup":     return 5;
            case "Recycling":        return 20;
            default:                 return 1;
        }
    }

    private int getPointsPerUnit(String activityType) {
        switch (activityType) {
            case "Cycling":          return 3;
            case "Walked":           return 2;
            case "Public Transit":   return 1;
            case "Plant-based meal": return 25;
            case "Reusable cup":     return 10;
            case "Recycling":        return 2;
            case "Composting":       return 20;
            case "Energy saving":    return 15;
            default:                 return 5;
        }
    }

    // -------------------------------------------------------------------
    // Activity result launchers
    // -------------------------------------------------------------------

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;

                selectedProofUri = uri;

                if (imageProofPreview != null) {
                    imageProofPreview.setImageURI(uri);
                    imageProofPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }

                if (textProofStatus != null) {
                    textProofStatus.setText("Photo selected");
                }

                if (btnSelectProof != null) {
                    btnSelectProof.setText("Choose Another Photo");
                }
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) {
                    selectedProofUri = cameraImageUri;

                    if (imageProofPreview != null) {
                        imageProofPreview.setImageURI(selectedProofUri);
                        imageProofPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }

                    if (textProofStatus != null) {
                        textProofStatus.setText("Photo captured");
                    }

                    if (btnSelectProof != null) {
                        btnSelectProof.setText("Choose Another Photo");
                    }
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    Toast.makeText(
                            getContext(),
                            "Camera permission is needed to take proof photos",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

    // -------------------------------------------------------------------
    // Activity configuration — quantity helpers
    // -------------------------------------------------------------------

    private String getQuantityQuestion(String activityType) {
        switch (activityType) {
            case "Cycling":          return "How far did you cycle?";
            case "Walked":           return "How far did you walk?";
            case "Public Transit":   return "How far did you travel by public transit?";
            case "Plant-based meal": return "How many plant-based meals did you have?";
            case "Reusable cup":     return "How many times did you use a reusable cup?";
            case "Recycling":        return "How many items did you recycle?";
            default:                 return "";
        }
    }

    private boolean isQuantifiable(String activityType) {
        switch (activityType) {
            case "Cycling":
            case "Walked":
            case "Public Transit":
            case "Plant-based meal":
            case "Reusable cup":
            case "Recycling":
                return true;
            default:
                return false;
        }
    }

    private int calculatePoints(String activityType, int quantity) {
        if (!isQuantifiable(activityType)) {
            return getPointsPerUnit(activityType);
        }
        return getPointsPerUnit(activityType) * quantity;
    }

    private String buildUnitLabel(String activityType, int quantity) {
        String unit = getUnit(activityType);
        switch (unit) {
            case "meal": return quantity == 1 ? "meal"  : "meals";
            case "cup":  return quantity == 1 ? "cup"   : "cups";
            case "item": return quantity == 1 ? "item"  : "items";
            default:     return unit;
        }
    }

    private boolean isVerifiedMode() {
        return "pending_verification".equals(selectedStatus);
    }

    private void updateLogButtonText(String activityType, int quantity) {
        if (btnLogActivity == null || activityType == null) return;

        boolean isVerified = isVerifiedMode();

        if (!isQuantifiable(activityType)) {
            if (isVerified) {
                int pts = calculatePoints(activityType, 1);
                btnLogActivity.setText("Log Activity  •  +" + pts + " pts");
            } else {
                btnLogActivity.setText("Log Activity");
            }
            return;
        }

        String quantityText = quantity + " " + buildUnitLabel(activityType, quantity);

        if (isVerified) {
            int pts = calculatePoints(activityType, quantity);
            btnLogActivity.setText("Log Activity  •  " + quantityText + "  •  +" + pts + " pts");
        } else {
            btnLogActivity.setText("Log Activity  •  " + quantityText);
        }
    }

    // -------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        db      = FirebaseFirestore.getInstance();
        mAuth   = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        ImageView btnHistory = view.findViewById(R.id.btn_history);
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v ->
                    getParentFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new HistoryFragment())
                            .addToBackStack(null)
                            .commit()
            );
        }

        TextView btnLogInfo = view.findViewById(R.id.btn_log_info);
        if (btnLogInfo != null) {
            btnLogInfo.setOnClickListener(v -> showLogInfoDialog());
        }

        proofSection      = view.findViewById(R.id.card_verified_proof_section);
        imageProofPreview = view.findViewById(R.id.image_verified_proof_preview);
        textProofStatus   = view.findViewById(R.id.text_verified_proof_status);
        btnSelectProof    = view.findViewById(R.id.btn_select_verified_proof);
        btnTakeProof      = view.findViewById(R.id.btn_take_verified_photo);
        btnLogActivity    = view.findViewById(R.id.btn_log_activity);
        quickTab          = view.findViewById(R.id.btn_quick_log);
        verifiedTab       = view.findViewById(R.id.btn_verified_log);
        logScrollView     = view.findViewById(R.id.log_scroll_view);
        sliderSlotRow1    = view.findViewById(R.id.slider_slot_row_1);
        sliderSlotRow2    = view.findViewById(R.id.slider_slot_row_2);
        sliderSlotRow3    = view.findViewById(R.id.slider_slot_row_3);
        sliderSlotRow4    = view.findViewById(R.id.slider_slot_row_4);

        LinearLayout[] cards = new LinearLayout[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            final int index = i;
            cards[i] = view.findViewById(cardIds[i]);
            cards[i].setOnClickListener(v ->
                    selectCard(cards, (LinearLayout) v, activityNames[index])
            );
        }

        if (quickTab != null && verifiedTab != null) {
            quickTab.setOnClickListener(v -> {
                selectedStatus = "quick";
                setActiveTab(quickTab, verifiedTab);
                updateProofSection();
                if (selectedCard != null && selectedActivityName != null) {
                    showRowSlider(selectedCard, selectedActivityName);
                }
            });

            verifiedTab.setOnClickListener(v -> {
                selectedStatus = "pending_verification";
                setActiveTab(verifiedTab, quickTab);
                updateProofSection();
                if (selectedCard != null && selectedActivityName != null) {
                    showRowSlider(selectedCard, selectedActivityName);
                }
                scrollToProofSection(false);
            });

            setActiveTab(quickTab, verifiedTab);
        }

        updateProofSection();

        if (btnSelectProof != null) {
            btnSelectProof.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        }

        if (btnTakeProof != null) {
            btnTakeProof.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera();
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                }
            });
        }

        if (btnLogActivity != null) {
            btnLogActivity.setOnClickListener(v -> submitLog(cards));
        }

        applyIncomingArguments(cards);

        return view;
    }

    // -------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------

    private void launchCamera() {
        try {
            File imageFile = File.createTempFile(
                    "verified_proof_",
                    ".jpg",
                    requireContext().getCacheDir()
            );

            cameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    imageFile
            );

            takePictureLauncher.launch(cameraImageUri);

        } catch (Exception e) {
            Toast.makeText(
                    getContext(),
                    "Could not open camera: " + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // -------------------------------------------------------------------
    // Card selection + inline slider
    // -------------------------------------------------------------------

    private void deselectAll(LinearLayout[] cards) {
        for (LinearLayout c : cards) {
            c.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card));
        }

        hideAllSliderSlots();
    }

    private void selectCard(LinearLayout[] cards, LinearLayout card, String name) {
        deselectAll(cards);
        card.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card_selected));
        selectedCard         = card;
        selectedActivityName = name;
        showRowSlider(card, name);
    }

    private void hideAllSliderSlots() {
        LinearLayout[] slots = {
                sliderSlotRow1,
                sliderSlotRow2,
                sliderSlotRow3,
                sliderSlotRow4
        };

        for (LinearLayout slot : slots) {
            if (slot != null) {
                slot.removeAllViews();
                slot.setVisibility(View.GONE);
            }
        }
    }

    @Nullable
    private LinearLayout getSliderSlotForCard(@NonNull LinearLayout card) {
        int cardId = card.getId();

        if (cardId == R.id.card_cycling || cardId == R.id.card_transit) {
            return sliderSlotRow1;
        } else if (cardId == R.id.card_recycling || cardId == R.id.card_plantbased) {
            return sliderSlotRow2;
        } else if (cardId == R.id.card_reusable || cardId == R.id.card_composting) {
            return sliderSlotRow3;
        } else if (cardId == R.id.card_walked || cardId == R.id.card_energy) {
            return sliderSlotRow4;
        }

        return null;
    }

    private void showRowSlider(LinearLayout card, String activityType) {
        if (!isQuantifiable(activityType)) {
            selectedQuantity = 1;
            updateLogButtonText(activityType, 1);
            return;
        }

        LinearLayout targetSlot = getSliderSlotForCard(card);
        if (targetSlot == null) return;

        int dp4  = dpToPx(4);
        int dp8  = dpToPx(8);
        int dp10 = dpToPx(10);
        int dp14 = dpToPx(14);

        targetSlot.removeAllViews();
        targetSlot.setVisibility(View.VISIBLE);
        targetSlot.setBackground(requireContext().getDrawable(R.drawable.bg_leaderboard_row));
        targetSlot.setPadding(dp14, dp14, dp14, dp14);

        LinearLayout sliderContainer = new LinearLayout(requireContext());
        sliderContainer.setOrientation(LinearLayout.VERTICAL);
        sliderContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout headerRow = new LinearLayout(requireContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(getQuantityQuestion(activityType));
        tvLabel.setTextSize(13);
        tvLabel.setTextColor(0xFF2F5F3A);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(tvLabel);

        TextView tvPts = new TextView(requireContext());
        int initialPts = calculatePoints(activityType, 1);
        tvPts.setText(isVerifiedMode() ? "+" + initialPts + " pts" : "Verified logs earn points");
        tvPts.setTextSize(13);
        tvPts.setTextColor(0xFFC17B2F);
        tvPts.setTypeface(null, Typeface.BOLD);
        tvPts.setGravity(android.view.Gravity.END);
        headerRow.addView(tvPts);

        sliderContainer.addView(headerRow);

        LinearLayout sliderRow = new LinearLayout(requireContext());
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sliderRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sliderRowParams.topMargin = dp10;
        sliderRow.setLayoutParams(sliderRowParams);

        SeekBar seekBar = new SeekBar(requireContext());
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seekBar.setLayoutParams(seekParams);
        seekBar.setMax(getMaxQuantity(activityType));
        seekBar.setProgress(1);
        seekBar.getProgressDrawable().setColorFilter(
                new android.graphics.PorterDuffColorFilter(
                        0xFFC17B2F, android.graphics.PorterDuff.Mode.SRC_IN));
        seekBar.getThumb().setColorFilter(
                new android.graphics.PorterDuffColorFilter(
                        0xFFC17B2F, android.graphics.PorterDuff.Mode.SRC_IN));

        TextView tvValue = new TextView(requireContext());
        tvValue.setText("1 " + buildUnitLabel(activityType, 1));
        tvValue.setTextSize(14);
        tvValue.setTextColor(0xFFC17B2F);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setGravity(android.view.Gravity.CENTER);
        tvValue.setClickable(true);
        tvValue.setFocusable(true);
        tvValue.setBackgroundResource(R.drawable.bg_toggle_pill);
        tvValue.setPadding(dp10, dp4, dp10, dp4);
        tvValue.setContentDescription("Tap to type amount");
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(dp8);
        tvValue.setMinWidth(dpToPx(68));
        tvValue.setLayoutParams(valueParams);

        sliderRow.addView(seekBar);
        sliderRow.addView(tvValue);
        sliderContainer.addView(sliderRow);

        TextView tvHint = new TextView(requireContext());
        tvHint.setText("Drag the slider or tap the value to type an amount");
        tvHint.setTextSize(11);
        tvHint.setTextColor(0xFF7A8A78);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp4;
        tvHint.setLayoutParams(hintParams);
        sliderContainer.addView(tvHint);

        targetSlot.addView(sliderContainer);

        selectedQuantity = 1;
        updateLogButtonText(activityType, 1);

        tvValue.setOnClickListener(v -> showQuantityInputDialog(activityType, seekBar));

        scrollToSliderSlot(targetSlot);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int quantity = Math.max(1, progress);
                selectedQuantity = quantity;

                tvValue.setText(quantity + " " + buildUnitLabel(activityType, quantity));

                int pts = calculatePoints(activityType, quantity);
                tvPts.setText(isVerifiedMode() ? "+" + pts + " pts" : "Verified logs earn points");

                updateLogButtonText(activityType, quantity);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void showQuantityInputDialog(@NonNull String activityType, @NonNull SeekBar seekBar) {
        int maxQuantity = getMaxQuantity(activityType);

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(String.valueOf(selectedQuantity));
        input.setHint("1 - " + maxQuantity);
        input.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Enter amount")
                .setMessage("Type a whole number from 1 to " + maxQuantity + " "
                        + buildUnitLabel(activityType, maxQuantity) + ".")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            input.requestFocus();

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String rawValue = input.getText().toString().trim();

                if (rawValue.isEmpty()) {
                    input.setError("Enter a value");
                    return;
                }

                int typedQuantity;
                try {
                    typedQuantity = Integer.parseInt(rawValue);
                } catch (NumberFormatException e) {
                    input.setError("Use numbers only");
                    return;
                }

                if (typedQuantity < 1 || typedQuantity > maxQuantity) {
                    input.setError("Enter a number from 1 to " + maxQuantity);
                    return;
                }

                selectedQuantity = typedQuantity;
                seekBar.setProgress(typedQuantity);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void scrollToSliderSlot(@NonNull LinearLayout slot) {
        if (logScrollView == null) return;

        logScrollView.post(() -> {
            int targetY = Math.max(0, slot.getTop() - dpToPx(24));
            logScrollView.smoothScrollTo(0, targetY);
        });
    }

    // -------------------------------------------------------------------
    // Tab switching + proof section
    // -------------------------------------------------------------------

    private void applyIncomingArguments(@NonNull LinearLayout[] cards) {
        Bundle args = getArguments();
        if (args == null) return;

        String preselectedActivity = args.getString("preselected_activity");
        boolean openVerifiedFlow   = args.getBoolean("open_verified_flow", false);
        shouldAutoOpenProofPicker  = args.getBoolean("open_proof_picker", false);

        if (preselectedActivity != null) {
            for (int i = 0; i < activityNames.length; i++) {
                if (activityNames[i].equals(preselectedActivity)) {
                    selectCard(cards, cards[i], activityNames[i]);
                    break;
                }
            }
        }

        if (openVerifiedFlow && quickTab != null && verifiedTab != null) {
            selectedStatus = "pending_verification";
            setActiveTab(verifiedTab, quickTab);
            updateProofSection();
            if (selectedCard != null && selectedActivityName != null) {
                showRowSlider(selectedCard, selectedActivityName);
            }
            scrollToProofSection(shouldAutoOpenProofPicker);
        }
    }

    private void scrollToProofSection(boolean openPickerAfterScroll) {
        if (logScrollView == null || proofSection == null) return;

        logScrollView.post(() -> {
            logScrollView.smoothScrollTo(0, proofSection.getTop());

            if (openPickerAfterScroll && shouldAutoOpenProofPicker) {
                shouldAutoOpenProofPicker = false;
                pickImageLauncher.launch("image/*");
            }
        });
    }

    private void setActiveTab(View active, View inactive) {
        active.setBackgroundResource(R.drawable.bg_toggle_selected);
        inactive.setBackground(null);

        int white     = requireContext().getColor(android.R.color.white);
        int secondary = requireContext().getColor(R.color.color_text_secondary);

        animateTextColor((TextView) active,   secondary, white);
        animateTextColor((TextView) inactive, white,     secondary);
    }

    private void animateTextColor(TextView tv, int fromColor, int toColor) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        animator.setDuration(200);
        animator.addUpdateListener(anim -> tv.setTextColor((int) anim.getAnimatedValue()));
        animator.start();
    }

    private void updateProofSection() {
        if (proofSection == null) return;

        boolean isVerified = "pending_verification".equals(selectedStatus);
        proofSection.setVisibility(isVerified ? View.VISIBLE : View.GONE);

        if (!isVerified) return;

        if (selectedProofUri == null) {
            imageProofPreview.setImageResource(android.R.drawable.ic_menu_gallery);
            imageProofPreview.setScaleType(ImageView.ScaleType.CENTER);
            textProofStatus.setText("Required for verified logs");
            btnSelectProof.setText("Choose Photo");
        } else {
            imageProofPreview.setImageURI(selectedProofUri);
            imageProofPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            textProofStatus.setText("Photo selected");
            btnSelectProof.setText("Choose Another Photo");
        }
    }

    // -------------------------------------------------------------------
    // Log submission
    // -------------------------------------------------------------------

    private void submitLog(@NonNull LinearLayout[] cards) {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to submit a log", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCard == null || selectedActivityName == null) {
            Toast.makeText(getContext(), "Please select an activity first", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("pending_verification".equals(selectedStatus) && selectedProofUri == null) {
            Toast.makeText(getContext(), "Please attach a proof photo for verified logs", Toast.LENGTH_SHORT).show();
            return;
        }

        setLogButtonEnabled(false);

        boolean isVerifiedLog = LogPointsPolicy.shouldAwardPoints(selectedStatus);
        int calculatedPoints = LogPointsPolicy.pointsForStoredLog(
                selectedStatus,
                calculatePoints(selectedActivityName, selectedQuantity)
        );

        DocumentReference docRef = db.collection("activity_logs").document();

        if (isVerifiedLog) {
            uploadProofAndSaveLog(docRef, currentUser.getUid(), calculatedPoints, cards);
        } else {
            saveLogDocument(docRef, currentUser.getUid(), calculatedPoints, null, cards);
        }
    }

    private void uploadProofAndSaveLog(@NonNull DocumentReference docRef,
                                       @NonNull String userId,
                                       int points,
                                       @NonNull LinearLayout[] cards) {
        if (selectedProofUri == null) {
            setLogButtonEnabled(true);
            Toast.makeText(getContext(), "Please choose a proof photo first", Toast.LENGTH_SHORT).show();
            return;
        }

        StorageReference proofRef = storage.getReference()
                .child("proofs")
                .child(userId)
                .child(docRef.getId() + ".jpg");

        Context context = getContext();
        if (context == null) {
            setLogButtonEnabled(true);
            return;
        }

        byte[] compressedBytes = compressImage(context, selectedProofUri);

        if (compressedBytes != null) {
            StorageMetadata metadata = new StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .build();

            proofRef.putBytes(compressedBytes, metadata)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful() && task.getException() != null) {
                            throw task.getException();
                        }
                        return proofRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUri ->
                            saveLogDocument(docRef, userId, points, downloadUri.toString(), cards)
                    )
                    .addOnFailureListener(e -> {
                        setLogButtonEnabled(true);
                        Toast.makeText(getContext(),
                                "Failed to upload proof photo: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        } else {
            Log.w("LogFragment", "Compression failed — uploading original file as fallback");

            proofRef.putFile(selectedProofUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful() && task.getException() != null) {
                            throw task.getException();
                        }
                        return proofRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUri ->
                            saveLogDocument(docRef, userId, points, downloadUri.toString(), cards)
                    )
                    .addOnFailureListener(e -> {
                        setLogButtonEnabled(true);
                        Toast.makeText(getContext(),
                                "Failed to upload proof photo: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void saveLogDocument(@NonNull DocumentReference docRef,
                                 @NonNull String userId,
                                 int points,
                                 @Nullable String proofUrl,
                                 @NonNull LinearLayout[] cards) {

        final boolean isVerifiedLog = LogPointsPolicy.shouldAwardPoints(selectedStatus);

        double co2SavedKg = CarbonCalculator.calculateCo2SavedKg(
                selectedActivityName, selectedQuantity);

        ActivityLogEntity entity = new ActivityLogEntity(
                docRef.getId(),
                userId,
                selectedActivityName,
                selectedStatus,
                points,
                selectedQuantity,
                co2SavedKg,
                getUnit(selectedActivityName),
                proofUrl,
                System.currentTimeMillis()
        );

        Context context = requireContext();

        executor.execute(() -> {
            AppDatabase appDatabase = AppDatabase.getInstance(context);
            int localId = (int) appDatabase.activityLogDao().insert(entity);

            if (isVerifiedLog) {
                appDatabase.userDao().incrementPoints(userId, points);
            }

            appDatabase.userDao().incrementCo2(userId, co2SavedKg);
            NotificationHelper.scheduleMimiHungryReminder(context);

            Map<String, Object> logData = new HashMap<>();
            logData.put("logId",        docRef.getId());
            logData.put("userId",       userId);
            logData.put("activityType", selectedActivityName);
            logData.put("status",       selectedStatus);
            logData.put("points",       points);
            logData.put("bonusPoints",  0);
            logData.put("proofUrl",     proofUrl);
            logData.put("voteCount",    0);
            logData.put("quantity",     selectedQuantity);
            logData.put("co2SavedKg",   co2SavedKg);
            logData.put("unit",         getUnit(selectedActivityName));
            logData.put("timestamp",    Timestamp.now());

            docRef.set(logData)
                    .addOnSuccessListener(unused -> {
                        executor.execute(() ->
                                appDatabase.activityLogDao()
                                        .markSynced(localId, docRef.getId(), "synced")
                        );

                        db.collection("users")
                                .document(userId)
                                .update("co2SavedKg", FieldValue.increment(co2SavedKg));

                        if (isVerifiedLog) {
                            new PointsManager().awardBasePoints(context, userId, points);
                        }

                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                String message = LogPointsPolicy.buildSuccessMessage(selectedStatus, selectedActivityName);
                                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                                resetForm(cards);
                                setLogButtonEnabled(true);
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        executor.execute(() ->
                                appDatabase.activityLogDao().markFailed(localId)
                        );

                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (isVerifiedLog) {
                                    Toast.makeText(getContext(),
                                            "Saved locally — will upload when connected 📶",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(getContext(),
                                            selectedActivityName + " logged ✅",
                                            Toast.LENGTH_SHORT).show();
                                }

                                resetForm(cards);
                                setLogButtonEnabled(true);

                                MainActivity.triggerImmediateSync(requireContext());
                            });
                        }
                    });
        });
    }

    private void resetForm(@NonNull LinearLayout[] cards) {
        deselectAll(cards);
        selectedCard              = null;
        selectedActivityName      = null;
        selectedProofUri          = null;
        cameraImageUri            = null;
        selectedStatus            = "quick";
        selectedQuantity          = 1;
        shouldAutoOpenProofPicker = false;

        if (btnLogActivity != null) {
            btnLogActivity.setText("Log Activity");
        }

        if (quickTab != null && verifiedTab != null) {
            setActiveTab(quickTab, verifiedTab);
        }

        updateProofSection();
    }

    private void setLogButtonEnabled(boolean enabled) {
        if (btnLogActivity == null) return;
        btnLogActivity.setEnabled(enabled);
        btnLogActivity.setAlpha(enabled ? 1f : 0.6f);
    }

    // -------------------------------------------------------------------
    // Info dialog
    // -------------------------------------------------------------------

    private void showLogInfoDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_info_card, null);
        TextView tvTitle   = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvContent = dialogView.findViewById(R.id.tv_dialog_content);

        tvTitle.setText("About Log Activities");
        tvContent.setText(buildLogInfoText());

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Got it", null)
                .show();
    }

    private CharSequence buildLogInfoText() {
        String[] headings = {
                "🚲 Cycling",
                "🚌 Public Transit",
                "♻️ Recycling",
                "🥗 Plant-based meal",
                "💧 Reusable cup",
                "🌱 Composting",
                "👟 Walked",
                "💡 Energy saving",
                "How to use it"
        };

        String[] bodies = {
                "Log this when you use a bicycle instead of a car. Use the slider to select how many km you cycled. Verified logs earn +3 pts per km.",
                "Use this when you travel by bus, train, or shuttle. Slide to select the distance. Verified logs earn +1 pt per km.",
                "Log this when you recycle paper, plastic, cans, or other materials. Select how many items. Verified logs earn +2 pts per item.",
                "Use this when you choose a vegetarian or vegan meal. Select how many meals. Verified logs earn +25 pts per meal.",
                "Log this when you use your own reusable cup. Select how many times. Verified logs earn +10 pts per cup.",
                "Use this when food scraps are composted. Verified logs earn a flat reward of +20 pts per log.",
                "Log this when you walk instead of using a fuel-based ride. Select how many km. Verified logs earn +2 pts per km.",
                "Use this when you reduce electricity use like switching off lights. Verified logs earn a flat reward of +15 pts per log.",
                "Pick an activity card first. For quantifiable activities a full-width slider appears beneath the selected row. Quick Log saves the activity and CO₂ impact without points. Verified Log requires proof and can earn points."
        };

        StringBuilder fullText = new StringBuilder();
        int[] headingStarts = new int[headings.length];
        int[] headingEnds   = new int[headings.length];

        for (int i = 0; i < headings.length; i++) {
            headingStarts[i] = fullText.length();
            fullText.append(headings[i]);
            headingEnds[i] = fullText.length();
            fullText.append("\n");
            fullText.append(bodies[i]);
            if (i < headings.length - 1) fullText.append("\n\n");
        }

        SpannableString spannable = new SpannableString(fullText.toString());
        for (int i = 0; i < headings.length; i++) {
            spannable.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    headingStarts[i],
                    headingEnds[i],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        return spannable;
    }

    // -------------------------------------------------------------------
    // Image compression
    // -------------------------------------------------------------------

    @Nullable
    private byte[] compressImage(@NonNull Context context, @NonNull Uri imageUri) {
        final int MAX_IMAGE_DIMENSION = 1080;
        final int JPEG_QUALITY        = 80;

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) return null;

            Bitmap original = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (original == null) return null;

            int width  = original.getWidth();
            int height = original.getHeight();

            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                float scale = width >= height
                        ? (float) MAX_IMAGE_DIMENSION / width
                        : (float) MAX_IMAGE_DIMENSION / height;

                Bitmap resized = Bitmap.createScaledBitmap(
                        original,
                        Math.round(width  * scale),
                        Math.round(height * scale),
                        true
                );
                original.recycle();
                original = resized;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
            original.recycle();

            return outputStream.toByteArray();

        } catch (Exception e) {
            Log.e("LogFragment", "Image compression failed", e);
            return null;
        }
    }

    // -------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}