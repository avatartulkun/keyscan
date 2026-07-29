package com.secureqr.scanner.ui.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.clipboard.ClipboardImportNotifier;
import com.secureqr.scanner.clipboard.ClipboardImportSettings;
import com.secureqr.scanner.security.SecuritySettings;

/** Groups lock timing and clipboard protection settings under Security Center. */
public class SecurityLockClearFragment extends Fragment {
    private static final String PREFS = "secureqr_settings";
    private LinearLayout content;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(R.string.security_lock_clear_title));
        content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        renderSettings();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (content != null) renderSettings();
    }

    private void renderSettings() {
        content.removeAllViews();
        content.addView(SecurityUi.row(this, "◷", 0xFFF59E0B,
                getString(R.string.auto_lock_time),
                getString(R.string.security_auto_lock_explanation),
                autoLockStatus(), R.color.action_icon_tint,
                view -> open(SecurityChoiceFragment.autoLock())), SecurityUi.matchWrap(this, 14));
        content.addView(SecurityUi.row(this, "✂", 0xFF7C3AED,
                getString(R.string.clipboard_auto_clear),
                getString(R.string.security_clipboard_explanation),
                clipboardStatus(), R.color.action_icon_tint,
                view -> open(SecurityChoiceFragment.clipboard())), SecurityUi.matchWrap(this, 8));

        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Switch smartImport = switchCard(
                getString(R.string.clipboard_smart_import_title),
                getString(R.string.clipboard_smart_import_summary),
                preferences.getBoolean(ClipboardImportSettings.KEY_SMART_IMPORT_ENABLED, false));
        smartImport.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(ClipboardImportSettings.KEY_SMART_IMPORT_ENABLED, checked).apply();
            ClipboardImportNotifier.refresh(requireContext());
        });
        content.addView((View) smartImport.getParent(), SecurityUi.matchWrap(this, 8));

        Switch clearAfterSave = switchCard(
                getString(R.string.clipboard_clear_after_import),
                getString(R.string.clipboard_clear_after_import_summary),
                preferences.getBoolean(ClipboardImportSettings.KEY_CLEAR_AFTER_SAVE, false));
        clearAfterSave.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(ClipboardImportSettings.KEY_CLEAR_AFTER_SAVE, checked).apply());
        content.addView((View) clearAfterSave.getParent(), SecurityUi.matchWrap(this, 8));
    }

    private Switch switchCard(String title, String summary, boolean checked) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(SecurityUi.dp(this, 14), SecurityUi.dp(this, 12),
                SecurityUi.dp(this, 10), SecurityUi.dp(this, 12));
        card.setBackgroundResource(R.drawable.bg_card);

        LinearLayout textArea = new LinearLayout(requireContext());
        textArea.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = SecurityUi.text(this, title, 16, R.color.text_main, true);
        TextView summaryView = SecurityUi.text(this, summary, 12, R.color.text_secondary, false);
        textArea.addView(titleView);
        textArea.addView(summaryView, SecurityUi.matchWrap(this, 3));
        card.addView(textArea, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Switch toggle = new Switch(requireContext());
        toggle.setChecked(checked);
        toggle.setMinWidth(SecurityUi.dp(this, 52));
        card.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, SecurityUi.dp(this, 48)));
        card.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        return toggle;
    }

    private String autoLockStatus() {
        int minutes = SecuritySettings.autoLockMinutes(requireContext());
        return minutes <= 0
                ? getString(R.string.status_off)
                : getString(R.string.security_auto_lock_minutes, minutes);
    }

    private String clipboardStatus() {
        int seconds = SecuritySettings.clipboardTimeoutSeconds(requireContext());
        if (seconds <= 0) return getString(R.string.status_off);
        if (seconds == 30) return getString(R.string.option_30_seconds);
        if (seconds == 60) return getString(R.string.option_1_minute);
        if (seconds == 300) return getString(R.string.option_5_minutes);
        return seconds + "s";
    }

    private void open(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}
