package com.example.klimate;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class ZoomableFrameLayout extends FrameLayout {

    private View mapView;
    private ScaleGestureDetector scaleDetector;

    private float scale = 1f;
    private float minScale = 1f;
    private final float maxScale = 4f;

    private float translateX = 0f;
    private float translateY = 0f;

    private float lastX, lastY, downX, downY;
    private boolean dragged = false;
    private int touchSlop;

    public ZoomableFrameLayout(Context context) {
        this(context, null);
    }

    public ZoomableFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        setClickable(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mapView = getChildAt(0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        post(() -> {
            if (mapView == null) return;

            float scaleX = (float) getWidth() / mapView.getWidth();
            float scaleY = (float) getHeight() / mapView.getHeight();

            float fillScreenScale = Math.max(scaleX, scaleY);
            float fullMapScale = Math.min(scaleX, scaleY);

            minScale = fullMapScale;
            scale = fillScreenScale;

            translateX = (getWidth() - mapView.getWidth() * scale) / 2f;
            translateY = (getHeight() - mapView.getHeight() * scale) / 2f;

            applyTransform();
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                dragged = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;

                    if (Math.abs(event.getX() - downX) > touchSlop ||
                            Math.abs(event.getY() - downY) > touchSlop) {
                        dragged = true;
                    }

                    translateX += dx;
                    translateY += dy;

                    clampTranslation();
                    applyTransform();

                    lastX = event.getX();
                    lastY = event.getY();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!dragged) {
                    performMarkerClick(event.getX(), event.getY());
                }
                return true;
        }

        return true;
    }

    private void performMarkerClick(float screenX, float screenY) {
        if (!(mapView instanceof ViewGroup)) return;

        float mapX = (screenX - translateX) / scale;
        float mapY = (screenY - translateY) / scale;

        ViewGroup group = (ViewGroup) mapView;

        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);

            if (child.isClickable()
                    && mapX >= child.getLeft()
                    && mapX <= child.getRight()
                    && mapY >= child.getTop()
                    && mapY <= child.getBottom()) {
                child.performClick();
                return;
            }
        }
    }

    private void applyTransform() {
        if (mapView == null) return;

        mapView.setPivotX(0f);
        mapView.setPivotY(0f);
        mapView.setScaleX(scale);
        mapView.setScaleY(scale);
        mapView.setTranslationX(translateX);
        mapView.setTranslationY(translateY);
    }

    private void clampTranslation() {
        if (mapView == null) return;

        float scaledWidth = mapView.getWidth() * scale;
        float scaledHeight = mapView.getHeight() * scale;

        if (scaledWidth <= getWidth()) {
            translateX = (getWidth() - scaledWidth) / 2f;
        } else {
            float minX = getWidth() - scaledWidth;
            translateX = Math.min(0, Math.max(minX, translateX));
        }

        if (scaledHeight <= getHeight()) {
            translateY = (getHeight() - scaledHeight) / 2f;
        } else {
            float minY = getHeight() - scaledHeight;
            translateY = Math.min(0, Math.max(minY, translateY));
        }
    }
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float oldScale = scale;

            scale *= detector.getScaleFactor();
            scale = Math.max(minScale, Math.min(scale, maxScale));

            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            float factor = scale / oldScale;

            translateX = focusX - (focusX - translateX) * factor;
            translateY = focusY - (focusY - translateY) * factor;

            clampTranslation();
            applyTransform();

            return true;
        }
    }
}