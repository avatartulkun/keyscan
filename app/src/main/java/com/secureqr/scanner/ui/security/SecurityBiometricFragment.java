package com.secureqr.scanner.ui.security;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.PinLockHelper;

public class SecurityBiometricFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(R.string.security_biometric_settings));
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView icon = SecurityUi.icon(this, "●", 0xFF25B96F);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(SecurityUi.dp(this, 82), SecurityUi.dp(this, 82));
        iconParams.topMargin = SecurityUi.dp(this, 34);
        iconParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconParams);

        LinearLayout row = SecurityUi.card(this);
        LinearLayout line = new LinearLayout(requireContext());
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        line.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(SecurityUi.text(this, getString(R.string.security_biometric_unlock), 17, R.color.text_main, true));
        texts.addView(SecurityUi.text(this, getString(R.string.security_biometric_unlock_summary), 13, R.color.text_secondary, false), SecurityUi.matchWrap(this, 4));
        line.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(requireContext());
        sw.setChecked(BiometricUnlockHelper.isEnabled(requireContext()));
        line.addView(sw);
        row.addView(line);
        root.addView(row, SecurityUi.matchWrap(this, 28));

        LinearLayout note = SecurityUi.card(this);
        note.addView(SecurityUi.text(this, getString(R.string.security_tip), 15, R.color.success, true));
        note.addView(SecurityUi.text(this, getString(R.string.security_biometric_note), 14, R.color.text_secondary, false), SecurityUi.matchWrap(this, 8));
        root.addView(note, SecurityUi.matchWrap(this, 18));

        final boolean[] updatingSwitch = {false};
        sw.setOnCheckedChangeListener((buttonView, checked) -> {
            if (updatingSwitch[0]) return;
            boolean current = BiometricUnlockHelper.isEnabled(requireContext());
            if (checked == current) return;
            if (checked && !PinLockHelper.isConfigured(requireContext())) {
                updatingSwitch[0] = true;
                buttonView.setChecked(false);
                updatingSwitch[0] = false;
                Toast.makeText(requireContext(), R.string.security_pin_setup_first, Toast.LENGTH_SHORT).show();
                return;
            }
            if (checked && !BiometricUnlockHelper.isAvailable(requireContext())) {
                updatingSwitch[0] = true;
                buttonView.setChecked(false);
                updatingSwitch[0] = false;
                Toast.makeText(requireContext(), R.string.security_device_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            updatingSwitch[0] = true;
            buttonView.setChecked(current);
            updatingSwitch[0] = false;
            VaultAccessManager.requirePinAuthentication(
                    requireActivity(),
                    getString(R.string.biometric_change_pin_auth),
                    () -> {
                        BiometricUnlockHelper.setEnabled(requireContext(), checked);
                        SecuritySettings.prefs(requireContext()).edit()
                                .putBoolean(SecuritySettings.KEY_BIOMETRIC_ENABLED, checked)
                                .putLong(SecuritySettings.KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                                .apply();
                        updatingSwitch[0] = true;
                        buttonView.setChecked(checked);
                        updatingSwitch[0] = false;
                    });
        });
        return scrollView;
    }
}
