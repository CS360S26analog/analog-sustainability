/**
 * FriendsFragment.java
 *
 * Displays the user's friends list and provides an entry point for
 * adding new friends. Currently shows a static list of friends with
 * their eco tier, points, and streak days.
 *
 * Role in design: Part of the View layer. Accessible from the Friends
 * tab in the bottom navigation bar.
 *
 * Outstanding issues: friend data is hardcoded for Half checkpoint.
 * Dynamic friend loading from Firestore is planned for Full checkpoint.
 *
 * @author Team Analog
 */
package com.example.klimate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FriendsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        TextView btnAdd = view.findViewById(R.id.btn_add_friend);
        btnAdd.setOnClickListener(v ->
            Toast.makeText(getContext(), "Add Friend — coming soon!", Toast.LENGTH_SHORT).show()
        );

        return view;
    }
}
