package com.secureqr.scanner.ui.scanner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ScannerOverlayView extends View {
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pulse = 1f;

    public ScannerOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        maskPaint.setColor(0x99000000);
        cornerPaint.setColor(Color.parseColor("#4A90D9"));
        cornerPaint.setStrokeWidth(dp(3));
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerPaint.setStrokeJoin(Paint.Join.ROUND);
        startPulse();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float boxWidth = width * 0.72f * pulse;
        float boxHeight = boxWidth * 0.72f;
        float left = (width - boxWidth) / 2f;
        float top = (height - boxHeight) / 2f - dp(20);
        RectF box = new RectF(left, top, left + boxWidth, top + boxHeight);

        Path path = new Path();
        path.addRect(0, 0, width, height, Path.Direction.CW);
        path.addRoundRect(box, dp(18), dp(18), Path.Direction.CCW);
        canvas.drawPath(path, maskPaint);

        float len = dp(30);
        float radius = dp(18);
        canvas.drawArc(new RectF(box.left, box.top, box.left + radius * 2, box.top + radius * 2), 180, 90, false, cornerPaint);
        canvas.drawLine(box.left + radius, box.top, box.left + len, box.top, cornerPaint);
        canvas.drawLine(box.left, box.top + radius, box.left, box.top + len, cornerPaint);
        canvas.drawArc(new RectF(box.right - radius * 2, box.top, box.right, box.top + radius * 2), 270, 90, false, cornerPaint);
        canvas.drawLine(box.right - radius, box.top, box.right - len, box.top, cornerPaint);
        canvas.drawLine(box.right, box.top + radius, box.right, box.top + len, cornerPaint);
        canvas.drawArc(new RectF(box.left, box.bottom - radius * 2, box.left + radius * 2, box.bottom), 90, 90, false, cornerPaint);
        canvas.drawLine(box.left + radius, box.bottom, box.left + len, box.bottom, cornerPaint);
        canvas.drawLine(box.left, box.bottom - radius, box.left, box.bottom - len, cornerPaint);
        canvas.drawArc(new RectF(box.right - radius * 2, box.bottom - radius * 2, box.right, box.bottom), 0, 90, false, cornerPaint);
        canvas.drawLine(box.right - radius, box.bottom, box.right - len, box.bottom, cornerPaint);
        canvas.drawLine(box.right, box.bottom - radius, box.right, box.bottom - len, cornerPaint);
    }

    private void startPulse() {
        ValueAnimator animator = ValueAnimator.ofFloat(0.98f, 1.02f);
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(valueAnimator -> {
            pulse = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

