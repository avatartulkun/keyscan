package com.secureqr.scanner.ui.home;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Intercepts only clearly horizontal gestures so child vertical scrolling remains intact. */
public class PrimaryPageSwipeContainer extends FrameLayout {
    public interface OnPageSwipeListener {
        void onPageSwipe(boolean left);

        default void onPageDrag(float offsetFraction) {
        }

        default void onPageDragCancelled() {
        }
    }

    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean horizontalGesture;
    private boolean pageSwipeEnabled = true;
    private OnPageSwipeListener listener;

    public PrimaryPageSwipeContainer(@NonNull Context context) {
        this(context, null);
    }

    public PrimaryPageSwipeContainer(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PrimaryPageSwipeContainer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    public void setOnPageSwipeListener(OnPageSwipeListener listener) {
        this.listener = listener;
    }

    public void setPageSwipeEnabled(boolean enabled) {
        pageSwipeEnabled = enabled;
        if (!enabled) {
            horizontalGesture = false;
            listener = null;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!pageSwipeEnabled) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                horizontalGesture = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                    horizontalGesture = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                horizontalGesture = false;
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!pageSwipeEnabled) {
            return super.onTouchEvent(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && horizontalGesture) {
            float width = Math.max(1f, getWidth());
            float fraction = (event.getX() - downX) / width;
            fraction = Math.max(-1f, Math.min(1f, fraction));
            if (listener != null) listener.onPageDrag(fraction);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            boolean valid = horizontalGesture && Math.abs(dx) >= getWidth() * 0.12f;
            horizontalGesture = false;
            if (valid && listener != null) listener.onPageSwipe(dx < 0);
            else if (listener != null) listener.onPageDragCancelled();
            performClick();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            horizontalGesture = false;
            if (listener != null) listener.onPageDragCancelled();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
