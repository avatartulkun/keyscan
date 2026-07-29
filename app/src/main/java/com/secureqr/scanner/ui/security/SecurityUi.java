package com.secureqr.scanner.ui.security;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;

final class SecurityUi {
    private SecurityUi() {
    }

    static LinearLayout page(Fragment fragment, String title) {
        LinearLayout root = new LinearLayout(fragment.requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.surface_light));
        root.setPadding(dp(fragment, 18), dp(fragment, 14), dp(fragment, 18), dp(fragment, 24));

        LinearLayout top = new LinearLayout(fragment.requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        ImageButton back = new ImageButton(fragment.requireContext());
        back.setImageResource(R.drawable.ic_arrow_back_24);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> fragment.getParentFragmentManager().popBackStack());
        top.addView(back, new LinearLayout.LayoutParams(dp(fragment, 44), dp(fragment, 44)));

        TextView heading = text(fragment, title, 22, R.color.text_main, true);
        heading.setGravity(Gravity.CENTER);
        top.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        View spacer = new View(fragment.requireContext());
        top.addView(spacer, new LinearLayout.LayoutParams(dp(fragment, 44), dp(fragment, 44)));
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(fragment, 54)));
        return root;
    }

    static TextView text(Fragment fragment, String value, int sp, int colorRes, boolean bold) {
        TextView view = new TextView(fragment.requireContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(ContextCompat.getColor(fragment.requireContext(), colorRes));
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    static TextView icon(Fragment fragment, String value, int color) {
        TextView view = text(fragment, value, 22, R.color.surface_light, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = round(color, dp(fragment, 12));
        view.setBackground(bg);
        return view;
    }

    static LinearLayout card(Fragment fragment) {
        LinearLayout card = new LinearLayout(fragment.requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(fragment, 14), dp(fragment, 14), dp(fragment, 14), dp(fragment, 14));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation(dp(fragment, 1));
        return card;
    }

    static LinearLayout row(Fragment fragment, String iconText, int iconColor, String title, String subtitle, String status, View.OnClickListener listener) {
        return row(fragment, iconText, iconColor, title, subtitle, status, R.color.success, listener);
    }

    static LinearLayout row(Fragment fragment, String iconText, int iconColor, String title, String subtitle, String status, int statusColorRes, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(fragment.requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(fragment, 12), dp(fragment, 12), dp(fragment, 10), dp(fragment, 12));
        row.setBackgroundResource(R.drawable.bg_card);
        row.setOnClickListener(listener);
        row.setClickable(true);

        row.addView(icon(fragment, iconText, iconColor), new LinearLayout.LayoutParams(dp(fragment, 44), dp(fragment, 44)));
        LinearLayout texts = new LinearLayout(fragment.requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(fragment, 12), 0, 0, 0);
        texts.addView(text(fragment, title, 16, R.color.text_main, true));
        TextView sub = text(fragment, subtitle, 12, R.color.text_secondary, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(fragment, 2);
        texts.addView(sub, subParams);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView state = text(fragment, status, 13, statusColorRes, true);
        state.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(state, new LinearLayout.LayoutParams(dp(fragment, 72), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView arrow = text(fragment, ">", 20, R.color.text_hint, true);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(fragment, 20), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    static TextView section(Fragment fragment, String title) {
        TextView view = text(fragment, title, 15, R.color.text_main, true);
        view.setPadding(dp(fragment, 2), dp(fragment, 12), 0, 0);
        return view;
    }

    static Button primaryButton(Fragment fragment, String text) {
        Button button = new Button(fragment.requireContext());
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackground(round(0xFF2563EB, dp(fragment, 10)));
        return button;
    }

    static GradientDrawable round(@ColorInt int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    static LinearLayout.LayoutParams matchWrap(Fragment fragment, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(fragment, topMargin);
        return params;
    }

    static int dp(Fragment fragment, int value) {
        return Math.round(value * fragment.getResources().getDisplayMetrics().density);
    }
}
