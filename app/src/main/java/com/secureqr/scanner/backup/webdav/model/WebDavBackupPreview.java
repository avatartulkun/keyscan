package com.secureqr.scanner.backup.webdav.model;

/** Public, transport-neutral summary of a WebDAV backup. */
public final class WebDavBackupPreview {
    public final int records;
    public final int passwordGroups;
    public final int passwords;
    public final int otpTokens;
    public final int passwordNotes;
    public final int passwordGenerations;
    public final int vaultItems;
    public final int vaultAttachments;

    public WebDavBackupPreview(int records, int passwordGroups, int passwords, int otpTokens,
                               int passwordNotes, int passwordGenerations, int vaultItems,
                               int vaultAttachments) {
        this.records = records;
        this.passwordGroups = passwordGroups;
        this.passwords = passwords;
        this.otpTokens = otpTokens;
        this.passwordNotes = passwordNotes;
        this.passwordGenerations = passwordGenerations;
        this.vaultItems = vaultItems;
        this.vaultAttachments = vaultAttachments;
    }
}
