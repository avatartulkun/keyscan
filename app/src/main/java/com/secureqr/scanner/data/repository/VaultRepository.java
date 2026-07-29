package com.secureqr.scanner.data.repository;

import android.content.Context;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.VaultAttachmentDao;
import com.secureqr.scanner.data.database.VaultItemDao;
import com.secureqr.scanner.data.model.VaultAttachment;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.DataSyncState;
import com.secureqr.scanner.vault.VaultFileStore;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public final class VaultRepository {
    private final Context context; private final VaultItemDao items; private final VaultAttachmentDao attachments;
    private final VaultFileStore files; private final ExecutorService executor=Executors.newSingleThreadExecutor();
    public VaultRepository(Context context){ this.context=context.getApplicationContext(); AppDatabase db=AppDatabase.getInstance(context); items=db.vaultItemDao(); attachments=db.vaultAttachmentDao(); files=new VaultFileStore(context); }
    public LiveData<List<VaultItem>> observe(String query){ if(!canAccessSensitiveData())return emptyItemLiveData(); return query==null||query.trim().isEmpty()?items.observeAll():items.search(query.trim()); }
    public void getById(String id, Consumer<VaultItem> done){ if(!canAccessSensitiveData()){if(done!=null)done.accept(null);return;} executor.execute(()->done.accept(items.findById(id))); }
    public LiveData<List<VaultAttachment>> observeAttachments(String id){ if(!canAccessSensitiveData())return emptyAttachmentLiveData(); return attachments.observeForItem(id); }
    public void save(VaultItem item, Runnable done){ if(!canAccessSensitiveData())return; executor.execute(()->{ long now=System.currentTimeMillis(); if(item.createdTime<=0)item.createdTime=now; item.updatedTime=now; if(items.findById(item.id)==null)items.insert(item);else items.update(item); DataSyncState.markDirty(context); if(done!=null)done.run(); }); }
    public void delete(VaultItem item){ if(!canAccessSensitiveData())return; new TrashRepository(context).move(item); }
    public void addAttachment(String itemId, Uri uri, String mime, Consumer<Exception> done){ if(!canAccessSensitiveData()){if(done!=null)done.accept(new SecurityException("Vault is locked"));return;} executor.execute(()->{ Exception error=null; try{ VaultAttachment a=new VaultAttachment(); a.itemId=itemId; a.filename=files.filename(uri); a.mimeType=mime==null?"application/octet-stream":mime; VaultFileStore.Stored s=files.encrypt(uri,a.id); a.encryptedPath=s.path;a.hash=s.hash;a.size=s.size;a.createdTime=System.currentTimeMillis();attachments.insert(a);DataSyncState.markDirty(context);}catch(Exception e){error=e;} if(done!=null)done.accept(error); }); }
    public void addAttachment(String itemId, Uri uri, String mime, String filename, Consumer<Exception> done){ if(!canAccessSensitiveData()){if(done!=null)done.accept(new SecurityException("Vault is locked"));return;} executor.execute(()->{ Exception error=null; try{ VaultAttachment a=new VaultAttachment();a.itemId=itemId;a.filename=filename==null||filename.trim().isEmpty()?files.filename(uri):filename;a.mimeType=mime==null?"application/octet-stream":mime;VaultFileStore.Stored s=files.encrypt(uri,a.id);a.encryptedPath=s.path;a.hash=s.hash;a.size=s.size;a.createdTime=System.currentTimeMillis();attachments.insert(a);DataSyncState.markDirty(context);}catch(Exception e){error=e;}if(done!=null)done.accept(error);}); }
    public void deleteAttachment(VaultAttachment a){ if(!canAccessSensitiveData())return; executor.execute(()->{files.delete(a.encryptedPath);attachments.delete(a);DataSyncState.markDirty(context);}); }
    public void deleteAttachmentById(String id, Runnable done){ if(!canAccessSensitiveData()){if(done!=null)done.run();return;} executor.execute(()->{VaultAttachment a=attachments.findById(id);if(a!=null){files.delete(a.encryptedPath);attachments.delete(a);DataSyncState.markDirty(context);}if(done!=null)done.run();}); }
    public void decrypt(VaultAttachment a, Consumer<FileResult> done){ if(!canAccessSensitiveData()){if(done!=null)done.accept(new FileResult(null,new SecurityException("Vault is locked")));return;} executor.execute(()->{try{done.accept(new FileResult(files.decrypt(a.encryptedPath,a.filename),null));}catch(Exception e){done.accept(new FileResult(null,e));}}); }
    public void getAllNow(Consumer<List<VaultItem>> done){if(!canAccessSensitiveData()){if(done!=null)done.accept(Collections.emptyList());return;} executor.execute(()->done.accept(items.getAllNow()));}
    /** Returns portable attachment metadata only; callers cannot access device-local paths through it. */
    public void getAllAttachments(Consumer<List<VaultAttachment>> done){if(!canAccessSensitiveData()){if(done!=null)done.accept(Collections.emptyList());return;} executor.execute(()->{List<VaultAttachment> source=attachments.getAllNow();List<VaultAttachment> safe=new ArrayList<>();for(VaultAttachment attachment:source){VaultAttachment copy=new VaultAttachment();copy.id=attachment.id;copy.itemId=attachment.itemId;copy.filename=attachment.filename;copy.mimeType=attachment.mimeType;copy.hash=attachment.hash;copy.size=attachment.size;copy.createdTime=attachment.createdTime;safe.add(copy);}done.accept(safe);});}
    /** Streams one attachment to a caller-owned output without exposing its local encrypted path. */
    public void exportAttachment(String attachmentId, OutputStream output, Consumer<Exception> done){if(!canAccessSensitiveData()){if(done!=null)done.accept(new SecurityException("Vault is locked"));return;}executor.execute(()->{Exception error=null;try{VaultAttachment a=attachments.findById(attachmentId);if(a==null)throw new IllegalStateException("Attachment unavailable");files.decryptTo(a.encryptedPath,output);}catch(Exception e){error=e;}if(done!=null)done.accept(error);});}
    /** Exports vault metadata and plaintext attachments into one caller-owned ZIP stream. */
    public void exportArchive(String vaultJson, OutputStream output, Consumer<Exception> done) {
        if (!canAccessSensitiveData()) {
            if (done != null) done.accept(new SecurityException("Vault is locked"));
            return;
        }
        executor.execute(() -> {
            Exception error = null;
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                writeZipText(zip, "vault.json", vaultJson == null ? "[]" : vaultJson);
                JSONArray manifest = new JSONArray();
                for (VaultAttachment attachment : attachments.getAllNow()) {
                    String path = "attachments/" + safeZipPart(attachment.itemId) + "/"
                            + safeZipPart(attachment.id) + "_" + safeZipPart(attachment.filename);
                    JSONObject metadata = new JSONObject();
                    metadata.put("id", attachment.id);
                    metadata.put("itemId", attachment.itemId);
                    metadata.put("filename", attachment.filename);
                    metadata.put("mimeType", attachment.mimeType);
                    metadata.put("size", attachment.size);
                    metadata.put("hash", attachment.hash);
                    metadata.put("path", path);
                    manifest.put(metadata);
                    zip.putNextEntry(new ZipEntry(path));
                    files.decryptTo(attachment.encryptedPath, zip);
                    zip.closeEntry();
                }
                writeZipText(zip, "manifest.json", manifest.toString(2));
                zip.finish();
            } catch (Exception e) {
                error = e;
            }
            if (done != null) done.accept(error);
        });
    }
    private static void writeZipText(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static String safeZipPart(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        while (safe.contains("..")) safe = safe.replace("..", "_");
        return safe.isEmpty() ? "unnamed" : safe;
    }
    public enum AttachmentRestoreStatus { RESTORED, SKIPPED }
    /** Re-encrypts a verified backup attachment with the current device Keystore before persisting metadata. */
    public void restoreAttachment(String attachmentId,String itemId,String filename,String mimeType,long size,String hash,InputStream plain,Consumer<AttachmentRestoreStatus> done,Consumer<Exception> failure){if(!canAccessSensitiveData()){if(failure!=null)failure.accept(new SecurityException("Vault is locked"));return;}executor.execute(()->{try{if(attachments.findById(attachmentId)!=null){if(done!=null)done.accept(AttachmentRestoreStatus.SKIPPED);return;}VaultFileStore.Stored saved=files.encrypt(plain,attachmentId,size,hash);VaultAttachment restored=new VaultAttachment();restored.id=attachmentId;restored.itemId=itemId;restored.filename=filename==null?"":filename;restored.mimeType=mimeType==null?"application/octet-stream":mimeType;restored.encryptedPath=saved.path;restored.hash=saved.hash;restored.size=saved.size;restored.createdTime=System.currentTimeMillis();attachments.insert(restored);DataSyncState.markDirty(context);if(done!=null)done.accept(AttachmentRestoreStatus.RESTORED);}catch(Exception e){if(failure!=null)failure.accept(e);}});}
    public void getAttachmentNames(Consumer<Map<String,List<String>>> done){if(!canAccessSensitiveData()){if(done!=null)done.accept(Collections.emptyMap());return;} executor.execute(()->{Map<String,List<String>> out=new HashMap<>();for(VaultAttachment a:attachments.getAllNow()){List<String> names=out.get(a.itemId);if(names==null){names=new ArrayList<>();out.put(a.itemId,names);}names.add(a.filename==null?"":a.filename);}done.accept(out);});}
    public static final class FileResult { public final java.io.File file; public final Exception error; FileResult(java.io.File f,Exception e){file=f;error=e;} }
    private boolean canAccessSensitiveData(){return VaultAccessManager.canAccessSensitiveData(context);}
    private LiveData<List<VaultItem>> emptyItemLiveData(){MutableLiveData<List<VaultItem>> data=new MutableLiveData<>();data.setValue(Collections.emptyList());return data;}
    private LiveData<List<VaultAttachment>> emptyAttachmentLiveData(){MutableLiveData<List<VaultAttachment>> data=new MutableLiveData<>();data.setValue(Collections.emptyList());return data;}
}
