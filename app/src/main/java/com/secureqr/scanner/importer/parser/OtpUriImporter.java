package com.secureqr.scanner.importer.parser;

import android.net.Uri;

import com.secureqr.scanner.importer.model.ImportedOtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses standard TOTP otpauth URIs from a string or text stream. */
public final class OtpUriImporter {
    public List<ImportedOtp> parse(InputStream input) throws IOException {
        List<ImportedOtp> result = new ArrayList<>();
        if (input == null) return result;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) addIfValid(line, result);
        }
        return result;
    }

    public List<ImportedOtp> parse(String text) {
        List<ImportedOtp> result = new ArrayList<>();
        if (text == null) return result;
        for (String line : text.split("\\r?\\n")) addIfValid(line, result);
        return result;
    }

    private void addIfValid(String raw, List<ImportedOtp> result) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return;
        Uri uri;
        try { uri = Uri.parse(text); } catch (Exception ignored) { return; }
        if (!"otpauth".equalsIgnoreCase(uri.getScheme()) || !"totp".equalsIgnoreCase(uri.getHost())) return;
        String secret = trim(uri.getQueryParameter("secret"));
        if (secret.isEmpty()) return;

        String label = trim(uri.getPath());
        if (label.startsWith("/")) label = label.substring(1);
        String queryIssuer = trim(uri.getQueryParameter("issuer"));
        int separator = label.indexOf(':');
        String labelIssuer = separator >= 0 ? trim(label.substring(0, separator)) : "";
        String account = separator >= 0 ? trim(label.substring(separator + 1))
                : (!queryIssuer.isEmpty() && queryIssuer.equals(label) ? "" : label);

        ImportedOtp item = new ImportedOtp();
        item.issuer = first(queryIssuer, labelIssuer);
        item.account = account;
        item.secret = secret;
        item.algorithm = algorithm(uri.getQueryParameter("algorithm"));
        item.digits = positiveInt(uri.getQueryParameter("digits"), 6);
        item.period = positiveInt(uri.getQueryParameter("period"), 30);
        item.sourceFormat = "TOTP URI";
        result.add(item);
    }

    private String algorithm(String value) {
        String normalized = trim(value).toUpperCase(Locale.ROOT);
        return "SHA256".equals(normalized) || "SHA512".equals(normalized) || "SHA1".equals(normalized) ? normalized : "SHA1";
    }

    private int positiveInt(String value, int fallback) {
        try {
            int result = Integer.parseInt(trim(value));
            return result > 0 ? result : fallback;
        } catch (Exception ignored) { return fallback; }
    }

    private String first(String primary, String fallback) { return primary.isEmpty() ? fallback : primary; }
    private String trim(String value) { return value == null ? "" : value.trim(); }
}
