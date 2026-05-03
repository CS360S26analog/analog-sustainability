package com.example.klimate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * DoubleProgressRingView.java
 *
 * Custom View for the Klimate home dashboard.
 * Draws one or two circular progress rings depending on whether the user
 * has joined a personal monthly goal and/or a monthly challenge.
 *
 * Behavior:
 * - No personal goal and no monthly challenge: shows two inactive gray rings.
 * - Only personal goal joined: shows one active ring.
 * - Only monthly challenge joined: shows one active ring.
 * - Both joined: shows two active rings.
 *
 * Role in design: View component used by HomeFragment to visualize
 * monthly sustainability progress.
 *
 * @author Maryam Ali
 */
public class DoubleProgressRingView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint personalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint challengePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF outerBounds = new RectF();
    private final RectF innerBounds = new RectF();

    private boolean hasPersonalGoal = false;
    private boolean hasMonthlyChallenge = false;

    private float personalProgress = 0f;
    private float challengeProgress = 0f;

    private final int inactiveColor = Color.parseColor("#D8D6CD");
    private final int personalColor = Color.parseColor("#D18B36");
    private final int challengeColor = Color.parseColor("#345C3A");

    private float strokeWidth;

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

    /**
     * Initializes paint styles for the progress rings.
     */
    private void init() {
        strokeWidth = dpToPx(11);

        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);
        backgroundPaint.setColor(inactiveColor);

        personalPaint.setStyle(Paint.Style.STROKE);
        personalPaint.setStrokeWidth(strokeWidth);
        personalPaint.setStrokeCap(Paint.Cap.ROUND);
        personalPaint.setColor(personalColor);

        challengePaint.setStyle(Paint.Style.STROKE);
        challengePaint.setStrokeWidth(strokeWidth);
        challengePaint.setStrokeCap(Paint.Cap.ROUND);
        challengePaint.setColor(challengeColor);
    }

    /**
     * Updates the ring progress state.
     *
     * @param hasPersonalGoal true if the user has set a personal monthly goal
     * @param personalProgress progress toward personal goal, from 0.0 to 1.0
     * @param hasMonthlyChallenge true if the user has joined a monthly challenge
     * @param challengeProgress progress toward monthly challenge, from 0.0 to 1.0
     */
    public void setProgressState(boolean hasPersonalGoal,
                                 float personalProgress,
                                 boolean hasMonthlyChallenge,
                                 float challengeProgress) {
        this.hasPersonalGoal = hasPersonalGoal;
        this.hasMonthlyChallenge = hasMonthlyChallenge;
        this.personalProgress = clamp(personalProgress);
        this.challengeProgress = clamp(challengeProgress);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = Math.min(getWidth(), getHeight());
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        float outerRadius = (size / 2f) - strokeWidth;

        // Smaller gap between the two active rings.
        float innerRadius = outerRadius - dpToPx(15);

        outerBounds.set(
                centerX - outerRadius,
                centerY - outerRadius,
                centerX + outerRadius,
                centerY + outerRadius
        );

        innerBounds.set(
                centerX - innerRadius,
                centerY - innerRadius,
                centerX + innerRadius,
                centerY + innerRadius
        );

        boolean hasNeither = !hasPersonalGoal && !hasMonthlyChallenge;
        boolean hasBoth = hasPersonalGoal && hasMonthlyChallenge;

        if (hasNeither) {
            // Keep gray rings only when user has joined nothing.
            canvas.drawArc(outerBounds, 0, 360, false, backgroundPaint);
            canvas.drawArc(innerBounds, 0, 360, false, backgroundPaint);
            return;
        }

        if (hasBoth) {
            drawRing(canvas, outerBounds, personalPaint, personalProgress);
            drawRing(canvas, innerBounds, challengePaint, challengeProgress);
            return;
        }

        if (hasPersonalGoal) {
            drawRing(canvas, outerBounds, personalPaint, personalProgress);
        } else {
            drawRing(canvas, outerBounds, challengePaint, challengeProgress);
        }
    }

    /**
     * Draws a single active progress ring.
     * Does not draw a gray background track for active rings.
     *
     * @param canvas canvas to draw on
     * @param bounds ring bounds
     * @param activePaint paint for active progress
     * @param progress progress from 0.0 to 1.0
     */
    private void drawRing(Canvas canvas, RectF bounds, Paint activePaint, float progress) {
        canvas.drawArc(bounds, -90, progress * 360f, false, activePaint);
    }

    /**
     * Keeps progress values inside the valid 0.0 to 1.0 range.
     *
     * @param value raw progress value
     * @return clamped progress value
     */
    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Converts density-independent pixels to actual pixels.
     *
     * @param dp value in dp
     * @return value in pixels
     */
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}