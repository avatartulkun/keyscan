package com.secureqr.scanner.security;

import android.content.Context;
import android.content.SharedPreferences;

public final class OperationModeManager {
    public enum Mode { VIEW, EDIT, LOCKED }

    public static final String KEY_UNLOCK_DEFAULT = "operation_unlock_default";
    public static final String KEY_FEEDBACK_VIBRATE = "operation_feedback_vibrate";
    public static final String KEY_FEEDBACK_MESSAGE = "operation_feedback_message";
    public static final String DEFAULT_VIEW = "view";
    public static final String DEFAULT_EDIT = "edit";
    private static final String PREFS = "secureqr_settings";
    private static Mode mode;

    private OperationModeManager() {}

    public static synchronized Mode current(Context context) {
        if (mode == null) mode = defaultMode(context);
        return mode;
    }

    public static synchronized boolean canModify(Context context) {
        return current(context) == Mode.EDIT;
    }

    public static synchronized boolean isLocked(Context context) {
        return current(context) == Mode.LOCKED;
    }

    public static synchronized Mode toggleViewEdit(Context context) {
        mode = current(context) == Mode.EDIT ? Mode.VIEW : Mode.EDIT;
        return mode;
    }

    public static synchronized void enterEdit() {
        mode = Mode.EDIT;
    }

    public static synchronized void enterView() {
        mode = Mode.VIEW;
    }

    public static synchronized void lock() {
        mode = Mode.LOCKED;
        VaultLockManager.lockNow();
    }

    public static synchronized Mode unlock(Context context) {
        mode = defaultMode(context);
        return mode;
    }

    public static Mode defaultMode(Context context) {
        String value = prefs(context).getString(KEY_UNLOCK_DEFAULT, DEFAULT_VIEW);
        return DEFAULT_EDIT.equals(value) ? Mode.EDIT : Mode.VIEW;
    }

    public static boolean vibrationEnabled(Context context) {
        return prefs(context).getBoolean(KEY_FEEDBACK_VIBRATE, true);
    }

    public static boolean messageEnabled(Context context) {
        return prefs(context).getBoolean(KEY_FEEDBACK_MESSAGE, true);
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
