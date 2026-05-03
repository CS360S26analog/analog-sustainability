/**
 * RegisterActivity.java
 *
 * Handles new user registration for the Klimate app. Creates a Firebase
 * Authentication account using the provided email and password, then
 * writes a corresponding user document to the Firestore "users" collection.
 *
 * LUMS is currently the supported campus. More universities can be added later.
 *
 * If the user enters the correct staff code during registration, their
 * role is set to "staff", granting access to the staff management screens.
 * If the user enters an incorrect non-empty staff code, registration is
 * blocked and an inline error is shown.
 *
 * Role in design: Part of the Auth layer.
 *
 * Outstanding issues:
 * - No duplicate display name check across existing users.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import android.view.Window;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;

public class RegisterActivity extends AppCompatActivity {

    private static final String STAFF_CODE = "KLIMATE_STAFF_2026";
    private static final String SUPPORTED_UNIVERSITY = "LUMS";

    private EditText etName, etEmail, etPassword, etStaffCode;
    private TextView tvSelectedUniversity;

    private TextInputLayout tilName, tilEmail, tilPassword, tilStaffCode;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.color_auth_header));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.white));

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tilName = findViewById(R.id.til_name);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilStaffCode = findViewById(R.id.til_staff_code);

        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etStaffCode = findViewById(R.id.et_staff_code);
        tvSelectedUniversity = findViewById(R.id.tv_selected_university);

        Button btnRegister = findViewById(R.id.btn_register);
        View tvLogin = findViewById(R.id.tv_go_login);

        if (tvSelectedUniversity != null) {
            tvSelectedUniversity.setText(SUPPORTED_UNIVERSITY);
        }
        View universityPicker = findViewById(R.id.layout_university_picker);

        if (universityPicker != null) {
            universityPicker.setOnClickListener(v -> showUniversityDialog());
        }

        setupErrorClearing();

        btnRegister.setOnClickListener(v -> registerUser());
        tvLogin.setOnClickListener(v -> finish());

    }

    private void setupErrorClearing() {
        addErrorClearer(etName, tilName);
        addErrorClearer(etEmail, tilEmail);
        addErrorClearer(etPassword, tilPassword);
        addErrorClearer(etStaffCode, tilStaffCode);
    }

    private void addErrorClearer(EditText field, TextInputLayout layout) {
        if (field == null || layout == null) return;

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed.
            }
        });
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String staffCode = etStaffCode.getText().toString().trim();

        clearAllErrors();

        View firstInvalidField = validateInputs(name, email, password, staffCode);
        if (firstInvalidField != null) {
            firstInvalidField.requestFocus();
            return;
        }

        String role = TextUtils.isEmpty(staffCode) ? "student" : "staff";

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = mAuth.getCurrentUser();
                    if (firebaseUser == null) {
                        Toast.makeText(this, "Registration failed. Please try again.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String uid = firebaseUser.getUid();

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", uid);
                    userData.put("displayName", name);
                    userData.put("email", email);
                    userData.put("university", SUPPORTED_UNIVERSITY);
                    userData.put("role", role);
                    userData.put("totalPoints", 0);
                    userData.put("streakDays", 0);
                    userData.put("co2SavedKg", 0.0);
                    userData.put("createdAt", Timestamp.now());

                    db.collection("users").document(uid)
                            .set(userData)
                            .addOnSuccessListener(unused -> {
                                if ("staff".equals(role)) {
                                    Toast.makeText(this,
                                            "Staff account created! Welcome 🌿",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this,
                                            "Account created!",
                                            Toast.LENGTH_SHORT).show();
                                }

                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Firestore error: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Registration failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private View validateInputs(String name, String email, String password, String staffCode) {
        View firstInvalidField = null;

        if (TextUtils.isEmpty(name)) {
            tilName.setError("Display name is required");
            firstInvalidField = firstInvalidField == null ? etName : firstInvalidField;
        }

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Campus email is required");
            firstInvalidField = firstInvalidField == null ? etEmail : firstInvalidField;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address");
            firstInvalidField = firstInvalidField == null ? etEmail : firstInvalidField;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            firstInvalidField = firstInvalidField == null ? etPassword : firstInvalidField;
        } else if (password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            firstInvalidField = firstInvalidField == null ? etPassword : firstInvalidField;
        }

        if (!TextUtils.isEmpty(staffCode) && !STAFF_CODE.equals(staffCode)) {
            tilStaffCode.setError("Incorrect staff code. Leave blank if student.");
            firstInvalidField = firstInvalidField == null ? etStaffCode : firstInvalidField;
        }

        return firstInvalidField;
    }

    private void clearAllErrors() {
        if (tilName != null) tilName.setError(null);
        if (tilEmail != null) tilEmail.setError(null);
        if (tilPassword != null) tilPassword.setError(null);
        if (tilStaffCode != null) tilStaffCode.setError(null);
    }

    private void showUniversityDialog() {
        String[] universities = {"LUMS", "FAST", "NUST", "IBA"};

        new AlertDialog.Builder(this)
                .setTitle("Select university")
                .setItems(universities, (dialog, which) -> {
                    tvSelectedUniversity.setText(universities[which]);
                })
                .show();
    }
}