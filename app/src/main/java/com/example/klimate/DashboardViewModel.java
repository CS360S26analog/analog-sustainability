/**
 * DashboardViewModel.java
 *
 * ViewModel for the home dashboard screen (HomeFragment).
 * Queries Firestore to retrieve the current user's profile stats
 * (streak, points, CO2 saved) and their activity logs.
 * Exposes LiveData objects that HomeFragment observes to update the UI
 * whenever data changes.
 *
 * Role in design: Part of the ViewModel layer (MVVM pattern).
 * Sits between HomeFragment (View) and Firestore (Model).
 * Uses User.java and ActivityLog.java model classes.
 *
 * Outstanding issues: Time period filter (weekly/monthly) not yet
 * implemented — currently loads all logs for the current user.
 *
 * @author Maryam Ali
 */
package com.example.klimate;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.klimate.model.ActivityLog;
import com.example.klimate.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardViewModel extends ViewModel {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private final MutableLiveData<User> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ActivityLog>> logsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> co2LiveData = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> categoryCountLiveData = new MutableLiveData<>();

    /**
     * CO2 saved per activity type in kilograms.
     * Used to calculate the user's estimated carbon reduction.
     */
    private static final Map<String, Double> CO2_PER_ACTIVITY = new HashMap<String, Double>() {{
    	put("Cycling", 0.21);
    	put("Walked", 0.10);
    	put("Recycling", 0.15);
    	put("Composting", 0.12);
    	put("Public Transit", 0.08);
    	put("Plant-based meal", 0.50);
    	put("Reusable cup", 0.05);
    	put("Energy saving", 0.18);
	}};

    /**
     * Returns LiveData holding the current user's profile (name, streak, points).
     * HomeFragment observes this to update the greeting and stat cards.
     *
     * @return LiveData wrapping a User object
     */
    public LiveData<User> getUserLiveData() {
        return userLiveData;
    }

    /**
     * Returns LiveData holding the list of activity logs for the current user.
     * HomeFragment observes this to display activity counts.
     *
     * @return LiveData wrapping a list of ActivityLog objects
     */
    public LiveData<List<ActivityLog>> getLogsLiveData() {
        return logsLiveData;
    }

    /**
     * Returns LiveData holding the calculated total CO2 saved in kilograms.
     * Value is derived from the user's activity logs using CO2_PER_ACTIVITY constants.
     *
     * @return LiveData wrapping a Double representing kg of CO2 saved
     */
    public LiveData<Double> getCo2LiveData() {
        return co2LiveData;
    }

    /**
     * Returns LiveData holding a map of activity category names to log counts.
     * Used to display a breakdown of activities by category on the dashboard.
     *
     * @return LiveData wrapping a Map of category name to count
     */
    public LiveData<Map<String, Integer>> getCategoryCountLiveData() {
        return categoryCountLiveData;
    }

    /**
     * Loads the current user's profile document from Firestore.
     * Updates userLiveData on success so the UI can display
     * the real display name, streak, and points balance.
     */
    public void loadUserData() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    userLiveData.setValue(user);
                });
    }

    /**
     * Loads all activity logs belonging to the current user from Firestore.
     * After loading, calculates CO2 savings and category counts and posts
     * results to their respective LiveData objects.
     */
    public void loadActivityLogs() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        db.collection("activity_logs")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ActivityLog> logs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        ActivityLog log = doc.toObject(ActivityLog.class);
                        log.setLogId(doc.getId());
                        logs.add(log);
                    }
                    logsLiveData.setValue(logs);
                    calculateCo2(logs);
                    calculateCategoryCounts(logs);
                });
    }

    /**
     * Calculates the total estimated CO2 saved from a list of activity logs.
     * Uses the CO2_PER_ACTIVITY lookup table. Activities not in the table
     * contribute 0.0 kg. Posts the result to co2LiveData.
     *
     * @param logs list of ActivityLog objects to calculate from
     */
    private void calculateCo2(List<ActivityLog> logs) {
        double total = 0.0;
        for (ActivityLog log : logs) {
            Double co2 = CO2_PER_ACTIVITY.get(log.getActivityType());
            if (co2 != null) {
                total += co2;
            }
        }
        co2LiveData.setValue(total);
    }

    /**
     * Counts how many logs exist per activity category.
     * Posts the resulting map to categoryCountLiveData.
     *
     * @param logs list of ActivityLog objects to count
     */
    private void calculateCategoryCounts(List<ActivityLog> logs) {
        Map<String, Integer> counts = new HashMap<>();
        for (ActivityLog log : logs) {
            String type = log.getActivityType();
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        categoryCountLiveData.setValue(counts);
    }
}