package com.secureqr.scanner.ui.security;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.security.SecuritySettings;

public class SecurityChoiceFragment extends Fragment {
    private static final String ARG_MODE = "mode";
    private static final String MODE_AUTO_LOCK = "auto_lock";
    private static final String MODE_CLIPBOARD = "clipboard";

    public static SecurityChoiceFragment autoLock() {
        return create(MODE_AUTO_LOCK);
    }

    public static SecurityChoiceFragment clipboard() {
        return create(MODE_CLIPBOARD);
    }

    private static SecurityChoiceFragment create(String mode) {
        SecurityChoiceFragment fragment = new SecurityChoiceFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, mode);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        String mode = requireArguments().getString(ARG_MODE, MODE_AUTO_LOCK);
        boolean autoLock = MODE_AUTO_LOCK.equals(mode);
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(autoLock ? R.string.security_auto_lock_settings : R.string.security_clipboard_settings));
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.TextView icon = SecurityUi.icon(this, autoLock ? "◷" : "✂", autoLock ? 0xFFF5A623 : 0xFF7C3AED);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(SecurityUi.dp(this, 82), SecurityUi.dp(this, 82));
        iconParams.topMargin = SecurityUi.dp(this, 34);
        iconParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconParams);

        root.addView(SecurityUi.text(this, getString(autoLock ? R.string.security_auto_lock_choose : R.string.security_cleanup_time), 16, R.color.text_main, true), SecurityUi.matchWrap(this, 28));
        RadioGroup group = new RadioGroup(requireContext());
        group.setOrientation(RadioGroup.VERTICAL);
        LinearLayout card = SecurityUi.card(this);
        if (autoLock) {
            addOption(group, getString(R.string.option_now), 0, SecuritySettings.autoLockMinutes(requireContext()));
            addOption(group, getString(R.string.option_1_minute), 1, SecuritySettings.autoLockMinutes(requireContext()));
            addOption(group, getString(R.string.option_5_minutes), 5, SecuritySettings.autoLockMinutes(requireContext()));
            addOption(group, getString(R.string.option_15_minutes), 15, SecuritySettings.autoLockMinutes(requireContext()));
            addOption(group, getString(R.string.option_30_minutes), 30, SecuritySettings.autoLockMinutes(requireContext()));
        } else {
            addOption(group, getString(R.string.status_off), 0, SecuritySettings.clipboardTimeoutSeconds(requireContext()));
            addOption(group, getString(R.string.option_30_seconds), 30, SecuritySettings.clipboardTimeoutSeconds(requireContext()));
            addOption(group, getString(R.string.option_1_minute), 60, SecuritySettings.clipboardTimeoutSeconds(requireContext()));
            addOption(group, getString(R.string.option_5_minutes), 300, SecuritySettings.clipboardTimeoutSeconds(requireContext()));
        }
        card.addView(group);
        root.addView(card, SecurityUi.matchWrap(this, 12));

        LinearLayout note = SecurityUi.card(this);
        note.addView(SecurityUi.text(this,
                getString(autoLock ? R.string.security_auto_lock_explanation : R.string.security_clipboard_explanation),
                14, R.color.text_secondary, false));
        root.addView(note, SecurityUi.matchWrap(this, 20));

        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            RadioButton checked = radioGroup.findViewById(checkedId);
            if (checked == null) return;
            int value = (Integer) checked.getTag();
            if (autoLock) {
                SecuritySettings.setAutoLockMinutes(requireContext(), value);
            } else {
                SecuritySettings.setClipboardTimeoutSeconds(requireContext(), value);
            }
        });
        return scrollView;
    }

    private void addOption(RadioGroup group, String label, int value, int current) {
        RadioButton button = new RadioButton(requireContext());
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_main));
        button.setTag(value);
        button.setId(View.generateViewId());
        button.setMinHeight(SecurityUi.dp(this, 52));
        button.setChecked(value == current);
        group.addView(button, new RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
