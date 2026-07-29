package com.secureqr.scanner.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.secureqr.scanner.R;
import com.secureqr.scanner.network.NetworkAccessController;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanFileTransferServer {
    public interface Listener {
        void onStateChanged();
        void onExpired();
        void onError(Exception error);
    }

    public static final class FileItem {
        public final String fileId;
        public final Uri uri;
        public final String name;
        public final String mimeType;
        public final long size;
        public final boolean risky;
        public final byte[] inlineContent;

        public FileItem(Uri uri, String name, String mimeType, long size) {
            this(uri, name, mimeType, size, null);
        }

        private FileItem(Uri uri, String name, String mimeType, long size, byte[] inlineContent) {
            this.fileId = UUID.randomUUID().toString();
            this.uri = uri;
            this.name = name == null || name.trim().isEmpty() ? "file" : name.trim();
            this.mimeType = mimeType == null || mimeType.trim().isEmpty() ? "application/octet-stream" : mimeType.trim();
            this.size = Math.max(0L, size);
            String lower = this.name.toLowerCase(Locale.US);
            this.risky = lower.endsWith(".apk") || "application/vnd.android.package-archive".equalsIgnoreCase(this.mimeType);
            this.inlineContent = inlineContent;
        }

        public static FileItem fromText(String text) {
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            return new FileItem(null, "shared_text.txt", "text/plain; charset=utf-8", bytes.length, bytes);
        }
    }

    public static final class ReceiverSession {
        public final String sessionId;
        public final long createdAt;
        public volatile long lastSeenAt;
        public volatile boolean completed;

        ReceiverSession(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
            this.lastSeenAt = this.createdAt;
        }
    }

    private static final String START_PATH = "/s";
    private static final String AUTH_PATH = "/auth";
    private static final String API_STATE_PATH = "/api/state";
    private static final String DOWNLOAD_PATH = "/download";
    private static final int ACCEPT_TIMEOUT_MS = 1000;
    private static final int TOKEN_BYTES = 20;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final Context appContext;
    private final List<FileItem> files;
    private final long totalSize;
    private final int maxDevices;
    private final boolean requirePassword;
    private final boolean requireConfirmation;
    private final String accessPassword;
    private final long expiresAt;
    private final Listener listener;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Map<String, ReceiverSession> sessions = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private Thread thread;
    private InetAddress bindAddress;
    private int port;
    private final String sessionId = randomToken();
    private final String requestToken = randomToken();

    public LanFileTransferServer(Context context, List<FileItem> files, String accessPassword, long expiresAt, int maxDevices, boolean requirePassword, boolean requireConfirmation, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.files = new ArrayList<>(files == null ? Collections.emptyList() : files);
        long size = 0L;
        for (FileItem item : this.files) size += Math.max(0L, item.size);
        this.totalSize = size;
        this.accessPassword = accessPassword == null ? "" : accessPassword;
        this.expiresAt = expiresAt;
        this.maxDevices = Math.max(1, maxDevices);
        this.requirePassword = requirePassword;
        this.requireConfirmation = requireConfirmation;
        this.listener = listener;
    }

    public void start(String bindIp) throws IOException {
        if (!started.compareAndSet(false, true)) return;
        bindAddress = InetAddress.getByName(bindIp);
        serverSocket = new ServerSocket(0, 4, bindAddress);
        serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);
        port = serverSocket.getLocalPort();
        thread = new Thread(this::serveLoop, "KeyScanLanFileTransfer");
        thread.start();
    }

    public void stop() {
        stopped.set(true);
        closeQuietly();
        sessions.clear();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRequestToken() {
        return requestToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public int getPort() {
        return port;
    }

    public String getShareUrl() {
        return "http://" + bindAddress.getHostAddress() + ":" + port + START_PATH + "/" + sessionId + "?token=" + requestToken;
    }

    public String getBrowserEntryUrl() {
        String credential = requirePassword ? "&credential=" + Uri.encode(accessPassword) : "";
        return getShareUrl() + credential;
    }

    public String getAccessPassword() {
        return accessPassword;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public int getFileCount() {
        return files.size();
    }

    public int getActiveSessionCount() {
        int count = 0;
        for (ReceiverSession session : sessions.values()) {
            if (!session.completed) count++;
        }
        return count;
    }

    public int getCompletedSessionCount() {
        int count = 0;
        for (ReceiverSession session : sessions.values()) {
            if (session.completed) count++;
        }
        return count;
    }

    public boolean isRequirePassword() {
        return requirePassword;
    }

    public boolean isRequireConfirmation() {
        return requireConfirmation;
    }

    public List<FileItem> getFiles() {
        return new ArrayList<>(files);
    }

    public JSONObject buildQrPayload() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("protocolVersion", 1);
        payload.put("type", "keyscan_lan_file_transfer");
        payload.put("sessionId", sessionId);
        payload.put("senderIp", bindAddress.getHostAddress());
        payload.put("senderPort", port);
        payload.put("requestToken", requestToken);
        payload.put("accessCredential", requirePassword ? accessPassword : "");
        payload.put("expiresAt", expiresAt);
        payload.put("fileCount", getFileCount());
        payload.put("totalSize", totalSize);
        return payload;
    }

    public String statusSummary() {
        if (stopped.get()) return appContext.getString(R.string.lan_status_ended);
        int active = getActiveSessionCount();
        if (active <= 0) return appContext.getString(R.string.lan_web_waiting_for_devices);
        return appContext.getResources().getQuantityString(R.plurals.lan_web_devices_accessing, active, active);
    }

    private void serveLoop() {
        try {
            while (!stopped.get()) {
                if (System.currentTimeMillis() >= expiresAt) {
                    if (stopped.compareAndSet(false, true) && listener != null) listener.onExpired();
                    break;
                }
                try {
                    handle(serverSocket.accept());
                } catch (SocketTimeoutException ignored) {
                    // Check expiration and cancellation regularly.
                }
            }
        } catch (Exception e) {
            if (!stopped.get() && listener != null) listener.onError(e);
        } finally {
            closeQuietly();
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             OutputStream output = client.getOutputStream()) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                write(output, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request");
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                write(output, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request");
                return;
            }
            String method = parts[0].toUpperCase(Locale.US);
            String target = parts[1];
            String path = target;
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }
            Map<String, String> headers = readHeaders(reader);
            if ("OPTIONS".equals(method)) {
                writeCors(output, 204, "No Content", "", "text/plain; charset=utf-8");
                return;
            }
            if (path.equals("/") || path.equals(START_PATH + "/" + sessionId)) {
                if (!requestToken.equals(queryParam(query, "token")) && !requestToken.equals(headers.get("x-keyscan-token"))) {
                    write(output, 403, "Forbidden", "text/plain; charset=utf-8", "Forbidden");
                    return;
                }
                String credential = queryParam(query, "credential");
                if ((!requirePassword || accessPassword.equals(credential)) && getActiveSessionCount() < maxDevices) {
                    String sid = randomToken();
                    sessions.put(sid, new ReceiverSession(sid));
                    if (listener != null) listener.onStateChanged();
                    write(output, 200, "OK", "text/html; charset=utf-8", fileListPage(sid));
                } else {
                    write(output, 200, "OK", "text/html; charset=utf-8", loginPage());
                }
                return;
            }
            if (AUTH_PATH.equals(path)) {
                handleAuth(output, query, headers, reader);
                return;
            }
            if (API_STATE_PATH.equals(path)) {
                handleState(output, query, headers);
                return;
            }
            if (path.startsWith(DOWNLOAD_PATH)) {
                handleDownload(output, path, query, headers);
                return;
            }
            write(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found");
        } catch (IOException e) {
            if (!stopped.get() && listener != null) listener.onError(e);
        }
    }

    private void handleAuth(OutputStream output, String query, Map<String, String> headers, BufferedReader reader) throws IOException {
        if (System.currentTimeMillis() >= expiresAt) {
            write(output, 410, "Gone", "text/plain; charset=utf-8", "Expired");
            return;
        }
        if (!requestToken.equals(queryParam(query, "token")) && !requestToken.equals(headers.get("x-keyscan-token"))) {
            write(output, 403, "Forbidden", "text/plain; charset=utf-8", "Forbidden");
            return;
        }
        if (getActiveSessionCount() >= maxDevices) {
            write(output, 429, "Too Many Requests", "text/plain; charset=utf-8", "Too many devices");
            return;
        }
        Map<String, String> form = readFormBody(reader, headers);
        String password = form.getOrDefault("password", "");
        if (requirePassword && !accessPassword.equals(password)) {
            write(output, 401, "Unauthorized", "text/html; charset=utf-8", loginPage(appContext.getString(R.string.lan_web_wrong_password)));
            return;
        }
        String sid = randomToken();
        ReceiverSession session = new ReceiverSession(sid);
        sessions.put(sid, session);
        if (listener != null) listener.onStateChanged();
        write(output, 200, "OK", "text/html; charset=utf-8", fileListPage(sid));
    }

    private void handleState(OutputStream output, String query, Map<String, String> headers) throws IOException {
        String sid = sessionFrom(query, headers);
        ReceiverSession session = sid.isEmpty() ? null : sessions.get(sid);
        if (session == null) {
            write(output, 403, "Forbidden", "application/json; charset=utf-8", "{\"ok\":false}");
            return;
        }
        session.lastSeenAt = System.currentTimeMillis();
        JSONObject result = new JSONObject();
        try {
            result.put("ok", true);
            result.put("completed", session.completed);
            result.put("activeSessions", getActiveSessionCount());
            result.put("completedSessions", getCompletedSessionCount());
            result.put("maxDevices", maxDevices);
            result.put("status", statusSummary());
        } catch (JSONException e) {
            throw new IOException("Unable to build state response", e);
        }
        write(output, 200, "OK", "application/json; charset=utf-8", result.toString());
    }

    private void handleDownload(OutputStream output, String path, String query, Map<String, String> headers) throws IOException {
        String sid = sessionFrom(query, headers);
        ReceiverSession session = sid.isEmpty() ? null : sessions.get(sid);
        if (session == null || System.currentTimeMillis() >= expiresAt) {
            write(output, 403, "Forbidden", "text/plain; charset=utf-8", "Forbidden");
            return;
        }
        String fileId = queryParam(query, "file");
        if (fileId.isEmpty() && path.startsWith(DOWNLOAD_PATH + "/")) {
            fileId = path.substring((DOWNLOAD_PATH + "/").length());
        }
        FileItem item = null;
        for (FileItem candidate : files) {
            if (candidate.fileId.equals(fileId)) {
                item = candidate;
                break;
            }
        }
        if (item == null) {
            write(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found");
            return;
        }
        session.lastSeenAt = System.currentTimeMillis();
        session.completed = true;
        if (listener != null) listener.onStateChanged();
        if (item.inlineContent != null) {
            String disposition = "attachment; filename=\"" + sanitizeFilename(item.name) + "\"";
            String headersText = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + item.mimeType + "\r\n"
                    + "Content-Disposition: " + disposition + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n"
                    + "Content-Length: " + item.inlineContent.length + "\r\n\r\n";
            output.write(headersText.getBytes(StandardCharsets.UTF_8));
            output.write(item.inlineContent);
            output.flush();
            return;
        }
        ContentResolver resolver = appContext.getContentResolver();
        try (InputStream input = resolver.openInputStream(item.uri)) {
            if (input == null) {
                write(output, 500, "Internal Server Error", "text/plain; charset=utf-8", "Cannot open file");
                return;
            }
            String disposition = "attachment; filename=\"" + sanitizeFilename(item.name) + "\"";
            String headersText = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + item.mimeType + "\r\n"
                    + "Content-Disposition: " + disposition + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n";
            if (item.size > 0) {
                headersText += "Content-Length: " + item.size + "\r\n";
            }
            headersText += "\r\n";
            output.write(headersText.getBytes(StandardCharsets.UTF_8));
            copy(input, output);
            output.flush();
        }
    }

    private String loginPage() {
        return loginPage(null);
    }

    private String loginPage(String error) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        builder.append("<title>").append(escapeHtml(appContext.getString(R.string.lan_web_page_title))).append("</title>");
        builder.append("<style>");
        builder.append("body{font-family:sans-serif;margin:24px;color:#1f2937;background:#f7f7fb;}");
        builder.append(".card{max-width:720px;margin:0 auto;background:#fff;border:1px solid #dfe3ea;border-radius:16px;padding:20px;box-shadow:0 2px 8px rgba(0,0,0,.05);} ");
        builder.append("input,button{font:inherit;padding:12px 14px;border-radius:10px;border:1px solid #c8ced8;width:100%;box-sizing:border-box;}");
        builder.append("button{background:#2f6fed;color:#fff;border:none;margin-top:12px;}");
        builder.append(".muted{color:#667085;font-size:14px;line-height:1.6;}");
        builder.append(".err{color:#b42318;margin-top:10px;}");
        builder.append("</style></head><body><div class='card'>");
        builder.append("<h2>").append(escapeHtml(appContext.getString(R.string.lan_web_page_title))).append("</h2>");
        builder.append("<p class='muted'>").append(escapeHtml(appContext.getString(R.string.lan_web_trusted_network_notice))).append("</p>");
        builder.append("<p class='muted'>").append(escapeHtml(appContext.getString(R.string.lan_web_file_summary, files.size(), totalSize))).append("</p>");
        if (error != null && !error.isEmpty()) {
            builder.append("<div class='err'>").append(escapeHtml(error)).append("</div>");
        }
        builder.append("<form method='post' action='").append(AUTH_PATH).append("?token=").append(requestToken).append("'>");
        if (requirePassword) {
            builder.append("<input name='password' type='password' placeholder='").append(escapeHtml(appContext.getString(R.string.lan_access_password))).append("' autocomplete='off' />");
            builder.append("<button type='submit'>").append(escapeHtml(appContext.getString(R.string.lan_web_verify))).append("</button>");
        } else {
            builder.append("<button type='submit'>").append(escapeHtml(appContext.getString(R.string.continue_action))).append("</button>");
        }
        builder.append("</form></div></body></html>");
        return builder.toString();
    }

    private String fileListPage(String sid) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>");
        builder.append("<title>").append(escapeHtml(appContext.getString(R.string.lan_web_page_title))).append("</title>");
        builder.append("<style>");
        builder.append("body{font-family:sans-serif;margin:24px;color:#1f2937;background:#f7f7fb;}");
        builder.append(".card{max-width:860px;margin:0 auto;background:#fff;border:1px solid #dfe3ea;border-radius:16px;padding:20px;box-shadow:0 2px 8px rgba(0,0,0,.05);} ");
        builder.append(".file{display:flex;justify-content:space-between;gap:16px;padding:12px 0;border-bottom:1px solid #edf0f4;}");
        builder.append(".file:last-child{border-bottom:none;}");
        builder.append(".muted{color:#667085;font-size:14px;line-height:1.6;}");
        builder.append(".btn{display:inline-block;padding:10px 14px;border-radius:10px;text-decoration:none;background:#2f6fed;color:#fff;}");
        builder.append("</style></head><body><div class='card'>");
        builder.append("<h2>").append(escapeHtml(appContext.getString(R.string.lan_web_file_list))).append("</h2>");
        builder.append("<p class='muted'>").append(escapeHtml(appContext.getString(R.string.lan_web_trusted_network_notice))).append("</p>");
        builder.append("<p class='muted'>").append(escapeHtml(appContext.getString(R.string.lan_web_current_status, statusSummary()))).append("</p>");
        builder.append("<p class='muted'>").append(escapeHtml(appContext.getString(R.string.lan_web_accessing_devices, getActiveSessionCount(), maxDevices))).append("</p>");
        for (FileItem item : files) {
            builder.append("<div class='file'>");
            builder.append("<div><div>").append(escapeHtml(item.name)).append(item.risky ? " <span style='color:#b42318;'>" + escapeHtml(appContext.getString(R.string.lan_web_risk_warning)) + "</span>" : "").append("</div>");
            builder.append("<div class='muted'>").append(escapeHtml(item.mimeType)).append(" · ").append(item.size).append(" bytes</div></div>");
            builder.append("<a class='btn' href='").append(DOWNLOAD_PATH).append("?sid=").append(sid).append("&file=").append(item.fileId).append("'>").append(escapeHtml(appContext.getString(R.string.lan_web_download_file))).append("</a>");
            builder.append("</div>");
        }
        builder.append("</div></body></html>");
        return builder.toString();
    }

    private Map<String, String> readHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US), line.substring(colon + 1).trim());
        }
        return headers;
    }

    private Map<String, String> readFormBody(BufferedReader reader, Map<String, String> headers) throws IOException {
        int length = 0;
        try {
            length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        } catch (Exception ignored) {
        }
        char[] data = new char[Math.max(0, length)];
        int read = 0;
        while (read < length) {
            int count = reader.read(data, read, length - read);
            if (count < 0) break;
            read += count;
        }
        String body = new String(data, 0, read);
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = decodeUtf8(pair.substring(0, eq));
            String value = decodeUtf8(pair.substring(eq + 1));
            result.put(key, value);
        }
        return result;
    }

    private String sessionFrom(String query, Map<String, String> headers) {
        String sid = queryParam(query, "sid");
        if (!sid.isEmpty()) return sid;
        String cookie = headers.get("cookie");
        if (cookie == null) return "";
        for (String item : cookie.split(";")) {
            String trimmed = item.trim();
            if (trimmed.startsWith("ksftsid=")) return trimmed.substring("ksftsid=".length());
        }
        return "";
    }

    private String queryParam(String query, String key) {
        if (query == null || query.isEmpty()) return "";
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? pair.substring(0, eq) : pair;
            if (!key.equals(name)) continue;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            return decodeUtf8(value);
        }
        return "";
    }

    private String decodeUtf8(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private void write(OutputStream output, int code, String reason, String type, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.flush();
    }

    private void writeCors(OutputStream output, int code, String reason, String body, String type) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type, X-KeyScan-Token\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.flush();
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
    }

    private String sanitizeFilename(String value) {
        return value == null ? "file" : value.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private void closeQuietly() {
        if (serverSocket == null) return;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    public static String randomPasswordDigits(int length) {
        int count = Math.max(4, length);
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(random.nextInt(10));
        return builder.toString();
    }

    private static String randomToken() {
        byte[] data = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(data);
        char[] chars = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xff;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(chars);
    }

    public static String findLanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) continue;
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
