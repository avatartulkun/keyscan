package com.secureqr.scanner.ui.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.secureqr.scanner.R;

import java.util.List;

class ThemedSpinnerAdapter extends ArrayAdapter<String> {
    ThemedSpinnerAdapter(@NonNull Context context, @NonNull List<String> items) {
        super(context, android.R.layout.simple_spinner_item, items);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        return style(super.getView(position, convertView, parent), false);
    }

    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        return style(super.getDropDownView(position, convertView, parent), true);
    }

    private View style(View view, boolean dropdown) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_main));
            textView.setTextSize(15);
            textView.setPadding(dp(12), 0, dp(12), 0);
            textView.setGravity(android.view.Gravity.CENTER_VERTICAL);
        }
        view.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.card_background));
        if (dropdown) {
            view.setMinimumHeight(dp(48));
        }
        return view;
    }

    private int dp(int value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}

