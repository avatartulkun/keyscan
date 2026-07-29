package com.secureqr.scanner.backup;

import android.content.Context;

import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.security.DatabaseKeyManager;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes a v5 encrypted local backup container without plaintext staging or Base64 attachment payloads. */
public final class AttachmentBackupCoordinator {
    private final Context context;
    private final VaultRepository vault;

    public AttachmentBackupCoordinator(Context context) {
        this.context = context.getApplicationContext();
        vault = new VaultRepository(this.context);
    }

    public void write(OutputStream destination, BackupPayload payload, String dataProtectionKey) throws Exception {
        List<BackupAttachment> referenced = new ArrayList<>();
        for (BackupAttachment attachment : payload.attachments) {
            referenced.add(attachment.withContentReference(entryName(attachment)));
        }
        BackupPayload portable = new BackupPayload(BackupVersion.VERSION_5, payload.records, payload.passwordGroups,
                payload.passwords, payload.otpTokens, payload.passwordGenerations, payload.vaultItems, referenced);
        String databaseKey = DatabaseKeyManager.getDatabaseKey(context);
        String rootKey = DatabaseKeyManager.getBackupRootKey(context);
        if (databaseKey.isEmpty() || rootKey.isEmpty()) {
            throw new IllegalStateException("Secure vault key envelope is unavailable");
        }
        try (ZipOutputStream zip = new ZipOutputStream(
                BackupStreamCipher.encryptingV6(destination, databaseKey, rootKey))) {
            zip.putNextEntry(new ZipEntry("payload.json"));
            zip.write(portable.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            for (BackupAttachment attachment : referenced) writeAttachment(zip, attachment);
        }
    }

    private void writeAttachment(ZipOutputStream zip, BackupAttachment attachment) throws Exception {
        zip.putNextEntry(new ZipEntry(attachment.contentReference));
        CountDownLatch latch = new CountDownLatch(1); AtomicReference<Exception> error = new AtomicReference<>();
        vault.exportAttachment(attachment.attachmentId, zip, failure -> { error.set(failure); latch.countDown(); });
        latch.await();
        zip.closeEntry();
        if (error.get() != null) throw error.get();
    }

    static String entryName(BackupAttachment attachment) { return "attachments/" + attachment.attachmentId + ".bin"; }
}
