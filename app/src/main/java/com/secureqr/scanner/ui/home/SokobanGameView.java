package com.secureqr.scanner.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.secureqr.scanner.R;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class SokobanGameView extends View {
    public interface Listener {
        void onStatsChanged(int level, int moves);
        void onSolved(int level, int moves);
    }

    private static final String[][] LEVELS = {
            {"#####", "#@ $.#", "#####"},
            {"######", "#@ $.#", "#    #", "######"},
            {"######", "# @  #", "# $ .#", "######"},
            {"#######", "#@ $ .#", "#  $ .#", "#######"},
            {"#######", "# . . #", "# $$  #", "#  @  #", "#######"},
            {"########", "# .    #", "# $$ . #", "#  @   #", "########"},
            {"########", "#@     #", "# $$$..#", "#     .#", "########"},
            {"########", "#@     #", "# $$$  #", "# ...  #", "########"},
            {"########", "#      #", "#..$$  #", "#   #  #", "#    $.#", "#  @   #", "########"},
            {"#########", "#  #    #", "#   . $ #", "#  #$   #", "#     # #", "#.   #  #", "# .$ @  #", "#########"},
            {"#########", "#   $.. #", "# $ # $ #", "#    #  #", "#       #", "#  @    #", "#   . # #", "#########"},
            {"########", "# .  $ #", "# .#   #", "# $$   #", "# @   ##", "#      #", "# .    #", "########"},
            {"#########", "#    #  #", "##   $ .#", "#  ..$$ #", "#      ##", "#    @  #", "#########"},
            {"#########", "#  .    #", "#. @  # #", "#$   .$ #", "#     $ #", "#  #    #", "#########"},
            {"########", "#  .   #", "#  #   #", "#   @  #", "#  $$  #", "# $ .. #", "########"},
            {"########", "#  . # #", "#   $ .#", "#     $#", "# $    #", "#      #", "#  . @ #", "########"}
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Deque<State> undo = new ArrayDeque<>();
    private @Nullable Listener listener;
    private char[][] map;
    private Set<Integer> targets = new HashSet<>();
    private int playerRow;
    private int playerCol;
    private int level;
    private int moves;
    private float downX;
    private float downY;
    private boolean vibrationEnabled = true;

    public SokobanGameView(Context context) {
        super(context);
        loadLevel(0);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setVibrationEnabled(boolean enabled) {
        vibrationEnabled = enabled;
    }

    public static int getLevelCount() {
        return LEVELS.length;
    }

    public void loadLevel(int index) {
        level = Math.max(0, Math.min(index, LEVELS.length - 1));
        String[] rows = LEVELS[level];
        int h = rows.length;
        int w = 0;
        for (String row : rows) w = Math.max(w, row.length());
        map = new char[h][w];
        targets.clear();
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                char ch = c < rows[r].length() ? rows[r].charAt(c) : ' ';
                if (ch == '@') {
                    playerRow = r;
                    playerCol = c;
                    ch = ' ';
                } else if (ch == '.') {
                    targets.add(key(r, c));
                    ch = ' ';
                } else if (ch == '*') {
                    targets.add(key(r, c));
                    ch = '$';
                }
                map[r][c] = ch;
            }
        }
        moves = 0;
        undo.clear();
        notifyStats();
        invalidate();
    }

    public int getLevel() {
        return level + 1;
    }

    public int getMoves() {
        return moves;
    }

    public void resetLevel() {
        loadLevel(level);
    }

    public void undo() {
        State state = undo.pollLast();
        if (state == null) return;
        copy(state.map, map);
        playerRow = state.playerRow;
        playerCol = state.playerCol;
        moves = state.moves;
        notifyStats();
        invalidate();
    }

    public void move(int dr, int dc) {
        int nr = playerRow + dr;
        int nc = playerCol + dc;
        if (!inside(nr, nc) || map[nr][nc] == '#') return;
        int br = nr + dr;
        int bc = nc + dc;
        if (map[nr][nc] == '$') {
            if (!inside(br, bc) || map[br][bc] == '#' || map[br][bc] == '$') return;
            pushUndo();
            map[br][bc] = '$';
            map[nr][nc] = ' ';
            playerRow = nr;
            playerCol = nc;
            moves++;
            if (targets.contains(key(br, bc))) vibrate();
        } else {
            pushUndo();
            playerRow = nr;
            playerCol = nc;
            moves++;
        }
        notifyStats();
        invalidate();
        if (solved() && listener != null) listener.onSolved(level + 1, moves);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int rows = map.length;
        int cols = map[0].length;
        float board = Math.min(getWidth() - dp(24), getHeight() - dp(24));
        float cell = board / Math.max(rows, cols);
        float left = (getWidth() - cell * cols) / 2f;
        float top = (getHeight() - cell * rows) / 2f;
        boolean dark = isDarkTheme();
        canvas.drawColor(dark ? Color.rgb(15, 23, 42) : Color.rgb(246, 248, 245));
        RectF boardRect = new RectF(left - dp(8), top - dp(8), left + cell * cols + dp(5), top + cell * rows + dp(5));
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(boardRect.left, boardRect.top, boardRect.right, boardRect.bottom,
                dark ? Color.rgb(17, 24, 39) : Color.rgb(233, 239, 229),
                dark ? Color.rgb(30, 41, 59) : Color.rgb(251, 250, 244),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(boardRect, dp(18), dp(18), paint);
        paint.setShader(null);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                RectF rect = new RectF(left + c * cell, top + r * cell, left + (c + 1) * cell - dp(3), top + (r + 1) * cell - dp(3));
                boolean target = targets.contains(key(r, c));
                drawFloor(canvas, rect, dark, (r + c) % 2 == 0);
                if (map[r][c] == '#') {
                    drawWall(canvas, rect, dark);
                    continue;
                }
                if (target) {
                    drawTarget(canvas, rect, cell, dark);
                }
                if (map[r][c] == '$') {
                    drawBox(canvas, rect, target, dark);
                }
            }
        }
        RectF player = new RectF(left + playerCol * cell, top + playerRow * cell, left + (playerCol + 1) * cell - dp(3), top + (playerRow + 1) * cell - dp(3));
        drawPlayer(canvas, player, dark);
    }

    private void drawFloor(Canvas canvas, RectF rect, boolean dark, boolean alternate) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dark
                ? (alternate ? Color.rgb(22, 32, 48) : Color.rgb(25, 38, 55))
                : (alternate ? Color.rgb(238, 243, 234) : Color.rgb(247, 247, 239)));
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
    }

    private void drawWall(Canvas canvas, RectF rect, boolean dark) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                dark ? Color.rgb(71, 85, 105) : Color.rgb(91, 106, 118),
                dark ? Color.rgb(30, 41, 59) : Color.rgb(54, 67, 78),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, dp(7), dp(7), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(dark ? Color.rgb(100, 116, 139) : Color.rgb(126, 140, 150));
        float midY = rect.top + rect.height() * 0.52f;
        canvas.drawLine(rect.left + dp(5), midY, rect.right - dp(5), midY, paint);
        canvas.drawLine(rect.centerX(), rect.top + dp(5), rect.centerX(), midY - dp(2), paint);
        canvas.drawLine(rect.left + rect.width() * 0.34f, midY + dp(2), rect.left + rect.width() * 0.34f, rect.bottom - dp(5), paint);
    }

    private void drawTarget(Canvas canvas, RectF rect, float cell, boolean dark) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(rect.centerX(), rect.centerY(), cell * 0.34f,
                dark ? Color.argb(92, 45, 212, 191) : Color.argb(100, 74, 144, 217),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.32f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.2f));
        paint.setColor(dark ? Color.rgb(45, 212, 191) : Color.rgb(46, 175, 154));
        canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.22f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.06f, paint);
    }

    private void drawBox(Canvas canvas, RectF rect, boolean target, boolean dark) {
        RectF box = new RectF(rect.left + dp(4), rect.top + dp(4), rect.right - dp(4), rect.bottom - dp(4));
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                target ? Color.rgb(45, 212, 191) : Color.rgb(235, 181, 96),
                target ? Color.rgb(17, 122, 105) : Color.rgb(151, 94, 42),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(box, dp(7), dp(7), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(target ? Color.rgb(226, 252, 244) : (dark ? Color.rgb(251, 219, 155) : Color.rgb(117, 72, 31)));
        canvas.drawRoundRect(box, dp(7), dp(7), paint);
        paint.setStrokeWidth(dp(1.4f));
        canvas.drawLine(box.left + dp(7), box.top + dp(7), box.right - dp(7), box.bottom - dp(7), paint);
        canvas.drawLine(box.right - dp(7), box.top + dp(7), box.left + dp(7), box.bottom - dp(7), paint);
    }

    private void drawPlayer(Canvas canvas, RectF rect, boolean dark) {
        RectF body = new RectF(rect.left + dp(5), rect.top + dp(5), rect.right - dp(5), rect.bottom - dp(5));
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(body.left, body.top, body.right, body.bottom,
                dark ? Color.rgb(138, 180, 248) : Color.rgb(74, 144, 217),
                dark ? Color.rgb(34, 92, 176) : Color.rgb(26, 54, 93),
                Shader.TileMode.CLAMP));
        canvas.drawOval(body, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 255, 255, 255));
        canvas.drawCircle(body.left + body.width() * 0.34f, body.top + body.height() * 0.30f, body.width() * 0.10f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(dark ? Color.rgb(226, 232, 240) : Color.rgb(255, 255, 255));
        canvas.drawLine(body.centerX(), body.centerY(), body.right + dp(4), body.centerY(), paint);
        canvas.drawLine(body.right, body.centerY(), body.right, body.centerY() + dp(6), paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > Math.abs(dy)) move(0, dx > 0 ? 1 : -1);
            else move(dy > 0 ? 1 : -1, 0);
            return true;
        }
        return true;
    }

    private void pushUndo() {
        undo.addLast(new State(map, playerRow, playerCol, moves));
    }

    private boolean solved() {
        for (int key : targets) {
            int r = key / 100;
            int c = key % 100;
            if (map[r][c] != '$') return false;
        }
        return true;
    }

    private boolean inside(int r, int c) {
        return r >= 0 && c >= 0 && r < map.length && c < map[0].length;
    }

    private int key(int r, int c) {
        return r * 100 + c;
    }

    private void copy(char[][] from, char[][] to) {
        for (int r = 0; r < from.length; r++) System.arraycopy(from[r], 0, to[r], 0, from[r].length);
    }

    private void notifyStats() {
        if (listener != null) listener.onStatsChanged(level + 1, moves);
    }

    private void vibrate() {
        if (!vibrationEnabled) return;
        try {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(25);
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

    private static class State {
        final char[][] map;
        final int playerRow;
        final int playerCol;
        final int moves;

        State(char[][] source, int playerRow, int playerCol, int moves) {
            this.map = new char[source.length][source[0].length];
            for (int r = 0; r < source.length; r++) System.arraycopy(source[r], 0, this.map[r], 0, source[r].length);
            this.playerRow = playerRow;
            this.playerCol = playerCol;
            this.moves = moves;
        }
    }
}
