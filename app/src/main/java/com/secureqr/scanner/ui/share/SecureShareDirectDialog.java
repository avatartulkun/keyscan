package com.secureqr.scanner.ui.share;

import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.QRGenerator;

import org.json.JSONObject;

public final class SecureShareDirectDialog {
    private static final long[] LIFETIMES = {30_000L, 60_000L, 300_000L, 600_000L};
    private SecureShareDirectDialog() { }

    public static void show(Fragment fragment, PasswordEntry entry) {
        show(fragment, entry, null);
    }

    public static void show(Fragment fragment, PasswordEntry entry, Runnable onGenerated) {
        if (fragment == null || entry == null || !fragment.isAdded()) return;
        String[] choices = fragment.getResources().getStringArray(R.array.secure_share_lifetime_labels);
        Dialog dialog = secureDialog(fragment);
        LinearLayout card = card(fragment);
        card.addView(title(fragment, "⏱  " + fragment.getString(R.string.secure_share_choose_lifetime)));
        card.addView(body(fragment, fragment.getString(R.string.secure_share_lifetime_hint)),
                top(dp(fragment, 6)));

        LinearLayout options = new LinearLayout(fragment.requireContext());
        options.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams optionsParams = top(dp(fragment, 14));
        optionsParams.bottomMargin = dp(fragment, 6);
        card.addView(options, optionsParams);
        for (int i = 0; i < choices.length && i < LIFETIMES.length; i++) {
            final int selected = i;
            LinearLayout row = new LinearLayout(fragment.requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(fragment, 4), 0, dp(fragment, 4), 0);
            TextView radio = text(fragment, "○", 25, secondaryColor(fragment));
            row.addView(radio, new LinearLayout.LayoutParams(dp(fragment, 38), dp(fragment, 48)));
            TextView label = text(fragment, choices[i], 15, titleColor(fragment));
            label.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(fragment, 48), 1f));
            row.setOnClickListener(v -> {
                radio.setText("●");
                radio.setTextColor(accentColor(fragment));
                dialog.dismiss();
                VaultAccessManager.requireAuthentication(fragment.requireActivity(),
                        fragment.getString(R.string.secure_share_auth_prompt),
                        () -> generate(fragment, entry, LIFETIMES[selected], onGenerated));
            });
            options.addView(row);
        }
        TextView cancel = action(fragment, fragment.getString(R.string.common_action_cancel),
                secondaryColor(fragment));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout actions = actionRow(fragment);
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(fragment, 72), dp(fragment, 48)));
        card.addView(actions);
        dialog.setContentView(card);
        showSecureDialog(fragment, dialog);
    }

    private static void generate(Fragment fragment, PasswordEntry entry, long lifetimeMs,
                                 Runnable onGenerated) {
        try {
            JSONObject payload = new JSONObject()
                    .put("title", safe(entry.displayTitle()))
                    .put("website", safe(entry.websiteDomain))
                    .put("username", safe(entry.displayUsername()))
                    .put("password", safe(entry.password))
                    .put("createdAt", entry.createdAt)
                    .put("sharedAt", System.currentTimeMillis());
            String qr = SecureShareProtocol.createDirect(payload, lifetimeMs);
            SecureShareStateStore.recordShare(fragment.requireContext(), entry);
            if (onGenerated != null) onGenerated.run();
            showQr(fragment, qr, lifetimeMs);
        } catch (Exception error) {
            Toast.makeText(fragment.requireContext(), R.string.secure_share_generate_failed, Toast.LENGTH_LONG).show();
        }
    }

    private static void showQr(Fragment fragment, String content, long lifetimeMs) {
        Dialog dialog = secureDialog(fragment);
        LinearLayout box = card(fragment);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView heading = title(fragment,
                "🔐  " + fragment.getString(R.string.secure_share_response_qr_title));
        heading.setGravity(Gravity.START);
        box.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView hint = body(fragment, fragment.getString(R.string.secure_share_qr_import_hint));
        hint.setGravity(Gravity.START);
        box.addView(hint, top(dp(fragment, 6)));

        LinearLayout qrCard = new LinearLayout(fragment.requireContext());
        qrCard.setGravity(Gravity.CENTER);
        qrCard.setPadding(dp(fragment, 14), dp(fragment, 14), dp(fragment, 14), dp(fragment, 14));
        qrCard.setBackground(round(Color.WHITE, dp(fragment, 12)));
        ImageView image = new ImageView(fragment.requireContext());
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap bitmap = QRGenerator.generateQR(content, dp(fragment, 230));
        image.setImageBitmap(bitmap);
        qrCard.addView(image, new LinearLayout.LayoutParams(dp(fragment, 230), dp(fragment, 230)));
        box.addView(qrCard, top(dp(fragment, 16)));

        TextView countdown = text(fragment, "", 15, accentColor(fragment));
        countdown.setGravity(Gravity.CENTER);
        box.addView(countdown, top(dp(fragment, 14)));

        LinearLayout security = new LinearLayout(fragment.requireContext());
        security.setOrientation(LinearLayout.VERTICAL);
        security.setPadding(dp(fragment, 14), dp(fragment, 12), dp(fragment, 14), dp(fragment, 12));
        security.setBackground(round(securityCardColor(fragment), dp(fragment, 12)));
        security.addView(text(fragment,
                "🔒  " + fragment.getString(R.string.secure_share_encrypted_label),
                14, titleColor(fragment)));
        TextView warning = text(fragment, fragment.getString(R.string.secure_share_bearer_warning),
                12, securityBodyColor(fragment));
        warning.setPadding(0, dp(fragment, 5), 0, 0);
        security.addView(warning);
        box.addView(security, top(dp(fragment, 12)));

        LinearLayout actions = actionRow(fragment);
        TextView close = action(fragment, fragment.getString(R.string.common_action_close),
                accentColor(fragment));
        close.setOnClickListener(v -> dialog.dismiss());
        actions.addView(close, new LinearLayout.LayoutParams(dp(fragment, 72), dp(fragment, 48)));
        box.addView(actions, top(dp(fragment, 4)));
        dialog.setContentView(box);
        CountDownTimer timer = new CountDownTimer(lifetimeMs, 1_000L) {
            public void onTick(long millis) {
                countdown.setText("⏱  " + fragment.getString(R.string.secure_share_expires_seconds,
                        Math.max(1, (millis + 999) / 1000)));
            }
            public void onFinish() {
                countdown.setText(R.string.secure_share_expired);
                image.setAlpha(0.18f);
            }
        };
        dialog.setOnShowListener(unused -> timer.start());
        dialog.setOnDismissListener(unused -> timer.cancel());
        showSecureDialog(fragment, dialog);
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static Dialog secureDialog(Fragment fragment) {
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        return dialog;
    }
    private static LinearLayout card(Fragment fragment) {
        LinearLayout card = new LinearLayout(fragment.requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(fragment, 24), dp(fragment, 22), dp(fragment, 24), dp(fragment, 10));
        card.setBackground(round(dialogCardColor(fragment), dp(fragment, 20)));
        return card;
    }
    private static TextView title(Fragment fragment, String value) {
        TextView view = text(fragment, value, 18, titleColor(fragment));
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }
    private static TextView body(Fragment fragment, String value) {
        TextView view = text(fragment, value, 14, secondaryColor(fragment));
        view.setLineSpacing(0, 1.12f);
        return view;
    }
    private static TextView action(Fragment fragment, String value, int color) {
        TextView view = text(fragment, value, 14, color);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setClickable(true);
        return view;
    }
    private static TextView text(Fragment fragment, String value, float size, int color) {
        TextView view = new TextView(fragment.requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private static LinearLayout actionRow(Fragment fragment) {
        LinearLayout row = new LinearLayout(fragment.requireContext());
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        return row;
    }
    private static LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = margin;
        return params;
    }
    private static GradientDrawable round(int color, float radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }
    private static boolean isNight(Fragment fragment) {
        return (fragment.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
    private static int dialogCardColor(Fragment fragment) {
        return Color.parseColor(isNight(fragment) ? "#16243A" : "#FFFFFF");
    }
    private static int securityCardColor(Fragment fragment) {
        return Color.parseColor(isNight(fragment) ? "#1E293B" : "#EEF4FA");
    }
    private static int titleColor(Fragment fragment) {
        return Color.parseColor(isNight(fragment) ? "#F8FAFC" : "#0F172A");
    }
    private static int secondaryColor(Fragment fragment) {
        return Color.parseColor(isNight(fragment) ? "#94A3B8" : "#64748B");
    }
    private static int securityBodyColor(Fragment fragment) {
        return Color.parseColor(isNight(fragment) ? "#CBD5E1" : "#475569");
    }
    private static int accentColor(Fragment fragment) {
        return Color.parseColor(isNight(fragment) ? "#38BDF8" : "#0284C7");
    }
    private static void showSecureDialog(Fragment fragment, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams before = window.getAttributes();
            before.dimAmount = 0.72f;
            window.setAttributes(before);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            int width = Math.round(fragment.getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }
    private static int dp(Fragment fragment, int value) {
        return Math.round(value * fragment.getResources().getDisplayMetrics().density);
    }
}
