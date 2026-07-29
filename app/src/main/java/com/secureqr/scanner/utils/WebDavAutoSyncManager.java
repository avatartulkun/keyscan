package com.secureqr.scanner.utils;

import android.content.Context;

import com.secureqr.scanner.backup.webdav.WebDavBackupRouter;
import com.secureqr.scanner.backup.webdav.model.WebDavBackupPreview;

/** Schedules current v5 WebDAV backup and restore work. */
public final class WebDavAutoSyncManager {
    public interface SyncCallback { void onComplete(int successCount, int targetCount, String error); }
    public interface BackupPreviewCallback { void onComplete(WebDavBackupPreview preview, String error); }
    public interface RestoreCallback { void onComplete(WebDavBackupPreview preview, String error); }

    private WebDavAutoSyncManager() { }

    public static void syncOnAppOpen(Context context) {
        WebDavBackupRouter.syncOnAppOpen(context);
    }

    public static void requestHighPrioritySync(Context context) {
        WebDavBackupRouter.requestHighPrioritySync(context);
    }

    public static void requestManualSync(Context context, String ledgerPassword, SyncCallback callback) {
        WebDavBackupRouter.requestManualSync(context,
                callback == null ? null : callback::onComplete);
    }

    public static void requestBackupNow(Context context, SyncCallback callback) {
        WebDavBackupRouter.requestBackupNow(context,
                callback == null ? null : callback::onComplete);
    }

    /** Explicit current-format upload entry. */
    public static void requestV5BackupNow(Context context, SyncCallback callback) {
        requestBackupNow(context, callback);
    }

    public static void previewLatestBackup(Context context, String dataProtectionKey, BackupPreviewCallback callback) {
        WebDavBackupRouter.previewLatestBackup(context, dataProtectionKey,
                callback == null ? null : callback::onComplete);
    }

    public static void restoreLatestBackup(Context context, String dataProtectionKey, RestoreCallback callback) {
        WebDavBackupRouter.restoreLatestBackup(context, dataProtectionKey,
                callback == null ? null : callback::onComplete);
    }
}
