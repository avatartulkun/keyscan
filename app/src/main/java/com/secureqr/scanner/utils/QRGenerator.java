package com.secureqr.scanner.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

public final class QRGenerator {
    private QRGenerator() {
    }

    public static Bitmap generateQR(String text, int size) {
        return generateStyledQR(text, size, "classic", Color.BLACK, Color.WHITE);
    }

    public static Bitmap generateStyledQR(String text, int size, String style, int foreground, int background) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.drawColor(background);
            int width = matrix.getWidth();
            float cell = size / (float) width;
            boolean dots = "dots".equals(style) || "圆点样式".equals(style);
            boolean rounded = "rounded".equals(style) || "圆角样式".equals(style);
            boolean logo = "logo".equals(style) || "带 Logo 样式".equals(style);
            boolean blueGradient = "blue_purple".equals(style) || "蓝紫渐变".equals(style);
            boolean orangeGradient = "orange_yellow".equals(style) || "橙黄渐变".equals(style);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < width; y++) {
                    if (!matrix.get(x, y)) continue;
                    int color = foreground;
                    if (blueGradient) color = blend(Color.rgb(21, 101, 192), Color.rgb(142, 36, 170), y / (float) width);
                    if (orangeGradient) color = blend(Color.rgb(255, 140, 0), Color.rgb(255, 213, 79), y / (float) width);
                    paint.setColor(color);
                    float left = x * cell;
                    float top = y * cell;
                    if (dots) {
                        canvas.drawCircle(left + cell / 2f, top + cell / 2f, cell * 0.42f, paint);
                    } else if (rounded) {
                        canvas.drawRoundRect(new RectF(left, top, left + cell, top + cell), cell * 0.28f, cell * 0.28f, paint);
                    } else {
                        canvas.drawRect(left, top, left + cell + 0.2f, top + cell + 0.2f, paint);
                    }
                }
            }
            if (logo) drawLogo(canvas, size, background);
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }

    private static void drawLogo(Canvas canvas, int size, int background) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float box = size * 0.20f;
        float left = (size - box) / 2f;
        float top = (size - box) / 2f;
        paint.setColor(background);
        canvas.drawRoundRect(new RectF(left, top, left + box, top + box), box * 0.18f, box * 0.18f, paint);
        paint.setColor(Color.rgb(255, 140, 0));
        canvas.drawRoundRect(new RectF(left + box * 0.16f, top + box * 0.16f, left + box * 0.84f, top + box * 0.84f), box * 0.14f, box * 0.14f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(box * 0.28f);
        paint.setFakeBoldText(true);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText("KS", size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    }

    private static int blend(int start, int end, float t) {
        int r = (int) (Color.red(start) + (Color.red(end) - Color.red(start)) * t);
        int g = (int) (Color.green(start) + (Color.green(end) - Color.green(start)) * t);
        int b = (int) (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t);
        return Color.rgb(r, g, b);
    }
}

