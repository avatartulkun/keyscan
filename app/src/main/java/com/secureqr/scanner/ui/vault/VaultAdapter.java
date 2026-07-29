package com.secureqr.scanner.ui.vault;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.vault.VaultTypes;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class VaultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onOpenCategory(VaultTypes.Category category);
        void onOpenItem(VaultItem item);
        void onReorderCategories(List<VaultTypes.Category> categories);
        void onFavoriteChanged(VaultItem item, boolean favorite);
    }

    private static final int SECTION = 0;
    private static final int CATEGORY = 1;
    private static final int RECORD = 2;
    private static final int MORE = 3;
    private static final int PREVIEW_LIMIT = 5;

    private static final String SECTION_FAVORITES = "favorites";
    private static final String SECTION_RECENT = "recent";

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private List<VaultTypes.Category> categories = new ArrayList<>();
    private List<VaultItem> allItems = new ArrayList<>();
    private Map<String, List<String>> attachmentNames = new HashMap<>();
    private String query = "";
    private boolean stateLoaded;
    private boolean favoritesExpanded = true;
    private boolean recentExpanded = true;
    private boolean showAllFavorites;
    private boolean showAllRecent;

    public VaultAdapter(Listener listener) {
        this.listener = listener;
    }

    private static String s(int resId, Object... args) {
        return args.length == 0 ? VaultHost.context.getString(resId) : VaultHost.context.getString(resId, args);
    }

    public void submit(List<VaultItem> items, Map<String, List<String>> attachments, String queryText) {
        allItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        attachmentNames = attachments == null ? new HashMap<>() : attachments;
        query = queryText == null ? "" : queryText.trim();
        loadStateIfNeeded();
        rebuild();
    }

    public boolean canMove(int position) {
        return position >= 0 && position < rows.size() && rows.get(position).category != null && query.isEmpty();
    }

    public boolean moveCategory(int from, int to) {
        if (!canMove(from) || !canMove(to)) return false;
        Row fromRow = rows.get(from);
        Row toRow = rows.get(to);
        int fromCategory = categories.indexOf(fromRow.category);
        int toCategory = categories.indexOf(toRow.category);
        if (fromCategory < 0 || toCategory < 0) return false;
        Collections.swap(categories, fromCategory, toCategory);
        listener.onReorderCategories(categories);
        rebuild();
        return true;
    }

    private void loadStateIfNeeded() {
        if (stateLoaded || VaultHost.context == null) return;
        favoritesExpanded = VaultUiState.isSectionExpanded(VaultHost.context, SECTION_FAVORITES, true);
        recentExpanded = VaultUiState.isSectionExpanded(VaultHost.context, SECTION_RECENT, true);
        stateLoaded = true;
    }

    private void rebuild() {
        rows.clear();
        if (!query.isEmpty()) {
            List<SearchHit> hits = searchHits();
            rows.add(Row.section(null, s(R.string.vault_list_search_results), s(R.string.vault_list_item_count, hits.size()), false, true));
            for (SearchHit hit : hits) rows.add(Row.record(hit.item, hit.match));
            notifyDataSetChanged();
            return;
        }

        rows.add(Row.section(null, s(R.string.vault_list_categories), s(R.string.vault_list_reorder_hint), false, true));
        categories = VaultUiState.sortedCategories(VaultHost.context);
        for (VaultTypes.Category category : categories) rows.add(Row.category(category));
        notifyDataSetChanged();
    }

    private void addPreviewRows(List<VaultItem> items, String sectionKey, boolean showAll) {
        int count = showAll ? items.size() : Math.min(PREVIEW_LIMIT, items.size());
        for (int i = 0; i < count; i++) rows.add(Row.record(items.get(i), typeName(items.get(i))));
        if (items.size() > PREVIEW_LIMIT) {
            rows.add(Row.more(sectionKey, s(showAll ? R.string.vault_list_show_less : R.string.vault_list_show_more)));
        }
    }

    private String sectionCount(int count) {
        if (count == 0) return s(R.string.vault_list_none);
        return s(R.string.vault_list_item_count, count);
    }

    private List<VaultItem> byIds(List<String> ids, Set<String> excludedIds, int limit) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<VaultItem> result = new ArrayList<>();
        for (String id : ids) {
            if (id == null || seen.contains(id)) continue;
            if (excludedIds != null && excludedIds.contains(id)) continue;
            seen.add(id);
            for (VaultItem item : allItems) {
                if (id.equals(item.id)) {
                    result.add(item);
                    break;
                }
            }
            if (result.size() >= limit) break;
        }
        return result;
    }

    private List<SearchHit> searchHits() {
        ArrayList<SearchHit> result = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (VaultItem item : allItems) {
            String match = match(item, q);
            if (match != null) result.add(new SearchHit(item, match));
        }
        return result;
    }

    private String match(VaultItem item, String q) {
        if (item.title != null && item.title.toLowerCase(Locale.ROOT).contains(q)) return s(R.string.vault_match_name);
        VaultTypes.Type type = VaultTypes.resolveStored(item.type, item.fieldsJson);
        if (VaultHost.context.getString(type.labelRes).toLowerCase(Locale.ROOT).contains(q)) return s(R.string.vault_match_type);
        if (item.notes != null && item.notes.toLowerCase(Locale.ROOT).contains(q)) return s(R.string.vault_match_notes);
        try {
            JSONObject object = new JSONObject(item.fieldsJson == null ? "{}" : item.fieldsJson);
            String label = object.optString("label", "");
            if (label.toLowerCase(Locale.ROOT).contains(q)) return s(R.string.vault_match_label);
            String provider = object.optString("provider", object.optString("service", ""));
            if (provider.toLowerCase(Locale.ROOT).contains(q)) return s(R.string.vault_match_provider);
        } catch (Exception ignored) {}
        List<String> names = attachmentNames.get(item.id);
        if (names != null) {
            for (String name : names) {
                if (name != null && name.toLowerCase(Locale.ROOT).contains(q)) return s(R.string.vault_match_attachment);
            }
        }
        return null;
    }

    private int count(String categoryKey) {
        int n = 0;
        for (VaultItem item : allItems) if (categoryKey.equals(item.category)) n++;
        return n;
    }

    private String typeName(VaultItem item) {
        VaultTypes.Type type = VaultTypes.resolveStored(item.type, item.fieldsJson);
        return VaultHost.context.getString(type.labelRes);
    }

    private int icon(String key) {
        if (VaultTypes.KEYS.equals(key)) return R.drawable.ic_key_line;
        if (VaultTypes.IDENTITY.equals(key)) return R.drawable.ic_vault_identity;
        if (VaultTypes.FINANCIAL.equals(key)) return R.drawable.ic_vault_wallet;
        if (VaultTypes.CONTACT.equals(key)) return R.drawable.ic_vault_mail;
        if (VaultTypes.FILES.equals(key)) return R.drawable.ic_vault_file_lock;
        return R.drawable.ic_vault_gear;
    }

    private int color(String key) {
        if (VaultTypes.KEYS.equals(key)) return R.color.vault_icon_blue;
        if (VaultTypes.IDENTITY.equals(key)) return R.color.vault_icon_green;
        if (VaultTypes.FINANCIAL.equals(key)) return R.color.vault_icon_orange;
        if (VaultTypes.CONTACT.equals(key)) return R.color.vault_icon_purple;
        if (VaultTypes.FILES.equals(key)) return R.color.vault_icon_cyan;
        return R.color.vault_icon_yellow;
    }

    private String summary(String key) {
        if (VaultTypes.KEYS.equals(key)) return s(R.string.vault_category_keys_summary);
        if (VaultTypes.IDENTITY.equals(key)) return s(R.string.vault_category_identity_summary);
        if (VaultTypes.FINANCIAL.equals(key)) return s(R.string.vault_category_financial_summary);
        if (VaultTypes.CONTACT.equals(key)) return s(R.string.vault_category_contact_summary);
        if (VaultTypes.FILES.equals(key)) return s(R.string.vault_category_files_summary);
        return s(R.string.vault_category_custom_summary);
    }

    @Override public int getItemViewType(int position) {
        Row row = rows.get(position);
        if (row.moreText != null) return MORE;
        if (row.sectionTitle != null) return SECTION;
        if (row.category != null) return CATEGORY;
        return RECORD;
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SECTION) return new SectionHolder(sectionView(parent));
        if (viewType == CATEGORY) return new CategoryHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vault_category, parent, false));
        if (viewType == MORE) return new MoreHolder(moreView(parent));
        return new RecordHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vault_record, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder raw, int position) {
        Row row = rows.get(position);
        if (raw instanceof SectionHolder) {
            SectionHolder holder = (SectionHolder) raw;
            holder.title.setText(row.sectionTitle);
            holder.subtitle.setText(row.sectionSubtitle);
            holder.icon.setText(row.expanded ? "⌃" : "⌄");
            holder.icon.setVisibility(row.collapsible ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(row.collapsible ? v -> toggleSection(row.sectionKey) : null);
            return;
        }
        if (raw instanceof MoreHolder) {
            MoreHolder holder = (MoreHolder) raw;
            holder.text.setText(row.moreText);
            holder.itemView.setOnClickListener(v -> toggleShowAll(row.sectionKey));
            return;
        }
        if (raw instanceof CategoryHolder) {
            CategoryHolder holder = (CategoryHolder) raw;
            VaultTypes.Category category = row.category;
            holder.title.setText(category.labelRes);
            holder.summary.setText(summary(category.key));
            holder.count.setText(String.valueOf(count(category.key)));
            holder.count.setTextColor(holder.itemView.getContext().getColor(color(category.key)));
            holder.icon.setImageResource(icon(category.key));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(holder.itemView.getContext().getColor(color(category.key)));
            bg.setCornerRadius(holder.itemView.getResources().getDisplayMetrics().density * 8);
            holder.icon.setBackground(bg);
            holder.itemView.setOnClickListener(v -> listener.onOpenCategory(category));
            return;
        }
        RecordHolder holder = (RecordHolder) raw;
        VaultItem item = row.item;
        VaultTypes.Type type = VaultTypes.resolveStored(item.type, item.fieldsJson);
        holder.title.setText(item.title);
        holder.meta.setText(row.matchText == null ? s(R.string.vault_updated_meta, typeName(item), relativeTime(item.updatedTime)) : row.matchText);
        holder.icon.setImageResource(VaultRecordIcons.iconFor(item, type));
        int recordColor = holder.itemView.getContext().getColor(VaultRecordIcons.colorFor(item, type));
        holder.icon.setImageTintList(ColorStateList.valueOf(recordColor));
        holder.icon.setBackground(softRound(recordColor, holder.itemView));
        holder.favorite.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onOpenItem(item));
    }

    private void toggleSection(String sectionKey) {
        if (SECTION_FAVORITES.equals(sectionKey)) favoritesExpanded = !favoritesExpanded;
        if (SECTION_RECENT.equals(sectionKey)) recentExpanded = !recentExpanded;
        VaultUiState.setSectionExpanded(VaultHost.context, sectionKey, SECTION_FAVORITES.equals(sectionKey) ? favoritesExpanded : recentExpanded);
        rebuild();
    }

    private void toggleShowAll(String sectionKey) {
        if (SECTION_FAVORITES.equals(sectionKey)) showAllFavorites = !showAllFavorites;
        if (SECTION_RECENT.equals(sectionKey)) showAllRecent = !showAllRecent;
        rebuild();
    }

    @Override public int getItemCount() {
        return rows.size();
    }

    private static View sectionView(ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(parent, 14), 0, dp(parent, 8));

        TextView title = new TextView(context);
        title.setId(android.R.id.text1);
        title.setTextColor(context.getColor(R.color.text_main));
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView subtitle = new TextView(context);
        subtitle.setId(android.R.id.text2);
        subtitle.setTextColor(context.getColor(R.color.text_secondary));
        subtitle.setTextSize(12);
        row.addView(subtitle);

        TextView icon = new TextView(context);
        icon.setId(android.R.id.icon);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(context.getColor(R.color.text_main));
        icon.setTextSize(18);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(parent, 28), dp(parent, 28));
        iconParams.setMarginStart(dp(parent, 8));
        row.addView(icon, iconParams);
        return row;
    }

    private static View moreView(ViewGroup parent) {
        Context context = parent.getContext();
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(context.getColor(R.color.action_icon_tint));
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackgroundResource(R.drawable.bg_card);
        view.setPadding(0, dp(parent, 10), 0, dp(parent, 10));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(parent, 6));
        view.setLayoutParams(params);
        return view;
    }

    private static int dp(ViewGroup parent, int value) {
        return Math.round(value * parent.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable softRound(int color, View view) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor((color & 0x00FFFFFF) | 0x1A000000);
        drawable.setCornerRadius(view.getResources().getDisplayMetrics().density * 10);
        return drawable;
    }

    private String relativeTime(long time) {
        long diff = System.currentTimeMillis() - time;
        if (time <= 0 || diff < 0) return s(R.string.vault_time_unknown);
        long minute = 60_000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (diff < minute) return s(R.string.vault_time_just_now);
        if (diff < hour) return s(R.string.vault_time_minutes_ago, diff / minute);
        if (diff < day) return s(R.string.vault_time_hours_ago, diff / hour);
        if (diff < 2L * day) return s(R.string.vault_time_yesterday);
        return s(R.string.vault_time_days_ago, diff / day);
    }

    static final class VaultHost {
        static Context context;
    }

    private static final class Row {
        String sectionKey, sectionTitle, sectionSubtitle, matchText, moreText;
        boolean collapsible, expanded;
        VaultTypes.Category category;
        VaultItem item;

        static Row section(String key, String title, String subtitle, boolean collapsible, boolean expanded) {
            Row row = new Row();
            row.sectionKey = key;
            row.sectionTitle = title;
            row.sectionSubtitle = subtitle;
            row.collapsible = collapsible;
            row.expanded = expanded;
            return row;
        }

        static Row category(VaultTypes.Category category) {
            Row row = new Row();
            row.category = category;
            return row;
        }

        static Row record(VaultItem item, String match) {
            Row row = new Row();
            row.item = item;
            row.matchText = match;
            return row;
        }

        static Row more(String sectionKey, String text) {
            Row row = new Row();
            row.sectionKey = sectionKey;
            row.moreText = text;
            return row;
        }
    }

    private static final class SearchHit {
        final VaultItem item;
        final String match;
        SearchHit(VaultItem item, String match) {
            this.item = item;
            this.match = match;
        }
    }

    static final class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title, subtitle, icon;
        SectionHolder(View view) {
            super(view);
            title = view.findViewById(android.R.id.text1);
            subtitle = view.findViewById(android.R.id.text2);
            icon = view.findViewById(android.R.id.icon);
        }
    }

    static final class CategoryHolder extends RecyclerView.ViewHolder {
        final TextView title, summary, count;
        final ImageView icon;
        CategoryHolder(View view) {
            super(view);
            title = view.findViewById(R.id.tv_vault_category_title);
            summary = view.findViewById(R.id.tv_vault_category_summary);
            count = view.findViewById(R.id.tv_vault_category_count);
            icon = view.findViewById(R.id.iv_vault_category_icon);
        }
    }

    static final class RecordHolder extends RecyclerView.ViewHolder {
        final TextView title, meta;
        final ImageView icon, favorite;
        RecordHolder(View view) {
            super(view);
            title = view.findViewById(R.id.tv_vault_record_title);
            meta = view.findViewById(R.id.tv_vault_record_meta);
            icon = view.findViewById(R.id.iv_vault_record_icon);
            favorite = view.findViewById(R.id.iv_vault_record_favorite);
        }
    }

    static final class MoreHolder extends RecyclerView.ViewHolder {
        final TextView text;
        MoreHolder(View view) {
            super(view);
            text = (TextView) view;
        }
    }
}
