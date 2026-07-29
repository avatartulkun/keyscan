package com.secureqr.scanner.backup.webdav.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import com.secureqr.scanner.R;

import com.secureqr.scanner.backup.webdav.model.WebDavBackupPreview;
import com.secureqr.scanner.utils.WebDAVClient;
import com.secureqr.scanner.utils.CryptoHelper;
import com.secureqr.scanner.utils.DataSyncState;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.OtpTokenDao;
import com.secureqr.scanner.data.database.PasswordGroupDao;
import com.secureqr.scanner.data.database.PasswordEntryDao;
import com.secureqr.scanner.data.database.RecordDao;
import com.secureqr.scanner.data.database.PasswordNoteDao;
import com.secureqr.scanner.data.database.PasswordGenerationDao;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.model.PasswordNote;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.data.model.VaultAttachment;
import com.secureqr.scanner.vault.VaultFileStore;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.SecureSecretStore;
import com.secureqr.scanner.security.SecurityAuditLog;
import com.secureqr.scanner.security.VaultAccessManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LegacyWebDavBackupAdapter {
    public interface SyncCallback {
        void onComplete(int successCount, int targetCount, String error);
    }

    public interface BackupPreviewCallback {
        void onComplete(WebDavBackupPreview preview, String error);
    }

    public interface RestoreCallback {
        void onComplete(WebDavBackupPreview preview, String error);
    }
    private static final String PREFS = "secureqr_settings";
    private static final String LATEST_BACKUP = "/secure_backup.dat";
    private static final String KEY_RECOVERY_KEY = "webdav_recovery_key";
    private static volatile boolean running;

    private LegacyWebDavBackupAdapter() {
    }

    public static void syncOnAppOpen(Context context) {
        Context appContext = context.getApplicationContext();
        startSync(appContext, true);
    }

    public static void requestHighPrioritySync(Context context) {
        Context appContext = context.getApplicationContext();
        startSync(appContext, false);
    }

    public static void requestManualSync(Context context, String ledgerPassword, SyncCallback callback) {
        startSync(context.getApplicationContext(), false, ledgerPassword, callback);
    }

    public static void requestBackupNow(Context context, SyncCallback callback) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<WebDavTarget> targets = configuredTargets(appContext, prefs);
        String dataProtectionKey = SecuritySettings.getDataEncryptionKey(appContext);
        if (targets.isEmpty()) {
            if (callback != null) callback.onComplete(0, 0, appContext.getString(R.string.webdav_complete_config_required));
            return;
        }
        if (dataProtectionKey == null || dataProtectionKey.isEmpty()) {
            if (callback != null) callback.onComplete(0, targets.size(), appContext.getString(R.string.webdav_data_key_required));
            return;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (!VaultAccessManager.canAccessSensitiveData(appContext)) {
                    if (callback != null) callback.onComplete(0, targets.size(), appContext.getString(R.string.autofill_unlock_keyscan_first));
                    return;
                }
                AppDatabase database = AppDatabase.getInstance(appContext);
                int successCount = uploadLocalSnapshot(appContext, database, prefs, targets, dataProtectionKey);
                SecurityAuditLog.record(appContext, "WebDAV 手动备份", successCount > 0);
                if (callback != null) callback.onComplete(successCount, targets.size(), successCount > 0 ? null : appContext.getString(R.string.webdav_backup_check_config));
            } catch (Exception e) {
                SecurityAuditLog.record(appContext, "WebDAV 手动备份", false);
                if (callback != null) callback.onComplete(0, targets.size(), friendlySyncError(appContext, e));
            } finally {
                executor.shutdown();
            }
        });
    }

    public static void previewLatestBackup(Context context, String dataProtectionKey, BackupPreviewCallback callback) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<WebDavTarget> targets = configuredTargets(appContext, prefs);
        if (targets.isEmpty()) {
            if (callback != null) callback.onComplete(null, appContext.getString(R.string.webdav_complete_config_required));
            return;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (!VaultAccessManager.canAccessSensitiveData(appContext)) {
                    if (callback != null) callback.onComplete(null, appContext.getString(R.string.autofill_unlock_keyscan_first));
                    return;
                }
                BackupPayload payload = downloadFirstAvailable(targets, dataProtectionKey);
                if (payload == null) throw new IllegalStateException("backup missing");
                WebDavBackupPreview preview = previewOf(payload);
                SecurityAuditLog.record(appContext, "备份验证", true);
                if (callback != null) callback.onComplete(preview, null);
            } catch (Exception e) {
                SecurityAuditLog.record(appContext, "备份验证失败", false);
                if (callback != null) callback.onComplete(null, friendlyRestoreError(appContext, e));
            } finally {
                executor.shutdown();
            }
        });
    }

    public static void restoreLatestBackup(Context context, String dataProtectionKey, RestoreCallback callback) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<WebDavTarget> targets = configuredTargets(appContext, prefs);
        if (targets.isEmpty()) {
            if (callback != null) callback.onComplete(null, appContext.getString(R.string.webdav_complete_config_required));
            return;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (!VaultAccessManager.canAccessSensitiveData(appContext)) {
                    if (callback != null) callback.onComplete(null, appContext.getString(R.string.autofill_unlock_keyscan_first));
                    return;
                }
                AppDatabase database = AppDatabase.getInstance(appContext);
                BackupPayload payload = downloadFirstAvailable(targets, dataProtectionKey);
                if (payload == null) throw new IllegalStateException("backup missing");
                mergeRecords(database.recordDao(), payload.records);
                mergeGroups(database.passwordGroupDao(), payload.groups);
                mergePasswords(database.passwordEntryDao(), payload.passwords);
                mergeOtpTokens(database.otpTokenDao(), payload.otpTokens);
                mergePasswordNotes(database.passwordNoteDao(), payload.passwordNotes);
                mergePasswordGenerations(database.passwordGenerationDao(), payload.passwordGenerations);
                mergeVaultItems(database.vaultItemDao(), payload.vaultItems);
                restoreVaultAttachments(appContext, database, targets, payload.vaultAttachments, dataProtectionKey);
                WebDavBackupPreview preview = previewOf(payload);
                SecurityAuditLog.record(appContext, "WebDAV 恢复成功", true);
                if (callback != null) callback.onComplete(preview, null);
            } catch (Exception e) {
                SecurityAuditLog.record(appContext, "WebDAV 恢复失败", false);
                if (callback != null) callback.onComplete(null, friendlyRestoreError(appContext, e));
            } finally {
                executor.shutdown();
            }
        });
    }

    private static void startSync(Context appContext, boolean requireAutoSyncEnabled) {
        startSync(appContext, requireAutoSyncEnabled, null, null);
    }

    private static void startSync(Context appContext, boolean requireAutoSyncEnabled, String ledgerPassword, SyncCallback callback) {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (requireAutoSyncEnabled && !prefs.getBoolean("auto_sync", false)) return;
        if (running) {
            if (callback != null) callback.onComplete(0, 0, appContext.getString(R.string.sync_in_progress));
            return;
        }
        List<WebDavTarget> targets = configuredTargets(appContext, prefs);
        String backupPassword = SecuritySettings.getDataEncryptionKey(appContext);
        if (targets.isEmpty()) {
            if (callback != null) callback.onComplete(0, 0, appContext.getString(R.string.webdav_complete_config_required));
            return;
        }
        if (backupPassword == null || backupPassword.isEmpty()) {
            if (callback != null) callback.onComplete(0, targets.size(), appContext.getString(R.string.webdav_missing_data_key));
            return;
        }

        running = true;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (!VaultAccessManager.canAccessSensitiveData(appContext)) {
                    if (callback != null) callback.onComplete(0, targets.size(), appContext.getString(R.string.autofill_unlock_keyscan_first));
                    return;
                }
                AppDatabase database = AppDatabase.getInstance(appContext);
                BackupPayload remote = downloadFirstAvailable(targets, backupPassword);
                if (remote != null) {
                    mergeRecords(database.recordDao(), remote.records);
                    mergeGroups(database.passwordGroupDao(), remote.groups);
                    mergePasswords(database.passwordEntryDao(), remote.passwords);
                    mergeOtpTokens(database.otpTokenDao(), remote.otpTokens);
                    mergePasswordNotes(database.passwordNoteDao(), remote.passwordNotes);
                    mergePasswordGenerations(database.passwordGenerationDao(), remote.passwordGenerations);
                    mergeVaultItems(database.vaultItemDao(), remote.vaultItems);
                    restoreVaultAttachments(appContext, database, targets, remote.vaultAttachments, backupPassword);
                    SecurityAuditLog.record(appContext, "WebDAV 自动恢复", true);
                }
                int successCount = uploadLocalSnapshot(appContext, database, prefs, targets, backupPassword);
                SecurityAuditLog.record(appContext, "WebDAV 自动备份", successCount > 0);
                if (callback != null) callback.onComplete(successCount, targets.size(), null);
            } catch (Exception e) {
                SecurityAuditLog.record(appContext, "WebDAV 自动同步", false);
                prefs.edit()
                        .putString("last_sync_error", e.getMessage() == null ? "" : e.getMessage())
                        .putLong("last_sync_error_at", System.currentTimeMillis())
                        .apply();
                if (callback != null) callback.onComplete(0, targets.size(), friendlySyncError(appContext, e));
            } finally {
                running = false;
                executor.shutdown();
            }
        });
    }

    private static List<WebDavTarget> configuredTargets(Context context, SharedPreferences prefs) {
        List<WebDavTarget> targets = new ArrayList<>();
        WebDavTarget main = buildTarget(context,
                prefs.getString("url", ""),
                prefs.getString("user", ""),
                SecureSecretStore.getSecret(context, PREFS, "pass"),
                prefs.getString("main_sync_content", "all"));
        WebDavTarget backup = buildTarget(context,
                prefs.getString("backup_url", ""),
                prefs.getString("backup_user", ""),
                SecureSecretStore.getSecret(context, PREFS, "backup_pass"),
                prefs.getString("backup_sync_content", "all"));
        String selection = prefs.getString("backup_target_selection", "all");
        if (("main".equals(selection) || "all".equals(selection)) && main != null) targets.add(main);
        if (("backup".equals(selection) || "all".equals(selection)) && backup != null) targets.add(backup);
        return targets;
    }

    private static WebDavTarget buildTarget(Context context, String url, String user, String pass, String contentMode) {
        String safeUrl = url == null ? "" : url.trim();
        String safeUser = user == null ? "" : user.trim();
        String safePass = pass == null ? "" : pass;
        if (safeUrl.isEmpty() || safeUser.isEmpty() || safePass.isEmpty()) return null;
        return new WebDavTarget(new WebDAVClient(context, safeUrl, safeUser, safePass), contentMode == null ? "all" : contentMode);
    }

    private static BackupPayload downloadFirstAvailable(List<WebDavTarget> targets, String backupPassword) throws Exception {
        for (WebDavTarget target : targets) {
            String encrypted = target.client.download(LATEST_BACKUP);
            if (encrypted != null && !encrypted.trim().isEmpty()) {
                return parsePayload(decryptBackupPayload(encrypted, backupPassword));
            }
        }
        return null;
    }

    private static String decryptBackupPayload(String encrypted, String backupPassword) throws Exception {
        String trimmed = encrypted == null ? "" : encrypted.trim();
        if (trimmed.startsWith("{")) {
            JSONObject envelope = new JSONObject(trimmed);
            if (envelope.optInt("keyscanBackupVersion") >= 4) {
                return CryptoHelper.decrypt(envelope.getString("payload"), backupPassword);
            }
        }
        return CryptoHelper.decrypt(trimmed, backupPassword);
    }

    private static String friendlySyncError(Context context, Exception error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("decrypt") || lower.contains("mac") || lower.contains("badpadding")
                || lower.contains("tag") || lower.contains("json")) {
            return context.getString(R.string.webdav_security_credentials_mismatch);
        }
        return message.isEmpty() ? context.getString(R.string.sync_failed) : message;
    }

    private static String friendlyRestoreError(Context context, Exception error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.US);
        String name = error == null ? "" : error.getClass().getSimpleName().toLowerCase(Locale.US);
        if (message.contains("newer")) return context.getString(R.string.backup_from_newer_version);
        if (name.contains("aead") || message.contains("mac") || message.contains("tag")
                || message.contains("decrypt") || message.contains("badpadding")) {
            return context.getString(R.string.backup_wrong_data_key);
        }
        if (message.contains("backup missing") || name.contains("json") || message.contains("json")
                || message.contains("base64")) {
            return context.getString(R.string.backup_file_abnormal_recreate);
        }
        return context.getString(R.string.backup_file_abnormal_recreate);
    }

    private static BackupPayload parsePayload(String json) throws Exception {
        BackupPayload payload = new BackupPayload();
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("{")) {
            JSONObject root = new JSONObject(trimmed);
            payload.records = parseRecords(root.optJSONArray("records"));
            payload.groups = parseGroups(root.optJSONArray("passwordGroups"));
            payload.passwords = parsePasswords(root.optJSONArray("passwords"));
            payload.otpTokens = parseOtpTokens(root.optJSONArray("otpTokens"));
            payload.passwordNotes = parsePasswordNotes(root.optJSONArray("passwordNotes"));
            payload.passwordGenerations = parsePasswordGenerations(root.optJSONArray("passwordGenerations"));
            payload.vaultItems = parseVaultItems(root.optJSONArray("vaultItems"));
            payload.vaultAttachments = parseVaultAttachments(root.optJSONArray("vaultAttachments"));
        } else if (!trimmed.isEmpty()) {
            payload.records = parseRecords(new JSONArray(trimmed));
        }
        return payload;
    }

    private static void mergeRecords(RecordDao dao, List<ScanRecord> remoteRecords) {
        for (ScanRecord incoming : remoteRecords) {
            ScanRecord local = dao.findByContentAndType(incoming.content, incoming.type);
            if (local == null) {
                incoming.id = 0;
                dao.insert(incoming);
            } else if (incoming.timestamp >= local.timestamp) {
                incoming.id = local.id;
                dao.update(incoming);
            }
        }
        dao.trimNonStarredTo500();
    }

    private static void mergeGroups(PasswordGroupDao dao, List<PasswordGroup> remoteGroups) {
        for (PasswordGroup incoming : remoteGroups) {
            if (incoming == null) continue;
            if (incoming.id == null || incoming.id.trim().isEmpty()) incoming.id = PasswordGroup.DEFAULT_ID;
            if (incoming.name == null || incoming.name.trim().isEmpty()) incoming.name = PasswordGroup.DEFAULT_NAME;
            PasswordGroup local = dao.findById(incoming.id);
            if (local == null) {
                if (incoming.createdAt <= 0) incoming.createdAt = System.currentTimeMillis();
                if (incoming.updatedAt <= 0) incoming.updatedAt = incoming.createdAt;
                if (PasswordGroup.DEFAULT_ID.equals(incoming.id)) incoming.isDefault = true;
                dao.insert(incoming);
            } else if (incoming.updatedAt >= local.updatedAt || incoming.sortOrder != local.sortOrder || !nonEmpty(incoming.name).equals(nonEmpty(local.name))) {
                if (local.isDefault) {
                    local.id = PasswordGroup.DEFAULT_ID;
                    local.name = PasswordGroup.DEFAULT_NAME;
                    local.isDefault = true;
                    local.sortOrder = 0;
                } else {
                    local.name = incoming.name;
                    local.sortOrder = incoming.sortOrder;
                    local.isDefault = incoming.isDefault;
                }
                local.updatedAt = Math.max(local.updatedAt, incoming.updatedAt);
                dao.update(local);
            }
        }
    }

    private static void mergePasswords(PasswordEntryDao dao, List<PasswordEntry> remoteEntries) {
        for (PasswordEntry incoming : remoteEntries) {
            normalizePasswordEntry(incoming);
            String username = nonEmpty(incoming.username, incoming.account);
            PasswordEntry local = dao.findMatchingCredential(nonEmpty(incoming.websiteDomain), nonEmpty(incoming.appPackageName), username);
            if (local == null) local = dao.findByRemarkAndAccount(incoming.remark, incoming.account);
            if (local == null) {
                incoming.id = 0;
                dao.insert(incoming);
            } else if (incoming.updatedAt >= local.updatedAt || incoming.createdAt >= local.createdAt) {
                incoming.id = local.id;
                dao.update(incoming);
            }
        }
    }

    private static void mergeOtpTokens(OtpTokenDao dao, List<OtpToken> remoteTokens) {
        for (OtpToken incoming : remoteTokens) {
            OtpToken local = dao.findBySecretAndAccount(incoming.secret, incoming.accountName);
            if (local == null) {
                incoming.id = 0;
                dao.insert(incoming);
            } else if (incoming.updatedAt >= local.updatedAt) {
                incoming.id = local.id;
                dao.update(incoming);
            }
        }
    }

    private static void mergePasswordNotes(PasswordNoteDao dao, List<PasswordNote> remoteNotes) {
        for (PasswordNote incoming : remoteNotes) {
            PasswordNote local = dao.findMatching(nonEmpty(incoming.type), nonEmpty(incoming.title), nonEmpty(incoming.contentJson));
            if (local == null) {
                incoming.id = 0;
                incoming.sourcePasswordEntryId = 0;
                dao.insert(incoming);
            } else if (incoming.updatedAt >= local.updatedAt) {
                incoming.id = local.id;
                incoming.sourcePasswordEntryId = local.sourcePasswordEntryId;
                dao.update(incoming);
            }
        }
    }

    private static void mergePasswordGenerations(PasswordGenerationDao dao, List<PasswordGenerationRecord> remoteRecords) {
        for (PasswordGenerationRecord incoming : remoteRecords) {
            if (dao.findMatching(nonEmpty(incoming.password), incoming.createdAt) == null) {
                incoming.id = 0;
                dao.insert(incoming);
            }
        }
        dao.trimTo100();
    }

    private static void mergeVaultItems(com.secureqr.scanner.data.database.VaultItemDao dao,List<VaultItem> remote){
        for(VaultItem incoming:remote){VaultItem local=dao.findById(incoming.id);if(local==null)dao.insert(incoming);else if(incoming.updatedTime>=local.updatedTime)dao.update(incoming);}
    }

    private static void restoreVaultAttachments(Context context,AppDatabase db,List<WebDavTarget> targets,List<VaultAttachment> remote,String password){
        VaultFileStore store=new VaultFileStore(context);
        for(VaultAttachment incoming:remote){if(db.vaultAttachmentDao().findById(incoming.id)!=null)continue;String encrypted=null;for(WebDavTarget target:targets){encrypted=target.client.download("/attachments/"+incoming.id+".enc");if(encrypted!=null&&!encrypted.isEmpty())break;}if(encrypted==null||encrypted.isEmpty())continue;try{byte[] plain=Base64.decode(CryptoHelper.decrypt(encrypted,password),Base64.NO_WRAP);VaultFileStore.Stored saved=store.encryptBytes(plain,incoming.id);incoming.encryptedPath=saved.path;incoming.hash=saved.hash;incoming.size=saved.size;db.vaultAttachmentDao().insert(incoming);}catch(Exception ignored){}}
    }

    private static int uploadLocalSnapshot(Context context, AppDatabase database, SharedPreferences prefs, List<WebDavTarget> targets, String backupPassword) throws Exception {
        String historyPath = "/keybackup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".dat";
        int successCount = 0;
        for (WebDavTarget target : targets) {
            String json = toSyncJson(
                    database.recordDao().getSyncRecords(),
                    database.passwordGroupDao().getAllNow(),
                    database.passwordEntryDao().getAllNow(),
                    database.otpTokenDao().getAllNow(),
                    database.passwordNoteDao().getAllNow(),
                    database.passwordGenerationDao().getAllNow(),
                    database.vaultItemDao().getAllNow(),
                    database.vaultAttachmentDao().getAllNow(),
                    target.contentMode);
            String encrypted = createBackupEnvelope(context, prefs, json, backupPassword);
            boolean ok = target.client.upload(LATEST_BACKUP, encrypted) && target.client.upload(historyPath, encrypted)
                    && uploadVaultAttachments(context,target,database.vaultAttachmentDao().getAllNow(),backupPassword);
            if (ok) successCount++;
        }
        if (successCount > 0) {
            DataSyncState.markSynced(context);
            prefs.edit()
                    .putLong("last_sync", System.currentTimeMillis())
                    .remove("last_sync_error")
                    .remove("last_sync_error_at")
                    .apply();
        }
        return successCount;
    }

    private static boolean uploadVaultAttachments(Context context,WebDavTarget target,List<VaultAttachment> attachments,String password){
        if(attachments.isEmpty())return true;if(!target.client.ensureDirectory("/attachments"))return false;VaultFileStore store=new VaultFileStore(context);boolean all=true;for(VaultAttachment a:attachments){try{String payload=Base64.encodeToString(store.decryptBytes(a.encryptedPath),Base64.NO_WRAP);all=target.client.upload("/attachments/"+a.id+".enc",CryptoHelper.encrypt(payload,password,"AES-GCM"))&&all;}catch(Exception e){all=false;}}return all;
    }

    private static String createBackupEnvelope(Context context, SharedPreferences prefs, String json, String backupPassword) throws Exception {
        String recoveryKey = normalizedRecoveryKey(context);
        if (recoveryKey.isEmpty()) {
            String generated = generateRecoveryKey();
            SecureSecretStore.putSecret(context, PREFS, KEY_RECOVERY_KEY, generated);
            recoveryKey = normalizedRecoveryKey(context);
        }
        JSONObject envelope = new JSONObject();
        envelope.put("keyscanBackupVersion", 4);
        envelope.put("algorithm", "AES-GCM");
        envelope.put("payload", CryptoHelper.encrypt(json, backupPassword, "AES-GCM"));
        envelope.put("recovery", CryptoHelper.encrypt(backupPassword, recoveryKey, "AES-GCM"));
        return envelope.toString();
    }

    private static String toSyncJson(List<ScanRecord> records, List<PasswordGroup> groups, List<PasswordEntry> passwords, List<OtpToken> otpTokens, List<PasswordNote> passwordNotes, List<PasswordGenerationRecord> passwordGenerations, List<VaultItem> vaultItems, List<VaultAttachment> vaultAttachments, String contentMode) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 4);
        root.put("records", shouldSyncRecords(contentMode) ? recordsToJson(records) : new JSONArray());
        root.put("passwordGroups", shouldSyncPasswords(contentMode) ? passwordGroupsToJson(groups) : new JSONArray());
        root.put("passwords", shouldSyncPasswords(contentMode) ? passwordsToJson(passwords) : new JSONArray());
        root.put("otpTokens", shouldSyncOtp(contentMode) ? otpTokensToJson(otpTokens) : new JSONArray());
        root.put("passwordNotes", passwordNotesToJson(passwordNotes));
        root.put("passwordGenerations", passwordGenerationsToJson(passwordGenerations));
        root.put("vaultItems", vaultItemsToJson(vaultItems));
        root.put("vaultAttachments", vaultAttachmentsToJson(vaultAttachments));
        return root.toString();
    }

    private static JSONArray vaultItemsToJson(List<VaultItem> items)throws Exception{JSONArray a=new JSONArray();for(VaultItem i:items){JSONObject o=new JSONObject();o.put("id",i.id);o.put("type",i.type);o.put("category",i.category);o.put("title",i.title);o.put("fields",i.fieldsJson);o.put("notes",i.notes);o.put("createdTime",i.createdTime);o.put("updatedTime",i.updatedTime);a.put(o);}return a;}
    private static JSONArray vaultAttachmentsToJson(List<VaultAttachment> items)throws Exception{JSONArray a=new JSONArray();for(VaultAttachment i:items){JSONObject o=new JSONObject();o.put("id",i.id);o.put("itemId",i.itemId);o.put("filename",i.filename);o.put("mimeType",i.mimeType);o.put("hash",i.hash);o.put("size",i.size);o.put("createdTime",i.createdTime);a.put(o);}return a;}

    private static List<VaultItem> parseVaultItems(JSONArray array){List<VaultItem> out=new ArrayList<>();if(array==null)return out;for(int n=0;n<array.length();n++){JSONObject o=array.optJSONObject(n);if(o==null)continue;VaultItem i=new VaultItem();i.id=o.optString("id",i.id);i.type=o.optString("type","CUSTOM");i.category=o.optString("category","CUSTOM");i.title=o.optString("title","");i.fieldsJson=o.optString("fields","{}");i.notes=o.optString("notes","");i.createdTime=o.optLong("createdTime",System.currentTimeMillis());i.updatedTime=o.optLong("updatedTime",i.createdTime);out.add(i);}return out;}
    private static List<VaultAttachment> parseVaultAttachments(JSONArray array){List<VaultAttachment> out=new ArrayList<>();if(array==null)return out;for(int n=0;n<array.length();n++){JSONObject o=array.optJSONObject(n);if(o==null)continue;VaultAttachment i=new VaultAttachment();i.id=o.optString("id",i.id);i.itemId=o.optString("itemId","");i.filename=o.optString("filename","");i.mimeType=o.optString("mimeType","application/octet-stream");i.hash=o.optString("hash","");i.size=o.optLong("size");i.createdTime=o.optLong("createdTime");out.add(i);}return out;}

    private static boolean shouldSyncRecords(String contentMode) {
        return "all".equals(contentMode) || "records".equals(contentMode);
    }

    private static boolean shouldSyncPasswords(String contentMode) {
        return "all".equals(contentMode) || "passwords".equals(contentMode);
    }

    private static boolean shouldSyncOtp(String contentMode) {
        return "all".equals(contentMode) || "otp".equals(contentMode);
    }

    private static JSONArray recordsToJson(List<ScanRecord> records) throws Exception {
        JSONArray array = new JSONArray();
        for (ScanRecord record : records) {
            JSONObject object = new JSONObject();
            object.put("content", record.content);
            object.put("type", record.type);
            object.put("title", record.title);
            object.put("source", record.source);
            object.put("thumbnailBase64", record.thumbnailBase64);
            object.put("isStarred", record.isStarred);
            object.put("timestamp", record.timestamp);
            array.put(object);
        }
        return array;
    }

    private static JSONArray passwordsToJson(List<PasswordEntry> passwords) throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordEntry entry : passwords) {
            JSONObject object = new JSONObject();
            object.put("title", entry.title);
            object.put("websiteDomain", entry.websiteDomain);
            object.put("appPackageName", entry.appPackageName);
            object.put("username", entry.username);
            object.put("password", entry.password);
            object.put("account", entry.account);
            object.put("remark", entry.remark);
            object.put("notes", entry.notes);
            object.put("groupId", entry.groupId);
            object.put("lastUsedAt", entry.lastUsedAt);
            object.put("createdAt", entry.createdAt);
            object.put("updatedAt", entry.updatedAt);
            array.put(object);
        }
        return array;
    }

    private static JSONArray passwordGroupsToJson(List<PasswordGroup> groups) throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordGroup group : groups) {
            JSONObject object = new JSONObject();
            object.put("id", group.id);
            object.put("name", group.name);
            object.put("sortOrder", group.sortOrder);
            object.put("isDefault", group.isDefault);
            object.put("createdAt", group.createdAt);
            object.put("updatedAt", group.updatedAt);
            array.put(object);
        }
        return array;
    }

    private static JSONArray otpTokensToJson(List<OtpToken> tokens) throws Exception {
        JSONArray array = new JSONArray();
        for (OtpToken token : tokens) {
            JSONObject object = new JSONObject();
            object.put("accountName", token.accountName);
            object.put("issuer", token.issuer);
            object.put("secret", token.secret);
            object.put("digits", token.digits);
            object.put("period", token.period);
            object.put("algorithm", token.algorithm);
            object.put("pinned", token.pinned);
            object.put("sortOrder", token.sortOrder);
            object.put("createdAt", token.createdAt);
            object.put("updatedAt", token.updatedAt);
            array.put(object);
        }
        return array;
    }

    private static JSONArray passwordNotesToJson(List<PasswordNote> notes) throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordNote note : notes) {
            JSONObject object = new JSONObject();
            object.put("type", note.type);
            object.put("title", note.title);
            object.put("primaryText", note.primaryText);
            object.put("secondaryText", note.secondaryText);
            object.put("contentJson", note.contentJson);
            object.put("createdAt", note.createdAt);
            object.put("updatedAt", note.updatedAt);
            array.put(object);
        }
        return array;
    }

    private static JSONArray passwordGenerationsToJson(List<PasswordGenerationRecord> records) throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordGenerationRecord record : records) {
            JSONObject object = new JSONObject();
            object.put("password", record.password);
            object.put("remark", record.remark);
            object.put("length", record.length);
            object.put("configSummary", record.configSummary);
            object.put("createdAt", record.createdAt);
            array.put(object);
        }
        return array;
    }

    private static List<ScanRecord> parseRecords(JSONArray array) throws Exception {
        List<ScanRecord> records = new ArrayList<>();
        if (array == null) return records;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            ScanRecord record = new ScanRecord();
            record.content = object.optString("content");
            record.type = object.optString("type", ScanRecord.detectType(record.content));
            record.title = object.optString("title", record.content);
            record.source = object.optString("source", "SCAN");
            record.thumbnailBase64 = object.optString("thumbnailBase64", "");
            record.isStarred = object.optBoolean("isStarred");
            record.timestamp = object.optLong("timestamp");
            records.add(record);
        }
        return records;
    }

    private static List<PasswordEntry> parsePasswords(JSONArray array) {
        List<PasswordEntry> entries = new ArrayList<>();
        if (array == null) return entries;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            PasswordEntry entry = new PasswordEntry();
            entry.title = object.optString("title", object.optString("remark", ""));
            entry.websiteDomain = object.optString("websiteDomain", "");
            entry.appPackageName = object.optString("appPackageName", "");
            entry.username = object.optString("username", object.optString("account", ""));
            entry.password = object.optString("password", "");
            entry.account = object.optString("account", "");
            entry.remark = object.optString("remark", "");
            entry.notes = object.optString("notes", "");
            entry.groupId = object.optString("groupId", "");
            entry.lastUsedAt = object.optLong("lastUsedAt", 0);
            entry.createdAt = object.optLong("createdAt", System.currentTimeMillis());
            entry.updatedAt = object.optLong("updatedAt", entry.createdAt);
            entries.add(entry);
        }
        return entries;
    }

    private static List<PasswordGroup> parseGroups(JSONArray array) {
        List<PasswordGroup> groups = new ArrayList<>();
        if (array == null) return groups;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            PasswordGroup group = new PasswordGroup();
            group.id = object.optString("id", PasswordGroup.DEFAULT_ID);
            group.name = object.optString("name", PasswordGroup.DEFAULT_NAME);
            group.sortOrder = object.optInt("sortOrder", 0);
            group.isDefault = object.optBoolean("isDefault", PasswordGroup.DEFAULT_ID.equals(group.id));
            group.createdAt = object.optLong("createdAt", System.currentTimeMillis());
            group.updatedAt = object.optLong("updatedAt", group.createdAt);
            groups.add(group);
        }
        return groups;
    }

    private static List<OtpToken> parseOtpTokens(JSONArray array) {
        List<OtpToken> tokens = new ArrayList<>();
        if (array == null) return tokens;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            OtpToken token = new OtpToken();
            token.accountName = object.optString("accountName", "");
            token.issuer = object.optString("issuer", "");
            token.secret = object.optString("secret", "");
            token.digits = object.optInt("digits", 6);
            token.period = object.optInt("period", 30);
            token.algorithm = object.optString("algorithm", "SHA1");
            token.pinned = object.optBoolean("pinned");
            token.sortOrder = object.optInt("sortOrder");
            token.createdAt = object.optLong("createdAt", System.currentTimeMillis());
            token.updatedAt = object.optLong("updatedAt", token.createdAt);
            tokens.add(token);
        }
        return tokens;
    }

    private static List<PasswordNote> parsePasswordNotes(JSONArray array) {
        List<PasswordNote> notes = new ArrayList<>();
        if (array == null) return notes;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            PasswordNote note = new PasswordNote();
            note.type = object.optString("type", PasswordNote.TYPE_SECURE_NOTE);
            note.title = object.optString("title", "");
            note.primaryText = object.optString("primaryText", "");
            note.secondaryText = object.optString("secondaryText", "");
            note.contentJson = object.optString("contentJson", "{}");
            note.createdAt = object.optLong("createdAt", System.currentTimeMillis());
            note.updatedAt = object.optLong("updatedAt", note.createdAt);
            notes.add(note);
        }
        return notes;
    }

    private static List<PasswordGenerationRecord> parsePasswordGenerations(JSONArray array) {
        List<PasswordGenerationRecord> records = new ArrayList<>();
        if (array == null) return records;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            PasswordGenerationRecord record = new PasswordGenerationRecord();
            record.password = object.optString("password", "");
            record.remark = object.optString("remark", "");
            record.length = object.optInt("length", record.password.length());
            record.configSummary = object.optString("configSummary", "");
            record.createdAt = object.optLong("createdAt", System.currentTimeMillis());
            records.add(record);
        }
        return records;
    }

    private static void normalizePasswordEntry(PasswordEntry entry) {
        if (entry == null) return;
        long now = System.currentTimeMillis();
        if (entry.title == null || entry.title.trim().isEmpty()) entry.title = nonEmpty(entry.remark);
        if (entry.username == null || entry.username.trim().isEmpty()) entry.username = nonEmpty(entry.account);
        if (entry.groupId == null || entry.groupId.trim().isEmpty()) entry.groupId = com.secureqr.scanner.data.model.PasswordGroup.DEFAULT_ID;
        if (entry.createdAt <= 0) entry.createdAt = now;
        if (entry.updatedAt <= 0) entry.updatedAt = entry.createdAt;
    }

    private static String normalizedRecoveryKey(Context context) {
        return SecureSecretStore.getSecret(context, PREFS, KEY_RECOVERY_KEY)
                .replace("-", "")
                .replace(" ", "")
                .trim()
                .toUpperCase(Locale.US);
    }

    private static String generateRecoveryKey() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < 16; i++) raw.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    private static String nonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static class BackupPayload {
        List<ScanRecord> records = new ArrayList<>();
        List<PasswordGroup> groups = new ArrayList<>();
        List<PasswordEntry> passwords = new ArrayList<>();
        List<OtpToken> otpTokens = new ArrayList<>();
        List<PasswordNote> passwordNotes = new ArrayList<>();
        List<PasswordGenerationRecord> passwordGenerations = new ArrayList<>();
        List<VaultItem> vaultItems = new ArrayList<>();
        List<VaultAttachment> vaultAttachments = new ArrayList<>();
    }

    private static WebDavBackupPreview previewOf(BackupPayload payload) {
        return new WebDavBackupPreview(payload.records.size(), payload.groups.size(), payload.passwords.size(),
                payload.otpTokens.size(), payload.passwordNotes.size(), payload.passwordGenerations.size(),
                payload.vaultItems.size(), payload.vaultAttachments.size());
    }

    private static class WebDavTarget {
        final WebDAVClient client;
        final String contentMode;

        WebDavTarget(WebDAVClient client, String contentMode) {
            this.client = client;
            this.contentMode = contentMode;
        }
    }
}
