package com.secureqr.scanner.backup;

import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.model.VaultItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/** In-memory v5 backup data. Encryption and file output remain owned by callers. */
public final class BackupPayload {
    public final int version;
    public final List<ScanRecord> records;
    public final List<PasswordGroup> passwordGroups;
    public final List<PasswordEntry> passwords;
    public final List<OtpToken> otpTokens;
    public final List<PasswordGenerationRecord> passwordGenerations;
    public final List<VaultItem> vaultItems;
    public final List<BackupAttachment> attachments;

    public BackupPayload(List<ScanRecord> records, List<PasswordGroup> passwordGroups,
                         List<PasswordEntry> passwords, List<OtpToken> otpTokens, List<PasswordGenerationRecord> passwordGenerations,
                         List<VaultItem> vaultItems, List<BackupAttachment> attachments) {
        this(BackupVersion.VERSION_5, records, passwordGroups, passwords, otpTokens, passwordGenerations, vaultItems, attachments);
    }

    public BackupPayload(int version, List<ScanRecord> records, List<PasswordGroup> passwordGroups,
                         List<PasswordEntry> passwords, List<OtpToken> otpTokens, List<PasswordGenerationRecord> passwordGenerations,
                         List<VaultItem> vaultItems, List<BackupAttachment> attachments) {
        this.version = version;
        this.records = safe(records);
        this.passwordGroups = safe(passwordGroups);
        this.passwords = safe(passwords);
        this.otpTokens = safe(otpTokens);
        this.passwordGenerations = safe(passwordGenerations);
        this.vaultItems = safe(vaultItems);
        this.attachments = safe(attachments);
    }

    public String toJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", BackupVersion.VERSION_5);
        root.put("records", recordsToJson());
        root.put("passwordGroups", groupsToJson());
        root.put("passwords", passwordsToJson());
        root.put("otpTokens", otpTokensToJson());
        root.put("passwordGenerations", passwordGenerationsToJson());
        root.put("vaultItems", vaultItemsToJson());
        root.put("vaultAttachments", attachmentsToJson());
        return root.toString();
    }

    private JSONArray recordsToJson() throws Exception {
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

    private JSONArray groupsToJson() throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordGroup group : passwordGroups) {
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

    private JSONArray passwordGenerationsToJson() throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordGenerationRecord record : passwordGenerations) {
            JSONObject object = new JSONObject();
            object.put("itemId", record.itemId); object.put("password", record.password); object.put("remark", record.remark);
            object.put("length", record.length); object.put("configSummary", record.configSummary); object.put("createdAt", record.createdAt);
            object.put("source", record.source); object.put("website", record.website); object.put("account", record.account);
            object.put("linkedPasswordEntryItemId", record.linkedPasswordEntryItemId); array.put(object);
        }
        return array;
    }

    private JSONArray passwordsToJson() throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordEntry entry : passwords) {
            JSONObject object = new JSONObject();
            object.put("itemId", entry.itemId);
            object.put("otpItemId", entry.otpItemId);
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

    private JSONArray otpTokensToJson() throws Exception {
        JSONArray array = new JSONArray();
        for (OtpToken token : otpTokens) {
            JSONObject object = new JSONObject();
            object.put("itemId", token.itemId);
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

    private JSONArray vaultItemsToJson() throws Exception {
        JSONArray array = new JSONArray();
        for (VaultItem item : vaultItems) {
            JSONObject object = new JSONObject();
            object.put("id", item.id);
            object.put("type", item.type);
            object.put("category", item.category);
            object.put("title", item.title);
            object.put("fields", item.fieldsJson);
            object.put("notes", item.notes);
            object.put("createdTime", item.createdTime);
            object.put("updatedTime", item.updatedTime);
            array.put(object);
        }
        return array;
    }

    private JSONArray attachmentsToJson() throws Exception {
        JSONArray array = new JSONArray();
        for (BackupAttachment attachment : attachments) {
            JSONObject object = new JSONObject();
            object.put("id", attachment.attachmentId);
            object.put("itemId", attachment.vaultItemId);
            object.put("filename", attachment.filename);
            object.put("mimeType", attachment.mimeType);
            object.put("size", attachment.size);
            object.put("hash", attachment.hash);
            if (attachment.contentReference != null) object.put("contentReference", attachment.contentReference);
            array.put(object);
        }
        return array;
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? Collections.emptyList() : value;
    }
}
