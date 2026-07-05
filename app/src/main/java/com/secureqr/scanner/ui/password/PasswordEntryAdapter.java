package com.secureqr.scanner.ui.password;

import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PasswordEntryAdapter extends RecyclerView.Adapter<PasswordEntryAdapter.EntryViewHolder> {
    public interface Listener {
        void onCopy(PasswordEntry entry);
        void onDelete(PasswordEntry entry);
        void onEdit(PasswordEntry entry);
    }

    private final List<PasswordEntry> entries = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());
    private final Handler handler = new Handler(Looper.getMainLooper());

    public PasswordEntryAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password_entry, parent, false);
        return new EntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        PasswordEntry entry = entries.get(position);
        holder.remark.setText(entry.remark);
        holder.account.setText(entry.account);
        holder.time.setText(timeFormat.format(new Date(entry.createdAt)));
        holder.avatar.setText(initialFor(entry.remark));
        holder.copy.setText("复");
        holder.copy.setBackgroundTintList(null);
        holder.copy.setOnClickListener(v -> {
            listener.onCopy(entry);
            holder.copy.setText("✓");
            holder.copy.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary));
            holder.copy.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.card_background)));
            handler.postDelayed(() -> {
                holder.copy.setText("复");
                holder.copy.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_main));
                holder.copy.setBackgroundTintList(null);
            }, 1500);
        });
        holder.delete.setOnClickListener(v -> listener.onDelete(entry));
        holder.itemView.setOnClickListener(v -> listener.onEdit(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void submit(List<PasswordEntry> newEntries) {
        entries.clear();
        if (newEntries != null) {
            entries.addAll(newEntries);
        }
        notifyDataSetChanged();
    }

    public PasswordEntry getItem(int position) {
        return entries.get(position);
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        TextView avatar;
        TextView remark;
        TextView account;
        TextView time;
        Button copy;
        Button delete;

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tv_password_avatar);
            remark = itemView.findViewById(R.id.tv_password_remark);
            account = itemView.findViewById(R.id.tv_password_account);
            time = itemView.findViewById(R.id.tv_password_time);
            copy = itemView.findViewById(R.id.btn_copy_password);
            delete = itemView.findViewById(R.id.btn_delete_password);
        }
    }

    private String initialFor(String value) {
        if (value == null || value.trim().isEmpty()) return "🌐";
        String trimmed = value.trim();
        int codePoint = trimmed.codePointAt(0);
        if (Character.isLetterOrDigit(codePoint)) {
            return new String(Character.toChars(Character.toUpperCase(codePoint)));
        }
        return "🌐";
    }
}

