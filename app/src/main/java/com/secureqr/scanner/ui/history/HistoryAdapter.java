package com.secureqr.scanner.ui.history;

import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.ScanRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.RecordViewHolder> {
    public interface Listener {
        void onOpen(ScanRecord record);
        void onCopy(ScanRecord record);
        void onStar(ScanRecord record);
        void onEdit(ScanRecord record);
        void onLongPress(ScanRecord record);
        void onSelectionChanged(int count);
    }

    private final List<ScanRecord> records = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final Set<Long> selectedIds = new HashSet<>();
    private boolean maskSensitiveContent;
    private boolean selectionMode;

    public HistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        ScanRecord record = records.get(position);
        holder.title.setText(displayTitle(record));
        holder.time.setText(timeFormat.format(new Date(record.timestamp)));
        holder.typeIcon.setImageResource("URL".equals(record.type) ? R.drawable.ic_link : R.drawable.ic_text);
        holder.source.setText("GENERATE".equals(record.source) ? "生成" : "扫码");
        bindThumbnail(holder, record);
        holder.star.setImageResource(record.isStarred ? R.drawable.ic_star : R.drawable.ic_star_border);
        holder.itemView.setAlpha(isSelected(record) ? 0.55f : 1f);
        String group = groupLabel(record.timestamp);
        boolean showGroup = position == 0 || !group.equals(groupLabel(records.get(position - 1).timestamp));
        holder.group.setVisibility(showGroup ? View.VISIBLE : View.GONE);
        holder.group.setText(group);
        holder.itemView.setOnClickListener(v -> {
            if (selectionMode) toggleSelection(record);
            else listener.onOpen(record);
        });
        holder.copy.setOnClickListener(v -> listener.onCopy(record));
        holder.star.setOnClickListener(v -> listener.onStar(record));
        holder.edit.setOnClickListener(v -> listener.onEdit(record));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onLongPress(record);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public void submit(List<ScanRecord> newRecords) {
        records.clear();
        if (newRecords != null) records.addAll(newRecords);
        selectedIds.retainAll(currentIds());
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    public ScanRecord getItem(int position) {
        return records.get(position);
    }

    public void removeItem(int position) {
        records.remove(position);
        notifyItemRemoved(position);
    }

    public void setMaskSensitiveContent(boolean maskSensitiveContent) {
        this.maskSensitiveContent = maskSensitiveContent;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        if (!selectionMode) selectedIds.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public List<ScanRecord> selectedRecords() {
        List<ScanRecord> selected = new ArrayList<>();
        for (ScanRecord record : records) {
            if (selectedIds.contains(record.id)) selected.add(record);
        }
        return selected;
    }

    private void toggleSelection(ScanRecord record) {
        if (selectedIds.contains(record.id)) selectedIds.remove(record.id);
        else selectedIds.add(record.id);
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    private boolean isSelected(ScanRecord record) {
        return selectedIds.contains(record.id);
    }

    private Set<Long> currentIds() {
        Set<Long> ids = new HashSet<>();
        for (ScanRecord record : records) ids.add(record.id);
        return ids;
    }

    private String displayTitle(ScanRecord record) {
        String title = record.title == null || record.title.isEmpty() ? record.content : record.title;
        if (!maskSensitiveContent) return title;
        if ("URL".equals(record.type)) return title;
        if (record.title != null && !record.title.isEmpty() && !record.title.equals(record.content)) return record.title;
        return "****";
    }

    private void bindThumbnail(RecordViewHolder holder, ScanRecord record) {
        if (record.thumbnailBase64 == null || record.thumbnailBase64.isEmpty()) {
            holder.thumbnail.setVisibility(View.GONE);
            return;
        }
        try {
            byte[] data = Base64.decode(record.thumbnailBase64, Base64.DEFAULT);
            holder.thumbnail.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
            holder.thumbnail.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
            holder.thumbnail.setVisibility(View.GONE);
        }
    }

    private String groupLabel(long timestamp) {
        Calendar item = Calendar.getInstance();
        item.setTimeInMillis(timestamp);
        Calendar today = Calendar.getInstance();
        if (sameDay(item, today)) return "今天";
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(item, today)) return "昨天";
        return dateFormat.format(new Date(timestamp));
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {
        ImageView typeIcon;
        ImageView thumbnail;
        TextView title;
        TextView time;
        TextView group;
        TextView source;
        ImageView star;
        ImageView copy;
        Button edit;

        RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            typeIcon = itemView.findViewById(R.id.iv_type_icon);
            thumbnail = itemView.findViewById(R.id.iv_qr_thumbnail);
            title = itemView.findViewById(R.id.tv_title);
            time = itemView.findViewById(R.id.tv_time);
            group = itemView.findViewById(R.id.tv_group);
            source = itemView.findViewById(R.id.tv_source);
            star = itemView.findViewById(R.id.iv_star);
            copy = itemView.findViewById(R.id.iv_copy);
            edit = itemView.findViewById(R.id.btn_edit_record);
        }
    }
}
