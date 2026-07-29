package com.secureqr.scanner.ui.password;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.secureqr.scanner.R;

/**
 * Complete password-check mark: rounded dark tile, magnifying glass + key and
 * a deliberately overlapping state badge.  It is drawn as one visual asset so
 * its three states never drift apart in proportion or alignment.
 */
public final class PasswordSecurityCheckButton extends View {
    public enum State { UNCHECKED, CHECKING, SAFE, RISK }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path keyPath = new Path();
    private Bitmap uncheckedReference;
    private Bitmap safeReference;
    private Bitmap riskReference;
    private State state = State.UNCHECKED;
    private float scanProgress;
    private ValueAnimator scanner;

    public PasswordSecurityCheckButton(Context context) { super(context); init(); }
    public PasswordSecurityCheckButton(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public PasswordSecurityCheckButton(Context context, AttributeSet attrs, int style) { super(context, attrs, style); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        setContentDescription("Password security check");
        // These are the approved reference assets supplied for the finished states.
        // Keeping them as bitmaps prevents a hand-drawn approximation from changing
        // the key silhouette, tile curve or badge overlap.
        uncheckedReference = BitmapFactory.decodeResource(getResources(), R.drawable.password_security_check_unchecked);
        safeReference = BitmapFactory.decodeResource(getResources(), R.drawable.password_security_check_safe);
        riskReference = BitmapFactory.decodeResource(getResources(), R.drawable.password_security_check_risk);
    }

    public void startChecking() {
        state = State.CHECKING;
        if (scanner != null) scanner.cancel();
        scanner = ValueAnimator.ofFloat(0f, 1f);
        scanner.setDuration(1180L);
        scanner.setRepeatCount(ValueAnimator.INFINITE);
        scanner.setInterpolator(new LinearInterpolator());
        scanner.addUpdateListener(a -> { scanProgress = (float) a.getAnimatedValue(); invalidate(); });
        scanner.start();
        invalidate();
    }

    public void setResult(boolean safe) { setState(safe ? State.SAFE : State.RISK); }

    private void setState(State next) {
        state = next;
        if (scanner != null) { scanner.cancel(); scanner = null; }
        scanProgress = 0f;
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        if (scanner != null) scanner.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (state == State.UNCHECKED && uncheckedReference != null) {
            drawReferenceState(canvas, uncheckedReference);
            return;
        }
        if (state == State.CHECKING && uncheckedReference != null) {
            drawReferenceState(canvas, uncheckedReference);
            drawReferenceInspectionSweep(canvas);
            return;
        }
        if (state == State.SAFE && safeReference != null) {
            drawReferenceState(canvas, safeReference);
            return;
        }
        if (state == State.RISK && riskReference != null) {
            drawReferenceState(canvas, riskReference);
            return;
        }
        final float u = Math.min(getWidth(), getHeight()) / 48f;
        final boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_NO;
        drawTile(canvas, u, light);
        drawKey(canvas, u);
        if (state == State.CHECKING) {
            // The key stays still while the glass moves over it, like a deliberate inspection.
            float phase = scanProgress * (float) (Math.PI * 2d);
            float xOffset = (float) Math.sin(phase) * 1.45f * u;
            float yOffset = (float) Math.sin(phase * 2f) * .75f * u;
            drawMagnifier(canvas, u, xOffset, yOffset);
            drawInspectionSweep(canvas, u, xOffset, yOffset);
        } else {
            drawMagnifier(canvas, u, 0f, 0f);
        }
        // Unchecked and checking intentionally have no status badge.
    }

    /** Adds motion above the supplied icon without changing its artwork. */
    private void drawReferenceInspectionSweep(Canvas canvas) {
        float phase = scanProgress * (float) (Math.PI * 2d);
        // Keep the overlay centered: the state artwork must never appear to
        // slide sideways or reveal a coloured result marker while checking.
        // The supplied artwork remains fixed.  Only the translucent inspection lens
        // travels across the key, so the button does not jump or expose a result badge.
        float travel = getWidth() * .055f;
        float cx = getWidth() * .46f + (float) Math.sin(phase) * travel;
        float cy = getHeight() * .45f + (float) Math.sin(phase * 2f) * getHeight() * .018f;
        float radius = Math.min(getWidth(), getHeight()) * .205f;
        int pulse = 18 + Math.round(14f * ((float) Math.sin(phase) + 1f) / 2f);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{Color.argb(pulse, 255, 255, 255), 0x0A9CCBFF, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.min(getWidth(), getHeight()) * .022f);
        paint.setColor(0x9ED9FFFF);
        canvas.drawCircle(cx, cy, radius * .82f, paint);
    }

    private void drawReferenceState(Canvas canvas, Bitmap bitmap) {
        float scale = Math.min(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + width, top + height), paint);
    }

    private void drawTile(Canvas canvas, float u, boolean light) {
        RectF tile = new RectF(2.2f * u, 2.0f * u, 45.4f * u, 45.2f * u);
        int start = light ? 0xFFEAF3FF : 0xFF11213A;
        int end = light ? 0xFFD9E9FB : 0xFF081323;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(4 * u, 3 * u, 45 * u, 46 * u, start, end, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(tile, 11.7f * u, 11.7f * u, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.15f * u);
        paint.setColor(light ? 0xFF7A91AA : 0xFF42546D);
        canvas.drawRoundRect(tile, 11.7f * u, 11.7f * u, paint);
        // Subtle upper-left bloom gives the panel the same depth as the reference asset.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(13 * u, 10 * u, 24 * u,
                new int[]{light ? 0x2538BDF8 : 0x1E5A8BFF, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(tile, 11.7f * u, 11.7f * u, paint);
        paint.setShader(null);
    }

    private void drawMagnifier(Canvas canvas, float u, float xOffset, float yOffset) {
        LinearGradient cyanBlue = new LinearGradient(13 * u, 9 * u, 36 * u, 36 * u,
                new int[]{0xFF5CF0D2, 0xFF22C7FF, 0xFF2D7CFF}, null, Shader.TileMode.CLAMP);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.35f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setShader(cyanBlue);
        // The glass is deliberately large; the handle meets the status badge at its upper-left edge.
        canvas.drawCircle(21.2f * u + xOffset, 20.8f * u + yOffset, 12.25f * u, paint);
        canvas.drawLine(29.8f * u + xOffset, 29.45f * u + yOffset, 36.1f * u + xOffset, 35.7f * u + yOffset, paint);
        paint.setShader(null);
    }

    private void drawKey(Canvas canvas, float u) {
        LinearGradient cyanBlue = new LinearGradient(13 * u, 9 * u, 36 * u, 36 * u,
                new int[]{0xFF5CF0D2, 0xFF22C7FF, 0xFF2D7CFF}, null, Shader.TileMode.CLAMP);
        // Filled key, matching the single solid key silhouette in the supplied design.
        keyPath.reset();
        keyPath.addCircle(22.25f * u, 18.35f * u, 4.35f * u, Path.Direction.CW);
        keyPath.moveTo(19.35f * u, 21.48f * u);
        keyPath.lineTo(11.5f * u, 29.35f * u);
        keyPath.lineTo(14.0f * u, 31.82f * u);
        keyPath.lineTo(15.72f * u, 30.12f * u);
        keyPath.lineTo(17.45f * u, 31.85f * u);
        keyPath.lineTo(19.22f * u, 30.08f * u);
        keyPath.lineTo(20.88f * u, 31.76f * u);
        keyPath.lineTo(23.35f * u, 29.27f * u);
        keyPath.lineTo(21.55f * u, 27.48f * u);
        keyPath.lineTo(25.37f * u, 23.58f * u);
        keyPath.close();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(keyPath, paint);
        paint.setShader(null);
        paint.setColor(0xFF0C1A2B);
        canvas.drawCircle(22.25f * u, 18.35f * u, 1.58f * u, paint);
    }

    private void drawInspectionSweep(Canvas canvas, float u, float xOffset, float yOffset) {
        float x = (21.2f * u) + xOffset;
        float y = (20.8f * u) + yOffset;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(x - 6f * u, 0, x + 6f * u, 0,
                new int[]{0x005AF5FF, 0xB8E8FFFF, 0x005AF5FF}, null, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipRect(x - 10f * u, y - 10f * u, x + 10f * u, y + 10f * u);
        canvas.drawRect(x - 5.2f * u, y - 11f * u, x + 5.2f * u, y + 11f * u, paint);
        canvas.restore();
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f * u);
        paint.setColor(0xC9EFFFFF);
        canvas.drawCircle(x, y, 7.1f * u, paint);
    }

}
