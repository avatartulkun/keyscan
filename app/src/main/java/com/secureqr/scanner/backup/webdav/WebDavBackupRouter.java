package com.secureqr.scanner.backup.webdav;

import android.content.Context;

import com.secureqr.scanner.backup.webdav.model.WebDavBackupPreview;
import com.secureqr.scanner.backup.webdav.v5.WebDavV5BackupAdapter;
import com.secureqr.scanner.backup.webdav.v5.WebDavV5RestoreAdapter;

/** Routes current WebDAV v5 backup and restore operations. */
public final class WebDavBackupRouter {
    public interface Callback { void onComplete(WebDavBackupPreview preview, String error); }
    public interface SyncCallback { void onComplete(int successCount, int targetCount, String error); }

    private WebDavBackupRouter() { }

    /** v5 is the only format used by the current backup and restore flow. */
    public static void requestBackupNow(Context context, SyncCallback callback) {
        WebDavV5BackupAdapter.requestBackupNow(context,
                callback == null ? null : callback::onComplete);
    }

    public static void syncOnAppOpen(Context context) {
        WebDavV5BackupAdapter.syncOnAppOpen(context);
    }

    public static void requestHighPrioritySync(Context context) {
        WebDavV5BackupAdapter.requestHighPrioritySync(context);
    }

    public static void requestManualSync(Context context, SyncCallback callback) {
        WebDavV5BackupAdapter.requestManualSync(context,
                callback == null ? null : callback::onComplete);
    }

    public static void previewLatestBackup(Context context, String dataProtectionKey, Callback callback) {
        Callback safeCallback = callback == null ? (preview, error) -> { } : callback;
        WebDavV5RestoreAdapter.previewLatestBackup(context, dataProtectionKey, new WebDavV5RestoreAdapter.Callback() {
            @Override public void onComplete(WebDavBackupPreview preview, String error) { safeCallback.onComplete(preview, error); }
            @Override public void onNotV5() {
                safeCallback.onComplete(null, "Only KeyScan v5 backups can be restored");
            }
        });
    }

    public static void restoreLatestBackup(Context context, String dataProtectionKey, Callback callback) {
        Callback safeCallback = callback == null ? (preview, error) -> { } : callback;
        WebDavV5RestoreAdapter.restoreLatestBackup(context, dataProtectionKey, new WebDavV5RestoreAdapter.Callback() {
            @Override public void onComplete(WebDavBackupPreview preview, String error) { safeCallback.onComplete(preview, error); }
            @Override public void onNotV5() {
                safeCallback.onComplete(null, "Only KeyScan v5 backups can be restored");
            }
        });
    }
}
