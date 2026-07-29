package com.secureqr.scanner.backup.source;

import java.io.InputStream;

/** Reopens an encrypted backup stream for validation and restore passes. */
public interface BackupStreamSource {
    InputStream openStream() throws Exception;
}
