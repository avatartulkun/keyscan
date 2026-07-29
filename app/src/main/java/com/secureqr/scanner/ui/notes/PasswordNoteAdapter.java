package com.secureqr.scanner.ui.notes;

import android.view.LayoutInflater;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordNote;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PasswordNoteAdapter extends RecyclerView.Adapter<PasswordNoteAdapter.Holder> {
    private static final int VIEW_TYPE_GROUP = 1;
    private static final int VIEW_TYPE_NOTE = 2;

    public interface Listener {
        void onOpen(PasswordNote note);
        void onDelete(PasswordNote note);
    }

    private final Listener listener;
    private final List<PasswordNote> notes = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> collapsedTypes = new HashSet<>();
    private final Set<String> initializedTypes = new HashSet<>();
    private Context appContext;

    public PasswordNoteAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        appContext = parent.getContext().getApplicationContext();
        if (viewType == VIEW_TYPE_GROUP) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password_note_group, parent, false);
            return new Holder(view, true);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password_note, parent, false);
        return new Holder(view, false);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        if (row.isGroup) {
            boolean collapsed = collapsedTypes.contains(row.type);
            holder.groupTitle.setText((collapsed ? "\u25b8 " : "\u25be ") + typeLabel(holder.itemView.getContext(), row.type));
            holder.groupCount.setText(holder.itemView.getContext().getString(R.string.legacy_note_group_count, row.count));
            holder.itemView.setOnClickListener(v -> {
                if (collapsedTypes.contains(row.type)) {
                    collapsedTypes.remove(row.type);
                } else {
                    collapsedTypes.add(row.type);
                }
                rebuildRows();
                notifyDataSetChanged();
            });
            holder.itemView.setOnLongClickListener(null);
            return;
        }
        PasswordNote note = row.note;
        holder.type.setVisibility(View.GONE);
        holder.type.setText(null);
        holder.title.setText(displayTitle(note));
        holder.preview.setText(preview(note));
        holder.time.setText(DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(note.updatedAt > 0 ? note.updatedAt : note.createdAt)));
        holder.itemView.setOnClickListener(v -> listener.onOpen(note));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onDelete(note);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isGroup ? VIEW_TYPE_GROUP : VIEW_TYPE_NOTE;
    }

    public void submit(List<PasswordNote> newNotes) {
        notes.clear();
        if (newNotes != null) {
            notes.addAll(newNotes);
            for (PasswordNote note : newNotes) {
                if (note != null && initializedTypes.add(note.type)) {
                    collapsedTypes.add(note.type);
                }
            }
        }
        rebuildRows();
        notifyDataSetChanged();
    }

    @Nullable
    public PasswordNote getItem(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Row row = rows.get(position);
        return row.isGroup ? null : row.note;
    }

    private void rebuildRows() {
        rows.clear();
        String[] order = {
                PasswordNote.TYPE_BANK_CARD,
                PasswordNote.TYPE_SOFTWARE_LICENSE,
                PasswordNote.TYPE_SERVER,
                PasswordNote.TYPE_SECURE_NOTE,
                PasswordNote.TYPE_IDENTITY,
                PasswordNote.TYPE_CUSTOM,
                PasswordNote.TYPE_LOGIN
        };
        Set<String> emitted = new HashSet<>();
        for (String type : order) {
            addTypeRows(type);
            emitted.add(type);
        }
        for (PasswordNote note : notes) {
            if (note != null && !emitted.contains(note.type)) {
                addTypeRows(note.type);
                emitted.add(note.type);
            }
        }
    }

    private void addTypeRows(String type) {
        List<PasswordNote> group = new ArrayList<>();
        for (PasswordNote note : notes) {
            if (note != null && sameType(type, note.type)) group.add(note);
        }
        if (group.isEmpty()) return;
        rows.add(Row.group(type, group.size()));
        if (!collapsedTypes.contains(type)) {
            for (PasswordNote note : group) rows.add(Row.note(note));
        }
    }

    private boolean sameType(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }


    private String displayTitle(PasswordNote note) {
        Context context = holderContext();
        String title = firstNonEmpty(note == null ? null : note.title,
                note == null ? null : note.primaryText,
                note == null ? null : note.secondaryText,
                context.getString(R.string.legacy_note_unnamed));
        String type = typeLabel(context, note == null ? null : note.type);
        if (title.equals(type)) {
            String fallback = firstNonEmpty(note == null ? null : note.primaryText,
                    note == null ? null : note.secondaryText,
                    context.getString(R.string.legacy_note_unnamed));
            if (!fallback.equals(type)) {
                return fallback;
            }
        }
        return title;
    }

    private String preview(PasswordNote note) {
        if (isSensitive(note.type)) {
            return holderContext().getString(R.string.legacy_note_sensitive_preview);
        }
        String first = empty(note.primaryText) ? "" : note.primaryText;
        String second = empty(note.secondaryText) ? "" : note.secondaryText;
        if (!first.isEmpty() && !second.isEmpty()) return first + "  ·  " + second;
        if (!first.isEmpty()) return first;
        return second;
    }

    private boolean isSensitive(String type) {
        return PasswordNote.TYPE_BANK_CARD.equals(type)
                || PasswordNote.TYPE_SOFTWARE_LICENSE.equals(type)
                || PasswordNote.TYPE_SERVER.equals(type)
                || PasswordNote.TYPE_IDENTITY.equals(type);
    }

    public static String typeLabel(Context context, String type) {
        if (PasswordNote.TYPE_LOGIN.equals(type)) return context.getString(R.string.legacy_note_type_login);
        if (PasswordNote.TYPE_SECURE_NOTE.equals(type)) return context.getString(R.string.legacy_note_type_secure);
        if (PasswordNote.TYPE_BANK_CARD.equals(type)) return context.getString(R.string.legacy_note_type_bank);
        if (PasswordNote.TYPE_SOFTWARE_LICENSE.equals(type)) return context.getString(R.string.legacy_note_type_license);
        if (PasswordNote.TYPE_SERVER.equals(type)) return context.getString(R.string.legacy_note_type_server);
        if (PasswordNote.TYPE_IDENTITY.equals(type)) return context.getString(R.string.legacy_note_type_identity);
        return context.getString(R.string.legacy_note_type_custom);
    }

    private Context holderContext() {
        return appContext;
    }

    private boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView type;
        TextView title;
        TextView preview;
        TextView time;
        TextView groupTitle;
        TextView groupCount;

        Holder(@NonNull View itemView, boolean group) {
            super(itemView);
            if (group) {
                groupTitle = itemView.findViewById(R.id.tv_group_title);
                groupCount = itemView.findViewById(R.id.tv_group_count);
                return;
            }
            type = itemView.findViewById(R.id.tv_note_type);
            title = itemView.findViewById(R.id.tv_note_title);
            preview = itemView.findViewById(R.id.tv_note_preview);
            time = itemView.findViewById(R.id.tv_note_time);
        }
    }

    private static class Row {
        final boolean isGroup;
        final String type;
        final int count;
        final PasswordNote note;

        private Row(boolean isGroup, String type, int count, PasswordNote note) {
            this.isGroup = isGroup;
            this.type = type;
            this.count = count;
            this.note = note;
        }

        static Row group(String type, int count) {
            return new Row(true, type, count, null);
        }

        static Row note(PasswordNote note) {
            return new Row(false, note == null ? null : note.type, 0, note);
        }
    }
}
