package com.secureqr.scanner.ui.history;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.secureqr.scanner.R;
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.ExportSecurityGuard;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.GlobalWebDavSyncUi;
import com.secureqr.scanner.utils.ExcelExportHelper;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.utils.QRGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {
    private RecordRepository repository;
    private HistoryAdapter adapter;
    private LiveData<List<ScanRecord>> currentLiveData;
    private Button batchSelectButton;
    private Button deleteSelectedButton;
    private String query = "";
    private String filter = "ALL";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = RecordRepository.getInstance(requireContext());
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        SearchView searchView = view.findViewById(R.id.search_view);
        Switch maskSwitch = view.findViewById(R.id.sw_mask_sensitive);
        Chip chipAll = view.findViewById(R.id.chip_all);
        Chip chipUrl = view.findViewById(R.id.chip_url);
        Chip chipText = view.findViewById(R.id.chip_text);
        batchSelectButton = view.findViewById(R.id.btn_batch_select);
        deleteSelectedButton = view.findViewById(R.id.btn_delete_selected);
        view.findViewById(R.id.btn_history_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        view.findViewById(R.id.btn_history_menu).setOnClickListener(this::showHistoryMenu);

        adapter = new HistoryAdapter(new HistoryAdapter.Listener() {
            @Override
            public void onOpen(ScanRecord record) {
                openRecord(record);
            }

            @Override
            public void onCopy(ScanRecord record) {
                copyRecord(record);
            }

            @Override
            public void onStar(ScanRecord record) {
                OperationModeGuard.requireEdit(HistoryFragment.this, () -> {
                    record.isStarred = !record.isStarred;
                    repository.update(record);
                });
            }

            @Override
            public void onEdit(ScanRecord record) {
                OperationModeGuard.requireEdit(HistoryFragment.this, () -> showEditDialog(record));
            }

            @Override
            public void onLongPress(ScanRecord record) {
                showRecordActions(record);
            }

            @Override
            public void onSelectionChanged(int count) {
                deleteSelectedButton.setText(count > 0 ? getString(R.string.delete) + "(" + count + ")" : getString(R.string.delete_selected));
                deleteSelectedButton.setEnabled(count > 0);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        attachSwipe(recyclerView);
        tintSearchView(searchView);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String text) {
                query = text;
                observe();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                query = text;
                observe();
                return true;
            }
        });

        chipAll.setOnClickListener(v -> setFilter("ALL"));
        chipUrl.setOnClickListener(v -> setFilter("URL"));
        chipText.setOnClickListener(v -> setFilter("TEXT"));
        maskSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.setMaskSensitiveContent(isChecked));
        batchSelectButton.setOnClickListener(v -> {
            boolean enabled = !adapter.isSelectionMode();
            adapter.setSelectionMode(enabled);
            batchSelectButton.setText(enabled ? R.string.history_cancel_selection : R.string.history_batch_mode);
            deleteSelectedButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        });
        deleteSelectedButton.setOnClickListener(v ->
                OperationModeGuard.requireEdit(this, this::confirmDeleteSelected));
        observe();
    }

    private void showHistorySettings() {
        SharedPreferences prefs = requireContext().getSharedPreferences("secureqr_settings", Context.MODE_PRIVATE);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);

        List<String> limitValues = Arrays.asList(
                getString(R.string.option_100_items),
                getString(R.string.option_500_items),
                getString(R.string.option_1000_items),
                getString(R.string.option_unlimited));
        Spinner limitSpinner = historySettingSpinner(limitValues,
                prefs.getString("setting_history_limit", getString(R.string.option_500_items)));
        addHistorySetting(content, R.string.history_limit, limitSpinner);

        List<String> cleanupValues = Arrays.asList(
                getString(R.string.option_1_month),
                getString(R.string.option_3_months),
                getString(R.string.option_6_months),
                getString(R.string.option_forever));
        Spinner cleanupSpinner = historySettingSpinner(cleanupValues,
                prefs.getString("setting_history_cleanup", getString(R.string.option_forever)));
        addHistorySetting(content, R.string.auto_cleanup_time, cleanupSpinner);

        Switch autoSave = new Switch(requireContext());
        autoSave.setText(R.string.qr_history_auto_save);
        autoSave.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        autoSave.setChecked(prefs.getBoolean("setting_qr_history", true));
        autoSave.setMinHeight(dp(52));
        content.addView(autoSave, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.history_settings)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> prefs.edit()
                        .putString("setting_history_limit", limitSpinner.getSelectedItem().toString())
                        .putString("setting_history_cleanup", cleanupSpinner.getSelectedItem().toString())
                        .putBoolean("setting_qr_history", autoSave.isChecked())
                        .apply())
                .show();
    }

    private Spinner historySettingSpinner(List<String> values, String selected) {
        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, values.indexOf(selected)));
        spinner.setBackgroundResource(R.drawable.bg_settings_status);
        return spinner;
    }

    private void addHistorySetting(LinearLayout content, int labelRes, Spinner spinner) {
        TextView label = new TextView(requireContext());
        label.setText(labelRes);
        label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        label.setTextSize(14);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setPadding(0, dp(10), 0, dp(6));
        content.addView(label);
        content.addView(spinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private void showHistoryMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, getString(R.string.history_export_records));
        menu.getMenu().add(0, 2, 1, getString(R.string.history_settings));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) ExportSecurityGuard.require(requireActivity(), getString(R.string.export_auth_prompt), this::exportHistory);
            else if (item.getItemId() == 2) showHistorySettings();
            return true;
        });
        menu.show();
    }

    private void exportHistory() {
        repository.getAll(records -> {
            try {
                File file = writeHistoryExport(records);
                FragmentUi.run(this, () -> {
                    Toast.makeText(requireContext(), R.string.history_export_success, Toast.LENGTH_SHORT).show();
                    shareHistoryExport(file);
                });
            } catch (Exception e) {
                FragmentUi.run(this, () -> Toast.makeText(requireContext(), getString(R.string.history_export_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private File writeHistoryExport(List<ScanRecord> records) throws Exception {
        File dir = new File(requireContext().getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException(getString(R.string.history_export_directory_failed));
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "keyscan_scan_generate_history_" + stamp + ".xlsx");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        List<List<String>> rows = new ArrayList<>();
        for (ScanRecord record : records) {
            rows.add(Arrays.asList(
                    "GENERATE".equals(record.source) ? getString(R.string.history_source_generated) : getString(R.string.history_source_scanned),
                    record.type == null ? "" : record.type,
                    record.title == null ? "" : record.title,
                    record.content == null ? "" : record.content,
                    record.isStarred ? getString(R.string.yes) : getString(R.string.no),
                    dateFormat.format(new Date(record.timestamp))
            ));
        }
        byte[] data = ExcelExportHelper.workbookBytes(
                "History",
                Arrays.asList(getString(R.string.history_column_source), getString(R.string.history_column_type),
                        getString(R.string.history_column_title), getString(R.string.history_column_content),
                        getString(R.string.history_column_starred), getString(R.string.history_column_time)),
                rows,
                ""
        );
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
        return file;
    }

    private void shareHistoryExport(File file) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.history_share_export)));
    }

    private void setFilter(String value) {
        filter = value;
        observe();
    }

    private void observe() {
        if (repository == null) return;
        if (currentLiveData != null) currentLiveData.removeObservers(getViewLifecycleOwner());
        currentLiveData = repository.observeRecords(query, filter);
        currentLiveData.observe(getViewLifecycleOwner(), records -> adapter.submit(records));
    }

    private void attachSwipe(RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= adapter.getItemCount()) return;
                ScanRecord record = adapter.getItem(pos);
                adapter.notifyItemChanged(pos);
                OperationModeGuard.requireEdit(HistoryFragment.this, () -> confirmDeleteRecord(record));
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    private void openRecord(ScanRecord record) {
        if (record == null) return;
        String content = record.content == null ? "" : record.content;
        if ("GENERATE".equals(record.source)) {
            showGeneratedPreview(record);
            return;
        }
        if ("URL".equals(record.type)) {
            try {
                Uri uri = Uri.parse(content);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(requireContext(), R.string.scanner_no_browser, Toast.LENGTH_SHORT).show();
                }
            } catch (RuntimeException e) {
                Toast.makeText(requireContext(), R.string.scanner_no_browser, Toast.LENGTH_SHORT).show();
            }
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle(record.type == null || record.type.trim().isEmpty() ? getString(R.string.text) : record.type)
                    .setMessage(content)
                    .setPositiveButton(R.string.copy, (dialog, which) -> copyRecord(record))
                    .setNegativeButton(R.string.close, null)
                    .show();
        }
    }

    private void showGeneratedPreview(ScanRecord record) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), 0);
        ImageView image = new ImageView(requireContext());
        String contentValue = record.content == null ? "" : record.content;
        Bitmap bitmap = QRGenerator.generateQR(contentValue, dp(220));
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        TextView text = new TextView(requireContext());
        text.setText(contentValue + "\n\n" + getString(R.string.history_generated_time, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(record.timestamp))));
        text.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        text.setTextIsSelectable(true);
        content.addView(image, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));
        content.addView(text);
        new AlertDialog.Builder(requireContext())
                .setTitle(record.title == null || record.title.isEmpty() ? getString(R.string.history_generated_qr_title) : record.title)
                .setView(content)
                .setPositiveButton(R.string.history_generated_copy_content, (dialog, which) -> copyRecord(record))
                .setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.history_generated_regenerate, (dialog, which) -> showGeneratedPreview(record))
                .show();
    }

    private void copyRecord(ScanRecord record) {
        if (record == null) return;
        SecureClipboard.copySensitive(requireContext(), "KeyScan", record.content == null ? "" : record.content);
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmDeleteRecord(ScanRecord record) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.history_delete_title)
                .setMessage(R.string.history_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    repository.delete(record);
                    Toast.makeText(requireContext(), R.string.history_delete_selected_success, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void confirmDeleteSelected() {
        List<ScanRecord> selected = adapter.selectedRecords();
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.history_select_prompt, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.history_batch_delete_title)
                .setMessage(getString(R.string.history_batch_delete_message, selected.size()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    for (ScanRecord record : selected) repository.delete(record);
                    adapter.setSelectionMode(false);
                    batchSelectButton.setText(R.string.history_batch_mode);
                    deleteSelectedButton.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), getString(R.string.history_delete_selected_count_success, selected.size()), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showRecordActions(ScanRecord record) {
        new AlertDialog.Builder(requireContext())
                .setItems(new String[]{getString(R.string.action_edit)}, (dialog, which) -> showEditDialog(record))
                .show();
    }

    private void showEditDialog(ScanRecord record) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText titleInput = createInput(getString(R.string.history_note_hint));
        EditText contentInput = createInput(getString(R.string.history_content_hint));
        titleInput.setText("");
        contentInput.setText(record.content == null ? "" : record.content);
        content.addView(titleInput);
        content.addView(contentInput);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.history_edit_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String contentValue = contentInput.getText().toString().trim();
            if (contentValue.isEmpty()) {
                contentInput.setError(getString(R.string.history_content_empty_error));
                return;
            }
            record.title = titleInput.getText().toString().trim();
            record.content = contentValue;
            record.type = ScanRecord.detectType(contentValue);
            repository.update(record);
            dialog.dismiss();
            Toast.makeText(requireContext(), R.string.history_update_success, Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private EditText createInput(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        input.setHintTextColor(0xFF80868B);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void tintSearchView(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            textView.setHintTextColor(0xFF80868B);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintSearchView(group.getChildAt(i));
            }
        }
    }
}

