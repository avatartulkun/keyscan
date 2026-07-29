package com.secureqr.scanner.ui.password;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
        void onReveal(PasswordGenerationRecord record, Runnable reveal);
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
        if (PasswordGenerationRecord.SOURCE_REGISTRATION_AUTOFILL.equals(record.source)) {
            String site = record.website == null || record.website.trim().isEmpty() ? holder.itemView.getContext().getString(R.string.password_generation_unknown_site) : record.website;
            String account = record.account == null || record.account.trim().isEmpty() ? holder.itemView.getContext().getString(R.string.password_generation_account_missing) : record.account;
            holder.meta.setText(site + " · " + account + " · " + format.format(new Date(record.createdAt)));
            holder.remark.setText(record.linkedPasswordEntryId == null ? R.string.password_generation_unsaved : R.string.password_generation_saved);
        } else {
            holder.meta.setText(format.format(new Date(record.createdAt)) + " · " + record.length + " "
                    + holder.itemView.getContext().getString(R.string.unit_digits) + " · " + record.configSummary);
            holder.remark.setText(record.remark == null || record.remark.trim().isEmpty()
                    ? holder.itemView.getContext().getString(R.string.add_remark) : record.remark);
        }
        holder.toggle.setImageResource(visible ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24);
        holder.toggle.setContentDescription(holder.itemView.getContext().getString(
                visible ? R.string.credential_hide_password_desc : R.string.credential_show_password_desc));
        holder.toggle.setOnClickListener(v -> {
            int bindingPosition = holder.getBindingAdapterPosition();
            if (bindingPosition == RecyclerView.NO_POSITION) return;
            if (visibleIds.contains(record.id)){visibleIds.remove(record.id);notifyItemChanged(bindingPosition);}
            else listener.onReveal(record,()->{visibleIds.add(record.id);notifyItemChanged(bindingPosition);});
        });
        holder.copy.setOnClickListener(v -> listener.onCopy(record.password));
        holder.itemView.setOnClickListener(v -> listener.onQr(record.password));
        holder.edit.setOnClickListener(v -> listener.onEdit(record));
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
        for (int i = 0; i < length; i++) builder.append('\u2022');
        return builder.toString();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView password;
        TextView meta;
        TextView remark;
        ImageButton toggle;
        ImageButton copy;
        ImageButton edit;

        Holder(@NonNull View itemView) {
            super(itemView);
            password = itemView.findViewById(R.id.tv_history_password);
            meta = itemView.findViewById(R.id.tv_history_meta);
            remark = itemView.findViewById(R.id.tv_history_remark);
            toggle = itemView.findViewById(R.id.btn_history_toggle);
            copy = itemView.findViewById(R.id.btn_history_copy);
            edit = itemView.findViewById(R.id.btn_history_edit);
        }
    }
}
