package com.example.klimate;

public class LogPointsPolicy {

    public static final String STATUS_QUICK = "quick";
    public static final String STATUS_PENDING_VERIFICATION = "pending_verification";

    private LogPointsPolicy() {
        // Utility class.
    }

    public static boolean shouldAwardPoints(String status) {
        return STATUS_PENDING_VERIFICATION.equals(status);
    }

    public static int pointsForStoredLog(String status, int calculatedPoints) {
        if (!shouldAwardPoints(status)) {
            return 0;
        }

        return Math.max(calculatedPoints, 0);
    }

    public static String buildSuccessMessage(String status, String activityName) {
        if (shouldAwardPoints(status)) {
            return "Verified log submitted with proof";
        }

        String safeActivity = activityName == null || activityName.trim().isEmpty()
                ? "Activity"
                : activityName.trim();

        return safeActivity + " logged ✅";
    }
}