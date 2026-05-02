package com.example.klimate;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class StaffPagerAdapter extends FragmentStateAdapter {

    public StaffPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new StaffOverviewFragment();

            case 1:
                return new StaffManageFragment();

            case 2:
                return new StaffReportsFragment();

            case 3:
                return new StaffTipsFragment();

            case 4:
                return new ProfileFragment();

            default:
                return new StaffOverviewFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 5;
    }
}