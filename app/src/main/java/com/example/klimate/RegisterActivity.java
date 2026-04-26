/**
 * RegisterActivity.java
 *
 * Handles new user registration for the Klimate app. Creates a Firebase
 * Authentication account using the provided email and password, then
 * writes a corresponding User document to the Firestore "users" collection.
 *
 * Includes a university selector with a predefined list (LUMS, NUST).
 * The selected university is stored in the User document and displayed
 * on the profile screen.
 *
 * If the user enters the correct staff code during registration, their
 * role is set to "staff", granting access to the staff management screens.
 * Otherwise the role defaults to "student".
 *
 * Role in design: Part of the Auth layer (one-time onboarding screen).
 * Depends on User.java (Model layer) to structure the Firestore write.
 *
 * Outstanding issues:
 * - No duplicate display name check across existing users.
 *
 * @author Maryam Waseem
 */
package com.example.klimate;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.klimate.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    // The secret code that grants staff access on registration.
    // Share this only with LUMS sustainability staff members.
    private static final String STAFF_CODE = "KLIMATE_STAFF_2026";

    private EditText etName, etEmail, etPassword, etStaffCode;
    private TextView tvSelectedUniversity;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Predefined university list — extend this as needed
    private final String[] universities = {"LUMS", "NUST"};
    private String selectedUniversity = "LUMS"; // default

    /**
     * Initialises the registration screen. Sets up input fields,
     * the university selector dialog, and button listeners.
     *
     * @param savedInstanceState previously saved instance state, or null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        etName               = findViewById(R.id.et_name);
        etEmail              = findViewById(R.id.et_email);
        etPassword           = findViewById(R.id.et_password);
        etStaffCode          = findViewById(R.id.et_staff_code);
        tvSelectedUniversity = findViewById(R.id.tv_selected_university);
        Button btnRegister   = findViewById(R.id.btn_register);
        TextView tvLogin     = findViewById(R.id.tv_go_login);

        tvSelectedUniversity.setOnClickListener(v -> showUniversityPicker());

        btnRegister.setOnClickListener(v -> registerUser());
        tvLogin.setOnClickListener(v -> finish());
    }

    /**
     * Shows an AlertDialog with the predefined list of universities.
     * Updates the displayed selection and stores the chosen value.
     */
    private void showUniversityPicker() {
        new AlertDialog.Builder(this)
                .setTitle("Select your university")
                .setSingleChoiceItems(
                        universities,
                        getSelectedIndex(),
                        (dialog, which) -> {
                            selectedUniversity = universities[which];
                            tvSelectedUniversity.setText(selectedUniversity);
                            dialog.dismiss();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Returns the index of the currently selected university
     * in the universities array.
     *
     * @return index of selectedUniversity in the array, or 0 if not found
     */
    private int getSelectedIndex() {
        for (int i = 0; i < universities.length; i++) {
            if (universities[i].equals(selectedUniversity)) return i;
        }
        return 0;
    }

    /**
     * Reads all input fields, validates them, creates a Firebase Auth account,
     * then writes a new User document to Firestore including the selected
     * university and role. Navigates to MainActivity on success.
     */
    private void registerUser() {
        String name       = etName.getText().toString().trim();
        String email      = etEmail.getText().toString().trim();
        String password   = etPassword.getText().toString().trim();
        String staffCode  = etStaffCode.getText().toString().trim();
        String university = selectedUniversity;

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)
                || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this,
                    "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        // Determine role — staff code must match exactly, case-sensitive
        String role = staffCode.equals(STAFF_CODE) ? "staff" : "student";

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = mAuth.getCurrentUser();
                    if (firebaseUser == null) return;

                    String uid = firebaseUser.getUid();
                    User newUser = new User(uid, name, email, university,
                            Timestamp.now(), role);

                    db.collection("users").document(uid)
                            .set(newUser)
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
}