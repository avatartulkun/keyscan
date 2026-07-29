package com.secureqr.scanner.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.secureqr.scanner.backup.AttachmentBackupCoordinator;
import com.secureqr.scanner.backup.BackupCoordinator;
import com.secureqr.scanner.backup.BackupPayload;
import com.secureqr.scanner.security.SecuritySettings;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Writes current-format encrypted backups to the user-selected SAF directory. */
public final class LocalAutoBackupManager {
    public static final String KEY_ENABLED = "local_auto_backup";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private LocalAutoBackupManager() {}

    public static void requestBackup(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(DataSyncState.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, false)) return;
        String treeValue = prefs.getString("local_backup_tree_uri", "");
        if (treeValue == null || treeValue.isEmpty() || !RUNNING.compareAndSet(false, true)) return;
        Uri tree = Uri.parse(treeValue);
        if (!hasPermission(app, tree)) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            RUNNING.set(false);
            return;
        }
        new BackupCoordinator(app).createPayload(new BackupCoordinator.Callback() {
            @Override public void onSuccess(BackupPayload payload) {
                EXECUTOR.execute(() -> write(app, prefs, tree, payload));
            }
            @Override public void onFailure(Exception error) {
                RUNNING.set(false);
            }
        });
    }

    private static void write(Context app, SharedPreferences prefs, Uri tree, BackupPayload payload) {
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(app, tree);
            if (directory == null || !directory.canWrite()) throw new IllegalStateException("Backup directory unavailable");
            String name = "KS_" + new SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(new Date()) + ".dat";
            DocumentFile file = directory.createFile("application/octet-stream", name);
            if (file == null) throw new IllegalStateException("Unable to create backup file");
            try (OutputStream output = app.getContentResolver().openOutputStream(file.getUri(), "w")) {
                if (output == null) throw new IllegalStateException("Unable to open backup output");
                new AttachmentBackupCoordinator(app).write(output, payload, SecuritySettings.getDataEncryptionKey(app));
            }
            prefs.edit().putLong("last_local_backup", System.currentTimeMillis())
                    .putString("last_local_backup_name", name).remove("local_auto_backup_error").apply();
        } catch (Exception error) {
            prefs.edit().putString("local_auto_backup_error",
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()).apply();
        } finally {
            RUNNING.set(false);
        }
    }

    private static boolean hasPermission(Context context, Uri uri) {
        for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(permission.getUri()) && permission.isReadPermission() && permission.isWritePermission()) return true;
        }
        return false;
    }
}
