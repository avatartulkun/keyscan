package com.secureqr.scanner.ui.password;

import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.security.PasswordSecurityCheck;
import com.secureqr.scanner.ui.share.SecureShareStateStore;
import com.secureqr.scanner.utils.BrandIconRegistry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PasswordEntryAdapter extends RecyclerView.Adapter<PasswordEntryAdapter.EntryViewHolder> {
    public interface Listener {
        void onCopy(PasswordEntry entry);
        void onDelete(PasswordEntry entry);
        void onEdit(PasswordEntry entry);
        void onGroupMenu(View anchor, PasswordGroup group, int count);
        void onFavorite(PasswordEntry entry, boolean favorite);
        void onEntryMenu(View anchor, PasswordEntry entry);
        void onRisk(PasswordEntry entry);
        void onShare(PasswordEntry entry);
    }

    private static final int VIEW_TYPE_GROUP = 1;
    private static final int VIEW_TYPE_ENTRY = 2;

    private final List<PasswordGroup> groups = new ArrayList<>();
    private final List<PasswordEntry> entries = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> expandedGroupIds = new HashSet<>();
    private final Listener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean showEmptyGroups = true;
    private PasswordSecurityCheck.Result securityResult;

    public PasswordEntryAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GROUP) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password_group, parent, false);
            return new EntryViewHolder(view, true);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password_entry, parent, false);
        return new EntryViewHolder(view, false);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.isGroup) {
            boolean expanded = expandedGroupIds.contains(row.group.id);
            boolean defaultGroup = PasswordGroup.DEFAULT_ID.equals(row.group.id) || row.group.isDefault;
            boolean secureShareGroup = PasswordGroup.SECURE_SHARE_ID.equals(row.group.id);
            holder.groupTitle.setText(defaultGroup
                    ? holder.itemView.getContext().getString(R.string.password_default_group)
                    : secureShareGroup
                    ? holder.itemView.getContext().getString(R.string.secure_share_group_name)
                    : row.group.displayName());
            if (defaultGroup) {
                holder.groupSubtitle.setText(R.string.password_group_default_subtitle);
                holder.groupIcon.setImageResource(R.drawable.ic_password_group_default);
                holder.groupIcon.setBackgroundResource(R.drawable.bg_password_group_default_icon);
                holder.groupIcon.setColorFilter(ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.password_group_default_tint));
            } else if (secureShareGroup) {
                holder.groupSubtitle.setText(R.string.password_group_share_subtitle);
                holder.groupIcon.setImageResource(R.drawable.ic_password_group_share);
                holder.groupIcon.setBackgroundResource(R.drawable.bg_password_group_share_icon);
                holder.groupIcon.setColorFilter(ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.password_group_share_tint));
            } else {
                holder.groupSubtitle.setText(R.string.password_group_custom_subtitle);
                holder.groupIcon.setImageResource(R.drawable.ic_password_group_custom);
                holder.groupIcon.setBackgroundResource(R.drawable.bg_password_group_custom_icon);
                holder.groupIcon.setColorFilter(ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.password_group_custom_tint));
            }
            holder.groupCount.setText(holder.itemView.getResources().getQuantityString(R.plurals.password_items_count, row.count, row.count));
            holder.groupArrow.setImageResource(expanded ? R.drawable.ic_chevron_up_24 : R.drawable.ic_chevron_down_24);
            holder.itemView.setOnClickListener(v -> toggleGroup(row.group.id));
            boolean protectedShareGroup = secureShareGroup;
            holder.groupMore.setVisibility(protectedShareGroup ? View.GONE : View.VISIBLE);
            holder.groupMore.setOnClickListener(protectedShareGroup ? null
                    : v -> listener.onGroupMenu(v, row.group, row.count));
            return;
        }

        PasswordEntry entry = row.entry;
        holder.remark.setText(entry.displayTitle());
        holder.account.setText(entry.displayUsername());
        BrandIconRegistry registry = BrandIconRegistry.get(holder.itemView.getContext());
        BrandIconRegistry.BrandIcon brand = registry.websiteBrand(entry.websiteDomain);
        if (brand == null) brand = registry.issuerBrand(entry.displayTitle());
        if (brand != null) {
            holder.brand.setImageBitmap(brand.bitmap);
            holder.brand.setVisibility(View.VISIBLE);
            holder.avatar.setVisibility(View.GONE);
            holder.avatarContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        } else {
            holder.brand.setImageDrawable(null);
            holder.brand.setVisibility(View.GONE);
            holder.avatar.setVisibility(View.VISIBLE);
        }
        long time = entry.updatedAt > 0 ? entry.updatedAt : entry.createdAt;
        holder.time.setText(time > 0 ? timeFormat.format(new Date(time)) : "");
        holder.sharedBadge.setVisibility(
                SecureShareStateStore.shareCount(holder.itemView.getContext(), entry) > 0
                        ? View.VISIBLE : View.GONE);
        holder.avatar.setText(initialFor(entry.displayTitle()));
        int[] avatarColors = avatarColors(entry.displayTitle());
        holder.avatar.setTextColor(avatarColors[0]);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setColor(avatarColors[1]);
        avatarBg.setShape(GradientDrawable.OVAL);
        if (brand == null) holder.avatarContainer.setBackground(avatarBg);
        holder.more.setOnClickListener(v -> listener.onEntryMenu(v, entry));
        PasswordSecurityCheck.Risk risk = securityResult == null
                ? PasswordSecurityCheck.Risk.NORMAL : securityResult.riskFor(entry.id);
        holder.risk.setVisibility(View.VISIBLE);
        holder.risk.setImageResource(riskDrawable(risk));
        holder.risk.setOnClickListener(v -> listener.onRisk(entry));
        holder.itemView.setOnClickListener(v -> listener.onEdit(entry));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onShare(entry);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isGroup ? VIEW_TYPE_GROUP : VIEW_TYPE_ENTRY;
    }

    public void submit(List<PasswordGroup> newGroups, List<PasswordEntry> newEntries) {
        submit(newGroups, newEntries, true);
    }

    public void submit(List<PasswordGroup> newGroups, List<PasswordEntry> newEntries, boolean showEmptyGroups) {
        groups.clear();
        entries.clear();
        if (newGroups != null) groups.addAll(newGroups);
        if (newEntries != null) entries.addAll(newEntries);
        this.showEmptyGroups = showEmptyGroups;
        rebuildRows();
        notifyDataSetChanged();
    }

    public void setSecurityResult(PasswordSecurityCheck.Result result) {
        securityResult = result;
        notifyDataSetChanged();
    }

    private int riskDrawable(PasswordSecurityCheck.Risk risk) {
        switch (risk) {
            case WEAK: return R.drawable.ic_password_risk_weak;
            case STALE: return R.drawable.ic_password_risk_stale;
            case DUPLICATE: return R.drawable.ic_password_risk_dot;
            case CONFIRMED: return R.drawable.ic_password_risk_confirmed;
            default: return R.drawable.ic_password_risk_safe;
        }
    }

    public PasswordEntry getItem(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Row row = rows.get(position);
        return row.isGroup ? null : row.entry;
    }

    private void rebuildRows() {
        rows.clear();
        List<PasswordGroup> orderedGroups = orderedGroups();
        Map<String, List<PasswordEntry>> groupEntries = new HashMap<>();
        for (PasswordEntry entry : entries) {
            String groupId = firstNonEmpty(entry.groupId, PasswordGroup.DEFAULT_ID);
            groupEntries.computeIfAbsent(groupId, key -> new ArrayList<>()).add(entry);
        }
        for (PasswordGroup group : orderedGroups) {
            List<PasswordEntry> items = groupEntries.get(group.id);
            int count = items == null ? 0 : items.size();
            if (!showEmptyGroups && count == 0) continue;
            rows.add(Row.group(group, count));
            boolean expanded = expandedGroupIds.contains(group.id);
            if (expanded && items != null) {
                for (PasswordEntry entry : items) rows.add(Row.entry(entry));
            }
        }
    }

    private List<PasswordGroup> orderedGroups() {
        List<PasswordGroup> ordered = new ArrayList<>();
        PasswordGroup defaultGroup = null;
        for (PasswordGroup group : groups) {
            if (group == null) continue;
            if (PasswordGroup.DEFAULT_ID.equals(group.id) || group.isDefault) defaultGroup = group;
            else ordered.add(group);
        }
        ordered.sort((left, right) -> {
            int order = Integer.compare(left.sortOrder, right.sortOrder);
            if (order != 0) return order;
            return Long.compare(left.createdAt, right.createdAt);
        });
        if (defaultGroup == null) {
            defaultGroup = new PasswordGroup();
            defaultGroup.id = PasswordGroup.DEFAULT_ID;
            defaultGroup.name = PasswordGroup.DEFAULT_NAME;
            defaultGroup.isDefault = true;
        }
        List<PasswordGroup> result = new ArrayList<>();
        result.add(defaultGroup);
        result.addAll(ordered);
        return result;
    }

    private void toggleGroup(String groupId) {
        if (expandedGroupIds.contains(groupId)) expandedGroupIds.remove(groupId);
        else expandedGroupIds.add(groupId);
        rebuildRows();
        notifyDataSetChanged();
    }

    private String initialFor(String value) {
        if (value == null || value.trim().isEmpty()) return "K";
        String trimmed = value.trim();
        int codePoint = trimmed.codePointAt(0);
        if (Character.isLetterOrDigit(codePoint)) {
            return new String(Character.toChars(Character.toUpperCase(codePoint)));
        }
        return "K";
    }

    private int[] avatarColors(String value) {
        int seed = Math.abs((value == null ? "" : value).hashCode());
        switch (seed % 6) {
            case 0: return new int[]{0xFF2563EB, 0xFFEAF2FF};
            case 1: return new int[]{0xFF059669, 0xFFE7F8EF};
            case 2: return new int[]{0xFF7C3AED, 0xFFF1E8FF};
            case 3: return new int[]{0xFFEA580C, 0xFFFFEEE5};
            case 4: return new int[]{0xFF0891B2, 0xFFE6F8FC};
            default: return new int[]{0xFF475569, 0xFFF1F5F9};
        }
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        TextView avatar;
        ImageView brand;
        View avatarContainer;
        TextView remark;
        TextView account;
        TextView time;
        TextView sharedBadge;
        TextView groupTitle;
        TextView groupSubtitle;
        TextView groupCount;
        ImageView groupIcon;
        ImageView groupArrow;
        ImageButton groupMore;
        ImageButton risk;
        ImageButton more;

        EntryViewHolder(@NonNull View itemView, boolean group) {
            super(itemView);
            if (group) {
                groupTitle = itemView.findViewById(R.id.tv_password_group_title);
                groupSubtitle = itemView.findViewById(R.id.tv_password_group_subtitle);
                groupCount = itemView.findViewById(R.id.tv_password_group_count);
                groupIcon = itemView.findViewById(R.id.iv_password_group_icon);
                groupArrow = itemView.findViewById(R.id.tv_password_group_arrow);
                groupMore = itemView.findViewById(R.id.btn_password_group_more);
                return;
            }
            avatar = itemView.findViewById(R.id.tv_password_avatar);
            brand = itemView.findViewById(R.id.iv_password_brand);
            avatarContainer = itemView.findViewById(R.id.layout_password_brand);
            remark = itemView.findViewById(R.id.tv_password_remark);
            account = itemView.findViewById(R.id.tv_password_account);
            time = itemView.findViewById(R.id.tv_password_time);
            sharedBadge = itemView.findViewById(R.id.tv_password_shared_badge);
            risk = itemView.findViewById(R.id.btn_password_risk);
            more = itemView.findViewById(R.id.btn_more_password);
        }
    }

    private static class Row {
        final boolean isGroup;
        final PasswordGroup group;
        final int count;
        final PasswordEntry entry;

        private Row(boolean isGroup, PasswordGroup group, int count, PasswordEntry entry) {
            this.isGroup = isGroup;
            this.group = group;
            this.count = count;
            this.entry = entry;
        }

        static Row group(PasswordGroup group, int count) {
            return new Row(true, group, count, null);
        }

        static Row entry(PasswordEntry entry) {
            return new Row(false, null, 0, entry);
        }
    }
}
