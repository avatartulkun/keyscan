package com.secureqr.scanner.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public final class DataSyncState {
    public static final String PREFS = "secureqr_settings";
    public static final String KEY_DIRTY = "webdav_sync_dirty";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable pendingBackup;

    private DataSyncState() {
    }

    public static void markDirty(Context context) {
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DIRTY, true).apply();
        scheduleChangedDataBackup(app);
    }

    private static synchronized void scheduleChangedDataBackup(Context app) {
        if (pendingBackup != null) HANDLER.removeCallbacks(pendingBackup);
        pendingBackup = () -> {
            synchronized (DataSyncState.class) { pendingBackup = null; }
            if (app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_sync", true)) {
                WebDavAutoSyncManager.requestHighPrioritySync(app);
            }
            LocalAutoBackupManager.requestBackup(app);
        };
        HANDLER.postDelayed(pendingBackup, 5_000L);
    }

    public static void markSynced(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DIRTY, false).apply();
    }
}
