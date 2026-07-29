package com.secureqr.scanner.ui.share;

import android.content.Context;
import android.content.SharedPreferences;

import com.secureqr.scanner.data.model.PasswordEntry;

/** Stores non-secret sharing metadata only; no password or encrypted payload is retained. */
public final class SecureShareStateStore {
    private static final String PREFS = "secure_share_state";
    private SecureShareStateStore() { }

    public static void recordShare(Context context, PasswordEntry entry) {
        if (context == null || entry == null) return;
        String key = key(entry);
        if (key.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(key + ".count", 0) + 1;
        prefs.edit()
                .putInt(key + ".count", count)
                .putLong(key + ".last", System.currentTimeMillis())
                .putLong(key + ".version", entry.updatedAt)
                .putBoolean(key + ".remind", true)
                .apply();
    }

    public static boolean shouldRemindAfterPasswordChange(Context context, PasswordEntry entry) {
        if (context == null || entry == null) return false;
        String key = key(entry);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !key.isEmpty() && prefs.getBoolean(key + ".remind", false)
                && prefs.getInt(key + ".count", 0) > 0;
    }

    public static int shareCount(Context context, PasswordEntry entry) {
        return context == null || entry == null ? 0
                : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(entry) + ".count", 0);
    }

    public static long lastSharedAt(Context context, PasswordEntry entry) {
        return context == null || entry == null ? 0
                : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key(entry) + ".last", 0);
    }

    public static void disableReminder(Context context, PasswordEntry entry) {
        if (context == null || entry == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key(entry) + ".remind", false).apply();
    }

    public static void endShare(Context context, PasswordEntry entry) {
        if (context == null || entry == null) return;
        String key = key(entry);
        if (key.isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(key + ".count")
                .remove(key + ".last")
                .remove(key + ".version")
                .remove(key + ".remind")
                .apply();
    }

    private static String key(PasswordEntry entry) {
        if (entry.itemId != null && !entry.itemId.trim().isEmpty()) return "item." + entry.itemId;
        return entry.id > 0 ? "id." + entry.id : "";
    }
}
