package com.example.klimate;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class StaffProfileFragment extends Fragment {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private TextView tvName;
    private TextView tvEmail;
    private TextView tvRole;
    private TextView tvAvatar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_staff_profile, container, false);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        forceGreenHeaderColor();

        // ── View refs (MATCH XML EXACTLY)
        tvName   = view.findViewById(R.id.tv_staff_profile_name);
        tvEmail  = view.findViewById(R.id.tv_staff_profile_email);
        tvRole   = view.findViewById(R.id.tv_staff_profile_role);
        tvAvatar = view.findViewById(R.id.tv_avatar_initials);

        // ── IMPORTANT: copy student wiring
        wireSettingsRows(view);
        wireLogout(view);

        loadStaffInfo();

        return view;
    }

    private void forceGreenHeaderColor() {
        if (getActivity() == null || getContext() == null) return;

        Window window = requireActivity().getWindow();
        window.setStatusBarColor(
                ContextCompat.getColor(requireContext(), R.color.color_green_header)
        );
    }

    // ─────────────────────────────────────────
    // 🔥 COPY OF STUDENT LOGIC (THIS FIXES BUTTONS)
    // ─────────────────────────────────────────
    private void wireSettingsRows(View view) {
        LinearLayout rowAccount = view.findViewById(R.id.row_account);
        LinearLayout rowNotifications = view.findViewById(R.id.row_notifications);

        if (rowAccount != null) {
            rowAccount.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Account Settings — coming soon!", Toast.LENGTH_SHORT).show()
            );
        }

        if (rowNotifications != null) {
            rowNotifications.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Notifications — coming soon!", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void wireLogout(View view) {
        LinearLayout rowLogout = view.findViewById(R.id.row_staff_logout);

        if (rowLogout != null) {
            rowLogout.setOnClickListener(v -> {
                auth.signOut();

                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        }
    }

    // ─────────────────────────────────────────

    private void loadStaffInfo() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            setUI("Staff", "", "Staff Account");
            return;
        }

        String fallbackEmail = user.getEmail() != null ? user.getEmail() : "";

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("displayName");
                    String email = doc.getString("email");
                    String role = doc.getString("role");

                    if (TextUtils.isEmpty(name)) name = fallbackEmail;
                    if (TextUtils.isEmpty(email)) email = fallbackEmail;

                    String roleText = "staff".equalsIgnoreCase(role)
                            ? "Staff Account"
                            : "Account";

                    setUI(name, email, roleText);
                })
                .addOnFailureListener(e ->
                        setUI("Staff", fallbackEmail, "Staff Account")
                );
    }

    private void setUI(String name, String email, String role) {
        tvName.setText(name);
        tvEmail.setText(email);
        tvRole.setText(role);
        tvAvatar.setText(getInitials(name));
    }

    private String getInitials(String name) {
        if (TextUtils.isEmpty(name)) return "ST";

        String[] parts = name.trim().split("\\s+");

        if (parts.length >= 2) {
            return (parts[0].substring(0,1) + parts[1].substring(0,1)).toUpperCase();
        }

        return name.substring(0,1).toUpperCase();
    }
}