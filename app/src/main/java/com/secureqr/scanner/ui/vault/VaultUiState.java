package com.secureqr.scanner.ui.vault;

import android.content.Context;
import android.content.SharedPreferences;

import com.secureqr.scanner.vault.VaultTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class VaultUiState {
    private static final String PREFS = "keyscan_vault_ui";
    private static final String CATEGORY_ORDER = "category_order";
    private static final String TYPE_ORDER_PREFIX = "type_order_";
    private static final String FAVORITES = "favorites";
    private static final String RECENT = "recent";
    private static final String SECTION_EXPANDED_PREFIX = "section_expanded_";
    private static final int RECENT_LIMIT = 12;

    static List<VaultTypes.Category> sortedCategories(Context context) {
        List<VaultTypes.Category> categories = new ArrayList<>(VaultTypes.CATEGORIES);
        List<String> order = readOrder(prefs(context).getString(CATEGORY_ORDER, ""));
        categories.sort((a, b) -> Integer.compare(index(order, a.key), index(order, b.key)));
        return categories;
    }

    static void saveCategoryOrder(Context context, List<VaultTypes.Category> categories) {
        List<String> keys = new ArrayList<>();
        for (VaultTypes.Category category : categories) keys.add(category.key);
        prefs(context).edit().putString(CATEGORY_ORDER, join(keys)).apply();
    }

    static List<VaultTypes.Type> sortedTypes(Context context, VaultTypes.Category category) {
        List<VaultTypes.Type> types = new ArrayList<>(category.types);
        List<String> order = readOrder(prefs(context).getString(TYPE_ORDER_PREFIX + category.key, ""));
        types.sort((a, b) -> Integer.compare(index(order, a.key), index(order, b.key)));
        return types;
    }

    static void saveTypeOrder(Context context, VaultTypes.Category category, List<VaultTypes.Type> types) {
        List<String> keys = new ArrayList<>();
        for (VaultTypes.Type type : types) keys.add(type.key);
        prefs(context).edit().putString(TYPE_ORDER_PREFIX + category.key, join(keys)).apply();
    }

    static boolean isFavorite(Context context, String id) {
        return readSet(context, FAVORITES).contains(id);
    }

    static void setFavorite(Context context, String id, boolean favorite) {
        LinkedHashSet<String> set = readSet(context, FAVORITES);
        if (favorite) set.add(id); else set.remove(id);
        prefs(context).edit().putString(FAVORITES, join(new ArrayList<>(set))).apply();
    }

    static List<String> favorites(Context context) {
        return new ArrayList<>(readSet(context, FAVORITES));
    }

    static void markRecent(Context context, String id) {
        LinkedHashSet<String> set = readSet(context, RECENT);
        set.remove(id);
        LinkedHashSet<String> next = new LinkedHashSet<>();
        next.add(id);
        next.addAll(set);
        while (next.size() > RECENT_LIMIT) {
            String last = null;
            for (String value : next) last = value;
            if (last == null) break;
            next.remove(last);
        }
        prefs(context).edit().putString(RECENT, join(new ArrayList<>(next))).apply();
    }

    static List<String> recent(Context context) {
        return new ArrayList<>(readSet(context, RECENT));
    }

    static boolean isSectionExpanded(Context context, String sectionKey, boolean defaultValue) {
        return prefs(context).getBoolean(SECTION_EXPANDED_PREFIX + sectionKey, defaultValue);
    }

    static void setSectionExpanded(Context context, String sectionKey, boolean expanded) {
        prefs(context).edit().putBoolean(SECTION_EXPANDED_PREFIX + sectionKey, expanded).apply();
    }

    private static int index(List<String> order, String key) {
        int index = order.indexOf(key);
        return index < 0 ? 1000 : index;
    }

    private static LinkedHashSet<String> readSet(Context context, String key) {
        return new LinkedHashSet<>(readOrder(prefs(context).getString(key, "")));
    }

    private static List<String> readOrder(String value) {
        if (value == null || value.trim().isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(value.split(",")));
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(',');
            out.append(value);
        }
        return out.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private VaultUiState() {}
}
