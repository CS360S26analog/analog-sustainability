package com.example.klimate;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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

    private TextView tvStaffName;
    private TextView tvStaffEmail;
    private TextView tvStaffRole;
    private TextView tvTipsPublished;
    private TextView tvLogsReported;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_staff_profile, container, false);

        auth = FirebaseAuth.getInstance();
        forceGreenHeaderColor();
        db = FirebaseFirestore.getInstance();

        tvStaffName = view.findViewById(R.id.tv_staff_profile_name);
        tvStaffEmail = view.findViewById(R.id.tv_staff_profile_email);
        tvStaffRole = view.findViewById(R.id.tv_staff_profile_role);
        tvTipsPublished = view.findViewById(R.id.tv_staff_tips_published);
        tvLogsReported = view.findViewById(R.id.tv_staff_logs_reported);

        view.findViewById(R.id.row_staff_logout).setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        loadStaffInfo();
        loadStaffStats();

        return view;
    }

    private void forceGreenHeaderColor() {
        if (getActivity() == null) return;

        Window window = getActivity().getWindow();
        window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.color_green_header));
    }

    private void loadStaffInfo() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            tvStaffName.setText("Staff");
            tvStaffEmail.setText("");
            tvStaffRole.setText("Staff Account");
            return;
        }

        tvStaffEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "");

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String displayName = doc.getString("displayName");
                    String email = doc.getString("email");
                    String role = doc.getString("role");

                    if (TextUtils.isEmpty(displayName)) {
                        displayName = !TextUtils.isEmpty(email) ? email : "Staff";
                    }

                    tvStaffName.setText(displayName);
                    tvStaffEmail.setText(!TextUtils.isEmpty(email) ? email : currentUser.getEmail());
                    tvStaffRole.setText("staff".equalsIgnoreCase(role) ? "Staff Account" : "Account");
                })
                .addOnFailureListener(e -> {
                    tvStaffName.setText("Staff");
                    tvStaffRole.setText("Staff Account");
                });
    }

    private void loadStaffStats() {
        db.collection("tips")
                .get()
                .addOnSuccessListener(snap ->
                        tvTipsPublished.setText(String.valueOf(snap.size()))
                );

        db.collection("activity_logs")
                .whereEqualTo("reported", true)
                .get()
                .addOnSuccessListener(snap ->
                        tvLogsReported.setText(String.valueOf(snap.size()))
                );
    }
}
