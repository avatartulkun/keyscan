package com.secureqr.scanner.backup.source.webdav;

import com.secureqr.scanner.backup.source.BackupStreamSource;
import com.secureqr.scanner.utils.WebDAVClient;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streams one WebDAV backup download into a fresh input stream for each read pass.
 * The pipe only buffers in-flight bytes; it never caches a complete backup on disk or in memory.
 */
public final class WebDavBackupStreamSource implements BackupStreamSource {
    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    private final WebDAVClient client;
    private final String remotePath;

    public WebDavBackupStreamSource(WebDAVClient client, String remotePath) {
        if (client == null) throw new IllegalArgumentException("WebDAV client is required");
        if (remotePath == null || remotePath.trim().isEmpty()) throw new IllegalArgumentException("Remote path is required");
        this.client = client;
        this.remotePath = remotePath;
    }

    @Override
    public InputStream openStream() throws Exception {
        PipedInputStream input = new PipedInputStream(PIPE_BUFFER_SIZE);
        PipedOutputStream output = new PipedOutputStream(input);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread downloader = new Thread(() -> {
            try (PipedOutputStream destination = output) {
                if (!client.downloadStream(remotePath, destination)) {
                    failure.set(new IOException("WebDAV backup download failed"));
                }
            } catch (Exception error) {
                failure.set(error);
            }
        }, "KeyScan-WebDavBackupDownload");
        downloader.setDaemon(true);
        downloader.start();
        return new DownloadInputStream(input, failure);
    }

    private static final class DownloadInputStream extends FilterInputStream {
        private final AtomicReference<Exception> failure;

        DownloadInputStream(InputStream input, AtomicReference<Exception> failure) {
            super(input);
            this.failure = failure;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            throwIfDownloadFailed(value == -1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            throwIfDownloadFailed(count == -1);
            return count;
        }

        private void throwIfDownloadFailed(boolean finished) throws IOException {
            if (!finished) return;
            Exception error = failure.get();
            if (error instanceof IOException) throw (IOException) error;
            if (error != null) throw new IOException("WebDAV backup download failed", error);
        }
    }
}
