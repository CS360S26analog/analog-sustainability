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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * LogFragment.java
 *
 * Allows the user to select a sustainability activity and submit it
 * as either a quick log or a pending verification log.
 * For verified logs, the user can attach a proof photo which is uploaded
 * to Firebase Storage and saved to Firestore under the proofUrl field.
 *
 * Activities that are quantifiable show a slider so the user can select
 * how much they did. Points are calculated dynamically based on
 * quantity × rate per unit.
 */
public class LogFragment extends Fragment {

    private LinearLayout selectedCard = null;
    private String selectedActivityName = null;
    private String selectedStatus = "quick";
    private Uri selectedProofUri = null;
    private Uri cameraImageUri = null;
    private int selectedQuantity = 1;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private View proofSection;
    private View quantitySection;
    private ImageView imageProofPreview;
    private TextView textProofStatus;
    private TextView btnSelectProof;
    private TextView btnTakeProof;
    private TextView btnLogActivity;
    private View quickTab;
    private View verifiedTab;
    private NestedScrollView logScrollView;
    private SeekBar seekbarQuantity;
    private TextView tvQuantityLabel;
    private TextView tvQuantityValue;
    private TextView tvQuantityPoints;

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
            case "Cycling":
                return 20;

            case "Walked":
                return 15;

            case "Public Transit":
                return 50;

            case "Plant-based meal":
                return 3;

            case "Reusable cup":
                return 5;

            case "Recycling":
                return 20;

            default:
                return 1;
        }
    }

    private int getPointsPerUnit(String activityType) {
        switch (activityType) {
            case "Cycling":
                return 3;

            case "Walked":
                return 2;

            case "Public Transit":
                return 1;

            case "Plant-based meal":
                return 25;

            case "Reusable cup":
                return 10;

            case "Recycling":
                return 2;

            case "Composting":
                return 20;

            case "Energy saving":
                return 15;

            default:
                return 5;
        }
    }

    private String getQuantityQuestion(String activityType) {
        switch (activityType) {
            case "Cycling":
                return "How far did you cycle?";

            case "Walked":
                return "How far did you walk?";

            case "Public Transit":
                return "How far did you travel by public transit?";

            case "Plant-based meal":
                return "How many plant-based meals did you have?";

            case "Reusable cup":
                return "How many times did you use a reusable cup?";

            case "Recycling":
                return "How many items did you recycle?";

            default:
                return "";
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
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

        proofSection = view.findViewById(R.id.card_verified_proof_section);
        quantitySection = view.findViewById(R.id.card_quantity_section);
        imageProofPreview = view.findViewById(R.id.image_verified_proof_preview);
        textProofStatus = view.findViewById(R.id.text_verified_proof_status);
        btnSelectProof = view.findViewById(R.id.btn_select_verified_proof);
        btnTakeProof = view.findViewById(R.id.btn_take_verified_photo);
        btnLogActivity = view.findViewById(R.id.btn_log_activity);
        quickTab = view.findViewById(R.id.btn_quick_log);
        verifiedTab = view.findViewById(R.id.btn_verified_log);
        logScrollView = view.findViewById(R.id.log_scroll_view);
        seekbarQuantity = view.findViewById(R.id.seekbar_quantity);
        tvQuantityLabel = view.findViewById(R.id.tv_quantity_label);
        tvQuantityValue = view.findViewById(R.id.tv_quantity_value);
        tvQuantityPoints = view.findViewById(R.id.tv_quantity_points);

        LinearLayout[] cards = new LinearLayout[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            final int index = i;
            cards[i] = view.findViewById(cardIds[i]);

            if (cards[i] != null) {
                cards[i].setOnClickListener(v ->
                        selectCard(cards, (LinearLayout) v, activityNames[index])
                );
            }
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

        if (seekbarQuantity != null) {
            seekbarQuantity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int quantity = Math.max(1, progress);
                    selectedQuantity = quantity;
                    updateQuantityDisplay(quantity);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
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

    private void updateQuantityDisplay(int quantity) {
        if (selectedActivityName == null) return;

        String unit = getUnit(selectedActivityName);
        int pts = calculatePoints(selectedActivityName, quantity);

        String unitLabel;
        if (unit.equals("km")) {
            unitLabel = "km";
        } else if (unit.equals("meal")) {
            unitLabel = quantity == 1 ? "meal" : "meals";
        } else if (unit.equals("cup")) {
            unitLabel = quantity == 1 ? "cup" : "cups";
        } else if (unit.equals("item")) {
            unitLabel = quantity == 1 ? "item" : "items";
        } else {
            unitLabel = unit;
        }

        if (tvQuantityValue != null) {
            tvQuantityValue.setText(quantity + " " + unitLabel);
        }

        if (tvQuantityPoints != null) {
            tvQuantityPoints.setText("+" + pts + " pts");
        }

        if (btnLogActivity != null) {
            btnLogActivity.setText("Log Activity  •  +" + pts + " pts");
        }
    }

    private void showQuantitySlider(String activityType) {
        if (quantitySection == null) return;

        if (!isQuantifiable(activityType)) {
            quantitySection.setVisibility(View.GONE);
            selectedQuantity = 1;

            int pts = calculatePoints(activityType, 1);
            if (btnLogActivity != null) {
                btnLogActivity.setText("Log Activity  •  +" + pts + " pts");
            }

            return;
        }

        quantitySection.setVisibility(View.VISIBLE);

        if (tvQuantityLabel != null) {
            tvQuantityLabel.setText(getQuantityQuestion(activityType));
        }

        if (seekbarQuantity != null) {
            seekbarQuantity.setMax(getMaxQuantity(activityType));
            seekbarQuantity.setProgress(1);
        }

        selectedQuantity = 1;
        updateQuantityDisplay(1);
    }

    private void applyIncomingArguments(@NonNull LinearLayout[] cards) {
        Bundle args = getArguments();
        if (args == null) return;

        String preselectedActivity = args.getString("preselected_activity");
        boolean openVerifiedFlow = args.getBoolean("open_verified_flow", false);
        shouldAutoOpenProofPicker = args.getBoolean("open_proof_picker", false);

        if (preselectedActivity != null) {
            for (int i = 0; i < activityNames.length; i++) {
                if (activityNames[i].equals(preselectedActivity) && cards[i] != null) {
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

        int white = requireContext().getColor(android.R.color.white);
        int secondary = requireContext().getColor(R.color.color_text_secondary);

        animateTextColor((TextView) active, secondary, white);
        animateTextColor((TextView) inactive, white, secondary);
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
            if (imageProofPreview != null) {
                imageProofPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                imageProofPreview.setScaleType(ImageView.ScaleType.CENTER);
            }

            if (textProofStatus != null) {
                textProofStatus.setText("Required for verified logs");
            }

            if (btnSelectProof != null) {
                btnSelectProof.setText("Choose Photo");
            }
        } else {
            if (imageProofPreview != null) {
                imageProofPreview.setImageURI(selectedProofUri);
                imageProofPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }

            if (textProofStatus != null) {
                textProofStatus.setText("Photo selected");
            }

            if (btnSelectProof != null) {
                btnSelectProof.setText("Choose Another Photo");
            }
        }
    }

    private void selectCard(LinearLayout[] cards, LinearLayout card, String name) {
        deselectAll(cards);

        card.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card_selected));
        selectedCard = card;
        selectedActivityName = name;

        showQuantitySlider(name);
    }

    private void deselectAll(LinearLayout[] cards) {
        for (LinearLayout c : cards) {
            if (c != null) {
                c.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card));
            }
        }
    }

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
                        Toast.makeText(
                                getContext(),
                                "Failed to upload proof photo: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
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
                        Toast.makeText(
                                getContext(),
                                "Failed to upload proof photo: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    });
        }
    }

    private void saveLogDocument(@NonNull DocumentReference docRef,
                                 @NonNull String userId,
                                 int points,
                                 @Nullable String proofUrl,
                                 @NonNull LinearLayout[] cards) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("logId", docRef.getId());
        logData.put("userId", userId);
        logData.put("activityType", selectedActivityName);
        logData.put("status", selectedStatus);
        logData.put("points", points);
        logData.put("bonusPoints", 0);
        logData.put("proofUrl", proofUrl);
        logData.put("voteCount", 0);
        logData.put("quantity", selectedQuantity);
        logData.put("unit", getUnit(selectedActivityName));
        logData.put("timestamp", Timestamp.now());

        docRef.set(logData)
                .addOnSuccessListener(unused -> {
                    new PointsManager().awardBasePoints(userId, points);

                    String message = "pending_verification".equals(selectedStatus)
                            ? "Verified log submitted with proof"
                            : selectedActivityName + " logged — +" + points + " pts ✅";

                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    resetForm(cards);
                    setLogButtonEnabled(true);
                })
                .addOnFailureListener(e -> {
                    setLogButtonEnabled(true);
                    Toast.makeText(
                            getContext(),
                            "Failed to save log: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void resetForm(@NonNull LinearLayout[] cards) {
        deselectAll(cards);

        selectedCard = null;
        selectedActivityName = null;
        selectedProofUri = null;
        cameraImageUri = null;
        selectedStatus = "quick";
        selectedQuantity = 1;
        shouldAutoOpenProofPicker = false;

        if (quantitySection != null) {
            quantitySection.setVisibility(View.GONE);
        }

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

    private void showLogInfoDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_info_card, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
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
                "Use this when food scraps are composted. Flat rate: +20 pts.",
                "Log this when you walk instead of using a fuel-based ride. Select how many km. +2 pts per km.",
                "Use this when you reduce electricity use. Flat rate: +15 pts.",
                "Pick an activity card first, then use the slider if shown. Use Quick Log for a simple submission, or Verified Log to attach proof."
        };

        StringBuilder fullText = new StringBuilder();
        int[] headingStarts = new int[headings.length];
        int[] headingEnds = new int[headings.length];

        for (int i = 0; i < headings.length; i++) {
            headingStarts[i] = fullText.length();
            fullText.append(headings[i]);
            headingEnds[i] = fullText.length();
            fullText.append("\n");
            fullText.append(bodies[i]);

            if (i < headings.length - 1) {
                fullText.append("\n\n");
            }
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

    @Nullable
    private byte[] compressImage(@NonNull Context context, @NonNull Uri imageUri) {
        final int MAX_IMAGE_DIMENSION = 1080;
        final int JPEG_QUALITY = 80;

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) return null;

            Bitmap original = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (original == null) return null;

            int width = original.getWidth();
            int height = original.getHeight();

            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                float scale;

                if (width >= height) {
                    scale = (float) MAX_IMAGE_DIMENSION / width;
                } else {
                    scale = (float) MAX_IMAGE_DIMENSION / height;
                }

                int newWidth = Math.round(width * scale);
                int newHeight = Math.round(height * scale);

                Bitmap resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
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
}