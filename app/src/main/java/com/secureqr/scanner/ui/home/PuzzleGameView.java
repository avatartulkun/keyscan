package com.secureqr.scanner.ui.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.secureqr.scanner.R;

import java.util.Random;

public class PuzzleGameView extends View {
    public interface Listener {
        void onStatsChanged(int moves, int seconds);
        void onSolved(int moves, int seconds);
    }

    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private int size = 3;
    private int[] tiles;
    private int emptyIndex;
    private int moves;
    private long startTime;
    private boolean started;
    private boolean animating;
    private boolean pictureMode;
    private int picturePreset;
    private int animTile = -1;
    private int animFrom = -1;
    private int animTo = -1;
    private float animProgress;
    private @Nullable Bitmap customBitmap;
    private @Nullable Listener listener;

    public PuzzleGameView(Context context) {
        super(context);
        tilePaint.setColor(Color.WHITE);
        textPaint.setColor(Color.rgb(51, 65, 85));
        textPaint.setTextAlign(Paint.Align.CENTER);
        reset(3);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void reset(int size) {
        this.size = size;
        int count = size * size;
        tiles = new int[count];
        for (int i = 0; i < count - 1; i++) tiles[i] = i + 1;
        tiles[count - 1] = 0;
        emptyIndex = count - 1;
        moves = 0;
        started = false;
        startTime = 0;
        shuffle();
        notifyStats();
        invalidate();
    }

    public int getSizeMode() {
        return size;
    }

    public int getMoves() {
        return moves;
    }

    public boolean isPictureMode() {
        return pictureMode;
    }

    public int getPicturePreset() {
        return picturePreset;
    }

    public void setPictureMode(boolean pictureMode) {
        this.pictureMode = pictureMode;
        invalidate();
    }

    public void nextPicturePreset() {
        picturePreset = (picturePreset + 1) % 3;
        invalidate();
    }

    public void setCustomBitmap(@Nullable Bitmap bitmap) {
        customBitmap = bitmap;
        if (bitmap != null) pictureMode = true;
        invalidate();
    }

    public boolean hasCustomBitmap() {
        return customBitmap != null;
    }

    public @Nullable Bitmap createReferenceBitmap(int pixels) {
        if (!pictureMode) return null;
        if (customBitmap != null) {
            return Bitmap.createScaledBitmap(customBitmap, pixels, pixels, true);
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cell = pixels / (float) size;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int value = r * size + c + 1;
                if (value >= size * size) {
                    paint.setColor(Color.WHITE);
                } else {
                    paint.setColor(presetColor(value));
                }
                canvas.drawRect(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell, paint);
            }
        }
        return bitmap;
    }

    public int getSeconds() {
        return started ? (int) ((System.currentTimeMillis() - startTime) / 1000) : 0;
    }

    private void shuffle() {
        int count = 200 + random.nextInt(301);
        int previousEmpty = -1;
        for (int i = 0; i < count; i++) {
            int[] neighbors = neighbors(emptyIndex);
            int next = neighbors[random.nextInt(neighbors.length)];
            if (neighbors.length > 1 && next == previousEmpty) {
                next = neighbors[(random.nextInt(neighbors.length - 1) + 1) % neighbors.length];
            }
            previousEmpty = emptyIndex;
            swap(next, emptyIndex);
            emptyIndex = next;
        }
        if (isSolved()) shuffle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float board = Math.min(getWidth() - dp(32), getHeight() - dp(32));
        float left = (getWidth() - board) / 2f;
        float top = (getHeight() - board) / 2f;
        float gap = dp(6);
        float cell = (board - gap * (size - 1)) / size;
        textPaint.setTextSize(size == 3 ? dp(32) : dp(24));
        for (int i = 0; i < tiles.length; i++) {
            int value = tiles[i];
            if (value == 0) continue;
            float x = left + (i % size) * (cell + gap);
            float y = top + (i / size) * (cell + gap);
            if (animating && i == animTo && value == animTile) {
                float fromX = left + (animFrom % size) * (cell + gap);
                float fromY = top + (animFrom / size) * (cell + gap);
                x = fromX + (x - fromX) * animProgress;
                y = fromY + (y - fromY) * animProgress;
            }
            RectF rect = new RectF(x, y, x + cell, y + cell);
            if (pictureMode && customBitmap != null) {
                int sourceIndex = value - 1;
                int sourceCol = sourceIndex % size;
                int sourceRow = sourceIndex / size;
                int sliceW = customBitmap.getWidth() / size;
                int sliceH = customBitmap.getHeight() / size;
                Rect src = new Rect(sourceCol * sliceW, sourceRow * sliceH, (sourceCol + 1) * sliceW, (sourceRow + 1) * sliceH);
                canvas.drawBitmap(customBitmap, src, rect, null);
            } else {
                tilePaint.setColor(pictureMode ? presetColor(value) : Color.WHITE);
                canvas.drawRoundRect(rect, dp(12), dp(12), tilePaint);
            }
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(getResources().getColor(R.color.card_stroke));
            canvas.drawRoundRect(rect, dp(12), dp(12), stroke);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            if (!pictureMode) {
                canvas.drawText(String.valueOf(value), rect.centerX(), rect.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint);
            }
        }
    }

    private int presetColor(int value) {
        float ratio = value / (float) Math.max(1, size * size - 1);
        if (picturePreset == 0) {
            return blend(Color.rgb(26, 54, 93), Color.rgb(74, 144, 217), ratio);
        }
        if (picturePreset == 1) {
            return blend(Color.rgb(46, 175, 154), Color.rgb(201, 169, 110), ratio);
        }
        return blend(Color.rgb(51, 65, 85), Color.rgb(232, 237, 242), ratio);
    }

    private int blend(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.rgb((int) (ar + (br - ar) * t), (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || animating) return true;
        int index = indexAt(event.getX(), event.getY());
        if (index >= 0 && isNeighbor(index, emptyIndex)) move(index);
        return true;
    }

    private int indexAt(float x, float y) {
        float board = Math.min(getWidth() - dp(32), getHeight() - dp(32));
        float left = (getWidth() - board) / 2f;
        float top = (getHeight() - board) / 2f;
        float gap = dp(6);
        float cell = (board - gap * (size - 1)) / size;
        int col = (int) ((x - left) / (cell + gap));
        int row = (int) ((y - top) / (cell + gap));
        if (col < 0 || row < 0 || col >= size || row >= size) return -1;
        return row * size + col;
    }

    private void move(int index) {
        if (!started) {
            started = true;
            startTime = System.currentTimeMillis();
        }
        animTile = tiles[index];
        animFrom = index;
        animTo = emptyIndex;
        swap(index, emptyIndex);
        emptyIndex = index;
        moves++;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(160);
        animator.addUpdateListener(a -> {
            animating = true;
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                animating = false;
                animTile = -1;
                notifyStats();
                if (isSolved() && listener != null) listener.onSolved(moves, getSeconds());
            }
        });
        animator.start();
    }

    private boolean isSolved() {
        for (int i = 0; i < tiles.length - 1; i++) if (tiles[i] != i + 1) return false;
        return tiles[tiles.length - 1] == 0;
    }

    private boolean isNeighbor(int a, int b) {
        int ar = a / size, ac = a % size, br = b / size, bc = b % size;
        return Math.abs(ar - br) + Math.abs(ac - bc) == 1;
    }

    private int[] neighbors(int index) {
        int row = index / size;
        int col = index % size;
        int[] temp = new int[4];
        int count = 0;
        if (row > 0) temp[count++] = index - size;
        if (row < size - 1) temp[count++] = index + size;
        if (col > 0) temp[count++] = index - 1;
        if (col < size - 1) temp[count++] = index + 1;
        int[] result = new int[count];
        System.arraycopy(temp, 0, result, 0, count);
        return result;
    }

    private void swap(int a, int b) {
        int t = tiles[a];
        tiles[a] = tiles[b];
        tiles[b] = t;
    }

    private void notifyStats() {
        if (listener != null) listener.onStatsChanged(moves, getSeconds());
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
