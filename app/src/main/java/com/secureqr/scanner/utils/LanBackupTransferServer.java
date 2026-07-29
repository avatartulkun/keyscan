package com.secureqr.scanner.utils;

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
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class LanBackupTransferServer {
    public interface Listener {
        void onServed();
        void onExpired();
        void onError(Exception error);
    }

    private static final String BACKUP_PATH = "/keyscan/lan-backup";
    private static final String PING_PATH = "/keyscan/lan-ping";
    private static final int ACCEPT_TIMEOUT_MS = 1000;
    private static final int TOKEN_BYTES = 24;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String encryptedPackage;
    private final long packageSize;
    private final String sha256;
    private final long expiresAt;
    private final Listener listener;
    private final String token;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean served = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread thread;
    private InetAddress bindAddress;
    private int port;

    public LanBackupTransferServer(String encryptedPackage, String sha256, long expiresAt, Listener listener) {
        this.encryptedPackage = encryptedPackage;
        this.packageSize = encryptedPackage == null ? 0 : encryptedPackage.getBytes(StandardCharsets.UTF_8).length;
        this.sha256 = sha256;
        this.expiresAt = expiresAt;
        this.listener = listener;
        this.token = randomToken();
    }

    public void start(String bindIp) throws IOException {
        bindAddress = InetAddress.getByName(bindIp);
        serverSocket = new ServerSocket(0, 1, bindAddress);
        serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);
        port = serverSocket.getLocalPort();
        thread = new Thread(this::serveLoop, "KeyScanLanBackupTransfer");
        thread.start();
    }

    public void stop() {
        stopped.set(true);
        closeQuietly();
    }

    public String token() {
        return token;
    }

    public int port() {
        return port;
    }

    public long packageSize() {
        return packageSize;
    }

    public String sha256() {
        return sha256;
    }

    public String backupUrl() {
        return "http://" + bindAddress.getHostAddress() + ":" + port + BACKUP_PATH;
    }

    public String pingUrl() {
        return "http://" + bindAddress.getHostAddress() + ":" + port + PING_PATH;
    }

    private void serveLoop() {
        try {
            while (!stopped.get() && !served.get()) {
                if (System.currentTimeMillis() >= expiresAt) {
                    if (stopped.compareAndSet(false, true) && listener != null) listener.onExpired();
                    break;
                }
                try {
                    handle(serverSocket.accept());
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Exception error) {
            if (!stopped.get() && listener != null) listener.onError(error);
        } finally {
            closeQuietly();
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             OutputStream output = client.getOutputStream()) {
            String requestLine = reader.readLine();
            if (requestLine == null) {
                write(output, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request");
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0].toUpperCase(Locale.US))) {
                write(output, 405, "Method Not Allowed", "text/plain; charset=utf-8", "Method Not Allowed");
                return;
            }
            Map<String, String> headers = readHeaders(reader);
            String target = parts[1];
            String path = target;
            String query = "";
            int index = target.indexOf('?');
            if (index >= 0) {
                path = target.substring(0, index);
                query = target.substring(index + 1);
            }
            String requestToken = tokenFrom(query, headers);
            if (!token.equals(requestToken)) {
                write(output, 403, "Forbidden", "text/plain; charset=utf-8", "Forbidden");
                return;
            }
            if (System.currentTimeMillis() >= expiresAt) {
                write(output, 410, "Gone", "text/plain; charset=utf-8", "Expired");
                return;
            }
            if (PING_PATH.equals(path)) {
                write(output, 200, "OK", "application/json; charset=utf-8", "{\"ok\":true}");
                return;
            }
            if (!BACKUP_PATH.equals(path)) {
                write(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found");
                return;
            }
            if (!served.compareAndSet(false, true)) {
                write(output, 410, "Gone", "text/plain; charset=utf-8", "Already used");
                return;
            }
            write(output, 200, "OK", "application/vnd.keyscan.backup+json; charset=utf-8", encryptedPackage);
            stopped.set(true);
            if (listener != null) listener.onServed();
        } catch (IOException error) {
            if (!stopped.get() && listener != null) listener.onError(error);
        }
    }

    private Map<String, String> readHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US), line.substring(colon + 1).trim());
        }
        return headers;
    }

    private String tokenFrom(String query, Map<String, String> headers) {
        String fromHeader = headers.get("x-keyscan-token");
        if (fromHeader != null && !fromHeader.isEmpty()) return fromHeader;
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            if (equals <= 0 || !"token".equals(part.substring(0, equals))) continue;
            return decodeUtf8(part.substring(equals + 1));
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
        byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
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
}
