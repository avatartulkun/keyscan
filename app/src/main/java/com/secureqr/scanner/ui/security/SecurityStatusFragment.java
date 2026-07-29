package com.secureqr.scanner.ui.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.SecureSecretStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SecurityStatusFragment extends Fragment {
    private static final String SETTINGS_PREFS = "secureqr_settings";
    private static final String KEY_LAST_VERIFY_TIME = "recovery_last_verify_time";
    private static final String KEY_LAST_VERIFY_OK = "recovery_last_verify_ok";

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(R.string.security_status_title));
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = SecurityUi.card(this);
        card.addView(statusRow(getString(R.string.security_level_title), securityLevel(), securityLevelState(), basicProtection()));
        card.addView(statusRow(getString(R.string.security_level_basic), getString(R.string.security_basic_subtitle), protectionState(basicProtection()), basicProtection()), SecurityUi.matchWrap(this, 12));
        card.addView(statusRow(getString(R.string.security_level_enhanced), getString(R.string.security_enhanced_subtitle), protectionState(enhancedProtection()), enhancedProtection()), SecurityUi.matchWrap(this, 12));
        card.addView(statusRow(getString(R.string.security_level_advanced), getString(R.string.security_advanced_subtitle), protectionState(advancedProtection()), advancedProtection()), SecurityUi.matchWrap(this, 12));
        boolean vault = SecuritySettings.isVaultInitialized(requireContext());
        card.addView(statusRow(getString(R.string.security_vault_title), getString(vault ? R.string.security_vault_initialized : R.string.security_vault_needs_creation), getString(vault ? R.string.status_created_ok : R.string.status_not_created), vault), SecurityUi.matchWrap(this, 12));
        boolean dataKey = SecuritySettings.hasDataEncryptionKey(requireContext());
        card.addView(statusRow(getString(R.string.data_protection_key), getString(dataKey ? R.string.security_data_key_active : R.string.status_not_set_yet), getString(dataKey ? R.string.status_normal : R.string.status_needs_setup), dataKey), SecurityUi.matchWrap(this, 12));
        boolean database = DatabaseKeyManager.isDatabaseProtectionEnabled(requireContext());
        card.addView(statusRow(getString(R.string.security_database_protection), getString(database ? R.string.security_database_dynamic : R.string.security_database_enable_later), getString(database ? R.string.status_enabled_ok : R.string.status_not_enabled), database), SecurityUi.matchWrap(this, 12));
        card.addView(statusRow(getString(R.string.security_attachment_encryption), getString(R.string.security_attachment_encryption_summary), getString(R.string.status_normal), true), SecurityUi.matchWrap(this, 12));
        boolean autoLock = SecuritySettings.autoLockMinutes(requireContext()) != 0;
        card.addView(statusRow(getString(R.string.security_auto_lock), autoLockText(), getString(autoLock ? R.string.status_normal : R.string.status_off), autoLock), SecurityUi.matchWrap(this, 12));
        boolean webDav = hasWebDavConfig();
        card.addView(statusRow(getString(R.string.security_webdav_backup), webDavText(), getString(webDav ? R.string.status_connected_ok : R.string.status_not_set), webDav), SecurityUi.matchWrap(this, 12));
        boolean verified = isRecoveryVerified();
        card.addView(statusRow(getString(R.string.security_backup_verification), recoveryVerifyText(), recoveryVerifyState(), verified), SecurityUi.matchWrap(this, 12));
        root.addView(card, SecurityUi.matchWrap(this, 18));

        LinearLayout check = SecurityUi.card(this);
        check.addView(SecurityUi.text(this, getString(R.string.security_recent_check), 15, R.color.text_main, true));
        check.addView(SecurityUi.text(this, dateText(SecuritySettings.lastSecurityCheck(requireContext())), 15, R.color.info, true), SecurityUi.matchWrap(this, 8));
        check.addView(SecurityUi.text(this, getString(R.string.security_status_source_note), 13, R.color.text_secondary, false), SecurityUi.matchWrap(this, 6));
        root.addView(check, SecurityUi.matchWrap(this, 18));
        return scrollView;
    }

    private LinearLayout statusRow(String title, String subtitle, String state, boolean healthy) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(SecurityUi.text(this, title, 16, R.color.text_main, true));
        texts.addView(SecurityUi.text(this, subtitle, 12, R.color.text_secondary, false), SecurityUi.matchWrap(this, 2));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int color = healthy ? R.color.success : R.color.warning;
        android.widget.TextView status = SecurityUi.text(this, state, 14, color, true);
        status.setGravity(android.view.Gravity.END);
        row.addView(status, new LinearLayout.LayoutParams(SecurityUi.dp(this, 82), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private String autoLockText() {
        int minutes = SecuritySettings.autoLockMinutes(requireContext());
        return minutes == 0 ? getString(R.string.security_auto_lock_disabled) : getString(R.string.security_auto_lock_minutes, minutes);
    }

    private boolean hasWebDavConfig() {
        SharedPreferences prefs = requireContext().getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        String url = prefs.getString("url", "").trim();
        String user = prefs.getString("user", "").trim();
        return !url.isEmpty()
                && !user.isEmpty()
                && SecureSecretStore.hasSecret(requireContext(), SETTINGS_PREFS, "pass");
    }

    private boolean basicProtection() {
        return com.secureqr.scanner.utils.PinLockHelper.isConfigured(requireContext())
                && SecuritySettings.hasDataEncryptionKey(requireContext());
    }

    private boolean enhancedProtection() {
        return basicProtection() && DatabaseKeyManager.isDatabaseProtectionEnabled(requireContext());
    }

    private boolean advancedProtection() {
        return enhancedProtection()
                && com.secureqr.scanner.utils.BiometricUnlockHelper.isEnabled(requireContext())
                && hasWebDavConfig();
    }

    private String securityLevel() {
        if (advancedProtection()) return getString(R.string.security_level_advanced);
        if (enhancedProtection()) return getString(R.string.security_level_enhanced);
        if (basicProtection()) return getString(R.string.security_level_basic);
        return getString(R.string.security_level_incomplete);
    }

    private String securityLevelState() {
        return protectionState(basicProtection());
    }

    private String protectionState(boolean enabled) { return getString(enabled ? R.string.status_normal : R.string.security_level_incomplete); }

    private String webDavText() {
        if (!hasWebDavConfig()) return getString(R.string.security_webdav_not_configured);
        return getString(SecuritySettings.hasDataEncryptionKey(requireContext()) ? R.string.security_webdav_key_protected : R.string.security_webdav_key_needed);
    }

    private String recoveryVerifyText() {
        long time = SecuritySettings.prefs(requireContext()).getLong(KEY_LAST_VERIFY_TIME, 0);
        if (time <= 0) return getString(R.string.security_backup_not_verified);
        return dateText(time);
    }

    private String recoveryVerifyState() {
        long time = SecuritySettings.prefs(requireContext()).getLong(KEY_LAST_VERIFY_TIME, 0);
        if (time <= 0) return getString(R.string.status_not_verified);
        return getString(isRecoveryVerified() ? R.string.status_normal : R.string.status_failed);
    }

    private boolean isRecoveryVerified() { return SecuritySettings.prefs(requireContext()).getLong(KEY_LAST_VERIFY_TIME, 0) > 0 && SecuritySettings.prefs(requireContext()).getBoolean(KEY_LAST_VERIFY_OK, false); }

    private String dateText(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
