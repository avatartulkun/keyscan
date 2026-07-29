package com.secureqr.scanner.backup.source;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.InputStream;

/** Android Uri-backed source; no data is cached or copied. */
public final class LocalBackupStreamSource implements BackupStreamSource, StreamProvider {
    private final ContentResolver resolver;
    private final Uri uri;
    public LocalBackupStreamSource(Context context, Uri uri) { resolver = context.getApplicationContext().getContentResolver(); this.uri = uri; }
    @Override public InputStream openStream() throws Exception { InputStream input = resolver.openInputStream(uri); if (input == null) throw new IllegalStateException("Backup file is unavailable"); return input; }
}
