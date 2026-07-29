package com.secureqr.scanner.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** Crisp, density-independent KeyScan wordmark for the primary header. */
public final class KeyScanBrandLockupView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();

    public KeyScanBrandLockupView(Context context) { super(context); init(); }
    public KeyScanBrandLockupView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public KeyScanBrandLockupView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); init(); }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float baseW = dp(176f);
        float baseH = dp(76f);
        float s = Math.min(getWidth() / baseW, getHeight() / baseH);
        float left = (getWidth() - baseW * s) * .5f;
        float top = (getHeight() - baseH * s) * .5f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(s, s);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(38f));
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setShader(new LinearGradient(0, 0, dp(165f), 0,
                new int[]{0xFF159DFF, 0xFF346DFF, 0xFFC84CDA, 0xFFFF6B63, 0xFFFFB82E},
                null, Shader.TileMode.CLAMP));
        canvas.drawText("KeyScan", dp(2f), dp(40f), paint);
        paint.setShader(null);

        // Shield + check: compact, clean alternative to an emoji/font glyph.
        float shieldX = dp(5f), shieldY = dp(49f);
        path.reset();
        path.moveTo(shieldX + dp(10f), shieldY);
        path.lineTo(shieldX + dp(19f), shieldY + dp(3.5f));
        path.lineTo(shieldX + dp(17f), shieldY + dp(13f));
        path.quadTo(shieldX + dp(14f), shieldY + dp(18f), shieldX + dp(10f), shieldY + dp(20f));
        path.quadTo(shieldX + dp(6f), shieldY + dp(18f), shieldX + dp(3f), shieldY + dp(13f));
        path.lineTo(shieldX + dp(1f), shieldY + dp(3.5f));
        path.close();
        paint.setColor(0xFF149DFF);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.8f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(shieldX + dp(6f), shieldY + dp(10f));
        path.lineTo(shieldX + dp(9f), shieldY + dp(13f));
        path.lineTo(shieldX + dp(14f), shieldY + dp(7f));
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(18f));
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setColor(0xFF137EEA);
        canvas.drawText("安全", dp(29f), dp(67f), paint);
        paint.setColor(0xFF5C739A);
        canvas.drawText("·", dp(69f), dp(67f), paint);

        // Leaf mark before 自由, matching the original green accent without bitmap artifacts.
        float leafX = dp(87f), leafY = dp(52f);
        paint.setColor(0xFF11B977);
        path.reset();
        path.moveTo(leafX, leafY + dp(10f));
        path.cubicTo(leafX + dp(2f), leafY, leafX + dp(15f), leafY - dp(2f), leafX + dp(18f), leafY);
        path.cubicTo(leafX + dp(17f), leafY + dp(12f), leafX + dp(8f), leafY + dp(18f), leafX, leafY + dp(15f));
        path.close();
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.25f));
        paint.setColor(0xFF087A55);
        canvas.drawLine(leafX + dp(3f), leafY + dp(14f), leafX + dp(14f), leafY + dp(3f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF08AD6D);
        canvas.drawText("自由", dp(111f), dp(67f), paint);
        canvas.restore();
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
