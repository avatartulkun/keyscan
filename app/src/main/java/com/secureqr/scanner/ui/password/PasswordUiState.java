package com.secureqr.scanner.ui.password;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

final class PasswordUiState {
    private static final String PREFS = "keyscan_password_ui";
    private static final String FAVORITES = "favorites";

    static boolean isFavorite(Context context, long id) {
        return readSet(context, FAVORITES).contains(String.valueOf(id));
    }

    static void setFavorite(Context context, long id, boolean favorite) {
        LinkedHashSet<String> set = readSet(context, FAVORITES);
        String value = String.valueOf(id);
        if (favorite) set.add(value);
        else set.remove(value);
        prefs(context).edit().putString(FAVORITES, join(new ArrayList<>(set))).apply();
    }

    private static LinkedHashSet<String> readSet(Context context, String key) {
        return new LinkedHashSet<>(readList(prefs(context).getString(key, "")));
    }

    private static List<String> readList(String value) {
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

    private PasswordUiState() {}
}
