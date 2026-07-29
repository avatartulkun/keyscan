package com.secureqr.scanner.backup.webdav.v5;

import android.content.Context;
import android.content.SharedPreferences;

import com.secureqr.scanner.backup.AttachmentBackupCoordinator;
import com.secureqr.scanner.backup.BackupCoordinator;
import com.secureqr.scanner.backup.BackupPayload;
import com.secureqr.scanner.security.SecurityAuditLog;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.SecureSecretStore;
import com.secureqr.scanner.utils.DataSyncState;
import com.secureqr.scanner.utils.WebDAVClient;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Creates and uploads v5 encrypted backup containers without plaintext staging. */
public final class WebDavV5BackupAdapter {
    public interface Callback { void onComplete(int successCount, int targetCount, String error); }

    private static final String PREFS = "secureqr_settings";
    private static final String DEFAULT_PREFIX = "filebackup";
    private static final int HISTORY_LIMIT = 5;
    private static final int PIPE_BUFFER_SIZE = 64 * 1024;
    private static volatile boolean running;
    private static volatile boolean pendingAutomaticUpload;

    private WebDavV5BackupAdapter() { }

    public static void syncOnAppOpen(Context context) {
        requestUpload(context, true, true, null);
    }

    public static void requestHighPrioritySync(Context context) {
        if (running) {
            pendingAutomaticUpload = true;
            return;
        }
        requestUpload(context, false, false, null);
    }

    public static void requestManualSync(Context context, Callback callback) {
        requestUpload(context, false, false, callback);
    }

    public static void requestBackupNow(Context context, Callback callback) {
        requestUpload(context, false, false, callback);
    }

    private static void requestUpload(Context context, boolean requireAutoSyncEnabled,
                                      boolean protectExistingRemote, Callback callback) {
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (requireAutoSyncEnabled && !preferences.getBoolean("auto_sync", true)) return;
        if (requireAutoSyncEnabled && !preferences.getBoolean(DataSyncState.KEY_DIRTY, false)) return;
        if (running) {
            complete(callback, 0, 0, "WebDAV sync is already running");
            return;
        }
        String dataProtectionKey = SecuritySettings.getDataEncryptionKey(appContext);
        List<WebDavTarget> targets = configuredTargets(appContext);
        if (targets.isEmpty()) {
            complete(callback, 0, 0, "WebDAV is not configured");
            return;
        }
        if (dataProtectionKey == null || dataProtectionKey.isEmpty()) {
            complete(callback, 0, targets.size(), "Data protection key is required");
            return;
        }
        running = true;
        try {
            new BackupCoordinator(appContext).createPayload(new BackupCoordinator.Callback() {
            @Override public void onSuccess(BackupPayload payload) {
                Thread uploadThread = new Thread(() -> {
                    int successCount = 0;
                    try {
                        if (protectExistingRemote && preferences.getLong("last_sync", 0L) == 0L
                                && !preferences.getBoolean("remote_backup_restored", false)
                                && hasRemoteBackup(targets)) {
                            complete(callback, 0, targets.size(), "Remote backup found. Restore it before automatic upload.");
                            return;
                        }
                        String prefix = safePrefix(preferences.getString("backup_file_prefix", DEFAULT_PREFIX));
                        String latestPath = "/" + prefix + "_latest.dat";
                        String historyPath = "/" + prefix + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".dat";
                        for (WebDavTarget target : targets) {
                            boolean latest = upload(target.client, appContext, payload, dataProtectionKey, latestPath);
                            boolean history = latest && upload(target.client, appContext, payload, dataProtectionKey, historyPath);
                            if (latest && history) {
                                pruneHistory(target.client);
                                successCount++;
                            }
                        }
                        if (successCount > 0) {
                            markUploadSuccess(appContext);
                            SecurityAuditLog.record(appContext, "WebDAV v5 backup", true);
                        } else {
                            markUploadFailure(appContext);
                            SecurityAuditLog.record(appContext, "WebDAV v5 backup", false);
                        }
                        complete(callback, successCount, targets.size(),
                                successCount == targets.size() ? null : "WebDAV backup upload failed");
                    } finally {
                        running = false;
                        runPendingAutomaticUpload(appContext);
                    }
                }, "KeyScan-WebDavV5Backup");
                uploadThread.start();
            }

            @Override public void onFailure(Exception error) {
                markUploadFailure(appContext);
                SecurityAuditLog.record(appContext, "WebDAV v5 backup", false);
                running = false;
                runPendingAutomaticUpload(appContext);
                complete(callback, 0, targets.size(), appContext.getString(
                        com.secureqr.scanner.R.string.backup_unlock_required));
            }
            });
        } catch (RuntimeException error) {
            markUploadFailure(appContext);
            SecurityAuditLog.record(appContext, "WebDAV v5 backup", false);
            running = false;
            runPendingAutomaticUpload(appContext);
            complete(callback, 0, targets.size(), "WebDAV backup upload failed");
        }
    }

    private static void runPendingAutomaticUpload(Context context) {
        if (!pendingAutomaticUpload) return;
        pendingAutomaticUpload = false;
        requestHighPrioritySync(context);
    }

    private static boolean hasRemoteBackup(List<WebDavTarget> targets) {
        for (WebDavTarget target : targets) {
            if (!target.client.listBackupFiles().isEmpty()) return true;
        }
        return false;
    }

    private static boolean upload(WebDAVClient client, Context context, BackupPayload payload, String dataProtectionKey, String remotePath) {
        AtomicReference<Exception> writeFailure = new AtomicReference<>();
        try (PipedInputStream input = new PipedInputStream(PIPE_BUFFER_SIZE)) {
            PipedOutputStream output = new PipedOutputStream(input);
            Thread writer = new Thread(() -> {
                try (PipedOutputStream destination = output) {
                    new AttachmentBackupCoordinator(context).write(destination, payload, dataProtectionKey);
                } catch (Exception error) {
                    writeFailure.set(error);
                }
            }, "KeyScan-WebDavV5Package");
            writer.start();
            boolean uploaded = client.uploadStream(remotePath, input);
            writer.join();
            return uploaded && writeFailure.get() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String safePrefix(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (cleaned.isEmpty()) return DEFAULT_PREFIX;
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private static void pruneHistory(WebDAVClient client) {
        List<WebDAVClient.BackupFile> history = new ArrayList<>();
        for (WebDAVClient.BackupFile file : client.listBackupFiles()) {
            if (file.path != null && file.path.matches("/[A-Za-z0-9_-]{1,32}_[0-9]{8}_[0-9]{6}\\.dat")) {
                history.add(file);
            }
        }
        history.sort((left, right) -> right.name.compareTo(left.name));
        for (int i = HISTORY_LIMIT; i < history.size(); i++) client.delete(history.get(i).path);
    }

    private static List<WebDavTarget> configuredTargets(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<WebDavTarget> targets = new ArrayList<>();
        String selection = prefs.getString("backup_target_selection", "all");
        if ("main".equals(selection) || "all".equals(selection)) {
            addTarget(targets, context, prefs.getString("url", ""), prefs.getString("user", ""),
                    SecureSecretStore.getSecret(context, PREFS, "pass"));
        }
        if ("backup".equals(selection) || "all".equals(selection)) {
            addTarget(targets, context, prefs.getString("backup_url", ""), prefs.getString("backup_user", ""),
                    SecureSecretStore.getSecret(context, PREFS, "backup_pass"));
        }
        return targets;
    }

    private static void addTarget(List<WebDavTarget> targets, Context context, String url, String username, String password) {
        String safeUrl = url == null ? "" : url.trim();
        String safeUsername = username == null ? "" : username.trim();
        if (!safeUrl.isEmpty() && !safeUsername.isEmpty() && password != null && !password.isEmpty()) {
            targets.add(new WebDavTarget(new WebDAVClient(context, safeUrl, safeUsername, password)));
        }
    }

    private static void complete(Callback callback, int successCount, int targetCount, String error) {
        if (callback != null) callback.onComplete(successCount, targetCount, error);
    }

    private static void markUploadSuccess(Context context) {
        DataSyncState.markSynced(context);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("last_sync", System.currentTimeMillis())
                .remove("last_sync_error")
                .remove("last_sync_error_at")
                .apply();
    }

    private static void markUploadFailure(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(DataSyncState.KEY_DIRTY, true)
                .putString("last_sync_error", "WEBDAV_UPLOAD_FAILED")
                .putLong("last_sync_error_at", System.currentTimeMillis())
                .apply();
    }

    private static final class WebDavTarget {
        final WebDAVClient client;
        WebDavTarget(WebDAVClient client) { this.client = client; }
    }
}
