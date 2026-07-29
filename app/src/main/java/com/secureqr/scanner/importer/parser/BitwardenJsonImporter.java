package com.secureqr.scanner.importer.parser;

import com.secureqr.scanner.importer.model.ImportedPassword;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Reads Bitwarden password items into KeyScan's in-memory import model. */
public final class BitwardenJsonImporter {
    public List<ImportedPassword> parse(InputStream input) throws IOException, BitwardenImportException {
        if (input == null) throw new BitwardenImportException("Missing input");
        try {
            JSONObject root = new JSONObject(readUtf8(input));
            HashMap<String, String> folders = folders(root.optJSONArray("folders"));
            JSONArray items = root.optJSONArray("items");
            if (items == null) throw new BitwardenImportException("Missing items");

            List<ImportedPassword> result = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || item.optInt("type", -1) != 1) continue;
                result.add(map(item, folders));
            }
            return result;
        } catch (BitwardenImportException e) {
            throw e;
        } catch (Exception e) {
            throw new BitwardenImportException("Invalid Bitwarden JSON");
        }
    }

    private HashMap<String, String> folders(JSONArray source) {
        HashMap<String, String> result = new HashMap<>();
        if (source == null) return result;
        for (int i = 0; i < source.length(); i++) {
            JSONObject folder = source.optJSONObject(i);
            if (folder == null) continue;
            String id = value(folder, "id");
            if (!id.isEmpty()) result.put(id, trim(value(folder, "name")));
        }
        return result;
    }

    private ImportedPassword map(JSONObject source, HashMap<String, String> folders) {
        ImportedPassword item = new ImportedPassword();
        JSONObject login = source.optJSONObject("login");
        item.title = trim(value(source, "name"));
        item.username = trim(login == null ? "" : value(login, "username"));
        item.password = login == null ? "" : value(login, "password");
        item.websiteDomain = firstUri(login == null ? null : login.optJSONArray("uris"));
        item.notes = trim(value(source, "notes"));
        item.folderName = trim(folders.get(value(source, "folderId")));
        item.sourceFormat = "Bitwarden JSON";
        appendCustomFields(item, source.optJSONArray("fields"));
        return item;
    }

    private String firstUri(JSONArray uris) {
        if (uris == null) return "";
        for (int i = 0; i < uris.length(); i++) {
            JSONObject uri = uris.optJSONObject(i);
            if (uri == null) continue;
            String value = trim(value(uri, "uri"));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private void appendCustomFields(ImportedPassword item, JSONArray fields) {
        if (fields == null) return;
        StringBuilder notes = new StringBuilder(item.notes == null ? "" : item.notes);
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.optJSONObject(i);
            if (field == null) continue;
            String name = trim(value(field, "name"));
            String value = value(field, "value");
            if (name.isEmpty()) continue;
            String normalized = name.toLowerCase(Locale.ROOT);
            if ("keyscan_original_title".equals(normalized)) {
                item.title = value;
                continue;
            }
            if ("keyscan_original_username".equals(normalized)) {
                item.username = value;
                continue;
            }
            if (value.isEmpty()) continue;
            if ("username".equals(normalized) || "account".equals(normalized)) {
                if (item.account == null || item.account.trim().isEmpty()) item.account = value;
                continue;
            }
            if (notes.length() > 0) notes.append('\n');
            notes.append(name).append("：").append(value);
        }
        item.notes = notes.toString().trim();
    }

    private String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private String value(JSONObject object, String name) {
        return object == null ? "" : object.optString(name, "");
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }

    public static final class BitwardenImportException extends Exception {
        public BitwardenImportException(String message) { super(message); }
    }
}
