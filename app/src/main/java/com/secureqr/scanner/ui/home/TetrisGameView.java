package com.secureqr.scanner.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;

import androidx.annotation.Nullable;

import com.secureqr.scanner.R;

import java.util.Random;

public class TetrisGameView extends View {
    public interface Listener {
        void onStatsChanged(int score, int lines, int level);
        void onGameOver(int score);
    }

    private static final int ROWS = 20;
    private static final int COLS = 10;
    private static final int[][][][] SHAPES = {
            {
                    {{1, 0}, {1, 1}, {1, 2}, {1, 3}},
                    {{0, 2}, {1, 2}, {2, 2}, {3, 2}},
                    {{2, 0}, {2, 1}, {2, 2}, {2, 3}},
                    {{0, 1}, {1, 1}, {2, 1}, {3, 1}}
            },
            {
                    {{1, 1}, {1, 2}, {2, 1}, {2, 2}},
                    {{1, 1}, {1, 2}, {2, 1}, {2, 2}},
                    {{1, 1}, {1, 2}, {2, 1}, {2, 2}},
                    {{1, 1}, {1, 2}, {2, 1}, {2, 2}}
            },
            {
                    {{1, 0}, {1, 1}, {1, 2}, {2, 1}},
                    {{0, 1}, {1, 1}, {1, 2}, {2, 1}},
                    {{1, 1}, {2, 0}, {2, 1}, {2, 2}},
                    {{0, 1}, {1, 0}, {1, 1}, {2, 1}}
            },
            {
                    {{1, 1}, {1, 2}, {2, 0}, {2, 1}},
                    {{0, 1}, {1, 1}, {1, 2}, {2, 2}},
                    {{1, 1}, {1, 2}, {2, 0}, {2, 1}},
                    {{0, 1}, {1, 1}, {1, 2}, {2, 2}}
            },
            {
                    {{1, 0}, {1, 1}, {2, 1}, {2, 2}},
                    {{0, 2}, {1, 1}, {1, 2}, {2, 1}},
                    {{1, 0}, {1, 1}, {2, 1}, {2, 2}},
                    {{0, 2}, {1, 1}, {1, 2}, {2, 1}}
            },
            {
                    {{0, 0}, {1, 0}, {1, 1}, {1, 2}},
                    {{0, 1}, {0, 2}, {1, 1}, {2, 1}},
                    {{1, 0}, {1, 1}, {1, 2}, {2, 2}},
                    {{0, 1}, {1, 1}, {2, 0}, {2, 1}}
            },
            {
                    {{0, 2}, {1, 0}, {1, 1}, {1, 2}},
                    {{0, 1}, {1, 1}, {2, 1}, {2, 2}},
                    {{1, 0}, {1, 1}, {1, 2}, {2, 0}},
                    {{0, 0}, {0, 1}, {1, 1}, {2, 1}}
            }
    };
    private static final int[] PIECE_COLORS = {
            Color.rgb(38, 198, 218),
            Color.rgb(255, 202, 40),
            Color.rgb(171, 71, 188),
            Color.rgb(102, 187, 106),
            Color.rgb(239, 83, 80),
            Color.rgb(92, 107, 192),
            Color.rgb(255, 112, 67)
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final int[][] board = new int[ROWS][COLS];
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            softDropInternal();
            if (running && !gameOver) {
                handler.postDelayed(this, dropDelay());
            }
        }
    };
    private @Nullable Listener listener;
    private int currentType;
    private int currentRotation;
    private int currentRow;
    private int currentCol;
    private int score;
    private int lines;
    private int level = 1;
    private boolean running;
    private boolean gameOver;
    private boolean vibrationEnabled = true;

    public TetrisGameView(Context context) {
        super(context);
        resetBoard();
        spawnPiece();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
        notifyStats();
    }

    public void setVibrationEnabled(boolean enabled) {
        vibrationEnabled = enabled;
    }

    public void startGame() {
        running = true;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, dropDelay());
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    public void resetGame() {
        resetBoard();
        score = 0;
        lines = 0;
        level = 1;
        gameOver = false;
        spawnPiece();
        notifyStats();
        invalidate();
        startGame();
    }

    public void moveLeft() {
        if (gameOver) return;
        if (canPlace(currentRow, currentCol - 1, currentRotation)) {
            currentCol--;
            invalidate();
        }
    }

    public void moveRight() {
        if (gameOver) return;
        if (canPlace(currentRow, currentCol + 1, currentRotation)) {
            currentCol++;
            invalidate();
        }
    }

    public void rotate() {
        if (gameOver) return;
        int nextRotation = (currentRotation + 1) % 4;
        if (canPlace(currentRow, currentCol, nextRotation)) {
            currentRotation = nextRotation;
        } else if (canPlace(currentRow, currentCol - 1, nextRotation)) {
            currentCol--;
            currentRotation = nextRotation;
        } else if (canPlace(currentRow, currentCol + 1, nextRotation)) {
            currentCol++;
            currentRotation = nextRotation;
        }
        invalidate();
    }

    public void softDrop() {
        if (gameOver) return;
        if (canPlace(currentRow + 1, currentCol, currentRotation)) {
            currentRow++;
            score += 1;
            notifyStats();
            invalidate();
        } else {
            lockPiece();
        }
    }

    public void hardDrop() {
        if (gameOver) return;
        int distance = 0;
        while (canPlace(currentRow + 1, currentCol, currentRotation)) {
            currentRow++;
            distance++;
        }
        score += distance * 2;
        vibrate(18);
        lockPiece();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean dark = isDarkTheme();
        canvas.drawColor(dark ? Color.rgb(15, 23, 42) : Color.rgb(246, 248, 245));
        float cell = Math.min((getWidth() - dp(28)) / COLS, (getHeight() - dp(28)) / ROWS);
        if (cell <= 0) {
            return;
        }
        float boardWidth = cell * COLS;
        float boardHeight = cell * ROWS;
        float left = (getWidth() - boardWidth) / 2.0f;
        float top = (getHeight() - boardHeight) / 2.0f;
        RectF boardRect = new RectF(left - dp(8), top - dp(8), left + boardWidth + dp(8), top + boardHeight + dp(8));
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(boardRect.left, boardRect.top, boardRect.right, boardRect.bottom,
                dark ? Color.rgb(17, 24, 39) : Color.rgb(233, 239, 229),
                dark ? Color.rgb(30, 41, 59) : Color.rgb(255, 251, 238),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(boardRect, dp(18), dp(18), paint);
        paint.setShader(null);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                RectF rect = cellRect(left, top, cell, r, c);
                drawCell(canvas, rect, dark, (r + c) % 2 == 0);
                if (board[r][c] != 0) {
                    drawBlock(canvas, rect, board[r][c]);
                }
            }
        }
        drawCurrentPiece(canvas, left, top, cell);
        if (gameOver) {
            drawGameOver(canvas, boardRect);
        }
    }

    private void softDropInternal() {
        if (gameOver) return;
        if (canPlace(currentRow + 1, currentCol, currentRotation)) {
            currentRow++;
            invalidate();
        } else {
            lockPiece();
        }
    }

    private void lockPiece() {
        boolean lockedAboveBoard = false;
        for (int[] cell : SHAPES[currentType][currentRotation]) {
            int r = currentRow + cell[0];
            int c = currentCol + cell[1];
            if (r < 0) {
                lockedAboveBoard = true;
            } else if (r < ROWS && c >= 0 && c < COLS) {
                board[r][c] = PIECE_COLORS[currentType];
            }
        }
        if (lockedAboveBoard) {
            finishGame();
            return;
        }
        clearLines();
        spawnPiece();
        notifyStats();
        invalidate();
    }

    private void clearLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
                for (int shift = r; shift > 0; shift--) {
                    System.arraycopy(board[shift - 1], 0, board[shift], 0, COLS);
                }
                for (int c = 0; c < COLS; c++) {
                    board[0][c] = 0;
                }
                r++;
            }
        }
        if (cleared > 0) {
            lines += cleared;
            level = lines / 10 + 1;
            score += cleared * cleared * 100 * level;
            vibrate(26);
        }
    }

    private void spawnPiece() {
        currentType = random.nextInt(SHAPES.length);
        currentRotation = 0;
        currentRow = -1;
        currentCol = COLS / 2 - 2;
        if (!canPlace(currentRow, currentCol, currentRotation)) {
            finishGame();
        }
    }

    private void finishGame() {
        gameOver = true;
        running = false;
        handler.removeCallbacks(tick);
        notifyStats();
        invalidate();
        if (listener != null) {
            listener.onGameOver(score);
        }
    }

    private boolean canPlace(int row, int col, int rotation) {
        for (int[] cell : SHAPES[currentType][rotation]) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (c < 0 || c >= COLS || r >= ROWS) {
                return false;
            }
            if (r >= 0 && board[r][c] != 0) {
                return false;
            }
        }
        return true;
    }

    private void resetBoard() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = 0;
            }
        }
    }

    private void notifyStats() {
        if (listener != null) {
            listener.onStatsChanged(score, lines, level);
        }
    }

    private void drawCell(Canvas canvas, RectF rect, boolean dark, boolean alternate) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(dark
                ? (alternate ? Color.rgb(22, 32, 48) : Color.rgb(25, 38, 55))
                : (alternate ? Color.rgb(238, 243, 234) : Color.rgb(247, 247, 239)));
        canvas.drawRoundRect(rect, dp(5), dp(5), paint);
    }

    private void drawCurrentPiece(Canvas canvas, float left, float top, float cellSize) {
        for (int[] cell : SHAPES[currentType][currentRotation]) {
            int r = currentRow + cell[0];
            int c = currentCol + cell[1];
            if (r >= 0) {
                drawBlock(canvas, cellRect(left, top, cellSize, r, c), PIECE_COLORS[currentType]);
            }
        }
    }

    private RectF cellRect(float left, float top, float cellSize, int row, int col) {
        return new RectF(left + col * cellSize + dp(1.5f), top + row * cellSize + dp(1.5f),
                left + (col + 1) * cellSize - dp(1.5f), top + (row + 1) * cellSize - dp(1.5f));
    }

    private void drawBlock(Canvas canvas, RectF rect, int color) {
        RectF block = new RectF(rect.left + dp(1.5f), rect.top + dp(1.5f), rect.right - dp(1.5f), rect.bottom - dp(1.5f));
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(block.left, block.top, block.right, block.bottom,
                mix(color, Color.WHITE, 0.28f), mix(color, Color.BLACK, 0.22f), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(block, dp(6), dp(6), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(Color.argb(160, 255, 255, 255));
        canvas.drawLine(block.left + dp(4), block.top + dp(4), block.right - dp(4), block.top + dp(4), paint);
        canvas.drawLine(block.left + dp(4), block.top + dp(4), block.left + dp(4), block.bottom - dp(4), paint);
        paint.setColor(Color.argb(90, 0, 0, 0));
        canvas.drawLine(block.right - dp(4), block.top + dp(5), block.right - dp(4), block.bottom - dp(4), paint);
        canvas.drawLine(block.left + dp(5), block.bottom - dp(4), block.right - dp(4), block.bottom - dp(4), paint);
    }

    private void drawGameOver(Canvas canvas, RectF boardRect) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 0, 0, 0));
        canvas.drawRoundRect(boardRect, dp(18), dp(18), paint);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(24));
        paint.setColor(Color.WHITE);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = boardRect.centerY() - (metrics.ascent + metrics.descent) / 2.0f;
        canvas.drawText(getResources().getString(R.string.tetris_game_over), boardRect.centerX(), baseline, paint);
    }

    private int mix(int from, int to, float amount) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
        return Color.rgb(r, g, b);
    }

    private long dropDelay() {
        return Math.max(160L, 650L - (level - 1) * 45L);
    }

    private void vibrate(int duration) {
        if (!vibrationEnabled) return;
        try {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        } catch (Exception ignored) {
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private boolean isDarkTheme() {
        int color = getResources().getColor(R.color.surface_light);
        return Color.red(color) + Color.green(color) + Color.blue(color) < 382;
    }
}
