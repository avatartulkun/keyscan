package com.secureqr.scanner.backup.webdav.v5;

import android.content.Context;
import android.content.SharedPreferences;

import com.secureqr.scanner.backup.BackupPackageReader;
import com.secureqr.scanner.backup.BackupPayload;
import com.secureqr.scanner.backup.BackupRestoreManager;
import com.secureqr.scanner.backup.BackupRestoreResult;
import com.secureqr.scanner.backup.source.webdav.WebDavBackupStreamSource;
import com.secureqr.scanner.backup.webdav.model.WebDavBackupPreview;
import com.secureqr.scanner.security.SecureSecretStore;
import com.secureqr.scanner.utils.WebDAVClient;

import java.util.ArrayList;
import java.util.List;

/** Reads and restores only current v5 WebDAV backup containers. */
public final class WebDavV5RestoreAdapter {
    public interface Callback {
        void onComplete(WebDavBackupPreview preview, String error);
        void onNotV5();
    }

    private static final String PREFS = "secureqr_settings";
    private static final String LATEST_BACKUP = "/secure_backup.dat";

    private WebDavV5RestoreAdapter() { }

    public static void previewLatestBackup(Context context, String dataProtectionKey, Callback callback) {
        run(context, dataProtectionKey, callback, false);
    }

    public static void restoreLatestBackup(Context context, String dataProtectionKey, Callback callback) {
        run(context, dataProtectionKey, callback, true);
    }

    private static void run(Context context, String dataProtectionKey, Callback callback, boolean restore) {
        Context appContext = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            try {
                WebDavBackupStreamSource source = findV5Source(appContext);
                if (source == null) {
                    callback.onNotV5();
                    return;
                }
                BackupPayload payload = new BackupPackageReader(appContext).read(source, dataProtectionKey);
                WebDavBackupPreview preview = previewOf(payload);
                if (!restore) {
                    callback.onComplete(preview, null);
                    return;
                }
                new BackupRestoreManager(appContext).restore(payload, source, dataProtectionKey,
                        new BackupRestoreManager.Callback() {
                            @Override public void onComplete(BackupRestoreResult result) {
                                callback.onComplete(preview, result.errors().isEmpty() ? null : "Backup restore completed with errors");
                            }

                            @Override public void onFailure(Exception error) {
                                callback.onComplete(null, "WebDAV backup restore failed");
                            }
                        });
            } catch (Exception error) {
                callback.onComplete(null, "WebDAV backup could not be read");
            }
        }, restore ? "KeyScan-WebDavV5Restore" : "KeyScan-WebDavV5Preview");
        worker.start();
    }

    private static WebDavBackupStreamSource findV5Source(Context context) throws Exception {
        boolean foundLegacy = false;
        Exception lastFailure = null;
        for (WebDAVClient client : configuredClients(context)) {
            List<String> candidates = new ArrayList<>();
            String prefix = safePrefix(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("backup_file_prefix", "filebackup"));
            candidates.add("/" + prefix + "_latest.dat");
            for (WebDAVClient.BackupFile file : client.listBackupFiles()) {
                if (file.path != null && file.path.matches("/[A-Za-z0-9_-]{1,32}_latest\\.dat")
                        && !candidates.contains(file.path)) candidates.add(file.path);
            }
            candidates.add(LATEST_BACKUP);
            for (String path : candidates) {
                WebDavBackupStreamSource source = new WebDavBackupStreamSource(client, path);
                try {
                    if (BackupPackageReader.isV5Container(source)) return source;
                    foundLegacy = true;
                } catch (Exception error) {
                    lastFailure = error;
                }
            }
        }
        if (foundLegacy) return null;
        if (lastFailure != null) throw lastFailure;
        return null;
    }

    private static String safePrefix(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (cleaned.isEmpty()) return "filebackup";
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private static List<WebDAVClient> configuredClients(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<WebDAVClient> clients = new ArrayList<>();
        String selection = prefs.getString("backup_target_selection", "all");
        if ("main".equals(selection) || "all".equals(selection)) {
            addClient(clients, context, prefs.getString("url", ""), prefs.getString("user", ""),
                    SecureSecretStore.getSecret(context, PREFS, "pass"));
        }
        if ("backup".equals(selection) || "all".equals(selection)) {
            addClient(clients, context, prefs.getString("backup_url", ""), prefs.getString("backup_user", ""),
                    SecureSecretStore.getSecret(context, PREFS, "backup_pass"));
        }
        return clients;
    }

    private static void addClient(List<WebDAVClient> clients, Context context, String url, String username, String password) {
        String safeUrl = url == null ? "" : url.trim();
        String safeUsername = username == null ? "" : username.trim();
        if (!safeUrl.isEmpty() && !safeUsername.isEmpty() && password != null && !password.isEmpty()) {
            clients.add(new WebDAVClient(context, safeUrl, safeUsername, password));
        }
    }

    private static WebDavBackupPreview previewOf(BackupPayload payload) {
        return new WebDavBackupPreview(payload.records.size(), payload.passwordGroups.size(), payload.passwords.size(),
                payload.otpTokens.size(), 0, 0, payload.vaultItems.size(), payload.attachments.size());
    }
}
