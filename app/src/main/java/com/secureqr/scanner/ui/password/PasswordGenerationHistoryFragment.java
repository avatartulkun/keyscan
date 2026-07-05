package com.secureqr.scanner.ui.password;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;
import com.secureqr.scanner.data.repository.PasswordGenerationRepository;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.QRGenerator;

public class PasswordGenerationHistoryFragment extends Fragment {
    private PasswordGenerationRepository historyRepository;
    private LiveData<java.util.List<PasswordGenerationRecord>> historySource;
    private PasswordGenerationHistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_password_generation_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        historyRepository = PasswordGenerationRepository.getInstance(requireContext());
        view.findViewById(R.id.btn_back_generation_history).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());
        view.findViewById(R.id.btn_generation_history_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        RecyclerView historyList = view.findViewById(R.id.recycler_generation_history);
        historyList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PasswordGenerationHistoryAdapter(new PasswordGenerationHistoryAdapter.Listener() {
            @Override public void onCopy(String password) { copyText(password); }
            @Override public void onQr(String password) { showQrDialog(password); }
            @Override public void onEdit(PasswordGenerationRecord record) { showEditHistoryDialog(record); }
            @Override public void onDelete(PasswordGenerationRecord record) { confirmDeleteHistory(record); }
        });
        historyList.setAdapter(adapter);
        attachSwipeDelete(historyList, adapter);
        SearchView search = view.findViewById(R.id.search_generation_history);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                bindHistory(query);
                search.clearFocus();
                return true;
            }

            @Override public boolean onQueryTextChange(String newText) {
                bindHistory(newText);
                return true;
            }
        });
        bindHistory("");
    }

    private void bindHistory(String query) {
        if (historySource != null) historySource.removeObservers(getViewLifecycleOwner());
        historySource = historyRepository.observeRecent(query);
        historySource.observe(getViewLifecycleOwner(), adapter::submit);
    }

    private void attachSwipeDelete(RecyclerView list, PasswordGenerationHistoryAdapter adapter) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                PasswordGenerationRecord record = adapter.getItem(position);
                adapter.notifyItemChanged(position);
                confirmDeleteHistory(record);
            }
        }).attachToRecyclerView(list);
    }

    private void copyText(String text) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("KeyScan random password", text));
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void showEditHistoryDialog(PasswordGenerationRecord record) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 8, 24, 0);
        EditText input = new EditText(requireContext());
        input.setText(record.password == null ? "" : record.password);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(getResources().getColor(R.color.text_main));
        EditText remarkInput = new EditText(requireContext());
        remarkInput.setText(record.remark == null ? "" : record.remark);
        remarkInput.setSingleLine(true);
        remarkInput.setHint(R.string.remark_max_100);
        remarkInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        remarkInput.setBackgroundResource(R.drawable.bg_edit_text);
        remarkInput.setTextColor(getResources().getColor(R.color.text_main));
        remarkInput.setHintTextColor(getResources().getColor(R.color.text_hint));
        LinearLayout.LayoutParams remarkParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        remarkParams.topMargin = 16;
        content.addView(input);
        content.addView(remarkInput, remarkParams);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_generation_history)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString();
            if (value.trim().isEmpty()) {
                input.setError(getString(R.string.password_required));
                return;
            }
            record.password = value;
            record.remark = remarkInput.getText().toString().trim();
            record.length = value.length();
            historyRepository.update(record);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void confirmDeleteHistory(PasswordGenerationRecord record) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_generation_history)
                .setMessage(R.string.delete_item_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> historyRepository.delete(record))
                .show();
    }

    private void showQrDialog(String text) {
        Bitmap bitmap = QRGenerator.generateQR(text, 520);
        if (bitmap == null) {
            Toast.makeText(requireContext(), R.string.qr_generation_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        ImageView image = new ImageView(requireContext());
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setPadding(24, 24, 24, 24);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_qr)
                .setView(image)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
