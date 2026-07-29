package com.secureqr.scanner.ui.scanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.secureqr.scanner.R;

public final class OcrScanOverlayView extends View {
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String mode = "BANK";
    private String documentType = "BANK_CARD";
    private String feedback;

    public OcrScanOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        feedback = context.getString(R.string.scanner_overlay_feedback);
        maskPaint.setColor(0x99000000);
        cornerPaint.setColor(Color.parseColor("#8EC5FF"));
        cornerPaint.setStrokeWidth(dp(4));
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        guidePaint.setColor(0x55FFFFFF);
        guidePaint.setStrokeWidth(dp(1));
        guidePaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(15));
    }

    public void configure(String mode, String documentType) {
        this.mode = mode == null ? "BANK" : mode;
        this.documentType = documentType == null ? "BANK_CARD" : documentType;
        invalidate();
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback == null ? "" : feedback;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF box = scanBox();
        Path path = new Path();
        path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        path.addRoundRect(box, dp(16), dp(16), Path.Direction.CCW);
        canvas.drawPath(path, maskPaint);

        float len = dp(38);
        float radius = dp(16);
        drawCorner(canvas, box.left, box.top, len, radius, 0);
        drawCorner(canvas, box.right, box.top, len, radius, 1);
        drawCorner(canvas, box.left, box.bottom, len, radius, 2);
        drawCorner(canvas, box.right, box.bottom, len, radius, 3);
        if (!"FILE".equals(mode)) {
            canvas.drawLine(box.left + box.width() / 3f, box.top, box.left + box.width() / 3f, box.bottom, guidePaint);
            canvas.drawLine(box.left + box.width() * 2f / 3f, box.top, box.left + box.width() * 2f / 3f, box.bottom, guidePaint);
            canvas.drawLine(box.left, box.top + box.height() / 3f, box.right, box.top + box.height() / 3f, guidePaint);
            canvas.drawLine(box.left, box.top + box.height() * 2f / 3f, box.right, box.top + box.height() * 2f / 3f, guidePaint);
        }

        canvas.drawText(primaryHint(), getWidth() / 2f, box.top - dp(42), textPaint);
        canvas.drawText(feedback, getWidth() / 2f, box.bottom + dp(34), textPaint);
    }

    public RectF scanBoxBounds() {
        return new RectF(scanBox());
    }

    private RectF scanBox() {
        if ("FILE".equals(mode)) {
            float width=getWidth()*0.90f;
            float height=Math.min(getHeight()*0.68f,width*1.414f);
            float left=(getWidth()-width)/2f;
            float top=(getHeight()-height)/2f-dp(8);
            return new RectF(left,top,left+width,top+height);
        }
        float width = getWidth() * 0.82f;
        float ratio = "PASSPORT".equals(documentType) ? 1.42f : 1.586f;
        if (!"BANK".equals(mode) && "NATIONAL_ID".equals(documentType)) ratio = 1.58f;
        float height = width / ratio;
        if (height > getHeight() * 0.42f) {
            height = getHeight() * 0.42f;
            width = height * ratio;
        }
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f - dp(24);
        return new RectF(left, top, left + width, top + height);
    }

    private String primaryHint() {
        if ("BANK".equals(mode)) return getContext().getString(R.string.ocr_guide_bank);
        if ("FILE".equals(mode)) return getContext().getString(R.string.ocr_guide_file);
        if ("PASSPORT".equals(documentType)) return getContext().getString(R.string.ocr_guide_passport);
        if ("DRIVER_LICENSE".equals(documentType)) return getContext().getString(R.string.ocr_guide_driver_license);
        if ("NATIONAL_ID".equals(documentType)) return getContext().getString(R.string.ocr_guide_id_front);
        return getContext().getString(R.string.ocr_guide_document);
    }

    private void drawCorner(Canvas canvas, float x, float y, float len, float radius, int corner) {
        RectF arc;
        if (corner == 0) {
            arc = new RectF(x, y, x + radius * 2, y + radius * 2);
            canvas.drawArc(arc, 180, 90, false, cornerPaint);
            canvas.drawLine(x + radius, y, x + len, y, cornerPaint);
            canvas.drawLine(x, y + radius, x, y + len, cornerPaint);
        } else if (corner == 1) {
            arc = new RectF(x - radius * 2, y, x, y + radius * 2);
            canvas.drawArc(arc, 270, 90, false, cornerPaint);
            canvas.drawLine(x - radius, y, x - len, y, cornerPaint);
            canvas.drawLine(x, y + radius, x, y + len, cornerPaint);
        } else if (corner == 2) {
            arc = new RectF(x, y - radius * 2, x + radius * 2, y);
            canvas.drawArc(arc, 90, 90, false, cornerPaint);
            canvas.drawLine(x + radius, y, x + len, y, cornerPaint);
            canvas.drawLine(x, y - radius, x, y - len, cornerPaint);
        } else {
            arc = new RectF(x - radius * 2, y - radius * 2, x, y);
            canvas.drawArc(arc, 0, 90, false, cornerPaint);
            canvas.drawLine(x - radius, y, x - len, y, cornerPaint);
            canvas.drawLine(x, y - radius, x, y - len, cornerPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
