package com.secureqr.scanner.exporter;

import java.util.List;

public final class PasswordCsvExporter {
    private PasswordCsvExporter() {
    }

    public static String export(List<ExportPasswordItem> items) {
        StringBuilder out = new StringBuilder();
        out.append('\uFEFF');
        out.append("title,website,username,account,password,notes,folder\n");
        if (items == null) return out.toString();
        for (ExportPasswordItem item : items) {
            out.append(cell(item.title)).append(',')
                    .append(cell(item.website)).append(',')
                    .append(cell(item.username)).append(',')
                    .append(cell(item.account)).append(',')
                    .append(cell(item.password)).append(',')
                    .append(cell(item.notes)).append(',')
                    .append(cell(item.folder))
                    .append('\n');
        }
        return out.toString();
    }

    private static String cell(String value) {
        String safe = CsvValueSanitizer.protect(value);
        boolean quote = safe.indexOf(',') >= 0
                || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0;
        safe = safe.replace("\"", "\"\"");
        return quote ? "\"" + safe + "\"" : safe;
    }

}
