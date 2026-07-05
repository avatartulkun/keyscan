package com.secureqr.scanner.ui.settings;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.utils.CryptoHelper;
import com.secureqr.scanner.utils.PinLockHelper;
import com.secureqr.scanner.utils.WebDAVClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {
    private static final String PREFS = "secureqr_settings";
    private static final String LATEST_BACKUP = "/secure_backup.dat";
    private static final String LEGACY_BACKUP_PASSWORD = "DefaultKey2024";
    private static final String KEY_BACKUP_PASSWORD = "webdav_backup_password";
    private static final String KEY_BACKUP_INDEPENDENT = "webdav_backup_password_independent";
    private static final String KEY_RECOVERY_KEY = "webdav_recovery_key";
    private static final String KEY_HISTORY_MAIN_URL = "webdav_history_main_url";
    private static final String KEY_HISTORY_MAIN_USER = "webdav_history_main_user";
    private static final String KEY_HISTORY_BACKUP_URL = "webdav_history_backup_url";
    private static final String KEY_HISTORY_BACKUP_USER = "webdav_history_backup_user";
    private static final String KEY_MAIN_CARD_EXPANDED = "webdav_main_card_expanded";
    private static final String KEY_BACKUP_CARD_EXPANDED = "webdav_backup_card_expanded";
    private static final long AUTO_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    private EditText webdavUrl;
    private EditText webdavUser;
    private EditText webdavPass;
    private EditText backupEncryptionPassword;
    private EditText backupWebdavUrl;
    private EditText backupWebdavUser;
    private EditText backupWebdavPass;
    private CheckBox autoSync;
    private CheckBox independentBackupPassword;
    private TextView independentBackupWarning;
    private Button convertLegacyButton;
    private boolean bindingBackupPassword;
    private Spinner algorithmSpinner;
    private Spinner mainSyncContent;
    private Spinner backupSyncContent;
    private TextView lastSync;
    private TextView backupHistory;
    private LinearLayout cloudBackups;
    private LinearLayout mainWebdavCard;
    private LinearLayout backupWebdavCard;
    private TextView mainWebdavArrow;
    private TextView backupWebdavArrow;
    private RecordRepository repository;
    private PasswordRepository passwordRepository;
    private OtpRepository otpRepository;
    private ExecutorService executor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        executor = Executors.newSingleThreadExecutor();
        repository = RecordRepository.getInstance(requireContext());
        passwordRepository = PasswordRepository.getInstance(requireContext());
        otpRepository = OtpRepository.getInstance(requireContext());
        webdavUrl = view.findViewById(R.id.et_webdav_url);
        webdavUser = view.findViewById(R.id.et_webdav_user);
        webdavPass = view.findViewById(R.id.et_webdav_pass);
        backupEncryptionPassword = view.findViewById(R.id.et_backup_encryption_password);
        backupWebdavUrl = view.findViewById(R.id.et_webdav_backup_url);
        backupWebdavUser = view.findViewById(R.id.et_webdav_backup_user);
        backupWebdavPass = view.findViewById(R.id.et_webdav_backup_pass);
        autoSync = view.findViewById(R.id.cb_auto_sync);
        independentBackupPassword = view.findViewById(R.id.cb_independent_backup_password);
        independentBackupWarning = view.findViewById(R.id.tv_independent_backup_warning);
        convertLegacyButton = view.findViewById(R.id.btn_convert_main_legacy_backup);
        algorithmSpinner = view.findViewById(R.id.sp_webdav_algorithm);
        mainSyncContent = view.findViewById(R.id.sp_main_sync_content);
        backupSyncContent = view.findViewById(R.id.sp_backup_sync_content);
        lastSync = view.findViewById(R.id.tv_last_sync);
        backupHistory = view.findViewById(R.id.tv_backup_history);
        cloudBackups = view.findViewById(R.id.ll_cloud_backups);
        mainWebdavCard = view.findViewById(R.id.card_main_webdav);
        backupWebdavCard = view.findViewById(R.id.card_backup_webdav);
        mainWebdavArrow = view.findViewById(R.id.tv_main_webdav_arrow);
        backupWebdavArrow = view.findViewById(R.id.tv_backup_webdav_arrow);
        setupPasswordVisibilityToggle(view.findViewById(R.id.btn_toggle_webdav_pass), webdavPass);
        setupPasswordVisibilityToggle(view.findViewById(R.id.btn_toggle_backup_encryption_password), backupEncryptionPassword);
        setupPasswordVisibilityToggle(view.findViewById(R.id.btn_toggle_backup_webdav_pass), backupWebdavPass);
        setupHistoryDropdown(webdavUrl, KEY_HISTORY_MAIN_URL);
        setupHistoryDropdown(webdavUser, KEY_HISTORY_MAIN_USER);
        setupHistoryDropdown(backupWebdavUrl, KEY_HISTORY_BACKUP_URL);
        setupHistoryDropdown(backupWebdavUser, KEY_HISTORY_BACKUP_USER);

        algorithmSpinner.setAdapter(new ThemedSpinnerAdapter(requireContext(), java.util.Arrays.asList(CryptoHelper.supportedAlgorithms())));
        List<String> contentModes = syncContentLabels();
        mainSyncContent.setAdapter(new ThemedSpinnerAdapter(requireContext(), contentModes));
        backupSyncContent.setAdapter(new ThemedSpinnerAdapter(requireContext(), contentModes));

        loadPrefs();
        setupBackupPasswordBinding();
        autoSync.setOnCheckedChangeListener((buttonView, isChecked) -> savePrefs());
        view.findViewById(R.id.btn_save_webdav_settings).setOnClickListener(v -> saveWebDavSettingsWithRecoveryKey());
        convertLegacyButton.setOnClickListener(v -> convertLegacyBackup(mainTarget()));
        view.findViewById(R.id.btn_test_main_webdav).setOnClickListener(v -> testTarget(mainTarget()));
        view.findViewById(R.id.btn_test_backup_webdav).setOnClickListener(v -> testTarget(backupTarget()));
        view.findViewById(R.id.btn_sync_main_webdav).setOnClickListener(v -> syncTargets(java.util.Collections.singletonList(mainTarget()), false));
        view.findViewById(R.id.btn_sync_backup_webdav).setOnClickListener(v -> syncTargets(java.util.Collections.singletonList(backupTarget()), false));
        view.findViewById(R.id.btn_restore_main_webdav).setOnClickListener(v -> restoreTarget(mainTarget()));
        view.findViewById(R.id.btn_restore_backup_webdav).setOnClickListener(v -> restoreTarget(backupTarget()));
        view.findViewById(R.id.btn_sync).setOnClickListener(v -> showBackupTargetChooser());
        view.findViewById(R.id.btn_choose_backup_target).setOnClickListener(v -> showBackupTargetChooser());
        view.findViewById(R.id.btn_backup_history).setOnClickListener(v -> loadBackupHistory(true));
        setupCollapsibleCard(view.findViewById(R.id.row_main_webdav_title), mainWebdavCard, mainWebdavArrow, KEY_MAIN_CARD_EXPANDED, true);
        setupCollapsibleCard(view.findViewById(R.id.row_backup_webdav_title), backupWebdavCard, backupWebdavArrow, KEY_BACKUP_CARD_EXPANDED, false);

        maybeAutoSync();
        checkLegacyBackupAvailability();
        maybeLoadCloudBackups();
    }

    private void loadPrefs() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        webdavUrl.setText(prefs.getString("url", ""));
        webdavUser.setText(prefs.getString("user", ""));
        webdavPass.setText(prefs.getString("pass", ""));
        independentBackupPassword.setChecked(prefs.getBoolean(KEY_BACKUP_INDEPENDENT, false));
        backupEncryptionPassword.setText(prefs.getString(KEY_BACKUP_PASSWORD, webdavPass.getText().toString()));
        independentBackupWarning.setVisibility(independentBackupPassword.isChecked() ? View.VISIBLE : View.GONE);
        backupWebdavUrl.setText(prefs.getString("backup_url", ""));
        backupWebdavUser.setText(prefs.getString("backup_user", ""));
        backupWebdavPass.setText(prefs.getString("backup_pass", ""));
        autoSync.setChecked(prefs.getBoolean("auto_sync", false));
        setSyncContentSelection(mainSyncContent, prefs.getString("main_sync_content", "all"));
        setSyncContentSelection(backupSyncContent, prefs.getString("backup_sync_content", "all"));
        String algorithm = prefs.getString("webdav_algorithm", "AES-GCM");
        setSpinnerSelection(algorithmSpinner, algorithm);
        updateLastSyncText(prefs.getLong("last_sync", 0));
    }

    private void setupPasswordVisibilityToggle(Button toggleButton, EditText passwordInput) {
        if (toggleButton == null || passwordInput == null) return;
        passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        toggleButton.setText(R.string.webdav_visibility_show);
        toggleButton.setContentDescription(getString(R.string.webdav_visibility_show_desc));
        toggleButton.setOnClickListener(v -> {
            boolean isHidden = passwordInput.getTransformationMethod() instanceof PasswordTransformationMethod;
            passwordInput.setTransformationMethod(isHidden
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            toggleButton.setText(isHidden ? R.string.webdav_visibility_hide : R.string.webdav_visibility_show);
            toggleButton.setContentDescription(isHidden ? getString(R.string.webdav_visibility_hide_desc) : getString(R.string.webdav_visibility_show_desc));
            passwordInput.setSelection(passwordInput.getText().length());
        });
    }

    private void savePrefs() {
        saveInputHistory(KEY_HISTORY_MAIN_URL, webdavUrl.getText().toString().trim());
        saveInputHistory(KEY_HISTORY_MAIN_USER, webdavUser.getText().toString().trim());
        saveInputHistory(KEY_HISTORY_BACKUP_URL, backupWebdavUrl.getText().toString().trim());
        saveInputHistory(KEY_HISTORY_BACKUP_USER, backupWebdavUser.getText().toString().trim());
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("url", webdavUrl.getText().toString().trim())
                .putString("user", webdavUser.getText().toString().trim())
                .putString("pass", webdavPass.getText().toString())
                .putString(KEY_BACKUP_PASSWORD, backupEncryptionPassword.getText().toString())
                .putBoolean(KEY_BACKUP_INDEPENDENT, independentBackupPassword.isChecked())
                .putString("backup_url", backupWebdavUrl.getText().toString().trim())
                .putString("backup_user", backupWebdavUser.getText().toString().trim())
                .putString("backup_pass", backupWebdavPass.getText().toString())
                .putString("main_sync_content", selectedSyncContentKey(mainSyncContent))
                .putString("backup_sync_content", selectedSyncContentKey(backupSyncContent))
                .putBoolean("auto_sync", autoSync.isChecked())
                .putString("webdav_algorithm", selectedAlgorithm())
                .apply();
    }

    private void setupCollapsibleCard(View titleRow, LinearLayout card, TextView arrow, String key, boolean defaultExpanded) {
        if (titleRow == null || card == null || arrow == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setCardExpanded(card, arrow, prefs.getBoolean(key, defaultExpanded), false);
        titleRow.setOnClickListener(v -> {
            boolean expanded = !isCardExpanded(card);
            setCardExpanded(card, arrow, expanded, true);
            prefs.edit().putBoolean(key, expanded).apply();
        });
    }

    private boolean isCardExpanded(LinearLayout card) {
        return card.getChildCount() < 2 || card.getChildAt(1).getVisibility() == View.VISIBLE;
    }

    private void setCardExpanded(LinearLayout card, TextView arrow, boolean expanded, boolean animate) {
        for (int i = 1; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (animate && expanded) {
                child.setAlpha(0f);
                child.setVisibility(View.VISIBLE);
                child.animate().alpha(1f).setDuration(300).start();
            } else if (animate) {
                child.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    child.setVisibility(View.GONE);
                    child.setAlpha(1f);
                }).start();
            } else {
                child.setVisibility(expanded ? View.VISIBLE : View.GONE);
                child.setAlpha(1f);
            }
        }
        arrow.setText(expanded ? "⌃" : "⌄");
    }

    private void setupHistoryDropdown(EditText input, String key) {
        input.setOnClickListener(v -> showInputHistory(input, key));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showInputHistory(input, key);
        });
    }

    private void showInputHistory(EditText input, String key) {
        List<String> history = readInputHistory(key);
        if (history.isEmpty()) return;
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_card);
        int width = Math.max(input.getWidth(), dp(260));
        PopupWindow popup = new PopupWindow(content, width, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        for (String value : history) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(6), dp(6), dp(6));
            TextView text = new TextView(requireContext());
            text.setText(value);
            text.setTextColor(getResources().getColor(R.color.text_main));
            text.setSingleLine(true);
            Button delete = new Button(requireContext());
            delete.setText("✕");
            delete.setMinWidth(0);
            delete.setPadding(0, 0, 0, 0);
            row.addView(text, new LinearLayout.LayoutParams(0, dp(38), 1));
            row.addView(delete, new LinearLayout.LayoutParams(dp(38), dp(38)));
            row.setOnClickListener(v -> {
                input.setText(value);
                input.setSelection(input.getText().length());
                popup.dismiss();
            });
            delete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setTitle("删除历史记录")
                    .setMessage("确定删除该条历史记录吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> {
                        removeInputHistory(key, value);
                        popup.dismiss();
                    })
                    .show());
            content.addView(row);
        }
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(4));
        popup.showAsDropDown(input, 0, dp(4));
    }

    private List<String> readInputHistory(String key) {
        String raw = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "");
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String item : raw.split("\\n")) {
            String value = item.trim();
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private void saveInputHistory(String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        List<String> history = readInputHistory(key);
        history.remove(value);
        history.add(0, value);
        while (history.size() > 5) history.remove(history.size() - 1);
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key, joinHistory(history))
                .apply();
    }

    private void removeInputHistory(String key, String value) {
        List<String> history = readInputHistory(key);
        history.remove(value);
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key, joinHistory(history))
                .apply();
    }

    private String joinHistory(List<String> history) {
        StringBuilder builder = new StringBuilder();
        for (String item : history) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(item);
        }
        return builder.toString();
    }

    private void setupBackupPasswordBinding() {
        webdavPass.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!independentBackupPassword.isChecked()) {
                    bindingBackupPassword = true;
                    backupEncryptionPassword.setText(s.toString());
                    bindingBackupPassword = false;
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        backupEncryptionPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!bindingBackupPassword && !independentBackupPassword.isChecked() && !s.toString().equals(webdavPass.getText().toString())) {
                    independentBackupPassword.setChecked(true);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        independentBackupPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            independentBackupWarning.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                bindingBackupPassword = true;
                backupEncryptionPassword.setText(webdavPass.getText().toString());
                bindingBackupPassword = false;
            }
            savePrefs();
        });
    }

    private void saveWebDavSettingsWithRecoveryKey() {
        String backupPassword = currentBackupPassword();
        if (backupPassword.isEmpty()) {
            Toast.makeText(requireContext(), R.string.webdav_backup_password_needed, Toast.LENGTH_SHORT).show();
            return;
        }
        savePrefs();
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getString(KEY_RECOVERY_KEY, "").isEmpty()) {
            String recoveryKey = generateRecoveryKey();
            prefs.edit().putString(KEY_RECOVERY_KEY, recoveryKey).apply();
            showRecoveryKeyDialog(recoveryKey);
        } else {
            Toast.makeText(requireContext(), R.string.webdav_saved, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRecoveryKeyDialog(String recoveryKey) {
        TextView keyView = new TextView(requireContext());
        keyView.setText(recoveryKey);
        keyView.setTextColor(getResources().getColor(R.color.text_main));
        keyView.setTextSize(24);
        keyView.setTextIsSelectable(true);
        keyView.setGravity(android.view.Gravity.CENTER);
        keyView.setPadding(dp(16), dp(16), dp(16), dp(16));
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_recovery_key_title)
                .setMessage(R.string.webdav_recovery_key_message)
                .setView(keyView)
                .setPositiveButton(R.string.webdav_recovery_key_saved, (dialog, which) -> Toast.makeText(requireContext(), R.string.webdav_saved, Toast.LENGTH_SHORT).show())
                .setCancelable(false)
                .show();
    }

    private String generateRecoveryKey() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < 16; i++) raw.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    private String normalizedRecoveryKey() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RECOVERY_KEY, "")
                .replace("-", "")
                .trim()
                .toUpperCase(Locale.US);
    }

    private String currentBackupPassword() {
        return backupEncryptionPassword == null ? "" : backupEncryptionPassword.getText().toString();
    }

    private boolean ensureBackupPasswordConfigured() {
        if (!currentBackupPassword().isEmpty()) return true;
        Toast.makeText(requireContext(), R.string.webdav_backup_password_needed, Toast.LENGTH_SHORT).show();
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void maybeAutoSync() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong("last_sync", 0);
        if (autoSync.isChecked() && System.currentTimeMillis() - last > AUTO_SYNC_INTERVAL_MS) {
            syncTargets(configuredTargets(true), true);
        }
    }

    private void showBackupTargetChooser() {
        savePrefs();
        List<WebDavTarget> all = configuredTargets(true);
        if (all.isEmpty()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.choose_backup_target)
                .setItems(new String[]{getString(R.string.backup_target_main), getString(R.string.backup_target_backup), getString(R.string.backup_target_both)}, (dialog, which) -> {
                    if (which == 0) syncTargets(java.util.Collections.singletonList(mainTarget()), false);
                    else if (which == 1) syncTargets(java.util.Collections.singletonList(backupTarget()), false);
                    else syncTargets(all, false);
                })
                .show();
    }

    private void testTarget(WebDavTarget target) {
        savePrefs();
        if (target == null) return;
        executor.execute(() -> {
            boolean ok = target.client.testConnection();
            runOnUi(() -> Toast.makeText(requireContext(),
                    ok ? target.label + getString(R.string.webdav_connection_success) : getString(R.string.webdav_connection_failed),
                    Toast.LENGTH_SHORT).show());
        });
    }

    private void maybeLoadCloudBackups() {
        if (!webdavUrl.getText().toString().trim().isEmpty()
                && !webdavUser.getText().toString().trim().isEmpty()
                && !webdavPass.getText().toString().isEmpty()) {
            loadBackupHistoryForTarget(mainTarget(), false);
        } else if (!backupWebdavUrl.getText().toString().trim().isEmpty()
                && !backupWebdavUser.getText().toString().trim().isEmpty()
                && !backupWebdavPass.getText().toString().isEmpty()) {
            loadBackupHistoryForTarget(backupTarget(), false);
        }
    }

    private String createBackupEnvelope(String json, String backupPassword) throws Exception {
        String recoveryKey = normalizedRecoveryKey();
        if (recoveryKey.isEmpty()) {
            recoveryKey = normalizeRecoveryInput(generateRecoveryKey());
            requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_RECOVERY_KEY, recoveryKey)
                    .apply();
        }
        JSONObject envelope = new JSONObject();
        envelope.put("keyscanBackupVersion", 4);
        envelope.put("algorithm", "AES-GCM");
        envelope.put("payload", CryptoHelper.encrypt(json, backupPassword, "AES-GCM"));
        envelope.put("recovery", CryptoHelper.encrypt(backupPassword, recoveryKey, "AES-GCM"));
        return envelope.toString();
    }

    private String decryptBackupPayload(String encrypted, String backupPassword) throws Exception {
        String trimmed = encrypted == null ? "" : encrypted.trim();
        if (trimmed.startsWith("{")) {
            JSONObject envelope = new JSONObject(trimmed);
            if (envelope.optInt("keyscanBackupVersion") >= 4) {
                return CryptoHelper.decrypt(envelope.getString("payload"), backupPassword);
            }
        }
        return CryptoHelper.decrypt(trimmed, LEGACY_BACKUP_PASSWORD);
    }

    private String backupPasswordFromRecovery(String encrypted, String recoveryKey) throws Exception {
        JSONObject envelope = new JSONObject(encrypted.trim());
        if (envelope.optInt("keyscanBackupVersion") < 4) {
            throw new IllegalStateException(getString(R.string.webdav_legacy_backup_unsupported));
        }
        return CryptoHelper.decrypt(envelope.getString("recovery"), normalizeRecoveryInput(recoveryKey));
    }

    private String normalizeRecoveryInput(String value) {
        return value == null ? "" : value.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.US);
    }

    private void checkLegacyBackupAvailability() {
        WebDavTarget target = mainTarget();
        if (target == null || convertLegacyButton == null) return;
        executor.execute(() -> {
            String encrypted = target.client.download(LATEST_BACKUP);
            boolean legacy = encrypted != null && !encrypted.trim().startsWith("{");
            runOnUi(() -> convertLegacyButton.setVisibility(legacy ? View.VISIBLE : View.GONE));
        });
    }

    private void convertLegacyBackup(WebDavTarget target) {
        savePrefs();
        if (target == null || !ensureBackupPasswordConfigured()) return;
        ProgressDialog dialog = ProgressDialog.show(requireContext(), getString(R.string.webdav_convert_legacy_title), getString(R.string.webdav_convert_legacy_progress), true, false);
        executor.execute(() -> {
            try {
                String encrypted = target.client.download(LATEST_BACKUP);
                if (encrypted == null || encrypted.isEmpty()) throw new IllegalStateException(getString(R.string.webdav_backup_file_empty));
                String json = CryptoHelper.decrypt(encrypted, LEGACY_BACKUP_PASSWORD);
                String converted = createBackupEnvelope(json, currentBackupPassword());
                boolean ok = target.client.upload(LATEST_BACKUP, converted);
                runOnUi(() -> {
                    dialog.dismiss();
                    convertLegacyButton.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), ok ? R.string.webdav_legacy_backup_converted : R.string.webdav_legacy_backup_upload_failed, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void syncTargets(List<WebDavTarget> targets, boolean automatic) {
        savePrefs();
        if (!ensureBackupPasswordConfigured()) return;
        List<WebDavTarget> valid = new ArrayList<>();
        for (WebDavTarget target : targets) if (target != null) valid.add(target);
        if (valid.isEmpty()) {
            Toast.makeText(requireContext(), R.string.webdav_complete_config_required, Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressDialog dialog = ProgressDialog.show(requireContext(), automatic ? getString(R.string.webdav_auto_sync) : getString(R.string.sync_now), getString(R.string.webdav_encrypting_uploading), true, false);
        repository.getSyncRecords(records -> passwordRepository.getAll(passwords -> otpRepository.getAll(otpTokens -> executor.execute(() -> {
            try {
                int success = 0;
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                for (WebDavTarget target : valid) {
                    String json = toSyncJson(records, passwords, otpTokens, target.contentMode);
                    String encrypted = createBackupEnvelope(json, currentBackupPassword());
                    if (target.client.upload(LATEST_BACKUP, encrypted) && target.client.upload("/keybackup_" + stamp + ".dat", encrypted)) {
                        success++;
                    }
                }
                int finalSuccess = success;
                runOnUi(() -> {
                    dialog.dismiss();
                    if (finalSuccess > 0) {
                        long now = System.currentTimeMillis();
                        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_sync", now).apply();
                        updateLastSyncText(now);
                        loadBackupHistory(false);
                        if (finalSuccess == valid.size()) {
                            Toast.makeText(requireContext(), R.string.webdav_sync_complete, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.webdav_sync_partial, finalSuccess, valid.size()), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), R.string.webdav_sync_failed_check, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.webdav_sync_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        }))));
    }

    private void restoreTarget(WebDavTarget target) {
        savePrefs();
        if (target == null) return;
        showRestorePasswordDialog(target, LATEST_BACKUP);
    }

    private void restoreTargetWithPassword(WebDavTarget target, String remotePath, String password) {
        ProgressDialog dialog = ProgressDialog.show(requireContext(), getString(R.string.webdav_restore_data_title), getString(R.string.webdav_restore_data_downloading), true, false);
        executor.execute(() -> {
            try {
                String encrypted = target.client.download(remotePath);
                if (encrypted == null || encrypted.isEmpty()) throw new IllegalStateException(getString(R.string.webdav_backup_file_empty));
                JSONObject object = new JSONObject(decryptBackupPayload(encrypted, password));
                mergeRestoredObject(object, () -> runOnUi(() -> {
                    dialog.dismiss();
                    backupEncryptionPassword.setText(password);
                    savePrefs();
                    Toast.makeText(requireContext(), R.string.webdav_restore_success, Toast.LENGTH_SHORT).show();
                    promptSetLocalUnlockPassword();
                }));
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    showRestoreFailedDialog(target, remotePath);
                });
            }
        });
    }

    private void showRestorePasswordDialog(WebDavTarget target, String remotePath) {
        EditText input = createPasswordInput(getString(R.string.webdav_backup_password_required));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_restore_data_title)
                .setMessage(R.string.otp_export_password_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.webdav_restore_button, null)
                .setNeutralButton(R.string.webdav_use_recovery_key_reset, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = input.getText().toString();
                if (password.isEmpty()) {
                    input.setError(getString(R.string.webdav_backup_password_required));
                    return;
                }
                dialog.dismiss();
                restoreTargetWithPassword(target, remotePath, password);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                showRecoveryKeyResetDialog(target, remotePath);
            });
        });
        dialog.show();
    }

    private void showRestoreFailedDialog(WebDavTarget target, String remotePath) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_backup_password_error)
                .setMessage(R.string.webdav_backup_password_error)
                .setPositiveButton(R.string.retry, (dialog, which) -> showRestorePasswordDialog(target, remotePath))
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.webdav_use_recovery_key_reset, (dialog, which) -> showRecoveryKeyResetDialog(target, remotePath))
                .show();
    }

    private void showRecoveryKeyResetDialog(WebDavTarget target, String remotePath) {
        EditText recoveryInput = createPasswordInput(getString(R.string.webdav_recovery_key_input_hint));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_recovery_key_reset_title)
                .setMessage(R.string.webdav_recovery_key_reset_message)
                .setView(recoveryInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String recoveryKey = normalizeRecoveryInput(recoveryInput.getText().toString());
            if (recoveryKey.length() != 16) {
                recoveryInput.setError(getString(R.string.webdav_recovery_key_reset_error));
                return;
            }
            dialog.dismiss();
            recoverWithRecoveryKey(target, remotePath, recoveryKey);
        }));
        dialog.show();
    }

    private void recoverWithRecoveryKey(WebDavTarget target, String remotePath, String recoveryKey) {
        ProgressDialog dialog = ProgressDialog.show(requireContext(), getString(R.string.webdav_restore_data_title), getString(R.string.webdav_restore_data_using_key), true, false);
        executor.execute(() -> {
            try {
                String encrypted = target.client.download(remotePath);
                if (encrypted == null || encrypted.isEmpty()) throw new IllegalStateException(getString(R.string.webdav_backup_file_empty));
                String oldBackupPassword = backupPasswordFromRecovery(encrypted, recoveryKey);
                JSONObject object = new JSONObject(decryptBackupPayload(encrypted, oldBackupPassword));
                runOnUi(() -> {
                    dialog.dismiss();
                    showNewBackupPasswordDialog(target, object);
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.webdav_recovery_key_validation_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showNewBackupPasswordDialog(WebDavTarget target, JSONObject restoredObject) {
        EditText input = createPasswordInput(getString(R.string.webdav_backup_password_required));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_recovery_key_reset_title)
                .setMessage(R.string.webdav_recovery_key_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newPassword = input.getText().toString();
            if (newPassword.isEmpty()) {
                input.setError(getString(R.string.webdav_backup_password_required));
                return;
            }
            backupEncryptionPassword.setText(newPassword);
            independentBackupPassword.setChecked(true);
            savePrefs();
            dialog.dismiss();
            mergeRestoredObject(restoredObject, () -> {
                reuploadRestoredBackup(target, restoredObject, newPassword);
                runOnUi(this::promptSetLocalUnlockPassword);
            });
        }));
        dialog.show();
    }

    private void mergeRestoredObject(JSONObject object, Runnable done) {
        try {
            List<ScanRecord> records = parseRecords(object.optJSONArray("records"));
            List<PasswordEntry> passwords = parsePasswords(object.optJSONArray("passwords"));
            List<OtpToken> otpTokens = parseOtpTokens(object.optJSONArray("otpTokens"));
            repository.mergeRecords(records, () -> passwordRepository.mergeEntries(passwords, () -> otpRepository.mergeTokens(otpTokens, done)));
        } catch (Exception e) {
            runOnUi(() -> Toast.makeText(requireContext(), getString(R.string.webdav_restore_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
        }
    }

    private void reuploadRestoredBackup(WebDavTarget target, JSONObject restoredObject, String newPassword) {
        executor.execute(() -> {
            try {
                String encrypted = createBackupEnvelope(restoredObject.toString(), newPassword);
                target.client.upload(LATEST_BACKUP, encrypted);
            } catch (Exception ignored) {
            }
        });
    }

    private void promptSetLocalUnlockPassword() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText passwordInput = createPasswordInput(getString(R.string.password_ledger_unlock_hint));
        Spinner questionSpinner = new Spinner(requireContext());
        questionSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, PinLockHelper.securityQuestions(requireContext())));
        EditText answerInput = createPasswordInput(getString(R.string.password_ledger_answer_hint));
        content.addView(passwordInput);
        content.addView(questionSpinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        content.addView(answerInput);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_ledger_setup_title)
                .setMessage(R.string.password_ledger_setup_message)
                .setView(content)
                .setCancelable(false)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText().toString().trim();
            String answer = answerInput.getText().toString().trim();
            if (!PinLockHelper.isValidPin(password)) {
                passwordInput.setError(getString(R.string.password_ledger_input_error));
                return;
            }
            if (answer.isEmpty()) {
                answerInput.setError(getString(R.string.password_ledger_security_answer_empty));
                return;
            }
            PinLockHelper.saveCredentials(requireContext(), password, "", questionSpinner.getSelectedItem().toString(), answer);
            dialog.dismiss();
            Toast.makeText(requireContext(), R.string.webdav_restore_done, Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void syncNow(boolean automatic) {
        savePrefs();
        if (!ensureBackupPasswordConfigured()) return;
        List<WebDavTarget> targets = configuredTargets(true);
        if (targets.isEmpty()) return;
        String algorithm = selectedAlgorithm();
        ProgressDialog dialog = ProgressDialog.show(requireContext(), automatic ? getString(R.string.webdav_auto_sync) : getString(R.string.sync_now), getString(R.string.webdav_encrypting_uploading), true, false);
        executor.execute(() -> {
            try {
                String encrypted = downloadFirstAvailable(targets);
                if (encrypted != null && !encrypted.isEmpty()) {
                    String json = decryptBackupPayload(encrypted, currentBackupPassword());
                    List<ScanRecord> remote = parseRecords(json);
                    repository.mergeRecords(remote, () -> uploadAfterMerge(targets, dialog, algorithm));
                } else {
                    uploadAfterMerge(targets, dialog, algorithm);
                }
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.webdav_sync_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String downloadFirstAvailable(List<WebDavTarget> targets) {
        for (WebDavTarget target : targets) {
            String encrypted = target.client.download(LATEST_BACKUP);
            if (encrypted != null && !encrypted.isEmpty()) {
                return encrypted;
            }
        }
        return null;
    }

    private void uploadAfterMerge(List<WebDavTarget> targets, ProgressDialog dialog, String algorithm) {
        repository.getSyncRecords(records -> executor.execute(() -> {
            try {
                String json = toJson(records);
                String encrypted = createBackupEnvelope(json, currentBackupPassword());
                String historyPath = "/keybackup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".dat";
                int successCount = 0;
                for (WebDavTarget target : targets) {
                    boolean ok = target.client.upload(LATEST_BACKUP, encrypted) && target.client.upload(historyPath, encrypted);
                    if (ok) successCount++;
                }
                int finalSuccessCount = successCount;
                runOnUi(() -> {
                    dialog.dismiss();
                    if (finalSuccessCount > 0) {
                        long now = System.currentTimeMillis();
                        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_sync", now).apply();
                        updateLastSyncText(now);
                        backupHistory.setText(getString(R.string.webdav_cloud_added, historyPath.substring(1), finalSuccessCount, targets.size()));
                        if (finalSuccessCount == targets.size()) {
                            Toast.makeText(requireContext(), R.string.webdav_sync_complete, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.webdav_sync_partial, finalSuccessCount, targets.size()), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), R.string.webdav_upload_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.webdav_sync_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        }));
    }

    private void loadBackupHistory(boolean showActions) {
        savePrefs();
        List<WebDavTarget> targets = configuredTargets(true);
        if (targets.isEmpty()) return;
        if (targets.size() > 1 && showActions) {
            showTargetPicker(targets);
            return;
        }
        loadBackupHistoryForTarget(targets.get(0), showActions);
    }

    private void showTargetPicker(List<WebDavTarget> targets) {
        String[] labels = new String[targets.size()];
        for (int i = 0; i < targets.size(); i++) labels[i] = targets.get(i).label;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_choose_account)
                .setItems(labels, (dialog, which) -> loadBackupHistoryForTarget(targets.get(which), true))
                .show();
    }

    private void loadBackupHistoryForTarget(WebDavTarget target, boolean showActions) {
        backupHistory.setText(getString(R.string.webdav_cloud_scanning, target.label));
        cloudBackups.removeAllViews();
        executor.execute(() -> {
            List<WebDAVClient.BackupFile> backups = target.client.listBackupFiles();
            runOnUi(() -> {
                if (backups.isEmpty()) {
                    backupHistory.setText(getString(R.string.webdav_cloud_no_files, target.label));
                    renderCloudBackupEmptyState(target);
                    return;
                }
                backupHistory.setText(getString(R.string.webdav_cloud_file_count, target.label, backups.size()));
                renderCloudBackups(target, backups);
            });
        });
    }

    private void showBackupPicker(WebDAVClient client, List<String> backups) {
        String[] labels = new String[backups.size()];
        for (int i = 0; i < backups.size(); i++) labels[i] = backups.get(i).substring(1);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_cloud_backup_title)
                .setItems(labels, (dialog, which) -> showBackupActions(client, backups.get(which)))
                .show();
    }

    private void showBackupActions(WebDAVClient client, String remotePath) {
        new AlertDialog.Builder(requireContext())
                .setTitle(remotePath.substring(1))
                .setItems(new String[]{getString(R.string.restore_this_backup), getString(R.string.delete_this_backup)}, (dialog, which) -> {
                    if (which == 0) confirmRestoreBackup(new WebDavTarget(getString(R.string.history_backup), client, "all"), remotePath);
                    else confirmDeleteBackup(new WebDavTarget(getString(R.string.history_backup), client, "all"), remotePath);
                })
                .show();
    }

    private void restoreBackup(WebDAVClient client, String remotePath) {
        showRestorePasswordDialog(new WebDavTarget(getString(R.string.history_backup), client, "all"), remotePath);
    }

    private void renderCloudBackupEmptyState(WebDavTarget target) {
        cloudBackups.removeAllViews();
        LinearLayout row = createBackupRowContainer();
        row.setOrientation(LinearLayout.VERTICAL);
        TextView empty = createBackupText(getString(R.string.webdav_backup_empty), 14, false, R.color.text_secondary);
        Button refresh = new Button(requireContext());
        refresh.setText(R.string.webdav_sync_now_button);
        refresh.setOnClickListener(v -> syncTargets(java.util.Collections.singletonList(target), false));
        row.addView(empty);
        row.addView(refresh, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        cloudBackups.addView(row);
    }

    private void renderCloudBackups(WebDavTarget target, List<WebDAVClient.BackupFile> backups) {
        cloudBackups.removeAllViews();
        for (WebDAVClient.BackupFile file : backups) {
            cloudBackups.addView(createBackupFileRow(target, file));
        }
    }

    private View createBackupFileRow(WebDavTarget target, WebDAVClient.BackupFile file) {
        LinearLayout row = createBackupRowContainer();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(requireContext());
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(createBackupText(file.name, 14, true, R.color.text_main));
        left.addView(createBackupText(formatBackupTime(file), 12, false, R.color.text_secondary));

        LinearLayout right = new LinearLayout(requireContext());
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(android.view.Gravity.END);
        right.addView(createBackupText(formatFileSize(file.size), 12, false, R.color.text_secondary));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.END);
        Button restore = new Button(requireContext());
        restore.setText(R.string.webdav_restore_button);
        restore.setOnClickListener(v -> confirmRestoreBackup(target, file.path));
        Button delete = new Button(requireContext());
        delete.setText(R.string.webdav_delete_backup_button);
        delete.setOnClickListener(v -> confirmDeleteBackup(target, file.path));
        actions.addView(restore, new LinearLayout.LayoutParams(dp(72), dp(40)));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(72), dp(40));
        deleteParams.leftMargin = dp(6);
        actions.addView(delete, deleteParams);
        right.addView(actions);

        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(right, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout createBackupRowContainer() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setBackgroundResource(R.drawable.bg_card);
        row.setElevation(dp(2));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private TextView createBackupText(String text, int sp, boolean bold, int colorRes) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(getResources().getColor(colorRes));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private void confirmRestoreBackup(WebDavTarget target, String remotePath) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_restore_backup_title)
                .setMessage(R.string.webdav_restore_backup_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> showRestorePasswordDialog(target, remotePath))
                .show();
    }

    private void confirmDeleteBackup(WebDavTarget target, String remotePath) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_delete_backup_title)
                .setMessage(R.string.webdav_delete_backup_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> deleteBackup(target, remotePath))
                .show();
    }

    private String formatBackupTime(WebDAVClient.BackupFile file) {
        String name = file.name;
        String stamp = "";
        if (name.startsWith("keybackup_")) {
            stamp = name.substring("keybackup_".length(), name.lastIndexOf('.'));
        } else if (name.startsWith("secure_backup_")) {
            stamp = name.substring("secure_backup_".length(), name.lastIndexOf('.'));
        }
        if (!stamp.isEmpty()) {
            try {
                Date date = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(stamp);
                return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date);
            } catch (Exception ignored) {
            }
        }
        if ("secure_backup.dat".equals(name)) return getString(R.string.webdav_latest_backup);
        return file.lastModified == null || file.lastModified.isEmpty() ? getString(R.string.webdav_time_unknown) : file.lastModified;
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return getString(R.string.webdav_size_unknown);
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb);
        return String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0);
    }

    private void deleteBackup(WebDavTarget target, String remotePath) {
        executor.execute(() -> {
            boolean ok = target.client.delete(remotePath);
            runOnUi(() -> {
                Toast.makeText(requireContext(), ok ? R.string.webdav_backup_deleted : R.string.webdav_backup_delete_failed, Toast.LENGTH_SHORT).show();
                if (ok) {
                    loadBackupHistoryForTarget(target, true);
                }
            });
        });
    }

    private List<WebDavTarget> configuredTargets(boolean showToast) {
        List<WebDavTarget> targets = new ArrayList<>();
        WebDavTarget main = mainTarget();
        WebDavTarget backup = backupTarget();
        if (main != null) targets.add(main);
        if (backup != null) targets.add(backup);
        if (targets.isEmpty() && showToast) {
            Toast.makeText(requireContext(), R.string.webdav_complete_config_required, Toast.LENGTH_SHORT).show();
        }
        return targets;
    }

    private WebDavTarget mainTarget() {
        return buildTarget(getString(R.string.backup_target_main), webdavUrl, webdavUser, webdavPass, selectedSyncContentKey(mainSyncContent));
    }

    private WebDavTarget backupTarget() {
        return buildTarget(getString(R.string.backup_target_backup), backupWebdavUrl, backupWebdavUser, backupWebdavPass, selectedSyncContentKey(backupSyncContent));
    }

    private WebDavTarget buildTarget(String label, EditText urlInput, EditText userInput, EditText passInput, String contentMode) {
        String url = urlInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString();
        if (url.isEmpty() && user.isEmpty() && pass.isEmpty()) return null;
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.webdav_complete_config_required) + "：" + label, Toast.LENGTH_SHORT).show();
            return null;
        }
        return new WebDavTarget(label, new WebDAVClient(url, user, pass), contentMode);
    }

    private String toSyncJson(List<ScanRecord> records, List<PasswordEntry> passwords, List<OtpToken> otpTokens, String contentMode) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 3);
        root.put("records", shouldSyncRecords(contentMode) ? recordsToJson(records) : new JSONArray());
        root.put("passwords", shouldSyncPasswords(contentMode) ? passwordsToJson(passwords) : new JSONArray());
        root.put("otpTokens", shouldSyncOtp(contentMode) ? otpTokensToJson(otpTokens) : new JSONArray());
        return root.toString();
    }

    private boolean shouldSyncRecords(String contentMode) {
        return "all".equals(contentMode) || "records".equals(contentMode);
    }

    private boolean shouldSyncPasswords(String contentMode) {
        return "all".equals(contentMode) || "passwords".equals(contentMode);
    }

    private boolean shouldSyncOtp(String contentMode) {
        return "all".equals(contentMode) || "otp".equals(contentMode);
    }

    private String toJson(List<ScanRecord> records) throws Exception {
        return recordsToJson(records).toString();
    }

    private JSONArray recordsToJson(List<ScanRecord> records) throws Exception {
        JSONArray array = new JSONArray();
        for (ScanRecord record : records) {
            JSONObject object = new JSONObject();
            object.put("content", record.content);
            object.put("type", record.type);
            object.put("title", record.title);
            object.put("source", record.source);
            object.put("thumbnailBase64", record.thumbnailBase64);
            object.put("isStarred", record.isStarred);
            object.put("timestamp", record.timestamp);
            array.put(object);
        }
        return array;
    }

    private JSONArray otpTokensToJson(List<OtpToken> tokens) throws Exception {
        JSONArray array = new JSONArray();
        for (OtpToken token : tokens) {
            JSONObject object = new JSONObject();
            object.put("accountName", token.accountName);
            object.put("issuer", token.issuer);
            object.put("secret", token.secret);
            object.put("digits", token.digits);
            object.put("period", token.period);
            object.put("algorithm", token.algorithm);
            object.put("pinned", token.pinned);
            object.put("sortOrder", token.sortOrder);
            object.put("createdAt", token.createdAt);
            object.put("updatedAt", token.updatedAt);
            array.put(object);
        }
        return array;
    }

    private JSONArray passwordsToJson(List<PasswordEntry> passwords) throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordEntry entry : passwords) {
            JSONObject object = new JSONObject();
            object.put("password", entry.password);
            object.put("account", entry.account);
            object.put("remark", entry.remark);
            object.put("createdAt", entry.createdAt);
            array.put(object);
        }
        return array;
    }

    private List<ScanRecord> parseRecords(String json) throws Exception {
        return parseRecords(new JSONArray(json));
    }

    private List<ScanRecord> parseRecords(@Nullable JSONArray array) throws Exception {
        List<ScanRecord> records = new ArrayList<>();
        if (array == null) return records;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            ScanRecord record = new ScanRecord();
            record.content = object.optString("content");
            record.type = object.optString("type", ScanRecord.detectType(record.content));
            record.title = object.optString("title", record.content);
            record.source = object.optString("source", "SCAN");
            record.thumbnailBase64 = object.optString("thumbnailBase64", "");
            record.isStarred = object.optBoolean("isStarred");
            record.timestamp = object.optLong("timestamp");
            records.add(record);
        }
        return records;
    }

    private List<PasswordEntry> parsePasswords(@Nullable JSONArray array) throws Exception {
        List<PasswordEntry> entries = new ArrayList<>();
        if (array == null) return entries;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            PasswordEntry entry = new PasswordEntry();
            entry.password = object.optString("password");
            entry.account = object.optString("account");
            entry.remark = object.optString("remark");
            entry.createdAt = object.optLong("createdAt");
            entries.add(entry);
        }
        return entries;
    }

    private List<OtpToken> parseOtpTokens(@Nullable JSONArray array) throws Exception {
        List<OtpToken> tokens = new ArrayList<>();
        if (array == null) return tokens;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            OtpToken token = new OtpToken();
            token.accountName = object.optString("accountName");
            token.issuer = object.optString("issuer");
            token.secret = object.optString("secret");
            token.digits = object.optInt("digits", 6);
            token.period = object.optInt("period", 30);
            token.algorithm = object.optString("algorithm", "SHA1");
            token.pinned = object.optBoolean("pinned");
            token.sortOrder = object.optInt("sortOrder");
            token.createdAt = object.optLong("createdAt");
            token.updatedAt = object.optLong("updatedAt", token.createdAt);
            tokens.add(token);
        }
        return tokens;
    }

    private void updateLastSyncText(long time) {
        if (time > 0) {
            lastSync.setText(getString(R.string.webdav_last_upload_prefix) + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time)));
        } else {
            lastSync.setText(R.string.last_upload_never);
        }
    }

    private String selectedAlgorithm() {
        Object item = algorithmSpinner.getSelectedItem();
        return item == null ? "AES-GCM" : item.toString();
    }

    private EditText createPasswordInput(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF636366);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private List<String> syncContentLabels() {
        return java.util.Arrays.asList(
                getString(R.string.sync_content_records),
                getString(R.string.sync_content_passwords),
                getString(R.string.sync_content_otp),
                getString(R.string.sync_content_all)
        );
    }

    private String selectedSyncContentKey(Spinner spinner) {
        int position = spinner == null ? 3 : spinner.getSelectedItemPosition();
        if (position == 0) return "records";
        if (position == 1) return "passwords";
        if (position == 2) return "otp";
        return "all";
    }

    private void setSyncContentSelection(Spinner spinner, String saved) {
        int index = 3;
        if ("records".equals(saved) || getString(R.string.sync_content_records).equals(saved)) index = 0;
        else if ("passwords".equals(saved) || getString(R.string.sync_content_passwords).equals(saved)) index = 1;
        else if ("otp".equals(saved) || getString(R.string.sync_content_otp).equals(saved)) index = 2;
        else if ("all".equals(saved) || getString(R.string.sync_content_all).equals(saved)) index = 3;
        spinner.setSelection(index);
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equals(spinner.getItemAtPosition(i).toString())) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void runOnUi(Runnable runnable) {
        if (isAdded()) requireActivity().runOnUiThread(runnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) executor.shutdown();
    }

    private static class WebDavTarget {
        final String label;
        final WebDAVClient client;
        final String contentMode;

        WebDavTarget(String label, WebDAVClient client, String contentMode) {
            this.label = label;
            this.client = client;
            this.contentMode = contentMode;
        }
    }
}

