package com.example.klimate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MimiPickerActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "mimi_prefs";
    public static final String KEY_MIMI_AVATAR_ID = "mimi_avatar_id";

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private boolean fromSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mimi_picker);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        fromSettings = getIntent().getBooleanExtra("from_settings", false);

        View card1 = findViewById(R.id.card_mimi_cat_1);
        View card2 = findViewById(R.id.card_mimi_cat_2);
        View card3 = findViewById(R.id.card_mimi_cat_3);

        View btn1 = findViewById(R.id.btn_choose_mimi_cat_1);
        View btn2 = findViewById(R.id.btn_choose_mimi_cat_2);
        View btn3 = findViewById(R.id.btn_choose_mimi_cat_3);

        View.OnClickListener chooseCat1 = v -> saveSelectedAvatar("mimi_cat_1");
        View.OnClickListener chooseCat2 = v -> saveSelectedAvatar("mimi_cat_2");
        View.OnClickListener chooseCat3 = v -> saveSelectedAvatar("mimi_cat_3");

        card1.setOnClickListener(chooseCat1);
        btn1.setOnClickListener(chooseCat1);

        card2.setOnClickListener(chooseCat2);
        btn2.setOnClickListener(chooseCat2);

        card3.setOnClickListener(chooseCat3);
        btn3.setOnClickListener(chooseCat3);
    }

    private void saveSelectedAvatar(String avatarId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_MIMI_AVATAR_ID, avatarId)
                .apply();

        if (currentUser == null) {
            Toast.makeText(this, "Cat selected!", Toast.LENGTH_SHORT).show();
            goNext();
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .update("mimiAvatarId", avatarId)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Your LUMS cat is ready 🐾", Toast.LENGTH_SHORT).show();
                    goNext();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Saved locally. Will sync later.", Toast.LENGTH_SHORT).show();
                    goNext();
                });
    }

    private void goNext() {
        if (fromSettings) {
            finish();
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}