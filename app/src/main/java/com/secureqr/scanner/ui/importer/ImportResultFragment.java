package com.secureqr.scanner.ui.importer;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.secureqr.scanner.R;

public final class ImportResultFragment extends Fragment {
    private static final String ARG_IMPORTED = "imported";
    private static final String ARG_SKIPPED = "skipped";
    private static final String ARG_FAILED = "failed";

    public static ImportResultFragment newInstance(int imported, int skipped, int failed) {
        ImportResultFragment fragment = new ImportResultFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_IMPORTED, imported);
        args.putInt(ARG_SKIPPED, skipped);
        args.putInt(ARG_FAILED, failed);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(24));
        scroll.addView(root);
        TextView title = text(getString(R.string.import_result_title), 24, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);
        root.addView(text(getString(R.string.import_result_success, args == null ? 0 : args.getInt(ARG_IMPORTED)), 16, false), wrap(24));
        root.addView(text(getString(R.string.import_result_skipped, args == null ? 0 : args.getInt(ARG_SKIPPED)), 16, false), wrap(10));
        root.addView(text(getString(R.string.import_result_failed, args == null ? 0 : args.getInt(ARG_FAILED)), 16, false), wrap(10));
        Button done = new Button(requireContext());
        done.setText(R.string.import_result_done);
        done.setOnClickListener(v -> getParentFragmentManager().popBackStack(null, getParentFragmentManager().POP_BACK_STACK_INCLUSIVE));
        root.addView(done, wrap(28));
        return scroll;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getResources().getColor(R.color.text_main));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams wrap(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
