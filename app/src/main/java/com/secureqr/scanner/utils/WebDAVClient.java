package com.secureqr.scanner.utils;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import com.secureqr.scanner.R;
import com.secureqr.scanner.network.NetworkAccessController;
import com.secureqr.scanner.network.NetworkBlockedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class WebDAVClient {
    private static final String TAG = "WebDAV";

    private final Context context;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    public WebDAVClient(Context context, String baseUrl, String username, String password) {
        this.context = context == null ? null : context.getApplicationContext();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.username = username;
        this.password = password;
    }

    public WebDAVClient(String baseUrl, String username, String password) {
        this(null, baseUrl, username, password);
    }

    public boolean upload(String remotePath, String data) {
        RequestBody body = RequestBody.create(data, MediaType.parse("text/plain; charset=utf-8"));
        Request request;
        try {
            request = authorizedBuilder(remotePath).put(body).build();
        } catch (IllegalArgumentException e) {
            logFailure("CONFIG_ERROR", e);
            return false;
        }
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            logFailure("NETWORK_ERROR", e);
            return false;
        }
    }

    /**
     * Uploads a binary stream without materializing it in memory. The caller retains ownership of
     * {@code inputStream} and must close it after this method returns.
     */
    public boolean uploadStream(String remotePath, InputStream inputStream, long contentLength) {
        if (inputStream == null || contentLength < 0) return false;
        RequestBody body = new RequestBody() {
            @Override public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override public long contentLength() {
                return contentLength;
            }

            @Override public void writeTo(BufferedSink sink) throws IOException {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    sink.write(buffer, 0, read);
                }
            }
        };
        Request request;
        try {
            request = authorizedBuilder(remotePath).put(body).build();
        } catch (IllegalArgumentException e) {
            logFailure("CONFIG_ERROR", e);
            return false;
        }
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            logFailure("NETWORK_ERROR", e);
            return false;
        }
    }

    /**
     * Uploads an unknown-length stream using the same authenticated PUT path as regular uploads.
     * This is used for encrypted streaming backup containers whose final size is not known in advance.
     */
    public boolean uploadStream(String remotePath, InputStream inputStream) {
        if (inputStream == null) return false;
        RequestBody body = new RequestBody() {
            @Override public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override public long contentLength() {
                return -1L;
            }

            @Override public void writeTo(BufferedSink sink) throws IOException {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    sink.write(buffer, 0, read);
                }
            }
        };
        Request request;
        try {
            request = authorizedBuilder(remotePath).put(body).build();
        } catch (IllegalArgumentException e) {
            logFailure("CONFIG_ERROR", e);
            return false;
        }
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            logFailure("NETWORK_ERROR", e);
            return false;
        }
    }

    public boolean ensureDirectory(String remotePath) {
        String path = remotePath.startsWith("/") ? remotePath : "/" + remotePath;
        if (!path.matches("/attachments")) return false;
        try {
            Request request = authorizedBuilder(path)
                    .method("MKCOL", RequestBody.create(new byte[0], null))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful() || response.code() == 405;
            }
        } catch (Exception e) {
            logFailure("SERVER_ERROR", e);
            return false;
        }
    }

    public String download(String remotePath) {
        Request request;
        try {
            request = authorizedBuilder(remotePath).get().build();
        } catch (IllegalArgumentException e) {
            logFailure("CONFIG_ERROR", e);
            return null;
        }
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return response.body().string();
        } catch (IOException e) {
            logFailure("NETWORK_ERROR", e);
            return null;
        }
    }

    /**
     * Downloads a binary response directly to a caller-owned stream. The caller must close
     * {@code outputStream} after this method returns.
     */
    public boolean downloadStream(String remotePath, OutputStream outputStream) {
        if (outputStream == null) return false;
        Request request;
        try {
            request = authorizedBuilder(remotePath).get().build();
        } catch (IllegalArgumentException e) {
            logFailure("CONFIG_ERROR", e);
            return false;
        }
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return false;
            try (InputStream input = response.body().byteStream()) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) outputStream.write(buffer, 0, read);
                outputStream.flush();
                return true;
            }
        } catch (IOException e) {
            logFailure("NETWORK_ERROR", e);
            return false;
        }
    }

    public boolean delete(String remotePath) {
        Request request;
        try {
            request = authorizedBuilder(remotePath).delete().build();
        } catch (IllegalArgumentException e) {
            logFailure("CONFIG_ERROR", e);
            return false;
        }
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            logFailure("NETWORK_ERROR", e);
            return false;
        }
    }

    public boolean testConnection() {
        return testConnectionDetailed().success;
    }

    public TestResult testConnectionDetailed() {
        RequestBody body = RequestBody.create(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><propfind xmlns=\"DAV:\"><prop><displayname/></prop></propfind>",
                MediaType.parse("application/xml; charset=utf-8"));
        Request request;
        try {
            if (context != null) NetworkAccessController.requireAllowed(context, baseUrl + "/", "WEBDAV_TEST");
            request = new Request.Builder()
                    .url(baseUrl + "/")
                    .method("PROPFIND", body)
                    .header("Depth", "0")
                    .header("Authorization", Credentials.basic(username, password))
                    .build();
        } catch (IllegalArgumentException e) {
            logFailure("CONFIG_ERROR", e);
            return TestResult.failure(context.getString(R.string.address_format_error));
        } catch (NetworkBlockedException e) {
            return TestResult.failure(context.getString(R.string.network_access_blocked));
        }
        long start = SystemClock.elapsedRealtimeNanos();
        try (Response response = client.newCall(request).execute()) {
            long elapsedMs = elapsedMs(start);
            if (response.isSuccessful()) return TestResult.success(elapsedMs);
            if (response.code() == 401 || response.code() == 403) return TestResult.failure(context.getString(R.string.authentication_failed));
            return TestResult.failure(context.getString(R.string.connection_failed));
        } catch (IOException e) {
            logFailure("NETWORK_ERROR", e);
            return TestResult.failure(shortNetworkReason(context, e));
        }
    }

    public List<String> listBackups() {
        List<BackupFile> files = listBackupFiles();
        List<String> paths = new ArrayList<>();
        for (BackupFile file : files) paths.add(file.path);
        return paths;
    }

    public List<BackupFile> listBackupFiles() {
        RequestBody body = RequestBody.create(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><propfind xmlns=\"DAV:\"><prop><displayname/><getcontentlength/><getlastmodified/></prop></propfind>",
                MediaType.parse("application/xml; charset=utf-8"));
        Request request;
        try {
            requireAllowed(baseUrl + "/", "WEBDAV_LIST");
            request = new Request.Builder()
                    .url(baseUrl + "/")
                    .method("PROPFIND", body)
                    .header("Depth", "1")
                    .header("Authorization", Credentials.basic(username, password))
                    .build();
        } catch (IllegalArgumentException e) {
            logFailure("CONFIG_ERROR", e);
            return Collections.emptyList();
        }
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
            return parseBackupFiles(response.body().string());
        } catch (IOException e) {
            logFailure("NETWORK_ERROR", e);
            return Collections.emptyList();
        }
    }

    private List<String> parseBackupPaths(String xml) {
        List<String> paths = new ArrayList<>();
        for (BackupFile file : parseBackupFiles(xml)) paths.add(file.path);
        Collections.sort(paths, (a, b) -> b.compareTo(a));
        return paths;
    }

    private List<BackupFile> parseBackupFiles(String xml) {
        List<BackupFile> files = new ArrayList<>();
        int index = 0;
        String lowerXml = xml.toLowerCase(Locale.US);
        while (true) {
            int start = nextResponseStart(lowerXml, index);
            if (start < 0) break;
            int end = nextResponseEnd(lowerXml, start);
            if (end < 0) break;
            String block = xml.substring(start, end);
            String href = firstTagValue(block, "href");
            String decoded = Uri.decode(href == null ? "" : href);
            int slash = decoded.lastIndexOf('/');
            String name = slash >= 0 ? decoded.substring(slash + 1) : decoded;
            if (isBackupName(name)) {
                long size = parseLong(firstTagValue(block, "getcontentlength"));
                String lastModified = firstTagValue(block, "getlastmodified");
                files.add(new BackupFile("/" + name, name, size, lastModified == null ? "" : lastModified));
            }
            index = end;
        }
        Collections.sort(files, (a, b) -> b.name.compareTo(a.name));
        return files;
    }

    private int nextResponseStart(String xml, int from) {
        int prefixed = xml.indexOf("<d:response", from);
        int plain = xml.indexOf("<response", from);
        if (prefixed < 0) return plain;
        if (plain < 0) return prefixed;
        return Math.min(prefixed, plain);
    }

    private int nextResponseEnd(String xml, int from) {
        int prefixed = xml.indexOf("</d:response>", from);
        int plain = xml.indexOf("</response>", from);
        if (prefixed < 0) return plain < 0 ? -1 : plain + "</response>".length();
        if (plain < 0) return prefixed + "</d:response>".length();
        return Math.min(prefixed + "</d:response>".length(), plain + "</response>".length());
    }

    private String firstTagValue(String xml, String tagName) {
        String lower = xml.toLowerCase(Locale.US);
        String lowerTag = tagName.toLowerCase(Locale.US);
        String[] openTags = {"<d:" + lowerTag, "<D:" + tagName, "<" + lowerTag};
        for (String openTag : openTags) {
            int open = lower.indexOf(openTag.toLowerCase(Locale.US));
            if (open < 0) continue;
            int contentStart = lower.indexOf('>', open);
            if (contentStart < 0) continue;
            String closeTag;
            if (openTag.toLowerCase(Locale.US).startsWith("<d:")) closeTag = "</d:" + lowerTag + ">";
            else closeTag = "</" + lowerTag + ">";
            int close = lower.indexOf(closeTag, contentStart + 1);
            if (close < 0) continue;
            return xml.substring(contentStart + 1, close).trim();
        }
        return null;
    }

    private boolean isBackupName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        return lower.equals("secure_backup.dat")
                || lower.matches("[a-z0-9_-]{1,32}_latest\\.dat")
                || lower.matches("[a-z0-9_-]{1,32}_[0-9]{8}_[0-9]{6}\\.dat");
    }

    private long parseLong(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Request.Builder authorizedBuilder(String remotePath) {
        String path = remotePath.startsWith("/") ? remotePath : "/" + remotePath;
        if (!isAllowedBackupPath(path)) throw new IllegalArgumentException("Unsupported WebDAV backup path");
        requireAllowed(baseUrl + path, "WEBDAV");
        return new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", Credentials.basic(username, password));
    }

    private boolean isAllowedBackupPath(String path) {
        if ("/secure_backup.dat".equals(path)) return true;
        if ("/attachments".equals(path)) return true;
        if (path.matches("/attachments/[A-Za-z0-9_-]+\\.enc")) return true;
        return path.matches("/[A-Za-z0-9_-]{1,32}_latest\\.dat")
                || path.matches("/[A-Za-z0-9_-]{1,32}_[0-9]{8}_[0-9]{6}\\.dat");
    }

    private void requireAllowed(String url, String purpose) {
        if (context == null) return;
        try {
            NetworkAccessController.requireAllowed(context, url, purpose);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private static long elapsedMs(long startNanos) {
        long diff = SystemClock.elapsedRealtimeNanos() - startNanos;
        return Math.max(0L, diff / 1_000_000L);
    }

    public static String shortNetworkReason(Context context, Throwable error) {
        if (error == null) return context.getString(R.string.connection_failed);
        String name = error.getClass().getSimpleName();
        if (name.contains("SocketTimeout")) return context.getString(R.string.connection_timeout);
        if (name.contains("UnknownHost")) return context.getString(R.string.dns_resolution_failed);
        if (name.contains("SSL") || name.contains("Cert")) return context.getString(R.string.tls_certificate_error);
        if (name.contains("Connect")) {
            String message = error.getMessage();
            if (message != null && message.toLowerCase(Locale.US).contains("refused")) return context.getString(R.string.port_not_open);
            return context.getString(R.string.host_unreachable);
        }
        String message = error.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.US);
            if (lower.contains("timeout")) return context.getString(R.string.connection_timeout);
            if (lower.contains("refused")) return context.getString(R.string.port_not_open);
            if (lower.contains("auth") || lower.contains("401") || lower.contains("403")) return context.getString(R.string.authentication_failed);
            if (lower.contains("host")) return context.getString(R.string.host_unreachable);
        }
        return context.getString(R.string.connection_failed);
    }

    private void logFailure(String type, Throwable error) {
        if (isDebuggable()) {
            Log.e(TAG, type, error);
        } else {
            Log.w(TAG, type);
        }
    }

    private boolean isDebuggable() {
        return context != null
                && (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public static final class BackupFile {
        public final String path;
        public final String name;
        public final long size;
        public final String lastModified;

        public BackupFile(String path, String name, long size, String lastModified) {
            this.path = path;
            this.name = name;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    public static final class TestResult {
        public final boolean success;
        public final long latencyMs;
        public final String reason;

        private TestResult(boolean success, long latencyMs, String reason) {
            this.success = success;
            this.latencyMs = latencyMs;
            this.reason = reason == null ? "" : reason;
        }

        public static TestResult success(long latencyMs) {
            return new TestResult(true, latencyMs, "");
        }

        public static TestResult failure(String reason) {
            return new TestResult(false, -1L, reason);
        }
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
