package com.secureqr.scanner.clipboard;

import android.content.Context;
import android.content.SharedPreferences;

public final class ClipboardImportSettings {
    public static final String PREFS = "secureqr_settings";
    public static final String KEY_SMART_IMPORT_ENABLED = "setting_clipboard_smart_import_enabled";
    public static final String KEY_CLEAR_AFTER_SAVE = "setting_clipboard_import_clear_after_save";

    private ClipboardImportSettings() {
    }

    public static boolean isSmartImportEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SMART_IMPORT_ENABLED, false);
    }

    public static boolean shouldClearAfterSave(Context context) {
        return prefs(context).getBoolean(KEY_CLEAR_AFTER_SAVE, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
