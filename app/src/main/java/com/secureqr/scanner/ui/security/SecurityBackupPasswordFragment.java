package com.secureqr.scanner.ui.security;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.security.SecurityAuditLog;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.DataProtectionKeyAttemptGuard;
import com.secureqr.scanner.security.SensitiveWindowGuard;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.PinLockHelper;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecurityBackupPasswordFragment extends Fragment {
    private static final String KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView keyValue;
    private android.widget.Button viewButton;
    private android.widget.Button copyButton;
    private boolean keyVisible;
    private boolean wasSecure;
    private Runnable hideRunnable;
    private ActivityResultLauncher<String[]> oldKeyImporter;
    private ActivityResultLauncher<String> newKeyDownloader;
    private EditText activeOldKeyInput;
    private EditText activeNewKeyInput;
    private CheckBox activeSavedCheck;
    private boolean newKeyPreserved;
    private String preservedNewKey = "";
    private int selectedKeyLength = 16;
    private TextView newKeyLengthValue;
    private Runnable oldKeyLockCountdown;

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        oldKeyImporter = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || activeOldKeyInput == null) return;
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException(getString(R.string.data_key_import_failed));
                String imported = parseDownloadedKey(new String(readAllBytes(input), StandardCharsets.UTF_8));
                if (imported.isEmpty()) throw new IllegalArgumentException(getString(R.string.data_key_import_invalid));
                activeOldKeyInput.setText(imported);
                activeOldKeyInput.setSelection(activeOldKeyInput.length());
                Toast.makeText(requireContext(), R.string.data_key_import_success, Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(requireContext(), error.getMessage() == null
                        ? getString(R.string.data_key_import_failed) : error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        newKeyDownloader = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
            if (uri == null || activeNewKeyInput == null) return;
            String key = normalizeKey(activeNewKeyInput.getText().toString());
            try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IllegalStateException(getString(R.string.vault_setup_download_failed, ""));
                output.write(getString(R.string.vault_setup_key_document, key).getBytes(StandardCharsets.UTF_8));
                markNewKeyPreserved(key);
                Toast.makeText(requireContext(), R.string.vault_setup_key_downloaded, Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(requireContext(), getString(R.string.vault_setup_download_failed,
                        error.getMessage() == null ? "" : error.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(R.string.data_protection_key));
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(SecurityUi.icon(this, "◆", 0xFF25B96F), centeredIconParams());

        LinearLayout card = SecurityUi.card(this);
        card.addView(SecurityUi.text(this, getString(R.string.data_protection_key), 18, R.color.text_main, true));
        card.addView(SecurityUi.text(this, getString(R.string.data_key_page_summary), 14, R.color.text_secondary, false), SecurityUi.matchWrap(this, 6));
        boolean configured = SecuritySettings.hasDataEncryptionKey(requireContext());
        card.addView(SecurityUi.text(this, getString(configured ? R.string.status_set_line : R.string.status_not_set_line), 14, configured ? R.color.success : R.color.warning, true), SecurityUi.matchWrap(this, 14));
        keyValue = SecurityUi.text(this, "********", 20, R.color.text_main, true);
        keyValue.setGravity(android.view.Gravity.CENTER);
        keyValue.setBackgroundResource(R.drawable.bg_edit_text);
        keyValue.setPadding(SecurityUi.dp(this, 12), SecurityUi.dp(this, 12), SecurityUi.dp(this, 12), SecurityUi.dp(this, 12));
        card.addView(keyValue, SecurityUi.matchWrap(this, 12));
        copyButton = SecurityUi.primaryButton(this, getString(R.string.data_key_copy));
        copyButton.setVisibility(View.GONE);
        copyButton.setOnClickListener(v -> copyVisibleKey());
        card.addView(copyButton, SecurityUi.matchWrap(this, 10));
        root.addView(card, SecurityUi.matchWrap(this, 24));

        LinearLayout usage = SecurityUi.card(this);
        usage.addView(SecurityUi.text(this, getString(R.string.data_key_usage_title), 15, R.color.text_main, true));
        usage.addView(SecurityUi.text(this, getString(R.string.data_key_usage_summary), 14, R.color.text_secondary, false), SecurityUi.matchWrap(this, 8));
        root.addView(usage, SecurityUi.matchWrap(this, 14));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        viewButton = SecurityUi.primaryButton(this, getString(R.string.data_key_view));
        viewButton.setOnClickListener(v -> {
            if (keyVisible) hideKey();
            else requireBiometricForView(getString(R.string.data_key_view_auth), this::showKey);
        });
        android.widget.Button modify = SecurityUi.primaryButton(this, getString(R.string.data_key_modify));
        modify.setOnClickListener(v -> requirePinOrBiometricForModify());
        actions.addView(viewButton, new LinearLayout.LayoutParams(0, SecurityUi.dp(this, 48), 1));
        LinearLayout.LayoutParams modifyParams = new LinearLayout.LayoutParams(0, SecurityUi.dp(this, 48), 1);
        modifyParams.setMarginStart(SecurityUi.dp(this, 12));
        actions.addView(modify, modifyParams);
        root.addView(actions, SecurityUi.matchWrap(this, 18));

        LinearLayout note = SecurityUi.card(this);
        note.addView(SecurityUi.text(this, getString(R.string.security_tip), 15, R.color.warning, true));
        note.addView(SecurityUi.text(this, getString(R.string.data_key_security_note), 14, R.color.text_secondary, false), SecurityUi.matchWrap(this, 8));
        root.addView(note, SecurityUi.matchWrap(this, 20));
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        wasSecure = SensitiveWindowGuard.enable(requireActivity());
    }

    @Override
    public void onPause() {
        hideKey();
        SensitiveWindowGuard.restore(requireActivity(), wasSecure);
        super.onPause();
    }

    private LinearLayout.LayoutParams centeredIconParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(SecurityUi.dp(this, 72), SecurityUi.dp(this, 72));
        params.topMargin = SecurityUi.dp(this, 34);
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private void requireBiometricForView(String action, Runnable onSuccess) {
        if (!BiometricUnlockHelper.isEnabled(requireContext())) {
            SecurityAuditLog.record(requireContext(), action + "：生物识别未开启", false);
            Toast.makeText(requireContext(), R.string.data_key_biometric_required, Toast.LENGTH_LONG).show();
            return;
        }
        BiometricUnlockHelper.promptStrict(requireActivity(), action, getString(R.string.biometric_verify_face_fingerprint), () -> {
            SecurityAuditLog.record(requireContext(), action, true);
            onSuccess.run();
        }, () -> {
            SecurityAuditLog.record(requireContext(), action, false);
            Toast.makeText(requireContext(), R.string.authentication_incomplete, Toast.LENGTH_SHORT).show();
        });
    }

    private void requirePinOrBiometricForModify() {
        if (BiometricUnlockHelper.isEnabled(requireContext())) {
            String authenticatedPin = SecuritySettings.getPinForKeyOperations(requireContext());
            if (authenticatedPin.isEmpty()) {
                showPinVerifyDialog();
                return;
            }
            BiometricUnlockHelper.promptStrict(requireActivity(), getString(R.string.data_protection_key_change_title), getString(R.string.biometric_verify_face_fingerprint), () -> {
                SecurityAuditLog.record(requireContext(), "修改数据保护密钥：生物识别验证", true);
                showModifyDialog(authenticatedPin);
            }, () -> {
                SecurityAuditLog.record(requireContext(), "修改数据保护密钥：生物识别验证", false);
                showPinVerifyDialog();
            });
            return;
        }
        showPinVerifyDialog();
    }

    private void showPinVerifyDialog() {
        EditText input = passwordEdit(getString(R.string.data_key_unlock_pin_input));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.data_key_verify_security_title)
                .setMessage(R.string.data_key_verify_security_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_continue, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!PinLockHelper.verifyPin(requireContext(), input.getText().toString().trim())) {
                SecurityAuditLog.record(requireContext(), "修改数据保护密钥：PIN 验证", false);
                input.setError(getString(R.string.pin_incorrect));
                return;
            }
            SecurityAuditLog.record(requireContext(), "修改数据保护密钥：PIN 验证", true);
            String authenticatedPin = input.getText().toString().trim();
            SecuritySettings.rememberPinForKeyOperations(requireContext(), authenticatedPin);
            dialog.dismiss();
            showModifyDialog(authenticatedPin);
        }));
        dialog.show();
    }

    private void showKey() {
        String value = SecuritySettings.getDataEncryptionKey(requireContext());
        if (value == null || value.isEmpty()) {
            Toast.makeText(requireContext(), R.string.data_key_not_set, Toast.LENGTH_SHORT).show();
            return;
        }
        keyValue.setText(value);
        keyVisible = true;
        if (copyButton != null) copyButton.setVisibility(View.VISIBLE);
        if (viewButton != null) viewButton.setText(R.string.data_key_hide);
        scheduleHide();
    }

    private void scheduleHide() {
        if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
        hideRunnable = this::hideKey;
        handler.postDelayed(hideRunnable, 30_000);
    }

    private void hideKey() {
        if (keyValue != null) keyValue.setText("********");
        keyVisible = false;
        if (copyButton != null) copyButton.setVisibility(View.GONE);
        if (viewButton != null) viewButton.setText(R.string.data_key_view);
        if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
        hideRunnable = null;
    }

    private void copyVisibleKey() {
        if (!keyVisible || keyValue == null) return;
        String value = keyValue.getText() == null ? "" : keyValue.getText().toString();
        if (value.isEmpty() || "********".equals(value)) return;
        SecureClipboard.copySensitive(requireContext(), getString(R.string.data_protection_key), value, 30_000L);
        Toast.makeText(requireContext(), R.string.data_key_copied_30s, Toast.LENGTH_LONG).show();
    }

    private void requirePinForView() {
        EditText input = passwordEdit(getString(R.string.data_key_unlock_pin_input));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.data_key_view_auth)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_continue, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!PinLockHelper.verifyPin(requireContext(), input.getText().toString().trim())) {
                input.setError(getString(R.string.pin_incorrect));
                return;
            }
            dialog.dismiss();
            showKey();
        }));
        dialog.show();
    }

    private void showModifyDialog(String authenticatedPin) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(SecurityUi.dp(this, 20), SecurityUi.dp(this, 8), SecurityUi.dp(this, 20), 0);
        EditText current = passwordEdit(getString(R.string.data_key_old_input));
        EditText password = passwordEdit(getString(R.string.vault_setup_input_password_short));
        activeOldKeyInput = current;
        activeNewKeyInput = password;
        newKeyPreserved = false;
        preservedNewKey = "";
        selectedKeyLength = 16;

        TextView hint = SecurityUi.text(this, getString(R.string.vault_setup_manual_auto_hint), 13, R.color.accent, false);
        TextView lockHint = SecurityUi.text(this, "", 13, R.color.warning, false);
        CheckBox savedCheck = new CheckBox(requireContext());
        activeSavedCheck = savedCheck;
        savedCheck.setEnabled(false);
        savedCheck.setText(R.string.vault_setup_key_not_preserved);
        savedCheck.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));

        LinearLayout oldRow = createFieldRow(current);
        Button importButton = SecurityUi.primaryButton(this, getString(R.string.data_key_import_downloaded));
        importButton.setOnClickListener(v -> oldKeyImporter.launch(new String[]{"text/plain", "text/*"}));

        content.addView(oldRow, marginTop(8));
        content.addView(lockHint, SecurityUi.matchWrap(this, 6));
        content.addView(importButton, marginTop(8));
        content.addView(hint, SecurityUi.matchWrap(this, 14));
        content.addView(createNewKeyLengthSelector(), SecurityUi.matchWrap(this, 8));
        content.addView(createNewKeyRow(password), marginTop(8));

        LinearLayout preserveActions = new LinearLayout(requireContext());
        preserveActions.setOrientation(LinearLayout.HORIZONTAL);
        Button download = SecurityUi.primaryButton(this, getString(R.string.vault_setup_download_key));
        download.setOnClickListener(v -> downloadNewKey());
        Button copy = SecurityUi.primaryButton(this, getString(R.string.vault_setup_copy_key));
        copy.setOnClickListener(v -> copyNewKey());
        preserveActions.addView(download, new LinearLayout.LayoutParams(0, SecurityUi.dp(this, 48), 1));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, SecurityUi.dp(this, 48), 1);
        copyParams.setMarginStart(SecurityUi.dp(this, 10));
        preserveActions.addView(copy, copyParams);
        content.addView(preserveActions, SecurityUi.matchWrap(this, 12));
        content.addView(savedCheck, SecurityUi.matchWrap(this, 8));

        password.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (normalizeKey(s.toString()).equals(preservedNewKey)) return;
                newKeyPreserved = false;
                savedCheck.setChecked(false);
                savedCheck.setText(R.string.vault_setup_key_not_preserved);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.data_protection_key_change_title)
                .setMessage(R.string.data_key_change_message)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            updateOldKeyLockCountdown(current, lockHint, save);
            save.setOnClickListener(v -> {
            if (DataProtectionKeyAttemptGuard.remainingLockMs(requireContext()) > 0L) {
                updateOldKeyLockCountdown(current, lockHint, save);
                return;
            }
            String old = normalizeKey(current.getText().toString());
            String next = normalizeKey(password.getText().toString());
            if (!PinLockHelper.verifyPin(requireContext(), authenticatedPin)) {
                Toast.makeText(requireContext(), R.string.authentication_incomplete, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
            }
            if (old.isEmpty()) {
                Toast.makeText(requireContext(), R.string.data_key_old_required, Toast.LENGTH_SHORT).show();
                SecurityAuditLog.record(requireContext(), "修改数据保护密钥保存", false);
                return;
            }
            if (!isValidDataProtectionKey(next)) {
                Toast.makeText(requireContext(), R.string.data_key_format_mismatch, Toast.LENGTH_SHORT).show();
                SecurityAuditLog.record(requireContext(), "修改数据保护密钥保存", false);
                return;
            }
            if (!newKeyPreserved || !next.equals(preservedNewKey)) {
                Toast.makeText(requireContext(), R.string.data_key_save_confirm_required, Toast.LENGTH_SHORT).show();
                return;
            }
            String actual = SecuritySettings.getDataEncryptionKey(requireContext());
            if (!old.equals(actual)) {
                int attemptsLeft = DataProtectionKeyAttemptGuard.recordFailure(requireContext());
                if (attemptsLeft > 0) {
                    current.setError(getString(R.string.data_key_old_incorrect_remaining, attemptsLeft));
                }
                updateOldKeyLockCountdown(current, lockHint, save);
                return;
            }
            DataProtectionKeyAttemptGuard.clearFailures(requireContext());
            boolean saved = SecuritySettings.changeDataEncryptionKey(requireContext(), old, next, authenticatedPin);
            SecurityAuditLog.record(requireContext(), "修改数据保护密钥保存", saved);
            Toast.makeText(requireContext(), saved ? R.string.data_key_changed : R.string.data_key_change_rolled_back, Toast.LENGTH_SHORT).show();
            if (saved) {
                hideKey();
                dialog.dismiss();
                SecurityChangeBackupPrompt.show(this, () -> { });
            }
            });
        });
        dialog.setOnDismissListener(ignored -> {
            if (oldKeyLockCountdown != null) handler.removeCallbacks(oldKeyLockCountdown);
            oldKeyLockCountdown = null;
            activeOldKeyInput = null;
            activeNewKeyInput = null;
            activeSavedCheck = null;
        });
        dialog.show();
    }

    private LinearLayout createFieldRow(EditText input) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_edit_text);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        row.addView(input, new LinearLayout.LayoutParams(0, SecurityUi.dp(this, 52), 1));
        ImageButton eye = iconButton(R.drawable.ic_visibility_off_24, R.string.show_password);
        eye.setOnClickListener(v -> toggleVisibility(input, eye));
        row.addView(eye);
        return row;
    }

    private LinearLayout createNewKeyRow(EditText input) {
        LinearLayout row = createFieldRow(input);
        ImageButton generate = iconButton(R.drawable.ic_refresh_24, R.string.action_generate);
        generate.setColorFilter(ContextCompat.getColor(requireContext(), R.color.accent));
        generate.setOnClickListener(v -> {
            String key = generateDataProtectionKey(selectedKeyLength);
            input.setText(key);
            input.setSelection(input.length());
            Toast.makeText(requireContext(), R.string.data_key_keep_safe_warning, Toast.LENGTH_LONG).show();
        });
        row.addView(generate, row.getChildCount() - 1);
        return row;
    }

    private LinearLayout createNewKeyLengthSelector() {
        LinearLayout selector = new LinearLayout(requireContext());
        selector.setOrientation(LinearLayout.VERTICAL);
        selector.setPadding(SecurityUi.dp(this, 14), SecurityUi.dp(this, 12), SecurityUi.dp(this, 14), SecurityUi.dp(this, 10));
        selector.setBackgroundResource(R.drawable.bg_vault_setup_input);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.addView(SecurityUi.text(this, getString(R.string.vault_setup_length_title), 14, R.color.text_main, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        newKeyLengthValue = SecurityUi.text(this, getString(R.string.vault_setup_length_current, selectedKeyLength), 14, R.color.primary_blue, true);
        newKeyLengthValue.setGravity(android.view.Gravity.END);
        header.addView(newKeyLengthValue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        selector.addView(header);

        SeekBar slider = new SeekBar(requireContext());
        slider.setMax(24);
        slider.setProgress(selectedKeyLength - 8);
        slider.setProgressDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_vault_key_length_track));
        slider.setThumb(ContextCompat.getDrawable(requireContext(), R.drawable.bg_vault_key_length_thumb));
        slider.setPadding(0, SecurityUi.dp(this, 3), 0, SecurityUi.dp(this, 3));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedKeyLength = 8 + progress;
                newKeyLengthValue.setText(getString(R.string.vault_setup_length_current, selectedKeyLength));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        selector.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SecurityUi.dp(this, 34)));

        LinearLayout limits = new LinearLayout(requireContext());
        limits.addView(SecurityUi.text(this, getString(R.string.vault_setup_length_min), 12, R.color.text_secondary, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView max = SecurityUi.text(this, getString(R.string.vault_setup_length_max), 12, R.color.text_secondary, false);
        max.setGravity(android.view.Gravity.END);
        limits.addView(max, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        selector.addView(limits);
        return selector;
    }

    private void updateOldKeyLockCountdown(EditText input, TextView hint, Button save) {
        long remaining = DataProtectionKeyAttemptGuard.remainingLockMs(requireContext());
        boolean locked = remaining > 0L;
        input.setEnabled(!locked);
        save.setEnabled(!locked);
        hint.setText(locked ? getString(R.string.data_key_old_locked_countdown,
                Math.max(1L, (remaining + 999L) / 1000L)) : "");
        if (oldKeyLockCountdown != null) handler.removeCallbacks(oldKeyLockCountdown);
        if (!locked) return;
        oldKeyLockCountdown = () -> updateOldKeyLockCountdown(input, hint, save);
        handler.postDelayed(oldKeyLockCountdown, 1000L);
    }

    private ImageButton iconButton(int icon, int description) {
        ImageButton button = new ImageButton(requireContext());
        button.setImageResource(icon);
        button.setContentDescription(getString(description));
        button.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        button.setPadding(SecurityUi.dp(this, 12), SecurityUi.dp(this, 12), SecurityUi.dp(this, 12), SecurityUi.dp(this, 12));
        button.setLayoutParams(new LinearLayout.LayoutParams(SecurityUi.dp(this, 48), SecurityUi.dp(this, 48)));
        return button;
    }

    private void toggleVisibility(EditText input, ImageButton button) {
        boolean hidden = input.getTransformationMethod() instanceof PasswordTransformationMethod;
        input.setTransformationMethod(hidden ? null : PasswordTransformationMethod.getInstance());
        button.setImageResource(hidden ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24);
        button.setContentDescription(getString(hidden ? R.string.hide_password : R.string.show_password));
        input.setSelection(input.length());
    }

    private void downloadNewKey() {
        String key = validActiveNewKey();
        if (key != null) newKeyDownloader.launch("KeyScan_Data_Protection_Key.txt");
    }

    private void copyNewKey() {
        String key = validActiveNewKey();
        if (key == null) return;
        SecureClipboard.copySensitive(requireContext(), "KeyScan", key, 30_000L);
        markNewKeyPreserved(key);
        Toast.makeText(requireContext(), R.string.vault_setup_copy_warning, Toast.LENGTH_LONG).show();
    }

    private String validActiveNewKey() {
        String key = normalizeKey(activeNewKeyInput == null ? "" : activeNewKeyInput.getText().toString());
        if (!isValidDataProtectionKey(key)) {
            if (activeNewKeyInput != null) activeNewKeyInput.setError(getString(R.string.vault_setup_key_generate_first));
            return null;
        }
        return key;
    }

    private void markNewKeyPreserved(String key) {
        newKeyPreserved = true;
        preservedNewKey = key;
        if (activeSavedCheck != null) {
            activeSavedCheck.setChecked(true);
            activeSavedCheck.setText(R.string.vault_setup_key_preserved);
        }
    }

    private LinearLayout.LayoutParams marginTop(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SecurityUi.dp(this, 52));
        params.topMargin = SecurityUi.dp(this, top);
        return params;
    }

    private String generateDataProtectionKey(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(KEY_ALPHABET.charAt(random.nextInt(KEY_ALPHABET.length())));
        }
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < builder.length(); i++) {
            if (i > 0 && i % 4 == 0) formatted.append('-');
            formatted.append(builder.charAt(i));
        }
        return formatted.toString();
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.US);
    }

    private boolean isValidDataProtectionKey(String key) {
        String compact = key.replace("-", "");
        return key.matches("[A-Z0-9-]+")
                && compact.matches("[A-Z0-9]{8,32}");
    }

    private String parseDownloadedKey(String document) {
        if (document == null) return "";
        Matcher matcher = Pattern.compile("(?m)^\\s*([A-Za-z0-9-]{8,39})\\s*$").matcher(document);
        while (matcher.find()) {
            String candidate = normalizeKey(matcher.group(1));
            if (isValidDataProtectionKey(candidate)) return candidate;
        }
        return "";
    }

    private byte[] readAllBytes(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private EditText passwordEdit(String hint) {
        EditText edit = new EditText(requireContext());
        edit.setHint(hint);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setTransformationMethod(PasswordTransformationMethod.getInstance());
        edit.setSingleLine(true);
        edit.setBackgroundResource(R.drawable.bg_edit_text);
        edit.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        edit.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        edit.setPadding(SecurityUi.dp(this, 12), 0, SecurityUi.dp(this, 12), 0);
        return edit;
    }
}
