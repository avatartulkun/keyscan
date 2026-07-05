package com.secureqr.scanner.ui.history;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.QRGenerator;

import java.util.List;

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
                record.isStarred = !record.isStarred;
                repository.update(record);
            }

            @Override
            public void onEdit(ScanRecord record) {
                showEditDialog(record);
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
        deleteSelectedButton.setOnClickListener(v -> confirmDeleteSelected());
        observe();
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
                ScanRecord record = adapter.getItem(pos);
                adapter.notifyItemChanged(pos);
                confirmDeleteRecord(record);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    private void openRecord(ScanRecord record) {
        if ("GENERATE".equals(record.source)) {
            showGeneratedPreview(record);
            return;
        }
        if ("URL".equals(record.type)) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(record.content)));
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle(record.type)
                    .setMessage(record.content)
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
        Bitmap bitmap = QRGenerator.generateQR(record.content, dp(220));
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        TextView text = new TextView(requireContext());
        text.setText(record.content + "\n\n" + getString(R.string.history_generated_time, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(record.timestamp))));
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
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("KeyScan", record.content));
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
        titleInput.setText(record.title == null ? "" : record.title);
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

