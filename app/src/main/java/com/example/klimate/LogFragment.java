/**
 * LogFragment.java
 *
 * Allows the user to select a sustainability activity and submit it
 * as either a quick log or a pending verification log.
 * For verified logs, the user can attach a proof photo which is uploaded
 * to Firebase Storage and saved to Firestore under the proofUrl field.
 *
 * Quantifiable activities (Cycling, Walking, etc.) show a dynamic slider
 * injected directly into the selected activity card. Points are calculated
 * live as the slider moves. Flat-rate activities (Composting, Energy saving)
 * show no slider — fixed points are awarded on submission.
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
import com.example.klimate.local.ActivityLogDao;
import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.example.klimate.local.ActivityLogEntity;
import com.example.klimate.local.AppDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Returns the unit label for a given activity type.
     * Returns empty string for flat-rate activities that have no unit.
     *
     * @param activityType the activity name
     * @return unit string e.g. "km", "meal", "item", or ""
     */
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
                return ""; // Composting and Energy saving — flat rate, no unit
        }
    }

    /**
     * Returns the maximum slider value for a given activity type.
     *
     * @param activityType the activity name
     * @return maximum quantity the slider can reach
     */
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

    /**
     * Returns the points awarded per unit of quantity for a given activity.
     * For flat-rate activities this is the total fixed award.
     *
     * @param activityType the activity name
     * @return points per unit, or fixed points for flat-rate activities
     */
    private int getPointsPerUnit(String activityType) {
        switch (activityType) {
            case "Cycling":          return 3;
            case "Walked":           return 2;
            case "Public Transit":   return 1;
            case "Plant-based meal": return 25;
            case "Reusable cup":     return 10;
            case "Recycling":        return 2;
            case "Composting":       return 20; // flat
            case "Energy saving":    return 15; // flat
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

    /**
     * Returns the slider question label for a given activity type.
     *
     * @param activityType the activity name
     * @return human-readable question string shown above the slider
     */
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

    /**
     * Returns whether an activity shows a quantity slider.
     * Composting and Energy saving are flat rate — no slider shown.
     *
     * @param activityType the activity name
     * @return true if the activity has a quantity slider
     */
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

    /**
     * Calculates total points for a log based on activity type and quantity.
     * For flat-rate activities quantity is ignored — fixed points returned.
     *
     * @param activityType the activity name
     * @param quantity     the slider quantity selected by the user
     * @return total points to award for this log
     */
    private int calculatePoints(String activityType, int quantity) {
        if (!isQuantifiable(activityType)) {
            return getPointsPerUnit(activityType);
        }
        return getPointsPerUnit(activityType) * quantity;
    }

    /**
     * Builds a correctly pluralised unit label for a given quantity.
     *
     * @param activityType the activity name
     * @param quantity     the current slider value
     * @return unit string e.g. "km", "meal", "meals", "item", "items"
     */
    private String buildUnitLabel(String activityType, int quantity) {
        String unit = getUnit(activityType);
        switch (unit) {
            case "meal": return quantity == 1 ? "meal"  : "meals";
            case "cup":  return quantity == 1 ? "cup"   : "cups";
            case "item": return quantity == 1 ? "item"  : "items";
            default:     return unit; // km does not pluralise
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
            });

            verifiedTab.setOnClickListener(v -> {
                selectedStatus = "pending_verification";
                setActiveTab(verifiedTab, quickTab);
                updateProofSection();
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

    /**
     * Creates a temporary file in the app cache, obtains a FileProvider Uri
     * for it, and launches the system camera. The Uri is stored in
     * cameraImageUri so the TakePicture launcher can read it on return.
     */
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

    /**
     * Deselects all cards and removes any previously injected inline slider.
     * Called before selecting a new card.
     *
     * @param cards the full array of activity card views
     */
    private void deselectAll(LinearLayout[] cards) {
        for (LinearLayout c : cards) {
            c.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card));

            // Remove inline slider that was injected into this card
            View existingSlider = c.findViewWithTag("inline_slider");
            if (existingSlider != null) {
                c.removeView(existingSlider);
            }
        }
    }

    /**
     * Selects an activity card, highlights it, and injects the inline slider.
     *
     * @param cards the full array of activity card views
     * @param card  the card that was tapped
     * @param name  the activity name associated with that card
     */
    private void selectCard(LinearLayout[] cards, LinearLayout card, String name) {
        deselectAll(cards);
        card.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card_selected));
        selectedCard         = card;
        selectedActivityName = name;
        showInlineSlider(card, name);
    }

    /**
     * Builds a quantity slider UI programmatically and injects it into
     * the bottom of the selected activity card. Tagged "inline_slider"
     * so deselectAll() can find and remove it when another card is tapped.
     *
     * For flat-rate activities (Composting, Energy saving) no slider is
     * injected — the log button simply updates with the fixed point value.
     *
     * @param card         the selected activity card LinearLayout
     * @param activityType the selected activity name
     */
    private void showInlineSlider(LinearLayout card, String activityType) {
        if (!isQuantifiable(activityType)) {
            // Flat rate — just update the log button, no slider needed
            selectedQuantity = 1;
            int pts = calculatePoints(activityType, 1);
            if (btnLogActivity != null) {
                btnLogActivity.setText("Log Activity  •  +" + pts + " pts");
            }
            return;
        }

        int dp4  = dpToPx(4);
        int dp8  = dpToPx(8);
        int dp12 = dpToPx(12);

        // Outer container — tagged so deselectAll() can remove it
        LinearLayout sliderContainer = new LinearLayout(requireContext());
        sliderContainer.setTag("inline_slider");
        sliderContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = dp12;
        sliderContainer.setLayoutParams(containerParams);

        // Question label
        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(getQuantityQuestion(activityType));
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(0xFF4A8A4A);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        sliderContainer.addView(tvLabel);

        // Slider row: SeekBar + value label side by side
        LinearLayout sliderRow = new LinearLayout(requireContext());
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sliderRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sliderRowParams.topMargin = dp8;
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

        // Value label — shows e.g. "1 km", "3 meals"
        TextView tvValue = new TextView(requireContext());
        tvValue.setText("1 " + buildUnitLabel(activityType, 1));
        tvValue.setTextSize(13);
        tvValue.setTextColor(0xFFC17B2F);
        tvValue.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(dp8);
        tvValue.setLayoutParams(valueParams);

        sliderRow.addView(seekBar);
        sliderRow.addView(tvValue);
        sliderContainer.addView(sliderRow);

        // Points row: "Points earned" label on the left, dynamic value on right
        LinearLayout ptsRow = new LinearLayout(requireContext());
        ptsRow.setOrientation(LinearLayout.HORIZONTAL);
        ptsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ptsRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ptsRowParams.topMargin = dp4;
        ptsRow.setLayoutParams(ptsRowParams);

        TextView tvPtsLabel = new TextView(requireContext());
        tvPtsLabel.setText("Points earned");
        tvPtsLabel.setTextSize(11);
        tvPtsLabel.setTextColor(0xFFEEF7EE);
        LinearLayout.LayoutParams ptsLabelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvPtsLabel.setLayoutParams(ptsLabelParams);
        ptsRow.addView(tvPtsLabel);

        TextView tvPts = new TextView(requireContext());
        int initialPts = calculatePoints(activityType, 1);
        tvPts.setText("+" + initialPts + " pts");
        tvPts.setTextSize(13);
        tvPts.setTextColor(0xFFC17B2F);
        tvPts.setTypeface(null, Typeface.BOLD);
        ptsRow.addView(tvPts);

        sliderContainer.addView(ptsRow);

        // Inject into card
        card.addView(sliderContainer);

        // Initialise state
        selectedQuantity = 1;
        if (btnLogActivity != null) {
            btnLogActivity.setText("Log Activity  •  1 " + buildUnitLabel(activityType, 1) + "  •  +" + initialPts + " pts");
        }

        // SeekBar listener — updates value label, pts label, and log button live
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int quantity = Math.max(1, progress);
                selectedQuantity = quantity;

                tvValue.setText(quantity + " " + buildUnitLabel(activityType, quantity));

                int pts = calculatePoints(activityType, quantity);
                tvPts.setText("+" + pts + " pts");

                if (btnLogActivity != null) {
                    btnLogActivity.setText("Log Activity  •  " + quantity + " "
                            + buildUnitLabel(activityType, quantity) + "  •  +" + pts + " pts");
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
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

        int calculatedPoints = calculatePoints(selectedActivityName, selectedQuantity);
        DocumentReference docRef = db.collection("activity_logs").document();

        if ("pending_verification".equals(selectedStatus)) {
            uploadProofAndSaveLog(docRef, currentUser.getUid(), calculatedPoints, cards);
        } else {
            saveLogDocument(docRef, currentUser.getUid(), calculatedPoints, null, cards);
        }
    }

    /**
     * Compresses the selected proof image then uploads the bytes to
     * Firebase Storage. Falls back to uploading the original file if
     * compression fails so the flow never breaks.
     *
     * @param docRef Firestore document reference for this log
     * @param userId UID of the submitting user
     * @param points calculated points for this log
     * @param cards  activity card array used to reset form on success
     */
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

        double co2SavedKg = CarbonCalculator.calculateCo2SavedKg(
                selectedActivityName, selectedQuantity);

        // ── 1. Write to Room immediately (instant UI feedback) ───────────────
        ActivityLogEntity entity = new ActivityLogEntity(
                docRef.getId(),         // logId already generated
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
            AppDatabase app_database = AppDatabase.getInstance(requireContext());
            int localId = (int) app_database.activityLogDao().insert(entity);

            // Increment user stats in Room immediately (optimistic update)
            app_database.userDao().incrementPoints(userId, points);
            app_database.userDao().incrementCo2(userId, co2SavedKg);

            // ── 2. Push to Firestore in background ───────────────────────────
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
                        // Mark synced in Room
                        executor.execute(() ->
                                app_database.activityLogDao()
                                        .markSynced(localId, docRef.getId(), "synced")
                        );

                        // Firestore user stats update (keeps Firestore in sync with Room)
                        db.collection("users")
                                .document(userId)
                                .update("co2SavedKg", FieldValue.increment(co2SavedKg));

                        new PointsManager().awardBasePoints(context, userId, points);

                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                String message = "pending_verification".equals(selectedStatus)
                                        ? "Verified log submitted with proof"
                                        : selectedActivityName + " logged — +" + points + " pts ✅";
                                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                                resetForm(cards);
                                setLogButtonEnabled(true);
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Mark failed in Room — SyncWorker will retry
                        executor.execute(() ->
                                app_database.activityLogDao().markFailed(localId)
                        );

                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                boolean isVerified =
                                        "pending_verification".equals(selectedStatus);

                                if (isVerified) {
                                    // Verified logs: tell the user explicitly
                                    Toast.makeText(getContext(),
                                            "Saved locally — will upload when connected 📶",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    // Quick logs: silently queue, show success to keep UX smooth
                                    Toast.makeText(getContext(),
                                            selectedActivityName + " logged ✅",
                                            Toast.LENGTH_SHORT).show();
                                }

                                resetForm(cards);
                                setLogButtonEnabled(true);

                                // Trigger immediate retry if connected
                                MainActivity.triggerImmediateSync(requireContext());
                            });
                        }
                    });
        });
    }

    private void resetForm(@NonNull LinearLayout[] cards) {
        deselectAll(cards); // also removes any inline slider
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
                "Log this when you use a bicycle instead of a car. Use the slider to select how many km you cycled. +3 pts per km.",
                "Use this when you travel by bus, train, or shuttle. Slide to select the distance. +1 pt per km.",
                "Log this when you recycle paper, plastic, cans, or other materials. Select how many items. +2 pts per item.",
                "Use this when you choose a vegetarian or vegan meal. Select how many meals. +25 pts per meal.",
                "Log this when you use your own reusable cup. Select how many times. +10 pts per cup.",
                "Use this when food scraps are composted. Flat reward of +20 pts per log.",
                "Log this when you walk instead of using a fuel-based ride. Select how many km. +2 pts per km.",
                "Use this when you reduce electricity use like switching off lights. Flat reward of +15 pts per log.",
                "Pick an activity card first. For quantifiable activities a slider appears inside the card — drag it to set your amount. Use Quick Log for a simple submission, or Verified Log to attach proof."
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

    /**
     * Compresses and resizes a Uri-referenced image before upload.
     * Resizes so the longest side is at most 1080px preserving aspect ratio,
     * then compresses to JPEG at 80% quality.
     *
     * @param context  application context for ContentResolver
     * @param imageUri the Uri returned by the image picker or camera
     * @return compressed image as a byte array, or null if processing failed
     */
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