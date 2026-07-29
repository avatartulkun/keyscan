package com.secureqr.scanner.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.TrashItemDao;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordNote;
import com.secureqr.scanner.data.model.TrashItem;
import com.secureqr.scanner.data.model.VaultAttachment;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.vault.VaultFileStore;
import com.secureqr.scanner.utils.DataSyncState;
import com.secureqr.scanner.R;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.io.File;

/** Local-only deleted-item storage. It is intentionally not part of backup serialization. */
public final class TrashRepository {
    public interface RestoreCallback { void onComplete(int restored, int failed, String message); }
    public static final class AttachmentResult {
        public final File file;
        public final Exception error;
        AttachmentResult(File file, Exception error) { this.file = file; this.error = error; }
    }
    public static final String PREFS = "trash_settings";
    public static final String KEY_RETENTION_DAYS = "retention_days";
    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final int KEEP_FOREVER = 0;
    private final Context context;
    private final AppDatabase db;
    private final TrashItemDao trash;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TrashRepository(Context context) {
        this.context = context.getApplicationContext();
        db = AppDatabase.getInstance(context);
        trash = db.trashItemDao();
        cleanupExpired();
    }

    public LiveData<List<TrashItem>> observeAll() { return trash.observeAll(); }

    public void getById(String id, Consumer<TrashItem> done) { executor.execute(() -> { if (done != null) done.accept(trash.findById(id)); }); }

    public void getVaultAttachmentNames(String itemId, Consumer<List<String>> done) {
        executor.execute(() -> { List<String> names = new ArrayList<>(); for (VaultAttachment attachment : db.vaultAttachmentDao().getForItemNow(itemId)) names.add(attachment.filename); if (done != null) done.accept(names); });
    }

    public void getVaultAttachments(String itemId, Consumer<List<VaultAttachment>> done) {
        executor.execute(() -> {
            List<VaultAttachment> result = new ArrayList<>();
            for (VaultAttachment source : db.vaultAttachmentDao().getForItemNow(itemId)) {
                VaultAttachment copy = new VaultAttachment();
                copy.id = source.id;
                copy.itemId = source.itemId;
                copy.filename = source.filename;
                copy.mimeType = source.mimeType;
                copy.hash = source.hash;
                copy.size = source.size;
                copy.createdTime = source.createdTime;
                result.add(copy);
            }
            if (done != null) done.accept(result);
        });
    }

    public void decryptVaultAttachment(String attachmentId, Consumer<AttachmentResult> done) {
        executor.execute(() -> {
            File file = null;
            Exception error = null;
            try {
                VaultAttachment attachment = db.vaultAttachmentDao().findById(attachmentId);
                if (attachment == null) throw new IllegalStateException(context.getString(R.string.trash_detail_attachment_missing));
                file = new VaultFileStore(context).decrypt(attachment.encryptedPath, attachment.filename);
            } catch (Exception failure) {
                error = failure;
            }
            if (done != null) done.accept(new AttachmentResult(file, error));
        });
    }

    public int retentionDays() { return prefs().getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS); }

    public void setRetentionDays(int days) {
        prefs().edit().putInt(KEY_RETENTION_DAYS, days).apply();
        cleanupExpired();
    }

    public void move(PasswordEntry value) { executeMove(TrashItem.PASSWORD, String.valueOf(value.id), value.displayTitle(), passwordJson(value), () -> db.passwordEntryDao().delete(value)); }
    public void move(OtpToken value) { executeMove(TrashItem.OTP, String.valueOf(value.id), first(value.issuer, value.accountName, "TOTP"), otpJson(value), () -> db.otpTokenDao().delete(value)); }
    public void move(PasswordNote value) { executeMove(TrashItem.NOTE, String.valueOf(value.id), first(value.title, context.getString(R.string.trash_default_secure_item)), noteJson(value), () -> db.passwordNoteDao().delete(value)); }
    public void move(VaultItem value) { executeMove(TrashItem.VAULT, value.id, first(value.title, context.getString(R.string.trash_default_secure_item)), vaultJson(value), () -> db.vaultItemDao().delete(value)); }

    public void restore(TrashItem item, Runnable done) {
        restoreManyWithResult(item == null ? null : java.util.Collections.singletonList(item), (ok,failed,message) -> { if (done != null && failed == 0) done.run(); });
    }
    public void restoreWithResult(TrashItem item, RestoreCallback callback) { restoreManyWithResult(item == null ? null : java.util.Collections.singletonList(item), callback); }

    public void restoreMany(List<TrashItem> items, Runnable done) {
        restoreManyWithResult(items, (ok,failed,message) -> { if (done != null) done.run(); });
    }

    public void restoreManyWithResult(List<TrashItem> items, RestoreCallback callback) {
        executor.execute(() -> { int ok=0, failed=0; String last=null; if(items!=null) for(TrashItem item:items){try{db.runInTransaction(() -> restoreNow(item));ok++;}catch(Exception error){failed++;last=error.getMessage();}} if(callback!=null)callback.onComplete(ok,failed,last); });
    }

    public void permanentlyDelete(TrashItem item, Runnable done) {
        executor.execute(() -> permanentlyDeleteNow(item));
        if (done != null) executor.execute(done);
    }

    public void permanentlyDeleteMany(List<TrashItem> items, Runnable done) {
        executor.execute(() -> { if (items != null) for (TrashItem item : items) permanentlyDeleteNow(item); });
        if (done != null) executor.execute(done);
    }

    public void clearAll(Runnable done) {
        executor.execute(() -> { for (TrashItem item : trash.getAllNow()) permanentlyDeleteNow(item); });
        if (done != null) executor.execute(done);
    }

    public void cleanupExpired() {
        int days = retentionDays();
        if (days == KEEP_FOREVER) return;
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        executor.execute(() -> {
            List<TrashItem> items = trash.getAllNow();
            // Attachment files need explicit cleanup; normal rows can be deleted in SQL.
            if (items != null) for (TrashItem item : items) if (item.deletedAt < cutoff && TrashItem.VAULT.equals(item.type)) deleteVaultFiles(item.originalId);
            trash.deleteOlderThan(cutoff);
        });
    }

    private void permanentlyDeleteNow(TrashItem item) {
        TrashItem stored = trash.findById(item.id);
        if (stored == null) return;
        if (TrashItem.VAULT.equals(stored.type)) deleteVaultFiles(stored.originalId);
        trash.deleteById(stored.id);
    }

    private void restoreNow(TrashItem item) {
        TrashItem stored = trash.findById(item.id);
        if (stored == null) return;
        try {
            JSONObject j = new JSONObject(stored.payload);
            ensureNoRestoreConflict(stored, j);
            switch (stored.type) {
                case TrashItem.PASSWORD: db.passwordEntryDao().insert(password(j)); break;
                case TrashItem.OTP: db.otpTokenDao().insert(otp(j)); break;
                case TrashItem.NOTE: db.passwordNoteDao().insert(note(j)); break;
                case TrashItem.VAULT: db.vaultItemDao().insert(vault(j)); break;
                default: return;
            }
            trash.deleteById(stored.id);
            DataSyncState.markDirty(context);
        } catch (Exception e) { throw new IllegalStateException(context.getString(R.string.trash_restore_content_failed), e); }
    }

    private void ensureNoRestoreConflict(TrashItem stored, JSONObject j) {
        long numericId=j.optLong("id",-1);
        if(TrashItem.PASSWORD.equals(stored.type)){PasswordEntry byId=numericId>0?db.passwordEntryDao().findById(numericId):null;PasswordEntry byItem=j.isNull("itemId")?null:db.passwordEntryDao().findByItemId(j.optString("itemId"));if(byId!=null||byItem!=null)throw new IllegalStateException(context.getString(R.string.trash_conflict_password));}
        if(TrashItem.OTP.equals(stored.type)){OtpToken byId=numericId>0?db.otpTokenDao().findById(numericId):null;OtpToken byItem=j.isNull("itemId")?null:db.otpTokenDao().findByItemId(j.optString("itemId"));if(byId!=null||byItem!=null)throw new IllegalStateException(context.getString(R.string.trash_conflict_otp));}
        if(TrashItem.NOTE.equals(stored.type)&&numericId>0&&db.passwordNoteDao().findById(numericId)!=null)throw new IllegalStateException(context.getString(R.string.trash_conflict_note));
        if(TrashItem.VAULT.equals(stored.type)&&db.vaultItemDao().findById(j.optString("id"))!=null)throw new IllegalStateException(context.getString(R.string.trash_conflict_vault));
    }

    private void deleteVaultFiles(String itemId) {
        VaultFileStore store = new VaultFileStore(context);
        for (VaultAttachment attachment : db.vaultAttachmentDao().getForItemNow(itemId)) store.delete(attachment.encryptedPath);
        db.vaultAttachmentDao().deleteForItem(itemId);
    }

    private void executeMove(String type, String originalId, String title, JSONObject payload, Runnable delete) {
        executor.execute(() -> db.runInTransaction(() -> {
            TrashItem item = new TrashItem(); item.type = type; item.originalId = originalId;
            item.title = first(title, context.getString(R.string.trash_unnamed)); item.payload = payload.toString(); item.deletedAt = System.currentTimeMillis();
            trash.insert(item); delete.run();
            DataSyncState.markDirty(context);
        }));
    }

    private SharedPreferences prefs() { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static String first(String... values) { for (String v : values) if (v != null && !v.trim().isEmpty()) return v; return ""; }
    private static void put(JSONObject j, String k, Object v) { try { j.put(k, v == null ? JSONObject.NULL : v); } catch (Exception ignored) {} }
    private static String s(JSONObject j, String k) { return j.isNull(k) ? null : j.optString(k, null); }

    private static JSONObject passwordJson(PasswordEntry v) { JSONObject j=new JSONObject(); put(j,"id",v.id);put(j,"itemId",v.itemId);put(j,"groupId",v.groupId);put(j,"otpId",v.otpId);put(j,"otpItemId",v.otpItemId);put(j,"title",v.title);put(j,"websiteDomain",v.websiteDomain);put(j,"appPackageName",v.appPackageName);put(j,"username",v.username);put(j,"password",v.password);put(j,"account",v.account);put(j,"remark",v.remark);put(j,"notes",v.notes);put(j,"lastUsedAt",v.lastUsedAt);put(j,"createdAt",v.createdAt);put(j,"updatedAt",v.updatedAt);return j; }
    private static PasswordEntry password(JSONObject j){PasswordEntry v=new PasswordEntry();v.id=j.optLong("id");v.itemId=s(j,"itemId");v.groupId=s(j,"groupId");v.otpId=j.isNull("otpId")?null:j.optLong("otpId");v.otpItemId=s(j,"otpItemId");v.title=s(j,"title");v.websiteDomain=s(j,"websiteDomain");v.appPackageName=s(j,"appPackageName");v.username=s(j,"username");v.password=s(j,"password");v.account=s(j,"account");v.remark=s(j,"remark");v.notes=s(j,"notes");v.lastUsedAt=j.optLong("lastUsedAt");v.createdAt=j.optLong("createdAt");v.updatedAt=j.optLong("updatedAt");return v;}
    private static JSONObject otpJson(OtpToken v){JSONObject j=new JSONObject();put(j,"id",v.id);put(j,"itemId",v.itemId);put(j,"accountName",v.accountName);put(j,"issuer",v.issuer);put(j,"secret",v.secret);put(j,"digits",v.digits);put(j,"period",v.period);put(j,"algorithm",v.algorithm);put(j,"pinned",v.pinned);put(j,"sortOrder",v.sortOrder);put(j,"createdAt",v.createdAt);put(j,"updatedAt",v.updatedAt);return j;}
    private static OtpToken otp(JSONObject j){OtpToken v=new OtpToken();v.id=j.optLong("id");v.itemId=s(j,"itemId");v.accountName=s(j,"accountName");v.issuer=s(j,"issuer");v.secret=s(j,"secret");v.digits=j.optInt("digits",6);v.period=j.optInt("period",30);v.algorithm=j.optString("algorithm","SHA1");v.pinned=j.optBoolean("pinned");v.sortOrder=j.optInt("sortOrder");v.createdAt=j.optLong("createdAt");v.updatedAt=j.optLong("updatedAt");return v;}
    private static JSONObject noteJson(PasswordNote v){JSONObject j=new JSONObject();put(j,"id",v.id);put(j,"type",v.type);put(j,"title",v.title);put(j,"primaryText",v.primaryText);put(j,"secondaryText",v.secondaryText);put(j,"contentJson",v.contentJson);put(j,"sourcePasswordEntryId",v.sourcePasswordEntryId);put(j,"createdAt",v.createdAt);put(j,"updatedAt",v.updatedAt);return j;}
    private static PasswordNote note(JSONObject j){PasswordNote v=new PasswordNote();v.id=j.optLong("id");v.type=s(j,"type");v.title=s(j,"title");v.primaryText=s(j,"primaryText");v.secondaryText=s(j,"secondaryText");v.contentJson=s(j,"contentJson");v.sourcePasswordEntryId=j.optLong("sourcePasswordEntryId");v.createdAt=j.optLong("createdAt");v.updatedAt=j.optLong("updatedAt");return v;}
    private static JSONObject vaultJson(VaultItem v){JSONObject j=new JSONObject();put(j,"id",v.id);put(j,"type",v.type);put(j,"category",v.category);put(j,"title",v.title);put(j,"fieldsJson",v.fieldsJson);put(j,"notes",v.notes);put(j,"createdTime",v.createdTime);put(j,"updatedTime",v.updatedTime);return j;}
    private static VaultItem vault(JSONObject j){VaultItem v=new VaultItem();v.id=j.optString("id",v.id);v.type=j.optString("type","CUSTOM");v.category=j.optString("category","CUSTOM");v.title=j.optString("title","");v.fieldsJson=j.optString("fieldsJson","{}");v.notes=j.optString("notes","");v.createdTime=j.optLong("createdTime");v.updatedTime=j.optLong("updatedTime");return v;}
}
