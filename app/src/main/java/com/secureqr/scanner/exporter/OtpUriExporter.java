package com.secureqr.scanner.exporter;

import android.net.Uri;

import java.util.List;

public final class OtpUriExporter {
    private OtpUriExporter() {
    }

    public static String export(List<ExportOtpItem> items) {
        StringBuilder out = new StringBuilder();
        if (items == null) return "";
        for (ExportOtpItem item : items) {
            if (item == null || normalizedSecret(item.secret).isEmpty()) continue;
            out.append(toUri(item)).append('\n');
        }
        return out.toString();
    }

    public static String toUri(ExportOtpItem item) {
        String label = label(item);
        StringBuilder uri = new StringBuilder("otpauth://totp/");
        uri.append(Uri.encode(label, ":"));
        uri.append("?secret=").append(Uri.encode(normalizedSecret(item.secret)));
        uri.append("&issuer=").append(Uri.encode(item.issuer));
        uri.append("&algorithm=").append(Uri.encode(normalizeAlgorithm(item.algorithm)));
        uri.append("&digits=").append(item.digits);
        uri.append("&period=").append(item.period);
        return uri.toString();
    }

    private static String label(ExportOtpItem item) {
        if (item.issuer.trim().isEmpty()) return item.account;
        if (item.account.trim().isEmpty()) return item.issuer;
        return item.issuer + ":" + item.account;
    }

    private static String normalizeAlgorithm(String algorithm) {
        String value = algorithm == null ? "" : algorithm.trim().toUpperCase(java.util.Locale.US);
        if ("SHA256".equals(value) || "SHA512".equals(value)) return value;
        return "SHA1";
    }

    private static String normalizedSecret(String secret) {
        return secret == null ? "" : secret.replace(" ", "").trim().toUpperCase(java.util.Locale.US);
    }
}
