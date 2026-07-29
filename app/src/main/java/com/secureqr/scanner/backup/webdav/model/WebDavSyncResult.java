package com.secureqr.scanner.backup.webdav.model;

/** Shared result model reserved for the adapter-based WebDAV sync flow. */
public final class WebDavSyncResult {
    public final int successCount;
    public final int targetCount;
    public final String error;

    public WebDavSyncResult(int successCount, int targetCount, String error) {
        this.successCount = successCount;
        this.targetCount = targetCount;
        this.error = error;
    }

    public boolean isSuccess() { return error == null && successCount > 0; }
}
