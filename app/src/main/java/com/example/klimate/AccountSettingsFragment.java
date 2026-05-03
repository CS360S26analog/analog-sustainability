/**
 * AccountSettingsFragment.java
 *
 * Lets the logged-in user update their display name and profile picture.
 *
 * Role in design: Part of the View layer. Reads and writes the current
 * user's profile fields in the users Firestore collection.
 *
 * @author Team Analog
 */
package com.example.klimate;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AccountSettingsFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private EditText etUsername;
    private TextView tvInitialsPreview;
    private ImageView ivPhotoPreview;
    private Button btnSave;

    private Uri selectedImageUri;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;

                selectedImageUri = uri;

                if (ivPhotoPreview != null) {
                    ivPhotoPreview.setImageURI(uri);
                    ivPhotoPreview.setVisibility(View.VISIBLE);
                }

                if (tvInitialsPreview != null) {
                    tvInitialsPreview.setVisibility(View.GONE);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account_settings, container, false);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        TextView btnBack = view.findViewById(R.id.btn_back_account_settings);
        etUsername = view.findViewById(R.id.et_username);
        tvInitialsPreview = view.findViewById(R.id.tv_initials_preview);
        ivPhotoPreview = view.findViewById(R.id.iv_photo_preview);
        Button btnChoosePhoto = view.findViewById(R.id.btn_choose_photo);
        btnSave = view.findViewById(R.id.btn_save_account);

        btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        btnChoosePhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        btnSave.setOnClickListener(v -> saveAccountChanges());

        loadCurrentProfile();

        return view;
    }

    private void loadCurrentProfile() {
        if (currentUser == null) return;

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String displayName = doc.getString("displayName");
                    String profilePhotoBase64 = doc.getString("profilePhotoBase64");

                    if (!TextUtils.isEmpty(displayName)) {
                        etUsername.setText(displayName);
                        tvInitialsPreview.setText(getInitials(displayName));
                    }

                    showSavedPhoto(profilePhotoBase64);
                });
    }

    private void saveAccountChanges() {
        if (currentUser == null) return;

        String username = etUsername.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Enter a username");
            return;
        }

        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", username);

        if (selectedImageUri != null) {
            String encodedImage = encodeImageToBase64(selectedImageUri);

            if (!TextUtils.isEmpty(encodedImage)) {
                updates.put("profilePhotoBase64", encodedImage);
            }
        }

        db.collection("users")
                .document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), "Account updated.", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    getParentFragmentManager().popBackStack();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(getContext(), "Failed to update account.", Toast.LENGTH_SHORT).show();
                });
    }

    private String encodeImageToBase64(Uri uri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);

            if (originalBitmap == null) {
                return "";
            }

            Bitmap squareBitmap = cropToSquare(originalBitmap);
            Bitmap smallBitmap = Bitmap.createScaledBitmap(squareBitmap, 300, 300, true);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            smallBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);

            byte[] imageBytes = outputStream.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.DEFAULT);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Could not read image.", Toast.LENGTH_SHORT).show();
            return "";
        }
    }

    private Bitmap cropToSquare(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);

        int x = (width - size) / 2;
        int y = (height - size) / 2;

        return Bitmap.createBitmap(bitmap, x, y, size, size);
    }

    private void showSavedPhoto(String profilePhotoBase64) {
        if (TextUtils.isEmpty(profilePhotoBase64)) return;

        try {
            byte[] imageBytes = Base64.decode(profilePhotoBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            if (bitmap == null) return;

            ivPhotoPreview.setImageBitmap(bitmap);
            ivPhotoPreview.setVisibility(View.VISIBLE);
            tvInitialsPreview.setVisibility(View.GONE);
        } catch (Exception ignored) {
        }
    }

    private String getInitials(String displayName) {
        if (TextUtils.isEmpty(displayName)) return "ST";

        String cleanName = displayName.trim();
        if (cleanName.isEmpty()) return "ST";

        String[] parts = cleanName.split("\\s+");

        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.getDefault());
        }

        if (cleanName.length() >= 2) {
            return cleanName.substring(0, 2).toUpperCase(Locale.getDefault());
        }

        return cleanName.substring(0, 1).toUpperCase(Locale.getDefault());
    }
}