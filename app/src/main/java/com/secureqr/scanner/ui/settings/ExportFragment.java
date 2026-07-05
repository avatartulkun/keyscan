package com.secureqr.scanner.ui.settings;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.utils.CryptoHelper;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.WebDAVClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExportFragment extends Fragment {
    private static final String JSON_MIME = "application/json";
    private static final String PREFS = "secureqr_settings";
    private static final String KEY_LAST_BACKUP_REMINDER = "last_data_insurance_backup_reminder";
    private static final long REMINDER_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L;

    private TextView scanCount;
    private TextView passwordCount;
    private TextView otpCount;
    private RecordRepository recordRepository;
    private PasswordRepository passwordRepository;
    private OtpRepository otpRepository;
    private ExecutorService executor;
    private ActivityResultLauncher<String> importPicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::importBackup);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        executor = Executors.newSingleThreadExecutor();
        recordRepository = RecordRepository.getInstance(requireContext());
        passwordRepository = PasswordRepository.getInstance(requireContext());
        otpRepository = OtpRepository.getInstance(requireContext());
        view.findViewById(R.id.btn_export_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        scanCount = view.findViewById(R.id.tv_scan_count);
        passwordCount = view.findViewById(R.id.tv_password_count);
        otpCount = view.findViewById(R.id.tv_otp_count);
        view.findViewById(R.id.btn_export).setOnClickListener(v -> showExportSelectionDialog());
        view.findViewById(R.id.btn_import_restore).setOnClickListener(v -> importPicker.launch(JSON_MIME));
        view.findViewById(R.id.btn_verify_integrity).setOnClickListener(v -> verifyIntegrity());
        refreshOverview();
        maybeShowBackupReminder();
    }

    private void refreshOverview() {
        recordRepository.getAll(records -> {
            int recordsCount = records.size();
            passwordRepository.getAll(passwords -> {
                int passwordsCount = passwords.size();
                otpRepository.getAll(tokens -> runOnUi(() -> {
                    scanCount.setText(getString(R.string.scan_records_count, recordsCount));
                    passwordCount.setText(getString(R.string.password_entries_count, passwordsCount));
                    otpCount.setText(getString(R.string.otp_tokens_count, tokens.size()));
                }));
            });
        });
    }

    private void maybeShowBackupReminder() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST_BACKUP_REMINDER, 0);
        if (System.currentTimeMillis() - last < REMINDER_INTERVAL_MS) return;
        prefs.edit().putLong(KEY_LAST_BACKUP_REMINDER, System.currentTimeMillis()).apply();
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_reminder)
                .setMessage(R.string.backup_reminder_message)
                .setNegativeButton(R.string.later, null)
                .setPositiveButton(R.string.backup_now, (dialog, which) -> showExportSelectionDialog())
                .show();
    }

    private void showExportSelectionDialog() {
        recordRepository.getAll(records -> passwordRepository.getAll(passwords -> otpRepository.getAll(tokens -> executor.execute(() -> {
            boolean[] checked = {true, true, true};
            String[] labels = {
                    getString(R.string.export_scan_option, records.size()),
                    getString(R.string.export_password_option, passwords.size()),
                    getString(R.string.export_otp_option, tokens.size())
            };
            runOnUi(() -> {
                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.choose_export_content)
                        .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.confirm_export, null)
                        .create();
                dialog.setOnShowListener(d -> {
                    android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positive.setEnabled(true);
                    positive.setOnClickListener(v -> {
                        if (!checked[0] && !checked[1] && !checked[2]) {
                            Toast.makeText(requireContext(), R.string.choose_at_least_one_data_type, Toast.LENGTH_SHORT).show();
                            positive.setEnabled(false);
                            return;
                        }
                        dialog.dismiss();
                        exportBackup(records, passwords, tokens, checked);
                    });
                    ((android.widget.ListView) dialog.getListView()).setOnItemClickListener((parent, view, position, id) -> {
                        checked[position] = dialog.getListView().isItemChecked(position);
                        positive.setEnabled(checked[0] || checked[1] || checked[2]);
                    });
                });
                dialog.show();
            });
        }))));
    }

    private void exportBackup(List<ScanRecord> records, List<PasswordEntry> passwords, List<OtpToken> tokens, boolean[] selected) {
        executor.execute(() -> {
            try {
                JSONObject root = new JSONObject();
                root.put("keyscanBackupVersion", 1);
                root.put("exportedAt", System.currentTimeMillis());
                root.put("records", selected[0] ? recordsJson(records) : new JSONArray());
                root.put("passwords", selected[1] ? passwordsJson(passwords) : new JSONArray());
                root.put("otpTokens", selected[2] ? otpJson(tokens) : new JSONArray());
                String name = "keyscan_data_insurance_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
                String json = root.toString(2);
                writeBytes(name, json.getBytes(StandardCharsets.UTF_8));
                uploadWebDavBackupIfConfigured(json);
                int exportedRows = (selected[0] ? records.size() : 0) + (selected[1] ? passwords.size() : 0) + (selected[2] ? tokens.size() : 0);
                runOnUi(() -> {
                    Toast.makeText(requireContext(), getString(R.string.exported_items, exportedRows), Toast.LENGTH_SHORT).show();
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.backup_complete_title)
                            .setMessage(R.string.backup_complete_message)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                });
            } catch (Exception error) {
                runOnUi(() -> Toast.makeText(requireContext(), getString(R.string.export_failed, error.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void uploadWebDavBackupIfConfigured(String json) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String url = prefs.getString("url", "").trim();
        String user = prefs.getString("user", "").trim();
        String pass = prefs.getString("pass", "");
        String backupPassword = prefs.getString("webdav_backup_password", pass);
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty() || backupPassword.isEmpty()) return;
        try {
            String encrypted = CryptoHelper.encrypt(json, backupPassword, "AES-GCM");
            new WebDAVClient(url, user, pass).upload("/keyscan_data_insurance_latest.dat", encrypted);
        } catch (Exception ignored) {
        }
    }

    private void importBackup(@Nullable Uri uri) {
        if (uri == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_restore_title)
                .setMessage(R.string.import_restore_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.import_action, (dialog, which) -> executor.execute(() -> {
                    try {
                        JSONObject root = new JSONObject(readText(uri));
                        List<ScanRecord> records = parseRecords(root.optJSONArray("records"));
                        List<PasswordEntry> passwords = parsePasswords(root.optJSONArray("passwords"));
                        List<OtpToken> tokens = parseOtp(root.optJSONArray("otpTokens"));
                        recordRepository.mergeRecords(records, () ->
                                passwordRepository.mergeEntries(passwords, () ->
                                        otpRepository.mergeTokens(tokens, () -> runOnUi(() -> {
                                            Toast.makeText(requireContext(), R.string.import_restore_done, Toast.LENGTH_SHORT).show();
                                            refreshOverview();
                                        }))));
                    } catch (Exception error) {
                        runOnUi(() -> Toast.makeText(requireContext(), getString(R.string.import_failed, error.getMessage()), Toast.LENGTH_SHORT).show());
                    }
                }))
                .show();
    }

    private void verifyIntegrity() {
        recordRepository.getAll(records -> passwordRepository.getAll(passwords -> otpRepository.getAll(tokens -> {
            int problems = 0;
            for (ScanRecord record : records) if (record.content == null || record.timestamp <= 0) problems++;
            for (PasswordEntry entry : passwords) if (entry.password == null || entry.createdAt <= 0) problems++;
            for (OtpToken token : tokens) if (token.secret == null || token.secret.trim().isEmpty()) problems++;
            int finalProblems = problems;
            runOnUi(() -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.integrity_check)
                    .setMessage(finalProblems == 0
                            ? getString(R.string.integrity_ok)
                            : getString(R.string.integrity_problem, finalProblems))
                    .setPositiveButton(R.string.ok, null)
                    .show());
        })));
    }

    private JSONArray recordsJson(List<ScanRecord> records) throws Exception {
        JSONArray array = new JSONArray();
        for (ScanRecord record : records) {
            JSONObject item = new JSONObject();
            item.put("content", record.content);
            item.put("type", record.type);
            item.put("title", record.title);
            item.put("source", record.source);
            item.put("thumbnailBase64", record.thumbnailBase64);
            item.put("isStarred", record.isStarred);
            item.put("timestamp", record.timestamp);
            array.put(item);
        }
        return array;
    }

    private JSONArray passwordsJson(List<PasswordEntry> entries) throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordEntry entry : entries) {
            JSONObject item = new JSONObject();
            item.put("password", entry.password);
            item.put("account", entry.account);
            item.put("remark", entry.remark);
            item.put("createdAt", entry.createdAt);
            array.put(item);
        }
        return array;
    }

    private JSONArray otpJson(List<OtpToken> tokens) throws Exception {
        JSONArray array = new JSONArray();
        for (OtpToken token : tokens) {
            JSONObject item = new JSONObject();
            item.put("accountName", token.accountName);
            item.put("issuer", token.issuer);
            item.put("secret", token.secret);
            item.put("digits", token.digits);
            item.put("period", token.period);
            item.put("algorithm", token.algorithm);
            item.put("pinned", token.pinned);
            item.put("sortOrder", token.sortOrder);
            item.put("createdAt", token.createdAt);
            item.put("updatedAt", token.updatedAt);
            array.put(item);
        }
        return array;
    }

    private List<ScanRecord> parseRecords(@Nullable JSONArray array) {
        List<ScanRecord> records = new ArrayList<>();
        if (array == null) return records;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            ScanRecord record = new ScanRecord();
            record.content = item.optString("content", "");
            record.type = item.optString("type", ScanRecord.detectType(record.content));
            record.title = item.optString("title", record.content);
            record.source = item.optString("source", "IMPORT");
            record.thumbnailBase64 = item.optString("thumbnailBase64", "");
            record.isStarred = item.optBoolean("isStarred", false);
            record.timestamp = item.optLong("timestamp", System.currentTimeMillis());
            records.add(record);
        }
        return records;
    }

    private List<PasswordEntry> parsePasswords(@Nullable JSONArray array) {
        List<PasswordEntry> entries = new ArrayList<>();
        if (array == null) return entries;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            PasswordEntry entry = new PasswordEntry();
            entry.password = item.optString("password", "");
            entry.account = item.optString("account", "");
            entry.remark = item.optString("remark", "");
            entry.createdAt = item.optLong("createdAt", System.currentTimeMillis());
            entries.add(entry);
        }
        return entries;
    }

    private List<OtpToken> parseOtp(@Nullable JSONArray array) {
        List<OtpToken> tokens = new ArrayList<>();
        if (array == null) return tokens;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            OtpToken token = new OtpToken();
            token.accountName = item.optString("accountName", "");
            token.issuer = item.optString("issuer", "");
            token.secret = item.optString("secret", "");
            token.digits = item.optInt("digits", 6);
            token.period = item.optInt("period", 30);
            token.algorithm = item.optString("algorithm", "SHA1");
            token.pinned = item.optBoolean("pinned", false);
            token.sortOrder = item.optInt("sortOrder", 0);
            token.createdAt = item.optLong("createdAt", System.currentTimeMillis());
            token.updatedAt = item.optLong("updatedAt", token.createdAt);
            tokens.add(token);
        }
        return tokens;
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException(getString(R.string.file_read_failed));
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
            return output.toString("UTF-8");
        }
    }

    private void writeBytes(String name, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, JSON_MIME);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KeyScan");
            Uri uri = requireContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException(getString(R.string.backup_file_create_failed));
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException(getString(R.string.backup_file_write_failed));
                out.write(data);
            }
            return;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KeyScan");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException(getString(R.string.backup_dir_create_failed));
        try (OutputStream out = new FileOutputStream(new File(dir, name))) {
            out.write(data);
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
}
