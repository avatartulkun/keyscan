package com.secureqr.scanner.ui.password;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.ui.scanner.ScannerFragment;
import com.secureqr.scanner.utils.ExcelExportHelper;
import com.secureqr.scanner.utils.PasswordGeneratorEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PasswordForgeFragment extends Fragment {
    public static final String PASSWORD_SCAN_REQUEST = "password_scan_request";
    public static final String PASSWORD_SCAN_VALUE = "password_scan_value";

    private static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private PasswordRepository repository;
    private PasswordEntryAdapter adapter;
    private View emptyState;
    private LiveData<List<PasswordEntry>> observedEntries;
    private CredentialEditor activeEditor;
    private AlertDialog activeCredentialDialog;
    private PasswordEntry pendingScanEntry;
    private String pendingScanSite = "";
    private String pendingScanAccount = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_password_forge, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = PasswordRepository.getInstance(requireContext());
        emptyState = view.findViewById(R.id.layout_password_empty);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_password_entries);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PasswordEntryAdapter(new PasswordEntryAdapter.Listener() {
            @Override
            public void onCopy(PasswordEntry entry) {
                copyText(entry.password);
            }

            @Override
            public void onDelete(PasswordEntry entry) {
                confirmDelete(entry);
            }

            @Override
            public void onEdit(PasswordEntry entry) {
                showCredentialDialog(entry);
            }
        });
        recyclerView.setAdapter(adapter);
        attachSwipeDelete(recyclerView);

        view.findViewById(R.id.btn_add_password_entry).setOnClickListener(v -> showAddDialog());
        view.findViewById(R.id.btn_password_menu).setOnClickListener(this::showMenu);
        SearchView searchView = view.findViewById(R.id.search_password_entries);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                observeEntries(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                observeEntries(newText);
                return true;
            }
        });

        getParentFragmentManager().setFragmentResultListener(PASSWORD_SCAN_REQUEST, getViewLifecycleOwner(), (requestKey, result) -> {
            String raw = result.getString(PASSWORD_SCAN_VALUE, "");
            if (pendingScanEntry != null || !pendingScanSite.isEmpty() || !pendingScanAccount.isEmpty()) {
                PasswordEntry entry = pendingScanEntry;
                String site = pendingScanSite;
                String account = pendingScanAccount;
                pendingScanEntry = null;
                pendingScanSite = "";
                pendingScanAccount = "";
                showCredentialDialog(entry, site, account, raw);
            } else if (activeEditor != null) {
                activeEditor.applyScannedPassword(raw);
            } else {
                showCredentialDialog(null, "", "", raw);
            }
        });
        observeEntries("");
    }

    private void observeEntries(String query) {
        if (observedEntries != null) {
            observedEntries.removeObservers(getViewLifecycleOwner());
        }
        observedEntries = repository.observe(query);
        observedEntries.observe(getViewLifecycleOwner(), entries -> {
            adapter.submit(entries);
            boolean empty = entries == null || entries.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
    }

    private void attachSwipeDelete(RecyclerView recyclerView) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                PasswordEntry entry = adapter.getItem(position);
                adapter.notifyItemChanged(position);
                confirmDelete(entry);
            }
        }).attachToRecyclerView(recyclerView);
    }

    private void showAddDialog() {
        showCredentialDialog(null, "", "", "");
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(R.string.export_backup);
        menu.setOnMenuItemClickListener(item -> {
            showExportFormatDialog();
            return true;
        });
        menu.show();
    }

    @ExperimentalGetImage
    private void openPasswordScanner() {
        if (activeEditor != null) {
            pendingScanSite = activeEditor.siteInput.getText().toString();
            pendingScanAccount = activeEditor.accountInput.getText().toString();
            if (activeCredentialDialog != null) {
                activeCredentialDialog.dismiss();
            }
        }
        ScannerFragment fragment = ScannerFragment.forPasswordCapture();
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showCredentialDialog(PasswordEntry entry) {
        showCredentialDialog(entry, entry.remark, entry.account, entry.password);
    }

    private void showCredentialDialog(@Nullable PasswordEntry editingEntry, String initialSite, String initialAccount, String initialPassword) {
        CredentialEditor editor = new CredentialEditor(editingEntry, initialSite, initialAccount, initialPassword);
        activeEditor = editor;
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(editingEntry == null ? R.string.credential_add_title : R.string.credential_edit_title)
                .setView(editor.root)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        activeCredentialDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeEditor == editor) activeEditor = null;
            if (activeCredentialDialog == dialog) activeCredentialDialog = null;
        });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String site = editor.siteInput.getText().toString().trim();
            String account = editor.accountInput.getText().toString().trim();
            if (TextUtils.isEmpty(site) || TextUtils.isEmpty(account)) {
                Toast.makeText(requireContext(), R.string.credential_fill_complete_info, Toast.LENGTH_SHORT).show();
                if (TextUtils.isEmpty(site)) editor.siteInput.setError(getString(R.string.credential_site_empty_error));
                if (TextUtils.isEmpty(account)) editor.accountInput.setError(getString(R.string.credential_account_empty_error));
                return;
            }
            PasswordEntry entry = editingEntry == null ? new PasswordEntry() : editingEntry;
            entry.remark = site;
            entry.account = account;
            entry.password = editor.currentPassword;
            if (editingEntry == null) {
                entry.createdAt = System.currentTimeMillis();
                repository.insert(entry);
            } else {
                repository.update(entry);
            }
            dialog.dismiss();
            Toast.makeText(requireContext(), R.string.credential_save_success, Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private class CredentialEditor {
        final LinearLayout root;
        final EditText siteInput;
        final EditText accountInput;
        final EditText passwordValue;
        final TextView lengthText;
        final TextView strengthText;
        final Button eye;
        final ProgressBar strengthProgress;
        final PasswordGeneratorEngine.Options options = new PasswordGeneratorEngine.Options();
        final PasswordEntry editingEntry;
        String currentPassword;
        int passwordLength = 8;
        boolean visible = false;
        boolean bindingPassword = false;

        CredentialEditor(@Nullable PasswordEntry editingEntry, String initialSite, String initialAccount, String initialPassword) {
            this.editingEntry = editingEntry;
            root = new LinearLayout(requireContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(18), dp(8), dp(18), 0);

            siteInput = input(getString(R.string.credential_site_hint));
            accountInput = input(getString(R.string.credential_account_hint));
            passwordValue = input(getString(R.string.credential_password_hint));
            passwordValue.setBackgroundResource(R.drawable.bg_edit_text);
            passwordValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            passwordValue.setTextSize(18);
            passwordValue.setPadding(dp(12), 0, dp(12), 0);

            eye = compactButton("显");
            Button refresh = compactButton("生成");
            Button scan = compactButton("扫描填充");
            Button help = compactButton("帮助");
            Button optionsButton = compactButton("生成选项");
            Button minus = compactButton("-");
            Button plus = compactButton("+");
            lengthText = new TextView(requireContext());
            lengthText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            lengthText.setTextSize(14);
            lengthText.setGravity(android.view.Gravity.CENTER_VERTICAL);
            strengthProgress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            strengthProgress.setMax(100);
            strengthText = new TextView(requireContext());
            strengthText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            strengthText.setTextSize(12);

            root.addView(siteInput, params(52));
            root.addView(accountInput, topParams(52, 10));
            LinearLayout passwordRow = new LinearLayout(requireContext());
            passwordRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            passwordRow.setOrientation(LinearLayout.HORIZONTAL);
            passwordRow.addView(passwordValue, new LinearLayout.LayoutParams(0, dp(48), 1));
            passwordRow.addView(eye, fixedParams(48, 8));
            passwordRow.addView(refresh, fixedParams(48, 8));
            root.addView(passwordRow, topParams(48, 12));

            LinearLayout toolsRow = new LinearLayout(requireContext());
            toolsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            toolsRow.setOrientation(LinearLayout.HORIZONTAL);
            toolsRow.addView(scan, new LinearLayout.LayoutParams(0, dp(44), 1));
            toolsRow.addView(optionsButton, new LinearLayout.LayoutParams(0, dp(44), 1));
            toolsRow.addView(help, new LinearLayout.LayoutParams(0, dp(44), 1));
            root.addView(toolsRow, topParams(44, 8));

            LinearLayout lengthRow = new LinearLayout(requireContext());
            lengthRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            lengthRow.setOrientation(LinearLayout.HORIZONTAL);
            lengthRow.addView(lengthText, new LinearLayout.LayoutParams(0, dp(44), 1));
            lengthRow.addView(minus, fixedParams(44, 8));
            lengthRow.addView(plus, fixedParams(44, 8));
            root.addView(lengthRow, topParams(44, 8));
            root.addView(strengthProgress, topParams(8, 8));
            root.addView(strengthText, topParams(24, 6));

            siteInput.setText(initialSite == null ? "" : initialSite);
            accountInput.setText(initialAccount == null ? "" : initialAccount);
            if (initialPassword != null && !initialPassword.isEmpty()) {
                currentPassword = initialPassword;
            } else {
                generatePassword();
            }
            updatePasswordView();
            updateStrength();

            eye.setOnClickListener(v -> {
                visible = !visible;
                updatePasswordView();
            });
            refresh.setOnClickListener(v -> generatePassword());
            scan.setOnClickListener(v -> {
                pendingScanEntry = this.editingEntry;
                openPasswordScanner();
            });
            help.setOnClickListener(v -> showQrHelp());
            optionsButton.setOnClickListener(v -> showGenerateOptionsDialog());
            minus.setOnClickListener(v -> {
                if (passwordLength > 4) {
                    passwordLength--;
                    generatePassword();
                }
            });
            plus.setOnClickListener(v -> {
                if (passwordLength < 32) {
                    passwordLength++;
                    generatePassword();
                }
            });
            passwordValue.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (bindingPassword) return;
                    currentPassword = s.toString();
                    updateStrength();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        private void showGenerateOptionsDialog() {
            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(20), dp(8), dp(20), 0);
            CheckBox zeroO = checkBox("排除 0（零）和 O（大写欧）", options.excludeZeroO);
            CheckBox oneI = checkBox("排除 1（一）和 I（大写爱）", options.excludeOneI);
            CheckBox lowerL = checkBox("排除 l（小写爱）", options.excludeLowerL);
            content.addView(zeroO);
            content.addView(oneI);
            content.addView(lowerL);
            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle("生成选项")
                    .setView(content)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("应用", null)
                    .create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                options.excludeZeroO = zeroO.isChecked();
                options.excludeOneI = oneI.isChecked();
                options.excludeLowerL = lowerL.isChecked();
                generatePassword();
                dialog.dismiss();
            }));
            dialog.show();
        }

        private void generatePassword() {
            options.length = passwordLength;
            String generated = PasswordGeneratorEngine.generate(options);
            if (generated.isEmpty()) {
                Toast.makeText(requireContext(), "可用字符太少，请减少排除字符", Toast.LENGTH_SHORT).show();
                return;
            }
            currentPassword = generated;
            visible = true;
            updatePasswordView();
            updateStrength();
        }

        private void updatePasswordView() {
            bindingPassword = true;
            passwordValue.setText(currentPassword);
            passwordValue.setSelection(passwordValue.getText().length());
            bindingPassword = false;
            passwordValue.setTransformationMethod(visible ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
            eye.setText(visible ? "隐藏" : "显示");
            eye.setContentDescription(visible ? "点击隐藏密码" : "点击显示密码");
            lengthText.setText("长度：" + passwordLength + " 位");
        }

        private void updateStrength() {
            int score = PasswordGeneratorEngine.strengthScore(currentPassword);
            int color;
            String label;
            if (score < 55) {
                color = Color.parseColor("#D93025");
                label = "强度：弱";
            } else if (score < 85) {
                color = Color.parseColor("#F9AB00");
                label = "强度：中";
            } else {
                color = Color.parseColor("#00A878");
                label = "强度：强";
            }
            strengthProgress.setProgress(score);
            strengthProgress.setProgressTintList(ColorStateList.valueOf(color));
            strengthText.setText(label);
        }

        private void applyScannedPassword(String value) {
            currentPassword = value == null ? "" : value;
            visible = true;
            updatePasswordView();
            updateStrength();
            Toast.makeText(requireContext(), "已填充到密码字段", Toast.LENGTH_SHORT).show();
        }
    }

    private EditText input(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        input.setHintTextColor(0xFF8A8F98);
        return input;
    }

    private Button compactButton(String text) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private CheckBox checkBox(String text, boolean checked) {
        CheckBox box = new CheckBox(requireContext());
        box.setText(text);
        box.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        box.setChecked(checked);
        return box;
    }

    private void showQrHelp() {
        new AlertDialog.Builder(requireContext())
                .setTitle("使用提示")
                .setMessage("你可以使用电脑端的「草料二维码」工具，将复杂文本（如 API Key、服务器密码）生成二维码，再用本机的‘扫描填充’功能一键录入，避免手动输入出错。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private LinearLayout.LayoutParams params(int heightDp) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private LinearLayout.LayoutParams topParams(int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = params(heightDp);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams fixedParams(int sizeDp, int marginStartDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.leftMargin = dp(marginStartDp);
        return params;
    }

    private void copyText(String text) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("KeyScan password", text));
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(PasswordEntry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.credential_delete_title)
                .setMessage(R.string.credential_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> repository.delete(entry))
                .show();
    }

    private void showExportFormatDialog() {
        showExportPasswordChoice();
    }

    private void showExportPasswordChoice() {
        new AlertDialog.Builder(requireContext())
                .setTitle("打开密码")
                .setMessage("是否对导出的 Excel 设置打开密码？")
                .setPositiveButton("是", (dialog, which) -> showExportPasswordInput())
                .setNegativeButton("否", (dialog, which) -> exportEntries(""))
                .show();
    }

    private void showExportPasswordInput() {
        EditText input = input("Excel 打开密码");
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("设置导出密码")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("导出", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = input.getText().toString();
            if (password.isEmpty()) {
                input.setError("请输入打开密码");
                return;
            }
            dialog.dismiss();
            exportEntries(password);
        }));
        dialog.show();
    }

    private void exportEntries(String password) {
        repository.getAll(entries -> {
            try {
                File exported = writeExport(entries, password);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "导出成功", Toast.LENGTH_SHORT).show();
                    shareFile(exported, EXCEL_MIME);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private File writeExport(List<PasswordEntry> entries, String password) throws Exception {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KeyScan");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建导出目录");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "keyscan_passwords_" + stamp + ".xlsx");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        List<List<String>> rows = new ArrayList<>();
        for (PasswordEntry entry : entries) {
            rows.add(Arrays.asList(entry.remark, entry.account, entry.password, format.format(new Date(entry.createdAt))));
        }
        byte[] data = ExcelExportHelper.workbookBytes("Passwords", Arrays.asList("网站", "账号", "密码", "创建时间"), rows, password);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
        return file;
    }

    private void shareFile(File file, String mime) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享导出文件"));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

