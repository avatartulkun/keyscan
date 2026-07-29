package com.secureqr.scanner.lan;

import android.content.Context;
import android.net.Uri;

import com.secureqr.scanner.network.NetworkAccessController;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NearbyLanRelayServer {
    public interface Listener {
        void onInviteReceived(Invite invite);

        void onStatusUpdate(StatusUpdate statusUpdate);

        void onError(Exception error);
    }

    public static final class Invite {
        public final String inviteId;
        public final String senderDeviceId;
        public final String senderDisplayName;
        public final String senderHost;
        public final int senderPort;
        public final String senderTransferUrl;
        public final String callbackUrl;
        public final String callbackToken;
        public final int fileCount;
        public final long totalSize;
        public final long sentAt;

        public Invite(String inviteId, String senderDeviceId, String senderDisplayName, String senderHost, int senderPort, String senderTransferUrl, String callbackUrl, String callbackToken, int fileCount, long totalSize, long sentAt) {
            this.inviteId = inviteId;
            this.senderDeviceId = senderDeviceId;
            this.senderDisplayName = senderDisplayName;
            this.senderHost = senderHost;
            this.senderPort = senderPort;
            this.senderTransferUrl = senderTransferUrl;
            this.callbackUrl = callbackUrl;
            this.callbackToken = callbackToken;
            this.fileCount = fileCount;
            this.totalSize = totalSize;
            this.sentAt = sentAt;
        }
    }

    public static final class StatusUpdate {
        public final String inviteId;
        public final String state;
        public final String message;
        public final long updatedAt;
        public final String peerDeviceId;
        public final String peerDisplayName;

        public StatusUpdate(String inviteId, String state, String message, long updatedAt, String peerDeviceId, String peerDisplayName) {
            this.inviteId = inviteId;
            this.state = state;
            this.message = message;
            this.updatedAt = updatedAt;
            this.peerDeviceId = peerDeviceId;
            this.peerDisplayName = peerDisplayName;
        }
    }

    private static final String INVITE_PATH = "/keyscan/nearby/invite";
    private static final String STATUS_PATH = "/keyscan/nearby/status";
    private static final String PING_PATH = "/keyscan/nearby/ping";
    private static final int ACCEPT_TIMEOUT_MS = 1000;

    private final Context appContext;
    private final String deviceId;
    private final String displayName;
    private final String token;
    private final Listener listener;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread thread;
    private InetAddress bindAddress;
    private int port;

    public NearbyLanRelayServer(Context context, String deviceId, String displayName, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.deviceId = deviceId;
        this.displayName = displayName;
        this.listener = listener;
        this.token = UUID.randomUUID().toString().replace("-", "");
    }

    public void start() throws IOException {
        NetworkAccessController.LanInfo info = NetworkAccessController.currentLanInfo(appContext);
        if (info == null) {
            throw new IOException("No Wi-Fi LAN address available");
        }
        bindAddress = InetAddress.getByName(info.ipv4);
        serverSocket = new ServerSocket(0, 4, bindAddress);
        serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);
        port = serverSocket.getLocalPort();
        thread = new Thread(this::serveLoop, "KeyScanNearbyLanRelay");
        thread.start();
    }

    public void stop() {
        stopped.set(true);
        closeQuietly();
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }

    public String getBaseUrl() {
        if (bindAddress == null || port <= 0) return "";
        return "http://" + bindAddress.getHostAddress() + ":" + port;
    }

    public String getInviteUrl() {
        return getBaseUrl() + INVITE_PATH;
    }

    public String getStatusUrl() {
        return getBaseUrl() + STATUS_PATH;
    }

    public String getPingUrl() {
        return getBaseUrl() + PING_PATH;
    }

    private void serveLoop() {
        try {
            while (!stopped.get()) {
                try {
                    handle(serverSocket.accept());
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Exception error) {
            if (!stopped.get() && listener != null) {
                listener.onError(error);
            }
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
            int index = target.indexOf('?');
            if (index >= 0) {
                path = target.substring(0, index);
                query = target.substring(index + 1);
            }
            Map<String, String> headers = readHeaders(reader);
            if (PING_PATH.equals(path)) {
                write(output, 200, "OK", "application/json; charset=utf-8", "{\"ok\":true,\"deviceId\":\"" + escapeJson(deviceId) + "\",\"displayName\":\"" + escapeJson(displayName) + "\"}");
                return;
            }
            if (!"POST".equals(method)) {
                write(output, 405, "Method Not Allowed", "text/plain; charset=utf-8", "Method Not Allowed");
                return;
            }
            if (!authorize(query, headers)) {
                write(output, 403, "Forbidden", "text/plain; charset=utf-8", "Forbidden");
                return;
            }
            if (INVITE_PATH.equals(path)) {
                Invite invite = parseInvite(reader, headers);
                if (invite == null) {
                    write(output, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request");
                    return;
                }
                if (listener != null) listener.onInviteReceived(invite);
                write(output, 202, "Accepted", "application/json; charset=utf-8", "{\"ok\":true,\"inviteId\":\"" + escapeJson(invite.inviteId) + "\"}");
                return;
            }
            if (STATUS_PATH.equals(path)) {
                StatusUpdate statusUpdate = parseStatus(reader, headers);
                if (statusUpdate == null) {
                    write(output, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request");
                    return;
                }
                if (listener != null) listener.onStatusUpdate(statusUpdate);
                write(output, 200, "OK", "application/json; charset=utf-8", "{\"ok\":true}");
                return;
            }
            write(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found");
        } catch (IOException error) {
            if (!stopped.get() && listener != null) listener.onError(error);
        }
    }

    private Invite parseInvite(BufferedReader reader, Map<String, String> headers) throws IOException {
        try {
            String body = readBody(reader, headers);
            JSONObject json = new JSONObject(body);
            return new Invite(
                    json.optString("inviteId", UUID.randomUUID().toString()),
                    json.optString("senderDeviceId", ""),
                    json.optString("senderDisplayName", ""),
                    json.optString("senderHost", ""),
                    json.optInt("senderPort", 0),
                    json.optString("senderTransferUrl", ""),
                    json.optString("callbackUrl", ""),
                    json.optString("callbackToken", ""),
                    json.optInt("fileCount", 0),
                    json.optLong("totalSize", 0L),
                    json.optLong("sentAt", System.currentTimeMillis())
            );
        } catch (JSONException error) {
            throw new IOException("Invalid invite payload", error);
        }
    }

    private StatusUpdate parseStatus(BufferedReader reader, Map<String, String> headers) throws IOException {
        try {
            String body = readBody(reader, headers);
            JSONObject json = new JSONObject(body);
            return new StatusUpdate(
                    json.optString("inviteId", ""),
                    json.optString("state", ""),
                    json.optString("message", ""),
                    json.optLong("updatedAt", System.currentTimeMillis()),
                    json.optString("peerDeviceId", ""),
                    json.optString("peerDisplayName", "")
            );
        } catch (JSONException error) {
            throw new IOException("Invalid status payload", error);
        }
    }

    private boolean authorize(String query, Map<String, String> headers) {
        String tokenValue = queryParam(query, "token");
        if (tokenValue.isEmpty()) {
            tokenValue = headers.getOrDefault("x-keyscan-token", "");
        }
        return token.equals(tokenValue);
    }

    private String readBody(BufferedReader reader, Map<String, String> headers) throws IOException {
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
        return new String(data, 0, read);
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
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type, X-KeyScan-Token\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.flush();
    }

    private void closeQuietly() {
        if (serverSocket == null) return;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
