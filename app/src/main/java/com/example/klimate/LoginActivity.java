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
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_login_password);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);

        Button btnLogin = findViewById(R.id.btn_login);
        TextView tvRegister = findViewById(R.id.tv_go_register);

        setupErrorClearing();

        btnLogin.setOnClickListener(v -> loginUser());
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
    }

    private void setupErrorClearing() {
        addErrorClearer(etEmail, tilEmail);
        addErrorClearer(etPassword, tilPassword);
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

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        clearAllErrors();

        View firstInvalidField = validateInputs(email, password);
        if (firstInvalidField != null) {
            firstInvalidField.requestFocus();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Login failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private View validateInputs(String email, String password) {
        View firstInvalidField = null;

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            firstInvalidField = firstInvalidField == null ? etEmail : firstInvalidField;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address");
            firstInvalidField = firstInvalidField == null ? etEmail : firstInvalidField;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            firstInvalidField = firstInvalidField == null ? etPassword : firstInvalidField;
        }

        return firstInvalidField;
    }

    private void clearAllErrors() {
        if (tilEmail != null) tilEmail.setError(null);
        if (tilPassword != null) tilPassword.setError(null);
    }
}