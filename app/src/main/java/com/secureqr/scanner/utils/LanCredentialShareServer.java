package com.secureqr.scanner.utils;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class LanCredentialShareServer {
    public interface Listener {
        void onServed();
        void onExpired();
        void onError(Exception error);
    }

    private static final int TOKEN_BYTES = 18;
    private static final int ACCEPT_TIMEOUT_MS = 1000;

    private final String token;
    private final String credentialJson;
    private final long expiresAt;
    private final Listener listener;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean served = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread thread;
    private InetAddress bindAddress;
    private int port;

    public LanCredentialShareServer(String credentialJson, long expiresAt, Listener listener) {
        this.token = randomToken();
        this.credentialJson = credentialJson;
        this.expiresAt = expiresAt;
        this.listener = listener;
    }

    public void start() throws IOException {
        bindAddress = findLanAddress();
        if (bindAddress == null) {
            throw new IOException("No LAN IPv4 address found");
        }
        serverSocket = new ServerSocket(0, 4, bindAddress);
        serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);
        port = serverSocket.getLocalPort();
        thread = new Thread(this::serveLoop, "KeyScanLanShare");
        thread.start();
    }

    public String getToken() {
        return token;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getBaseUrl() {
        return "http://" + bindAddress.getHostAddress() + ":" + port + "/credential";
    }

    public String getShareUrl() {
        return getBaseUrl() + "?token=" + token;
    }

    public void stop() {
        stopped.set(true);
        closeQuietly();
    }

    private void serveLoop() {
        try {
            while (!stopped.get() && !served.get()) {
                if (System.currentTimeMillis() >= expiresAt) {
                    if (stopped.compareAndSet(false, true) && listener != null) {
                        listener.onExpired();
                    }
                    break;
                }
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (SocketTimeoutException ignored) {
                    // Wake periodically so expiration and cancellation are honored quickly.
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
                writeResponse(output, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request");
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                writeResponse(output, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request");
                return;
            }
            String method = parts[0].toUpperCase(Locale.US);
            String target = parts[1];
            if ("OPTIONS".equals(method)) {
                writeResponse(output, 204, "No Content", "text/plain; charset=utf-8", "");
                return;
            }
            if (!"GET".equals(method)) {
                writeResponse(output, 405, "Method Not Allowed", "text/plain; charset=utf-8", "Method Not Allowed");
                return;
            }
            String path = target;
            String query = "";
            int queryIndex = target.indexOf('?');
            if (queryIndex >= 0) {
                path = target.substring(0, queryIndex);
                query = target.substring(queryIndex + 1);
            }
            if (!"/credential".equals(path)) {
                writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found");
                return;
            }
            if (System.currentTimeMillis() >= expiresAt) {
                writeResponse(output, 410, "Gone", "text/plain; charset=utf-8", "Expired");
                return;
            }
            if (!token.equals(queryParam(query, "token"))) {
                writeResponse(output, 403, "Forbidden", "text/plain; charset=utf-8", "Forbidden");
                return;
            }
            if (!served.compareAndSet(false, true)) {
                writeResponse(output, 410, "Gone", "text/plain; charset=utf-8", "Already used");
                return;
            }
            writeResponse(output, 200, "OK", "application/json; charset=utf-8", credentialJson);
            stopped.set(true);
            if (listener != null) {
                listener.onServed();
            }
        } catch (IOException error) {
            if (!stopped.get() && listener != null) {
                listener.onError(error);
            }
        }
    }

    private void writeResponse(OutputStream output, int code, String reason, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.flush();
    }

    private String queryParam(String query, String key) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            String name = equals >= 0 ? pair.substring(0, equals) : pair;
            if (!key.equals(name)) continue;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return "";
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
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static InetAddress findLanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                        return address;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
