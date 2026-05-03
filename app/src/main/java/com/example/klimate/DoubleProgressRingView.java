package com.example.klimate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * DoubleProgressRingView.java
 *
 * Custom View for the Klimate home dashboard.
 * Draws dynamic circular progress rings for monthly personal goal progress
 * and up to four monthly challenge progress values.
 *
 * UI behavior:
 * - No active progress: shows 2 soft gray rings only
 * - Active rings: show soft tinted tracks + colored progress arcs
 * - 1 to 5 active rings fit dynamically in the same space
 */
public class DoubleProgressRingView extends View {

    private static final int MAX_CHALLENGE_RINGS = 4;

    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<RingProgress> activeRings = new ArrayList<>();

    private final int inactiveColor = Color.parseColor("#D8D6CD");
    private final int personalColor = Color.parseColor("#D18B36");

    private final int[] challengeColors = {
            Color.parseColor("#345C3A"),
            Color.parseColor("#6F8F62"),
            Color.parseColor("#8AAE92"),
            Color.parseColor("#4F6F52")
    };

    public DoubleProgressRingView(Context context) {
        super(context);
        init();
    }

    public DoubleProgressRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DoubleProgressRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inactivePaint.setStyle(Paint.Style.STROKE);
        inactivePaint.setStrokeCap(Paint.Cap.ROUND);
        inactivePaint.setColor(inactiveColor);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        activePaint.setStyle(Paint.Style.STROKE);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Backward-compatible method for old 2-ring logic.
     */
    public void setProgressState(boolean hasPersonalGoal,
                                 float personalProgress,
                                 boolean hasMonthlyChallenge,
                                 float challengeProgress) {
        List<Float> challengeProgresses = new ArrayList<>();

        if (hasMonthlyChallenge && challengeProgress > 0f) {
            challengeProgresses.add(challengeProgress);
        }

        setDynamicProgressState(
                hasPersonalGoal && personalProgress > 0f,
                personalProgress,
                challengeProgresses
        );
    }

    /**
     * New dynamic ring method.
     */
    public void setDynamicProgressState(boolean hasPersonalGoalProgress,
                                        float personalProgress,
                                        List<Float> challengeProgresses) {
        activeRings.clear();

        if (hasPersonalGoalProgress && personalProgress > 0f) {
            activeRings.add(new RingProgress(clamp(personalProgress), personalColor));
        }

        if (challengeProgresses != null) {
            int limit = Math.min(challengeProgresses.size(), MAX_CHALLENGE_RINGS);

            for (int i = 0; i < limit; i++) {
                float progress = clamp(challengeProgresses.get(i));
                if (progress > 0f) {
                    activeRings.add(new RingProgress(progress, challengeColors[i]));
                }
            }
        }

        invalidate();
    }

    public void resetToInactiveState() {
        activeRings.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = Math.min(getWidth(), getHeight());
        if (size <= 0f) return;

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        if (activeRings.isEmpty()) {
            drawInactiveState(canvas, centerX, centerY, size);
            return;
        }

        drawActiveRings(canvas, centerX, centerY, size);
    }

    /**
     * Draws 2 soft gray rings only when nothing is active.
     */
    private void drawInactiveState(Canvas canvas, float centerX, float centerY, float size) {
        float strokeWidth = dpToPx(10);
        float gap = dpToPx(4);

        inactivePaint.setStrokeWidth(strokeWidth);

        float outerRadius = (size / 2f) - (strokeWidth / 2f);
        float innerRadius = outerRadius - strokeWidth - gap;

        drawFullRing(canvas, centerX, centerY, outerRadius, inactivePaint);
        drawFullRing(canvas, centerX, centerY, innerRadius, inactivePaint);
    }

    /**
     * Draws 1 to 5 active rings with soft tinted tracks.
     */
    private void drawActiveRings(Canvas canvas, float centerX, float centerY, float size) {
        int ringCount = Math.min(activeRings.size(), 1 + MAX_CHALLENGE_RINGS);

        float strokeWidth = getStrokeWidthForRingCount(ringCount);
        float gap = getGapForRingCount(ringCount);

        activePaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeWidth(strokeWidth);

        float radius = (size / 2f) - (strokeWidth / 2f);

        for (int i = 0; i < ringCount; i++) {
            RingProgress ring = activeRings.get(i);

            RectF bounds = new RectF(
                    centerX - radius,
                    centerY - radius,
                    centerX + radius,
                    centerY + radius
            );

            // soft tinted track
            trackPaint.setColor(makeTrackColor(ring.color));
            canvas.drawArc(bounds, 0, 360, false, trackPaint);

            // active progress arc
            activePaint.setColor(ring.color);
            canvas.drawArc(bounds, -90, ring.progress * 360f, false, activePaint);

            radius -= strokeWidth + gap;

            if (radius <= strokeWidth) {
                break;
            }
        }
    }

    private void drawFullRing(Canvas canvas, float centerX, float centerY, float radius, Paint paint) {
        RectF bounds = new RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
        );
        canvas.drawArc(bounds, 0, 360, false, paint);
    }

    /**
     * Faint tinted track based on the active color.
     */
    private int makeTrackColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(55, red, green, blue);
    }

    private float getStrokeWidthForRingCount(int ringCount) {
        if (ringCount <= 2) {
            return dpToPx(10);
        } else if (ringCount == 3) {
            return dpToPx(8.5f);
        } else if (ringCount == 4) {
            return dpToPx(7.5f);
        } else {
            return dpToPx(6.5f);
        }
    }

    private float getGapForRingCount(int ringCount) {
        if (ringCount <= 2) {
            return dpToPx(3.5f);
        } else if (ringCount == 3) {
            return dpToPx(3f);
        } else {
            return dpToPx(2.5f);
        }
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private static class RingProgress {
        private final float progress;
        private final int color;

        private RingProgress(float progress, int color) {
            this.progress = progress;
            this.color = color;
        }
    }
}