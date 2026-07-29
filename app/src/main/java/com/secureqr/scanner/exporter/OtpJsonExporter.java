package com.secureqr.scanner.exporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public final class OtpJsonExporter {
    private OtpJsonExporter() {
    }

    public static String export(List<ExportOtpItem> items) throws Exception {
        JSONArray array = new JSONArray();
        if (items != null) {
            for (ExportOtpItem item : items) {
                if (item == null || item.secret.trim().isEmpty()) continue;
                JSONObject object = new JSONObject();
                object.put("issuer", item.issuer);
                object.put("account", item.account);
                object.put("secret", item.secret);
                object.put("algorithm", normalizeAlgorithm(item.algorithm));
                object.put("digits", item.digits);
                object.put("period", item.period);
                array.put(object);
            }
        }
        return array.toString(2);
    }

    private static String normalizeAlgorithm(String algorithm) {
        String value = algorithm == null ? "" : algorithm.trim().toUpperCase(java.util.Locale.US);
        if ("SHA256".equals(value) || "SHA512".equals(value)) return value;
        return "SHA1";
    }
}
