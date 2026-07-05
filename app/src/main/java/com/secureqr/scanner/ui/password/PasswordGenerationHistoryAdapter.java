package com.secureqr.scanner.ui.password;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PasswordGenerationHistoryAdapter extends RecyclerView.Adapter<PasswordGenerationHistoryAdapter.Holder> {
    public interface Listener {
        void onCopy(String password);
        void onQr(String password);
        void onEdit(PasswordGenerationRecord record);
        void onDelete(PasswordGenerationRecord record);
    }

    private final List<PasswordGenerationRecord> records = new ArrayList<>();
    private final List<Long> visibleIds = new ArrayList<>();
    private final SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private final Listener listener;

    public PasswordGenerationHistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password_generation_record, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PasswordGenerationRecord record = records.get(position);
        boolean visible = visibleIds.contains(record.id);
        holder.password.setText(visible ? record.password : mask(record.password == null ? 0 : record.password.length()));
        holder.meta.setText(format.format(new Date(record.createdAt)) + " · " + record.length + " " + holder.itemView.getContext().getString(R.string.unit_digits) + " · " + record.configSummary);
        holder.remark.setText(record.remark == null || record.remark.trim().isEmpty() ? holder.itemView.getContext().getString(R.string.add_remark) : record.remark);
        holder.toggle.setText(visible ? R.string.hidden_short : R.string.show_short);
        holder.toggle.setOnClickListener(v -> {
            if (visibleIds.contains(record.id)) visibleIds.remove(record.id);
            else visibleIds.add(record.id);
            notifyItemChanged(position);
        });
        holder.copy.setOnClickListener(v -> listener.onCopy(record.password));
        holder.qr.setOnClickListener(v -> listener.onQr(record.password));
        holder.edit.setOnClickListener(v -> listener.onEdit(record));
        holder.delete.setOnClickListener(v -> listener.onDelete(record));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public void submit(List<PasswordGenerationRecord> newRecords) {
        records.clear();
        if (newRecords != null) records.addAll(newRecords);
        notifyDataSetChanged();
    }

    public PasswordGenerationRecord getItem(int position) {
        return records.get(position);
    }

    private String mask(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) builder.append("•");
        return builder.toString();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView password;
        TextView meta;
        TextView remark;
        Button toggle;
        Button copy;
        Button qr;
        Button edit;
        Button delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            password = itemView.findViewById(R.id.tv_history_password);
            meta = itemView.findViewById(R.id.tv_history_meta);
            remark = itemView.findViewById(R.id.tv_history_remark);
            toggle = itemView.findViewById(R.id.btn_history_toggle);
            copy = itemView.findViewById(R.id.btn_history_copy);
            qr = itemView.findViewById(R.id.btn_history_qr);
            edit = itemView.findViewById(R.id.btn_history_edit);
            delete = itemView.findViewById(R.id.btn_history_delete);
        }
    }
}

