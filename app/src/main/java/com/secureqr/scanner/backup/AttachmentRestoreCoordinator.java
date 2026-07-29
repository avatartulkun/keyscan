package com.secureqr.scanner.backup;

import android.content.Context;
import com.secureqr.scanner.backup.source.StreamProvider;

import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.security.DatabaseKeyManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Restores v5 attachment entries directly into the current device's Keystore-protected VaultFileStore. */
public final class AttachmentRestoreCoordinator {
    private final Context context;
    private final VaultRepository vault;

    public AttachmentRestoreCoordinator(Context context) { this.context = context.getApplicationContext(); vault = new VaultRepository(this.context); }

    public void restore(StreamProvider source, String dataProtectionKey, BackupPayload payload, BackupRestoreResult result) throws Exception {
        if (payload.attachments.isEmpty()) return;
        verifyContainer(source, dataProtectionKey);
        Map<String, BackupAttachment> expected = new HashMap<>();
        for (BackupAttachment attachment : payload.attachments) {
            if (attachment.contentReference == null || attachment.contentReference.trim().isEmpty()) { result.attachmentSkipped++; continue; }
            expected.put(attachment.contentReference, attachment);
        }
        try (InputStream raw = source.openStream()) {
            try (ZipInputStream zip = new ZipInputStream(decrypting(raw, dataProtectionKey))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    BackupAttachment attachment = expected.remove(entry.getName());
                    if (attachment != null && !entry.isDirectory()) restoreOne(zip, attachment, result);
                    zip.closeEntry();
                }
            }
        }
        result.attachmentSkipped += expected.size();
    }

    /** Completes an authenticated read before any attachment is written locally. */
    private void verifyContainer(StreamProvider source, String dataProtectionKey) throws Exception {
        try (InputStream raw = source.openStream()) {
            try (ZipInputStream zip = new ZipInputStream(decrypting(raw, dataProtectionKey))) {
                byte[] buffer = new byte[32768];
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) while (zip.read(buffer) != -1) { }
            }
        }
    }

    private InputStream decrypting(InputStream raw, String dataProtectionKey) throws Exception {
        java.io.BufferedInputStream buffered = new java.io.BufferedInputStream(raw);
        if (BackupStreamCipher.isV6Container(buffered)) {
            String rootKey = DatabaseKeyManager.getBackupRootKey(context);
            if (rootKey.isEmpty()) throw new SecurityException("Vault root key is unavailable");
            return BackupStreamCipher.decryptingV6(buffered, rootKey);
        }
        return BackupStreamCipher.decrypting(buffered, dataProtectionKey);
    }

    private void restoreOne(InputStream content, BackupAttachment attachment, BackupRestoreResult result) throws Exception {
        CountDownLatch latch = new CountDownLatch(1); AtomicReference<Exception> failure = new AtomicReference<>(); AtomicReference<VaultRepository.AttachmentRestoreStatus> status = new AtomicReference<>();
        vault.restoreAttachment(attachment.attachmentId, attachment.vaultItemId, attachment.filename, attachment.mimeType,
                attachment.size, attachment.hash, content, value -> { status.set(value); latch.countDown(); }, error -> { failure.set(error); latch.countDown(); });
        latch.await();
        if (failure.get() != null) { result.attachmentFailed++; result.addError("Attachment restore failed"); return; }
        if (status.get() == VaultRepository.AttachmentRestoreStatus.RESTORED) result.attachmentSuccess++; else result.attachmentSkipped++;
    }
}
