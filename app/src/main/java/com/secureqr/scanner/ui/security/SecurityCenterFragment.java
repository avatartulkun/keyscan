package com.secureqr.scanner.ui.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.SecureSecretStore;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.PinLockHelper;

public class SecurityCenterFragment extends Fragment {
    private static final String SETTINGS_PREFS = "secureqr_settings";
    private static final String KEY_LAST_VERIFY_TIME = "recovery_last_verify_time";
    private static final String KEY_LAST_VERIFY_OK = "recovery_last_verify_ok";

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(R.string.security_center_title));
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(statusCard(), SecurityUi.matchWrap(this, 14));

        root.addView(SecurityUi.row(this, "PIN", 0xFF2F7CF6, getString(R.string.security_unlock_pin), getString(R.string.security_unlock_pin_summary), pinStatus(), statusColor(hasPin()), v -> open(new SecurityPinFragment())), SecurityUi.matchWrap(this, 8));
        root.addView(SecurityUi.row(this, "◆", 0xFF25B96F, getString(R.string.data_protection_key), getString(R.string.security_data_key_summary), dataKeyStatus(), statusColor(hasDataKey()), v -> open(new SecurityBackupPasswordFragment())), SecurityUi.matchWrap(this, 8));
        root.addView(SecurityUi.row(this, "●", 0xFFB84DE5, getString(R.string.security_biometric_protection), getString(R.string.security_biometric_summary), biometricStatus(), statusColor(hasBiometric()), v -> open(new SecurityBiometricFragment())), SecurityUi.matchWrap(this, 8));
        root.addView(SecurityUi.row(this, "✂", 0xFFF59E0B, getString(R.string.security_lock_clear_title),
                getString(R.string.auto_lock_time) + " · " + getString(R.string.clipboard_auto_clear),
                lockClearStatus(), statusColor(isLockClearEnabled()), v -> open(new SecurityLockClearFragment())), SecurityUi.matchWrap(this, 8));
        root.addView(SecurityUi.row(this, "✓", 0xFF25B96F, getString(R.string.recovery_protection_title), getString(R.string.recovery_protection_summary), recoveryProtectionStatus(), statusColor(recoveryProtectionReady()), v -> open(new SecurityRecoveryFragment())), SecurityUi.matchWrap(this, 8));
        return scrollView;
    }

    private LinearLayout statusCard() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(SecurityUi.dp(this, 18), SecurityUi.dp(this, 18), SecurityUi.dp(this, 18), SecurityUi.dp(this, 16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF2563EB, 0xFF5B8DFF});
        bg.setCornerRadius(SecurityUi.dp(this, 18));
        card.setBackground(bg);
        card.setElevation(SecurityUi.dp(this, 2));
        card.setOnClickListener(v -> open(new SecurityStatusFragment()));

        TextView small = SecurityUi.text(this, getString(R.string.security_status_label), 13, R.color.surface_light, false);
        card.addView(small);
        TextView title = SecurityUi.text(this, getString(R.string.security_level_line, securityLevel()), 25, R.color.surface_light, true);
        card.addView(title, SecurityUi.matchWrap(this, 4));

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setGravity(Gravity.CENTER);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setPadding(0, SecurityUi.dp(this, 14), 0, 0);
        grid.addView(statusPill(getString(R.string.security_pill_vault), vaultStatus()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        grid.addView(statusPill(getString(R.string.security_pill_key), dataKeyStatus()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        grid.addView(statusPill("PIN", pinStatus()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        grid.addView(statusPill(getString(R.string.security_pill_database), databaseStatus()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        grid.addView(statusPill(getString(R.string.security_pill_backup), recoveryProtectionStatus()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(grid);
        return card;
    }

    private LinearLayout statusPill(String title, String value) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView dot = SecurityUi.text(this, "✓", 18, R.color.surface_light, true);
        dot.setGravity(Gravity.CENTER);
        box.addView(dot);
        TextView name = SecurityUi.text(this, title, 11, R.color.surface_light, true);
        name.setGravity(Gravity.CENTER);
        box.addView(name);
        TextView state = SecurityUi.text(this, value, 11, R.color.surface_light, false);
        state.setGravity(Gravity.CENTER);
        box.addView(state);
        return box;
    }

    private String securityLevel() {
        boolean basic = hasPin() && hasDataKey();
        boolean enhanced = basic && hasDatabaseProtection();
        boolean advanced = enhanced && hasBiometric() && hasWebDavConfig();
        if (advanced) return getString(R.string.security_level_advanced);
        if (enhanced) return getString(R.string.security_level_enhanced);
        if (basic) return getString(R.string.security_level_basic);
        return getString(R.string.security_level_incomplete);
    }

    private boolean hasVault() {
        return SecuritySettings.isVaultInitialized(requireContext());
    }

    private boolean hasDataKey() {
        return SecuritySettings.hasDataEncryptionKey(requireContext());
    }

    private boolean hasDatabaseProtection() {
        return DatabaseKeyManager.isDatabaseProtectionEnabled(requireContext());
    }

    private boolean hasPin() {
        return PinLockHelper.isConfigured(requireContext());
    }

    private boolean hasBiometric() {
        return BiometricUnlockHelper.isEnabled(requireContext());
    }

    private boolean hasWebDavConfig() {
        SharedPreferences prefs = requireContext().getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        String url = prefs.getString("url", "").trim();
        String user = prefs.getString("user", "").trim();
        return !url.isEmpty()
                && !user.isEmpty()
                && SecureSecretStore.hasSecret(requireContext(), SETTINGS_PREFS, "pass");
    }

    private String dataKeyStatus() {
        return getString(hasDataKey() ? R.string.status_set : R.string.status_not_set);
    }

    private String databaseStatus() {
        return getString(hasDatabaseProtection() ? R.string.status_enabled : R.string.status_not_enabled);
    }

    private String vaultStatus() {
        return getString(hasVault() ? R.string.status_created : R.string.status_not_created);
    }

    private String pinStatus() {
        return getString(hasPin() ? R.string.status_set : R.string.status_not_set);
    }

    private String biometricStatus() {
        return getString(hasBiometric() ? R.string.status_on : R.string.status_off);
    }

    private String lockClearStatus() {
        return getString(isLockClearEnabled()
                ? R.string.status_enabled : R.string.status_off);
    }

    private boolean isLockClearEnabled() {
        return SecuritySettings.autoLockMinutes(requireContext()) > 0
                || SecuritySettings.clipboardTimeoutSeconds(requireContext()) > 0;
    }

    private String webDavStatus() {
        return getString(hasWebDavConfig() ? R.string.status_connected : R.string.status_not_set);
    }

    private boolean hasSuccessfulRecoveryTest() {
        SharedPreferences prefs = SecuritySettings.prefs(requireContext());
        return prefs.getLong(KEY_LAST_VERIFY_TIME, 0) > 0 && prefs.getBoolean(KEY_LAST_VERIFY_OK, false);
    }

    private String recoveryStatus() {
        SharedPreferences prefs = SecuritySettings.prefs(requireContext());
        if (prefs.getLong(KEY_LAST_VERIFY_TIME, 0) <= 0) return getString(R.string.status_not_verified);
        return getString(prefs.getBoolean(KEY_LAST_VERIFY_OK, false) ? R.string.status_verify_success : R.string.status_verify_failed);
    }

    private String recoveryProtectionStatus() {
        SharedPreferences recovery = SecuritySettings.prefs(requireContext());
        SharedPreferences settings = requireContext().getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        long lastBackup = settings.getLong("last_sync", 0);
        long lastVerify = recovery.getLong(KEY_LAST_VERIFY_TIME, 0);
        boolean verifyOk = recovery.getBoolean(KEY_LAST_VERIFY_OK, false);
        if (!hasWebDavConfig() || !hasDataKey() || lastBackup <= 0) {
            return getString(R.string.recovery_protection_not_set);
        }
        if (lastVerify <= 0) return getString(R.string.recovery_protection_pending);
        if (!verifyOk || lastVerify < lastBackup) return getString(R.string.recovery_protection_update);
        if (System.currentTimeMillis() - lastBackup > 30L * 24L * 60L * 60L * 1000L) {
            return getString(R.string.recovery_protection_expired);
        }
        return getString(R.string.recovery_protection_ready);
    }

    private boolean recoveryProtectionReady() {
        return getString(R.string.recovery_protection_ready).equals(recoveryProtectionStatus());
    }

    private int statusColor(boolean ok) {
        return ok ? R.color.success : R.color.warning;
    }

    private void open(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}
