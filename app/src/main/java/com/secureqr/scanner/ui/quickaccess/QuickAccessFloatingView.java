package com.secureqr.scanner.ui.quickaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import com.secureqr.scanner.R;
import com.secureqr.scanner.MainActivity;

/**
 * In-app draggable shortcut orb. It never requests system overlay permission and
 * only lives above KeyScan's own fragment container.
 */
public final class QuickAccessFloatingView extends FrameLayout {
    public static final String DEST_SCAN = "scan";
    public static final String DEST_SHARE = "share";
    public static final String DEST_GENERATOR = "generator";
    public static final String DEST_VAULT = "vault";
    public static final String DEST_PASSWORDS = "passwords";
    public static final String DEST_OTP = "otp";
    public static final String DEST_SETTINGS = "settings";
    public static final String DEST_SECURITY = "security";
    public static final String DEST_BACKUP = "backup";
    public static final String DEST_EXPORT = "export";
    public static final String DEST_TRASH = "trash";
    public static final String DEST_HISTORY = "history";
    public static final String ORB_STYLE_SPARKLE = "sparkle";
    public static final String ORB_STYLE_KS = "ks";
    public static final String ORB_STYLE_KEY = "key";

    private static final String PREFS = "secureqr_settings";
    private static final String KEY_X = "quick_access_x_fraction";
    private static final String KEY_Y = "quick_access_y_fraction";

    public interface Listener {
        void onDestinationSelected(@NonNull String destination);
    }

    private final SharedPreferences preferences;
    private final Listener listener;
    private final OrbButton orb;
    private final GridLayout panel;
    private boolean expanded;
    private boolean restoredPosition;
    private float downRawX;
    private float downRawY;
    private float downX;
    private float downY;
    private boolean dragging;
    private boolean consumingDismissGesture;

    public QuickAccessFloatingView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        this.listener = listener;
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);

        panel = createPanel();
        panel.setVisibility(GONE);
        addView(panel);

        orb = new OrbButton(context);
        orb.setColorFilter(Color.WHITE);
        orb.setContentDescription(context.getString(R.string.quick_access_open));
        orb.setPadding(dp(11), dp(11), dp(11), dp(11));
        orb.setElevation(0);
        orb.setStateListAnimator(null);
        refreshOrbStyle();
        orb.setOnTouchListener(this::handleOrbTouch);
        LayoutParams orbParams = new LayoutParams(dp(46), dp(46));
        addView(orb, orbParams);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void refreshOrbStyle() {
        String style = preferences.getString(
                MainActivity.KEY_QUICK_ACCESS_ICON_STYLE, ORB_STYLE_SPARKLE);
        orb.setStyle(style);
        boolean lightTheme = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
        orb.setLightAppearance(lightTheme);
        // The sparkle is a white mark in dark appearance; surface_light is a
        // dark surface color there and previously made the first icon disappear.
        orb.setColorFilter(lightTheme
                ? ContextCompat.getColor(getContext(), R.color.primary_reference_title)
                : Color.WHITE);
    }

    public void collapse() {
        if (!expanded) return;
        expanded = false;
        orb.setExpanded(false);
        orb.setContentDescription(getContext().getString(R.string.quick_access_open));
        orb.animate().rotation(0f).setDuration(180L).start();
        panel.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(150L)
                .withEndAction(() -> panel.setVisibility(GONE))
                .start();
    }

    public void setShortcutVisible(boolean visible) {
        if (!visible) collapse();
        setVisibility(visible ? VISIBLE : GONE);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (expanded && event.getActionMasked() == MotionEvent.ACTION_DOWN
                && !isPointInside(panel, event.getX(), event.getY())
                && !isPointInside(orb, event.getX(), event.getY())) {
            consumingDismissGesture = true;
            collapse();
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!consumingDismissGesture) {
            return false;
        }
        // Consume only the gesture that closed the expanded panel so it
        // cannot accidentally activate the page underneath. All normal page
        // gestures continue through this full-screen overlay.
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            consumingDismissGesture = false;
        }
        return true;
    }

    private boolean isPointInside(View child, float x, float y) {
        return child.getVisibility() == VISIBLE
                && x >= child.getX()
                && x <= child.getX() + child.getWidth()
                && y >= child.getY()
                && y <= child.getY() + child.getHeight();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        if (orb.getWidth() <= 0 || orb.getHeight() <= 0) {
            post(this::restoreOrClampPosition);
            return;
        }
        restoreOrClampPosition();
    }

    private void restoreOrClampPosition() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || orb.getWidth() <= 0 || orb.getHeight() <= 0) return;
        if (!restoredPosition) {
            restoredPosition = true;
            float xFraction = preferences.getFloat(KEY_X, 0.93f);
            float yFraction = preferences.getFloat(KEY_Y, 0.18f);
            orb.setX(clamp(xFraction * width, edge(), width - orb.getWidth() - edge()));
            orb.setY(clamp(yFraction * height, edge(), height - orb.getHeight() - edge()));
        } else {
            orb.setX(clamp(orb.getX(), edge(), width - orb.getWidth() - edge()));
            orb.setY(clamp(orb.getY(), edge(), height - orb.getHeight() - edge()));
        }
        if (expanded) positionPanel();
    }

    private boolean handleOrbTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = orb.getX();
                downY = orb.getY();
                dragging = false;
                orb.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80L).start();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && Math.hypot(dx, dy) > dp(7)) {
                    dragging = true;
                    collapse();
                }
                if (dragging) {
                    orb.setX(clamp(downX + dx, edge(), getWidth() - orb.getWidth() - edge()));
                    orb.setY(clamp(downY + dy, edge(), getHeight() - orb.getHeight() - edge()));
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                orb.animate().scaleX(1f).scaleY(1f).setDuration(100L).start();
                if (dragging) {
                    savePosition();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    toggle();
                }
                return true;
            default:
                return false;
        }
    }

    private void toggle() {
        if (expanded) {
            collapse();
            return;
        }
        expanded = true;
        orb.setExpanded(true);
        orb.setContentDescription(getContext().getString(R.string.quick_access_close));
        orb.animate().rotation(90f).setDuration(190L).start();
        positionPanel();
        panel.setPivotX(orb.getX() < getWidth() / 2f ? 0f : panel.getWidth());
        panel.setPivotY(panel.getHeight() / 2f);
        panel.setAlpha(0f);
        panel.setScaleX(0.9f);
        panel.setScaleY(0.9f);
        panel.setVisibility(VISIBLE);
        panel.bringToFront();
        orb.bringToFront();
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start();
    }

    private void positionPanel() {
        int panelWidth = dp(310);
        // Six rows of shortcuts plus their margins require more than the old 326dp;
        // keep the final row fully visible instead of clipping its circular icon.
        // 6 × (51dp item + 2dp vertical margins) + 16dp grid padding = 334dp.
        // This keeps the top and bottom spacing mathematically equal.
        int panelHeight = dp(334);
        int gap = dp(10);
        boolean openRight = orb.getX() + orb.getWidth() / 2f < getWidth() / 2f;
        float x = openRight ? orb.getX() + orb.getWidth() + gap : orb.getX() - panelWidth - gap;
        x = clamp(x, edge(), getWidth() - panelWidth - edge());
        float y = orb.getY() + orb.getHeight() / 2f - panelHeight / 2f;
        y = clamp(y, edge(), getHeight() - panelHeight - edge());
        LayoutParams params = new LayoutParams(panelWidth, panelHeight);
        params.leftMargin = Math.round(x);
        params.topMargin = Math.round(y);
        panel.setLayoutParams(params);
    }

    private GridLayout createPanel() {
        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(2);
        grid.setRowCount(6);
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        grid.setElevation(dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(getContext(), R.color.card_background));
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), ContextCompat.getColor(getContext(), R.color.border_soft));
        grid.setBackground(background);

        addItem(grid, DEST_SCAN, R.string.smart_scan_title, R.drawable.ic_scan, 0xFF2F8DFF);
        addItem(grid, DEST_SHARE, R.string.home_generate, R.drawable.ic_primary_share, 0xFF22C98B);
        addItem(grid, DEST_GENERATOR, R.string.random_password_title, R.drawable.ic_key_gear, 0xFF8257E8);
        addItem(grid, DEST_VAULT, R.string.vault_title, R.drawable.ic_shield, 0xFF19B7B0);
        addItem(grid, DEST_PASSWORDS, R.string.home_password_forge, R.drawable.ic_key_line, 0xFF2563EB);
        addItem(grid, DEST_OTP, R.string.home_otp, R.drawable.ic_qr, 0xFF7C4DDF);
        addItem(grid, DEST_SETTINGS, R.string.settings_title, R.drawable.ic_settings, 0xFFFFA226);
        addItem(grid, DEST_SECURITY, R.string.security_center_title, R.drawable.ic_shield, 0xFF12B89A);
        addItem(grid, DEST_BACKUP, R.string.webdav_title, R.drawable.ic_primary_backup, 0xFF13AFC6);
        addItem(grid, DEST_EXPORT, R.string.export_data, R.drawable.ic_export, 0xFF3D73E8);
        addItem(grid, DEST_TRASH, R.string.trash_title, R.drawable.ic_delete_24, 0xFFE94684);
        addItem(grid, DEST_HISTORY, R.string.home_history, R.drawable.ic_history, 0xFF9259E8);
        return grid;
    }

    private void addItem(GridLayout grid, String destination, int labelRes, int iconRes, int color) {
        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(6), dp(4), dp(6), dp(4));
        item.setBackground(selectableBackground());
        item.setContentDescription(getContext().getString(labelRes));

        ImageView icon = new ImageView(getContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setElevation(0);
        icon.setStateListAnimator(null);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setShape(GradientDrawable.OVAL);
        iconBackground.setColor(color);
        icon.setBackground(iconBackground);
        item.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView label = new TextView(getContext());
        label.setText(labelRes);
        label.setTextColor(ContextCompat.getColor(getContext(), R.color.text_main));
        label.setTextSize(12);
        label.setMaxLines(2);
        label.setPadding(dp(8), 0, dp(2), 0);
        item.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        item.setOnClickListener(v -> {
            collapse();
            listener.onDestinationSelected(destination);
        });

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(147);
        params.height = dp(51);
        params.setMargins(dp(2), dp(1), dp(2), dp(1));
        grid.addView(item, params);
    }

    private android.graphics.drawable.Drawable selectableBackground() {
        android.util.TypedValue value = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return ContextCompat.getDrawable(getContext(), value.resourceId);
    }

    private void savePosition() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        preferences.edit()
                .putFloat(KEY_X, orb.getX() / getWidth())
                .putFloat(KEY_Y, orb.getY() / getHeight())
                .apply();
    }

    private int edge() {
        return dp(10);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }

    private static final class OrbButton extends AppCompatImageButton {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint closePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean expanded;
        private boolean lightAppearance;
        private String style = ORB_STYLE_SPARKLE;

        OrbButton(Context context) {
            super(context);
            setBackground(null);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            ringPaint.setShadowLayer(dp(context, 6), 0, 0, 0x702F8DFF);
            centerPaint.setColor(0xFF13233D);
            closePaint.setColor(Color.WHITE);
            closePaint.setStrokeWidth(dp(context, 3));
            closePaint.setStrokeCap(Paint.Cap.ROUND);
            glyphPaint.setColor(Color.WHITE);
            glyphPaint.setStrokeCap(Paint.Cap.ROUND);
            glyphPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setStyle(String style) {
            if (!ORB_STYLE_KS.equals(style) && !ORB_STYLE_KEY.equals(style)) {
                style = ORB_STYLE_SPARKLE;
            }
            this.style = style;
            setImageResource(ORB_STYLE_SPARKLE.equals(style)
                    ? R.drawable.ic_quick_access_sparkle : 0);
            invalidate();
        }

        void setLightAppearance(boolean lightAppearance) {
            this.lightAppearance = lightAppearance;
            centerPaint.setColor(lightAppearance ? 0xFFF8FAFC : 0xFF13233D);
            glyphPaint.setColor(lightAppearance ? 0xFF142A4A : Color.WHITE);
            closePaint.setColor(lightAppearance ? 0xFF142A4A : Color.WHITE);
            invalidate();
        }

        void setExpanded(boolean expanded) {
            this.expanded = expanded;
            setImageAlpha(expanded ? 0 : 255);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(getContext(), 3);
            ringPaint.setStrokeWidth(dp(getContext(), 3));
            ringPaint.setShadowLayer(dp(getContext(), 6), 0, 0,
                    lightAppearance ? 0x302F8DFF : 0x702F8DFF);
            ringPaint.setShader(new SweepGradient(cx, cy,
                    new int[]{0xFF23C7FF, 0xFF7357F0, 0xFFFF3F91, 0xFFFFA326, 0xFF23C7FF},
                    null));
            canvas.drawCircle(cx, cy, radius, ringPaint);
            ringPaint.clearShadowLayer();
            canvas.drawCircle(cx, cy, radius - dp(getContext(), 3), centerPaint);
            super.onDraw(canvas);
            if (expanded) {
                float offset = dp(getContext(), 6);
                canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, closePaint);
                canvas.drawLine(cx + offset, cy - offset, cx - offset, cy + offset, closePaint);
            } else if (ORB_STYLE_KS.equals(style)) {
                drawKs(canvas, cx, cy);
            } else if (ORB_STYLE_KEY.equals(style)) {
                drawKey(canvas, cx, cy);
            }
        }

        private void drawKs(Canvas canvas, float cx, float cy) {
            glyphPaint.setStyle(Paint.Style.FILL);
            glyphPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            glyphPaint.setTextAlign(Paint.Align.CENTER);
            glyphPaint.setTextSize(dp(getContext(), 15));
            Paint.FontMetrics metrics = glyphPaint.getFontMetrics();
            canvas.drawText("KS", cx, cy - (metrics.ascent + metrics.descent) / 2f, glyphPaint);
        }

        private void drawKey(Canvas canvas, float cx, float cy) {
            glyphPaint.setStyle(Paint.Style.STROKE);
            glyphPaint.setStrokeWidth(dp(getContext(), 2.6f));
            float ringX = cx - dp(getContext(), 5);
            float ringY = cy - dp(getContext(), 5);
            float ringRadius = dp(getContext(), 4);
            canvas.drawCircle(ringX, ringY, ringRadius, glyphPaint);
            canvas.drawLine(ringX + dp(getContext(), 3), ringY + dp(getContext(), 3),
                    cx + dp(getContext(), 8), cy + dp(getContext(), 8), glyphPaint);
            canvas.drawLine(cx + dp(getContext(), 3), cy + dp(getContext(), 3),
                    cx + dp(getContext(), 6), cy, glyphPaint);
            canvas.drawLine(cx + dp(getContext(), 6), cy + dp(getContext(), 6),
                    cx + dp(getContext(), 9), cy + dp(getContext(), 3), glyphPaint);
        }

        private static int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        private static float dp(Context context, float value) {
            return value * context.getResources().getDisplayMetrics().density;
        }
    }
}
