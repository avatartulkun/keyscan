package com.secureqr.scanner.importer.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Maps common password-manager CSV headers to KeyScan's import fields. */
public final class CsvHeaderMapper {
    private final Map<String, Integer> indices = new HashMap<>();

    public CsvHeaderMapper(List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            String header = normalize(headers.get(i));
            if (!header.isEmpty() && !indices.containsKey(header)) {
                indices.put(header, i);
            }
        }
    }

    public String title(List<String> row) {
        return value(row, "name", "title", "item");
    }

    public String website(List<String> row) {
        return value(row, "url", "website", "login_uri", "login_url");
    }

    public String username(List<String> row) {
        return value(row, "username", "login_username", "email");
    }

    public String account(List<String> row) {
        return value(row, "account", "account_name", "login_account");
    }

    public String password(List<String> row) {
        return value(row, "password", "login_password");
    }

    public String notes(List<String> row) {
        return value(row, "notes", "note", "extra");
    }

    public String folder(List<String> row) {
        return value(row, "folder", "group", "grouping");
    }

    public boolean supportsPasswords() {
        return indexOf("password", "login_password") >= 0;
    }

    private String value(List<String> row, String... aliases) {
        int index = indexOf(aliases);
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    private int indexOf(String... aliases) {
        for (String alias : aliases) {
            Integer index = indices.get(alias);
            if (index != null) return index;
        }
        return -1;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }
}
