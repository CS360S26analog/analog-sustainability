package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        int[] rowIds = {R.id.row_account, R.id.row_privacy, R.id.row_notifications, R.id.row_help};
        String[] rowLabels = {"Account Settings", "Privacy", "Notifications", "Help & Support"};

        for (int i = 0; i < rowIds.length; i++) {
            final String label = rowLabels[i];
            LinearLayout row = view.findViewById(rowIds[i]);
            if (row != null) {
                row.setOnClickListener(v ->
                    Toast.makeText(getContext(), label + " — coming soon!", Toast.LENGTH_SHORT).show()
                );
            }
        }

        TextView tvViewAll = view.findViewById(R.id.tv_view_all);
        if (tvViewAll != null) {
            tvViewAll.setOnClickListener(v ->
                Toast.makeText(getContext(), "All achievements — coming soon!", Toast.LENGTH_SHORT).show()
            );
        }

        return view;
    }
}
