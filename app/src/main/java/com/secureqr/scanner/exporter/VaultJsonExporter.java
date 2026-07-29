package com.secureqr.scanner.exporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public final class VaultJsonExporter {
    private VaultJsonExporter() {
    }

    public static String export(List<ExportVaultItem> items) throws Exception {
        JSONArray array = new JSONArray();
        if (items != null) {
            for (ExportVaultItem item : items) {
                if (item == null) continue;
                JSONObject object = new JSONObject();
                object.put("title", item.title);
                object.put("type", item.type);
                object.put("category", item.category);
                object.put("fields", parseFields(item.fields));
                object.put("notes", item.notes);
                object.put("createdTime", item.createdTime);
                object.put("updatedTime", item.updatedTime);
                array.put(object);
            }
        }
        return array.toString(2);
    }

    private static JSONObject parseFields(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        try {
            Object parsed = new JSONObject(raw);
            return parsed instanceof JSONObject ? (JSONObject) parsed : new JSONObject();
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
