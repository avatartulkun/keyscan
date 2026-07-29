package com.secureqr.scanner.ui.security;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.SecureSecretStore;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.OperationModeManager;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.ui.settings.SettingsFragment;
import com.secureqr.scanner.utils.WebDavAutoSyncManager;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.backup.webdav.model.WebDavBackupPreview;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecurityRecoveryFragment extends Fragment {
    private static final String SETTINGS_PREFS = "secureqr_settings";
    private static final String KEY_LAST_VERIFY_TIME = "recovery_last_verify_time";
    private static final String KEY_LAST_VERIFY_OK = "recovery_last_verify_ok";
    private static final String KEY_LAST_VERIFY_MESSAGE = "recovery_last_verify_message";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public android.view.View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(R.string.recovery_page_title));
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout status = SecurityUi.card(this);
        status.addView(SecurityUi.text(this, getString(R.string.recovery_protection_title), 18, R.color.text_main, true));
        status.addView(SecurityUi.text(this, getString(R.string.recovery_protection_explanation), 13, R.color.text_secondary, false), SecurityUi.matchWrap(this, 6));
        status.addView(SecurityUi.text(this, "WebDAV：" + webDavStatus(), 14, statusColor(hasWebDavConfig()), true), SecurityUi.matchWrap(this, 8));
        status.addView(SecurityUi.text(this, getString(R.string.recovery_backup_status_line, backupStatus()), 14, statusColor(hasWebDavConfig() && hasDataProtectionKey()), true), SecurityUi.matchWrap(this, 6));
        status.addView(SecurityUi.text(this, getString(R.string.recovery_latest_backup_line, latestBackupText()), 14, R.color.text_secondary, false), SecurityUi.matchWrap(this, 6));
        status.addView(SecurityUi.text(this, getString(R.string.recovery_validation_line, verifyStatusText()), 14, verifyStatusColor(), true), SecurityUi.matchWrap(this, 6));
        root.addView(status, SecurityUi.matchWrap(this, 18));

        LinearLayout info = SecurityUi.card(this);
        info.addView(SecurityUi.text(this, getString(R.string.recovery_instructions_title), 16, R.color.text_main, true));
        info.addView(SecurityUi.text(this, getString(R.string.recovery_instructions), 14, R.color.text_secondary, false), SecurityUi.matchWrap(this, 8));
        root.addView(info, SecurityUi.matchWrap(this, 14));

        android.widget.Button backupNow = SecurityUi.primaryButton(this, getString(R.string.backup_now));
        backupNow.setOnClickListener(v -> backupNow());
        root.addView(backupNow, SecurityUi.matchWrap(this, 18));

        android.widget.Button verify = SecurityUi.primaryButton(this, getString(R.string.recovery_verify_backup));
        verify.setOnClickListener(v -> authenticateThen(this::verifyBackup));
        root.addView(verify, SecurityUi.matchWrap(this, 10));

        android.widget.Button restore = SecurityUi.primaryButton(this, getString(R.string.recovery_restore_data));
        restore.setOnClickListener(v -> authenticateThen(this::previewRestore));
        root.addView(restore, SecurityUi.matchWrap(this, 10));

        android.widget.Button settings = SecurityUi.primaryButton(this, getString(R.string.recovery_open_webdav));
        settings.setOnClickListener(v -> open(new SettingsFragment()));
        root.addView(settings, SecurityUi.matchWrap(this, 10));
        return scrollView;
    }

    @Override
    public void onDestroyView() {
        executor.shutdownNow();
        super.onDestroyView();
    }

    private void backupNow() {
        if (!hasWebDavConfig()) {
            Toast.makeText(requireContext(), R.string.recovery_webdav_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasDataProtectionKey()) {
            Toast.makeText(requireContext(), R.string.recovery_data_key_required, Toast.LENGTH_SHORT).show();
            return;
        }
        VaultAccessManager.requireUnlocked(requireActivity(), getString(R.string.backup_unlock_prompt), () -> {
            ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.backup_now), getString(R.string.recovery_backup_progress), true, false);
            WebDavAutoSyncManager.requestBackupNow(requireContext(), (successCount, targetCount, error) -> FragmentUi.run(this, () -> {
                progress.dismiss();
                if (error == null) {
                    Toast.makeText(requireContext(), R.string.recovery_backup_success, Toast.LENGTH_SHORT).show();
                    refresh();
                } else {
                    showMessage(getString(R.string.recovery_backup_failed), error);
                }
            }));
        });
    }

    private void showKeyDialog(String title, String message, KeyAction action) {
        if (!hasWebDavConfig()) {
            Toast.makeText(requireContext(), R.string.recovery_webdav_required, Toast.LENGTH_SHORT).show();
            return;
        }
        EditText input = passwordEdit(getString(R.string.recovery_data_key_input));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String key = input.getText().toString();
            if (key.isEmpty()) {
                input.setError(getString(R.string.data_protection_key_required_short));
                return;
            }
            dialog.dismiss();
            action.run(key);
        }));
        dialog.show();
    }

    private void authenticateThen(KeyAction action) {
        if (!hasWebDavConfig()) {
            Toast.makeText(requireContext(), R.string.recovery_webdav_required, Toast.LENGTH_SHORT).show();
            return;
        }
        SensitiveActionGuard.requireAuthentication(requireActivity(), getString(R.string.recovery_auth_prompt), () -> {
            String currentDataKey = SecuritySettings.getDataEncryptionKey(requireContext());
            if (currentDataKey == null || currentDataKey.isEmpty()) {
                Toast.makeText(requireContext(), R.string.recovery_data_key_required, Toast.LENGTH_SHORT).show();
                return;
            }
            action.run(currentDataKey);
        });
    }

    private void verifyBackup(String dataProtectionKey) {
        ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.recovery_verify_backup), getString(R.string.recovery_verify_progress), true, false);
        WebDavAutoSyncManager.previewLatestBackup(requireContext(), dataProtectionKey, (preview, error) -> FragmentUi.run(this, () -> {
            progress.dismiss();
            boolean ok = error == null && preview != null;
            saveVerifyResult(ok, ok ? getString(R.string.recovery_verify_usable) : error);
            showMessage(getString(ok ? R.string.recovery_verify_success_title : R.string.recovery_verify_failed_title), ok ? getString(R.string.recovery_verify_usable) + "\n\n" + previewText(preview) : error);
            refresh();
        }));
    }

    private void previewRestore(String dataProtectionKey) {
        ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.recovery_read_backup), getString(R.string.recovery_read_progress), true, false);
        WebDavAutoSyncManager.previewLatestBackup(requireContext(), dataProtectionKey, (preview, error) -> FragmentUi.run(this, () -> {
            progress.dismiss();
            if (error != null || preview == null) {
                showMessage(getString(R.string.recovery_unable), error == null ? getString(R.string.recovery_backup_abnormal) : error);
                return;
            }
            checkExistingDataThenConfirm(preview, dataProtectionKey);
        }));
    }

    private void checkExistingDataThenConfirm(WebDavBackupPreview preview, String dataProtectionKey) {
        ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.recovery_check_device), getString(R.string.recovery_check_device_progress), true, false);
        executor.execute(() -> {
            ExistingData existing = existingData();
            FragmentUi.run(this, () -> {
                progress.dismiss();
                if (existing.hasAny()) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.recovery_existing_data_title)
                            .setMessage(getString(R.string.recovery_existing_data_message, existing.passwords, existing.otpTokens, existing.vaultItems))
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.action_continue, (dialog, which) -> showFinalRestoreConfirm(preview, dataProtectionKey))
                            .show();
                    return;
                }
                showFinalRestoreConfirm(preview, dataProtectionKey);
            });
        });
    }

    private ExistingData existingData() {
        try {
            if (!VaultAccessManager.canAccessSensitiveData(requireContext())) {
                return new ExistingData(0, 0, 0);
            }
            AppDatabase database = AppDatabase.getInstance(requireContext().getApplicationContext());
            return new ExistingData(
                    database.passwordEntryDao().getAllNow().size(),
                    database.otpTokenDao().getAllNow().size(),
                    database.vaultItemDao().getAllNow().size()
            );
        } catch (RuntimeException e) {
            return new ExistingData(0, 0, 0);
        }
    }

    private void showFinalRestoreConfirm(WebDavBackupPreview preview, String dataProtectionKey) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.recovery_confirm_title)
                .setMessage(getString(R.string.recovery_confirm_message, previewText(preview)))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.recovery_action, (dialog, which) -> restoreBackup(dataProtectionKey))
                .show();
    }

    private void restoreBackup(String dataProtectionKey) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, () -> restoreBackup(dataProtectionKey));
            return;
        }
        ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.recovery_restore_data), getString(R.string.recovery_progress), true, false);
        WebDavAutoSyncManager.restoreLatestBackup(requireContext(), dataProtectionKey, (preview, error) -> FragmentUi.run(this, () -> {
            progress.dismiss();
            if (error == null) {
                saveVerifyResult(true, getString(R.string.recovery_completed_reopen));
                showMessage(getString(R.string.recovery_success_title), getString(R.string.recovery_completed_reopen) + "\n\n" + previewText(preview));
                refresh();
            } else {
                saveVerifyResult(false, error);
                showMessage(getString(R.string.recovery_failed_title), error);
            }
        }));
    }

    private String previewText(WebDavBackupPreview preview) {
        return getString(R.string.recovery_preview, preview.passwords, preview.otpTokens, preview.vaultItems,
                preview.vaultAttachments, preview.records, preview.passwordGroups);
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

    private void saveVerifyResult(boolean success, String message) {
        SecuritySettings.prefs(requireContext()).edit()
                .putBoolean(KEY_LAST_VERIFY_OK, success)
                .putString(KEY_LAST_VERIFY_MESSAGE, message == null ? "" : message)
                .putLong(KEY_LAST_VERIFY_TIME, System.currentTimeMillis())
                .putLong(SecuritySettings.KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
    }

    private boolean hasWebDavConfig() {
        SharedPreferences prefs = requireContext().getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        return !prefs.getString("url", "").trim().isEmpty()
                && !prefs.getString("user", "").trim().isEmpty()
                && SecureSecretStore.hasSecret(requireContext(), SETTINGS_PREFS, "pass");
    }

    private boolean hasDataProtectionKey() {
        return SecuritySettings.hasDataEncryptionKey(requireContext());
    }

    private String webDavStatus() {
        return getString(hasWebDavConfig() ? R.string.status_connected : R.string.status_not_set);
    }

    private String backupStatus() {
        return getString(hasWebDavConfig() && hasDataProtectionKey() ? R.string.status_configured : R.string.status_not_configured);
    }

    private String latestBackupText() {
        long time = requireContext().getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getLong("last_sync", 0);
        if (time <= 0) return getString(R.string.status_no_record);
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(time));
    }

    private String verifyStatusText() {
        SharedPreferences prefs = SecuritySettings.prefs(requireContext());
        long time = prefs.getLong(KEY_LAST_VERIFY_TIME, 0);
        if (time <= 0) return getString(R.string.status_not_verified);
        String suffix = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(time));
        return getString(prefs.getBoolean(KEY_LAST_VERIFY_OK, false) ? R.string.recovery_verify_status_success : R.string.recovery_verify_status_failed, suffix);
    }

    private int verifyStatusColor() {
        SharedPreferences prefs = SecuritySettings.prefs(requireContext());
        if (prefs.getLong(KEY_LAST_VERIFY_TIME, 0) <= 0) return R.color.warning;
        return prefs.getBoolean(KEY_LAST_VERIFY_OK, false) ? R.color.success : R.color.warning;
    }

    private int statusColor(boolean ok) {
        return ok ? R.color.success : R.color.warning;
    }

    private EditText passwordEdit(String hint) {
        EditText edit = new EditText(requireContext());
        edit.setHint(hint);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setSingleLine(true);
        edit.setBackgroundResource(R.drawable.bg_edit_text);
        edit.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        edit.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        edit.setPadding(SecurityUi.dp(this, 12), 0, SecurityUi.dp(this, 12), 0);
        return edit;
    }

    private void refresh() {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SecurityRecoveryFragment())
                .commitAllowingStateLoss();
    }

    private void open(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private interface KeyAction {
        void run(String dataProtectionKey);
    }

    private static final class ExistingData {
        final int passwords;
        final int otpTokens;
        final int vaultItems;

        ExistingData(int passwords, int otpTokens, int vaultItems) {
            this.passwords = passwords;
            this.otpTokens = otpTokens;
            this.vaultItems = vaultItems;
        }

        boolean hasAny() {
            return passwords > 0 || otpTokens > 0 || vaultItems > 0;
        }
    }
}
