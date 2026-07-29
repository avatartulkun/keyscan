package com.secureqr.scanner.backup.webdav.model;

/** Shared result model reserved for future version-routed WebDAV restore. */
public final class WebDavRestoreResult {
    public final WebDavBackupPreview preview;
    public final String error;

    public WebDavRestoreResult(WebDavBackupPreview preview, String error) {
        this.preview = preview;
        this.error = error;
    }

    public boolean isSuccess() { return error == null && preview != null; }
}
