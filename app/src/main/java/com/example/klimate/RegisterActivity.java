/**
 * RegisterActivity.java
 *
 * Handles new user registration for the Klimate app. Creates a Firebase
 * Authentication account using the provided email and password, then
 * writes a corresponding User document to the Firestore "users" collection
 * under the document ID matching the Firebase Auth UID.
 *
 * The Firestore document is initialised with zero values for totalPoints,
 * streakDays, and co2SavedKg so that downstream reads always find a
 * valid document structure.
 *
 * Role in design: Part of the Auth layer (one-time onboarding screen).
 * Depends on User.java (Model layer) to structure the Firestore write.
 * On success, hands control to MainActivity.
 *
 * Outstanding issues:
 * - Campus email domain validation is a basic non-empty check only.
 *   Could be made stricter (e.g. must end in @lums.edu.pk).
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
import androidx.appcompat.app.AppCompatActivity;
import com.example.klimate.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    /**
     * Initialises the registration screen. Sets up the name, email,
     * and password input fields along with the register button
     * and the link back to the login screen.
     *
     * @param savedInstanceState previously saved instance state, or null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        etName     = findViewById(R.id.et_name);
        etEmail    = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        Button btnRegister = findViewById(R.id.btn_register);
        TextView tvLogin   = findViewById(R.id.tv_go_login);

        btnRegister.setOnClickListener(v -> registerUser());
        tvLogin.setOnClickListener(v -> finish()); // go back to login
    }

    /**
     * Reads the name, email, and password fields and validates they are
     * non-empty and that the password meets the minimum length requirement.
     * Creates a Firebase Auth account, then writes a new User document to
     * Firestore on success. Navigates to MainActivity when both operations
     * complete successfully.
     */
    private void registerUser() {
        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = mAuth.getCurrentUser();
                    if (firebaseUser == null) return;

                    String uid = firebaseUser.getUid();
                    User newUser = new User(uid, name, email, Timestamp.now());

                    db.collection("users").document(uid)
                            .set(newUser)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Firestore error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}
