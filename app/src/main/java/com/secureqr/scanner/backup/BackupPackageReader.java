package com.secureqr.scanner.backup;

import android.content.Context;
import com.secureqr.scanner.backup.source.BackupStreamSource;

import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.utils.CryptoHelper;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.SecuritySettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads an encrypted local backup into memory without creating plaintext files. */
public final class BackupPackageReader {
    private final Context context;
    public BackupPackageReader(Context context) { this.context = context.getApplicationContext(); }

    /** Checks the v5 container header without retaining downloaded backup content. */
    public static boolean isV5Container(BackupStreamSource source) throws Exception {
        if (source == null) return false;
        try (InputStream raw = source.openStream()) {
            return BackupStreamCipher.isV5Container(new BufferedInputStream(raw));
        }
    }

    public BackupPayload read(BackupStreamSource source, String dataProtectionKey) throws Exception {
        if (source == null) throw new IllegalArgumentException("Backup source is required");
        try (InputStream raw = source.openStream()) {
            BufferedInputStream input = new BufferedInputStream(raw);
            if (BackupStreamCipher.isV6Container(input)) return readV6Container(input, dataProtectionKey);
            throw new SecurityException("LEGACY_BACKUP_SECURITY_UNSUPPORTED");
        }
    }

    private BackupPayload readV6Container(InputStream input, String dataProtectionKey) throws Exception {
        String currentDataKey = SecuritySettings.getDataEncryptionKey(context);
        if (dataProtectionKey == null || !dataProtectionKey.equals(currentDataKey)) {
            throw new SecurityException("Data protection key does not match this vault");
        }
        String rootKey = DatabaseKeyManager.getBackupRootKey(context);
        if (rootKey.isEmpty()) throw new SecurityException("Vault root key is unavailable");
        try (ZipInputStream zip = new ZipInputStream(BackupStreamCipher.decryptingV6(input, rootKey));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BackupPayload payload = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("payload.json".equals(entry.getName())) {
                    byte[] buffer = new byte[8192]; int read;
                    while ((read = zip.read(buffer)) != -1) output.write(buffer, 0, read);
                    payload = parse(output.toString(StandardCharsets.UTF_8.name()));
                } else {
                    byte[] buffer = new byte[8192];
                    while (zip.read(buffer) != -1) { }
                }
            }
            if (payload != null) return payload;
        }
        throw new IllegalStateException("Backup payload is missing");
    }

    private BackupPayload readLegacy(InputStream input, String dataProtectionKey) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return parse(decrypt(output.toString(StandardCharsets.UTF_8.name()), dataProtectionKey));
        }
    }

    private BackupPayload readV5Container(InputStream input, String dataProtectionKey) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(BackupStreamCipher.decrypting(input, dataProtectionKey));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BackupPayload payload = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("payload.json".equals(entry.getName())) {
                    byte[] buffer = new byte[8192]; int read;
                    while ((read = zip.read(buffer)) != -1) output.write(buffer, 0, read);
                    payload = parse(output.toString(StandardCharsets.UTF_8.name()));
                } else {
                    byte[] buffer = new byte[8192];
                    while (zip.read(buffer) != -1) { }
                }
            }
            if (payload != null) return payload;
        }
        throw new IllegalStateException("Backup payload is missing");
    }

    private String decrypt(String encrypted, String dataProtectionKey) throws Exception {
        String trimmed = encrypted == null ? "" : encrypted.trim();
        if (trimmed.startsWith("{")) {
            JSONObject envelope = new JSONObject(trimmed);
            if (envelope.optInt("keyscanBackupVersion") >= 4) {
                return CryptoHelper.decrypt(envelope.getString("payload"), dataProtectionKey);
            }
        }
        return CryptoHelper.decrypt(trimmed, dataProtectionKey);
    }

    private BackupPayload parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version", 3);
        if (version != 3 && version != BackupVersion.VERSION_5) {
            throw new IllegalStateException("KEYSCAN_BACKUP_VERSION_UNSUPPORTED");
        }
        return new BackupPayload(version, parseRecords(root.optJSONArray("records")),
                parseGroups(root.optJSONArray("passwordGroups")), parsePasswords(root.optJSONArray("passwords")),
                parseOtpTokens(root.optJSONArray("otpTokens")), parsePasswordGenerations(root.optJSONArray("passwordGenerations")), parseVaultItems(root.optJSONArray("vaultItems")),
                parseAttachments(root.optJSONArray("vaultAttachments")));
    }

    private List<ScanRecord> parseRecords(JSONArray array) {
        List<ScanRecord> result = new ArrayList<>(); if (array == null) return result;
        for (int i = 0; i < array.length(); i++) { JSONObject o = array.optJSONObject(i); if (o == null) continue;
            ScanRecord item = new ScanRecord(); item.content = o.optString("content"); item.type = o.optString("type", ScanRecord.detectType(item.content)); item.title = o.optString("title", item.content); item.source = o.optString("source", "SCAN"); item.thumbnailBase64 = o.optString("thumbnailBase64", ""); item.isStarred = o.optBoolean("isStarred"); item.timestamp = o.optLong("timestamp"); result.add(item); }
        return result;
    }

    private List<PasswordGroup> parseGroups(JSONArray array) {
        List<PasswordGroup> result = new ArrayList<>(); if (array == null) return result;
        for (int i = 0; i < array.length(); i++) { JSONObject o = array.optJSONObject(i); if (o == null) continue;
            PasswordGroup item = new PasswordGroup(); item.id = o.optString("id", PasswordGroup.DEFAULT_ID); item.name = o.optString("name", PasswordGroup.DEFAULT_NAME); item.sortOrder = o.optInt("sortOrder"); item.isDefault = o.optBoolean("isDefault", PasswordGroup.DEFAULT_ID.equals(item.id)); item.createdAt = o.optLong("createdAt", System.currentTimeMillis()); item.updatedAt = o.optLong("updatedAt", item.createdAt); result.add(item); }
        return result;
    }

    private List<PasswordEntry> parsePasswords(JSONArray array) {
        List<PasswordEntry> result = new ArrayList<>(); if (array == null) return result;
        for (int i = 0; i < array.length(); i++) { JSONObject o = array.optJSONObject(i); if (o == null) continue;
            PasswordEntry item = new PasswordEntry(); item.itemId=o.optString("itemId",null); item.otpItemId=o.optString("otpItemId",null); item.title = o.optString("title", o.optString("remark")); item.websiteDomain = o.optString("websiteDomain", ""); item.appPackageName = o.optString("appPackageName", ""); item.username = o.optString("username", o.optString("account")); item.password = o.optString("password"); item.account = o.optString("account"); item.remark = o.optString("remark"); item.notes = o.optString("notes", ""); item.groupId = o.optString("groupId", ""); item.lastUsedAt = o.optLong("lastUsedAt"); item.createdAt = o.optLong("createdAt"); item.updatedAt = o.optLong("updatedAt", item.createdAt); result.add(item); }
        return result;
    }

    private List<OtpToken> parseOtpTokens(JSONArray array) {
        List<OtpToken> result = new ArrayList<>(); if (array == null) return result;
        for (int i = 0; i < array.length(); i++) { JSONObject o = array.optJSONObject(i); if (o == null) continue;
            OtpToken item = new OtpToken(); item.itemId=o.optString("itemId",null); item.accountName = o.optString("accountName"); item.issuer = o.optString("issuer"); item.secret = o.optString("secret"); item.digits = o.optInt("digits", 6); item.period = o.optInt("period", 30); item.algorithm = o.optString("algorithm", "SHA1"); item.pinned = o.optBoolean("pinned"); item.sortOrder = o.optInt("sortOrder"); item.createdAt = o.optLong("createdAt"); item.updatedAt = o.optLong("updatedAt", item.createdAt); result.add(item); }
        return result;
    }

    private List<PasswordGenerationRecord> parsePasswordGenerations(JSONArray array) {
        List<PasswordGenerationRecord> result=new ArrayList<>(); if(array==null)return result;
        for(int i=0;i<array.length();i++){JSONObject o=array.optJSONObject(i);if(o==null)continue;PasswordGenerationRecord item=new PasswordGenerationRecord();
            item.itemId=o.optString("itemId",null);item.password=o.optString("password",null);item.remark=o.optString("remark",null);item.length=o.optInt("length");item.configSummary=o.optString("configSummary",null);item.createdAt=o.optLong("createdAt");item.source=o.optString("source",PasswordGenerationRecord.SOURCE_GENERATOR);item.website=o.optString("website",null);item.account=o.optString("account",null);item.linkedPasswordEntryItemId=o.optString("linkedPasswordEntryItemId",null);result.add(item);}
        return result;
    }

    private List<VaultItem> parseVaultItems(JSONArray array) {
        List<VaultItem> result = new ArrayList<>(); if (array == null) return result;
        for (int i = 0; i < array.length(); i++) { JSONObject o = array.optJSONObject(i); if (o == null) continue;
            VaultItem item = new VaultItem(); item.id = o.optString("id", item.id); item.type = o.optString("type", "CUSTOM"); item.category = o.optString("category", "CUSTOM"); item.title = o.optString("title", ""); item.fieldsJson = o.optString("fields", "{}"); item.notes = o.optString("notes", ""); item.createdTime = o.optLong("createdTime", System.currentTimeMillis()); item.updatedTime = o.optLong("updatedTime", item.createdTime); result.add(item); }
        return result;
    }

    private List<BackupAttachment> parseAttachments(JSONArray array) {
        List<BackupAttachment> result = new ArrayList<>(); if (array == null) return result;
        for (int i = 0; i < array.length(); i++) { JSONObject o = array.optJSONObject(i); if (o == null) continue;
            result.add(BackupAttachment.create(o.optString("id"), o.optString("itemId"), o.optString("filename"), o.optString("mimeType", "application/octet-stream"), o.optLong("size"), o.optString("hash"), o.optString("contentReference", null))); }
        return result;
    }
}
