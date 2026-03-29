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

public class LogFragment extends Fragment {

    private LinearLayout selectedCard = null;

    private final int[] cardIds = {
        R.id.card_cycling, R.id.card_transit, R.id.card_recycling,
        R.id.card_plantbased, R.id.card_reusable, R.id.card_composting,
        R.id.card_walked, R.id.card_energy
    };

    private final String[] activityNames = {
        "Cycling", "Public Transit", "Recycling", "Plant-based meal",
        "Reusable cup", "Composting", "Walked", "Energy saving"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        LinearLayout[] cards = new LinearLayout[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            final int index = i;
            cards[i] = view.findViewById(cardIds[i]);
            cards[i].setOnClickListener(v -> selectCard(cards, (LinearLayout) v, activityNames[index]));
        }

        TextView btnLog = view.findViewById(R.id.btn_log_activity);
        btnLog.setOnClickListener(v -> {
            if (selectedCard != null) {
                Toast.makeText(getContext(), "Activity logged! ✅", Toast.LENGTH_SHORT).show();
                deselectAll(cards);
                selectedCard = null;
            } else {
                Toast.makeText(getContext(), "Please select an activity first", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void selectCard(LinearLayout[] cards, LinearLayout card, String name) {
        deselectAll(cards);
        card.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card_selected));
        selectedCard = card;
    }

    private void deselectAll(LinearLayout[] cards) {
        for (LinearLayout c : cards) {
            c.setBackground(requireContext().getDrawable(R.drawable.bg_activity_card));
        }
    }
}
