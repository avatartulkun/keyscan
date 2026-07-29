package com.secureqr.scanner.exporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BitwardenJsonExporter {
    private BitwardenJsonExporter() {
    }

    public static String export(List<ExportPasswordItem> items) throws Exception {
        JSONObject root = new JSONObject();
        root.put("encrypted", false);
        JSONArray folders = new JSONArray();
        JSONArray itemsArray = new JSONArray();
        Map<String, String> folderIds = new LinkedHashMap<>();

        if (items != null) {
            for (ExportPasswordItem item : items) {
                String folderId = "";
                if (!item.folder.trim().isEmpty()) {
                    folderId = folderIds.get(item.folder);
                    if (folderId == null) {
                        folderId = UUID.randomUUID().toString();
                        folderIds.put(item.folder, folderId);
                    }
                }
                itemsArray.put(loginItem(item, folderId));
            }
        }

        for (Map.Entry<String, String> entry : folderIds.entrySet()) {
            JSONObject folder = new JSONObject();
            folder.put("id", entry.getValue());
            folder.put("name", entry.getKey());
            folders.put(folder);
        }

        root.put("folders", folders);
        root.put("items", itemsArray);
        return root.toString(2);
    }

    private static JSONObject loginItem(ExportPasswordItem item, String folderId) throws Exception {
        JSONObject object = new JSONObject();
        object.put("id", UUID.randomUUID().toString());
        object.put("organizationId", JSONObject.NULL);
        object.put("folderId", folderId == null || folderId.isEmpty() ? JSONObject.NULL : folderId);
        object.put("type", 1);
        object.put("reprompt", 0);
        object.put("name", firstNonEmpty(item.title, item.website, item.username, "KeyScan Login"));
        object.put("notes", item.notes);
        object.put("favorite", false);

        JSONObject login = new JSONObject();
        login.put("username", firstNonEmpty(item.username, item.account));
        login.put("password", item.password);
        login.put("totp", JSONObject.NULL);
        JSONArray uris = new JSONArray();
        if (!item.website.trim().isEmpty()) {
            JSONObject uri = new JSONObject();
            uri.put("match", JSONObject.NULL);
            uri.put("uri", item.website);
            uris.put(uri);
        }
        login.put("uris", uris);
        object.put("login", login);
        JSONArray fields = new JSONArray();
        JSONObject originalTitle = new JSONObject();
        originalTitle.put("name", "keyscan_original_title");
        originalTitle.put("value", item.title);
        originalTitle.put("type", 0);
        fields.put(originalTitle);
        JSONObject originalUsername = new JSONObject();
        originalUsername.put("name", "keyscan_original_username");
        originalUsername.put("value", item.username);
        originalUsername.put("type", 0);
        fields.put(originalUsername);
        if (!item.account.trim().isEmpty()) {
            JSONObject accountField = new JSONObject();
            accountField.put("name", "account");
            accountField.put("value", item.account);
            accountField.put("type", 0);
            fields.put(accountField);
        }
        object.put("fields", fields);
        object.put("collectionIds", new JSONArray());
        return object;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return "";
    }
}
