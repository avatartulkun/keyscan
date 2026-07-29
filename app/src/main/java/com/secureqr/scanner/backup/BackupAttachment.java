package com.secureqr.scanner.backup;

import com.secureqr.scanner.data.model.VaultAttachment;

/** Portable attachment metadata. Device-local encryption paths are intentionally excluded. */
public final class BackupAttachment {
    public final String attachmentId;
    public final String vaultItemId;
    public final String filename;
    public final String mimeType;
    public final long size;
    public final String hash;
    public final String contentReference;

    private BackupAttachment(String attachmentId, String vaultItemId, String filename, String mimeType,
                             long size, String hash, String contentReference) {
        this.attachmentId = attachmentId;
        this.vaultItemId = vaultItemId;
        this.filename = filename;
        this.mimeType = mimeType;
        this.size = size;
        this.hash = hash;
        this.contentReference = contentReference;
    }

    public static BackupAttachment from(VaultAttachment attachment) {
        return new BackupAttachment(attachment.id, attachment.itemId, attachment.filename,
                attachment.mimeType, attachment.size, attachment.hash, null);
    }

    static BackupAttachment create(String attachmentId, String vaultItemId, String filename, String mimeType,
                                   long size, String hash, String contentReference) {
        return new BackupAttachment(attachmentId, vaultItemId, filename, mimeType, size, hash, contentReference);
    }

    BackupAttachment withContentReference(String reference) {
        return new BackupAttachment(attachmentId, vaultItemId, filename, mimeType, size, hash, reference);
    }
}
