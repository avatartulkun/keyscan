package com.secureqr.scanner.utils;

import android.net.Uri;

import com.secureqr.scanner.data.model.OtpToken;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class OtpHelper {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private OtpHelper() {
    }

    public static String code(OtpToken token, long millis) throws Exception {
        long counter = millis / 1000L / Math.max(1, token.period);
        byte[] key = base32Decode(token.secret);
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int modulo = (int) Math.pow(10, token.digits <= 0 ? 6 : token.digits);
        return String.format(Locale.US, "%0" + (token.digits <= 0 ? 6 : token.digits) + "d", binary % modulo);
    }

    public static int remainingSeconds(OtpToken token, long millis) {
        int period = Math.max(1, token.period);
        return period - (int) ((millis / 1000L) % period);
    }

    public static OtpToken parseUri(String raw) {
        Uri uri = Uri.parse(raw);
        if (!"otpauth".equals(uri.getScheme())) throw new IllegalArgumentException("不是有效的 OTP URI");
        OtpToken token = new OtpToken();
        String label = uri.getPath() == null ? "" : Uri.decode(uri.getPath().replaceFirst("^/", ""));
        String issuerParam = uri.getQueryParameter("issuer");
        if (label.contains(":")) {
            String[] parts = label.split(":", 2);
            token.issuer = issuerParam == null || issuerParam.isEmpty() ? parts[0] : issuerParam;
            token.accountName = parts[1];
        } else {
            token.issuer = issuerParam == null ? "" : issuerParam;
            token.accountName = label;
        }
        token.secret = normalizeSecret(uri.getQueryParameter("secret"));
        token.digits = parseInt(uri.getQueryParameter("digits"), 6);
        token.period = parseInt(uri.getQueryParameter("period"), 30);
        token.algorithm = "SHA1";
        long now = System.currentTimeMillis();
        token.createdAt = now;
        token.updatedAt = now;
        if (token.secret.isEmpty()) throw new IllegalArgumentException("Secret 不能为空");
        return token;
    }

    public static String toUri(OtpToken token) {
        String label = Uri.encode((token.issuer == null ? "" : token.issuer) + ":" + (token.accountName == null ? "" : token.accountName));
        return "otpauth://totp/" + label + "?secret=" + normalizeSecret(token.secret)
                + "&issuer=" + Uri.encode(token.issuer == null ? "" : token.issuer)
                + "&digits=" + token.digits + "&period=" + token.period;
    }

    public static String normalizeSecret(String secret) {
        return secret == null ? "" : secret.replace(" ", "").toUpperCase(Locale.US);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static byte[] base32Decode(String value) {
        String normalized = normalizeSecret(value).replace("=", "");
        ByteBuffer buffer = ByteBuffer.allocate(normalized.length() * 5 / 8 + 8);
        int bits = 0;
        int valueBuffer = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int val = ALPHABET.indexOf(normalized.charAt(i));
            if (val < 0) continue;
            valueBuffer = (valueBuffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                buffer.put((byte) ((valueBuffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        byte[] out = new byte[buffer.position()];
        buffer.flip();
        buffer.get(out);
        return out.length == 0 ? "empty".getBytes(StandardCharsets.UTF_8) : out;
    }
}

