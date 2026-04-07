/**
 * LoginActivity.java
 *
 * Entry point of the app for returning users. Handles email and password
 * authentication using Firebase Authentication. On successful login,
 * navigates the user to MainActivity. On failure, displays an error
 * message via Toast.
 *
 * If the user is already signed in when this screen opens, they are
 * redirected to MainActivity immediately without seeing the login form.
 *
 * Role in design: Part of the Auth layer. Works alongside RegisterActivity
 * to control access to the rest of the app. Uses Firebase Auth as the
 * identity provider — no passwords are stored locally.
 *
 * Outstanding issues:
 * - "Forgot password" / password reset flow not yet implemented.
 * - No input validation for email format beyond Firebase's own checks.
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
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private FirebaseAuth mAuth;

    /**
     * Initialises the login screen. If a user is already authenticated,
     * skips the form and navigates directly to MainActivity.
     * Otherwise sets up the email/password fields and button listeners.
     *
     * @param savedInstanceState previously saved instance state, or null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // If already logged in, go straight to main
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        etEmail    = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        Button btnLogin    = findViewById(R.id.btn_login);
        TextView tvRegister = findViewById(R.id.tv_go_register);

        btnLogin.setOnClickListener(v -> loginUser());
        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    /**
     * Reads the email and password fields, validates they are non-empty,
     * and attempts sign-in via Firebase Authentication.
     * Navigates to MainActivity on success; shows a Toast on failure.
     */
    private void loginUser() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}
