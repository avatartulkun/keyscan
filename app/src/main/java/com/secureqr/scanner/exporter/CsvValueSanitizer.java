package com.secureqr.scanner.exporter;

/** Applies spreadsheet formula protection while preserving round-trip information. */
public final class CsvValueSanitizer {
    private CsvValueSanitizer() { }

    public static String protect(String value) {
        if (value == null || value.isEmpty()) return "";
        if (isFormulaPrefix(value.charAt(0))) return "'" + value;
        // Preserve an original apostrophe before a formula marker for the importer.
        if (value.length() >= 2 && value.charAt(0) == '\'' && isFormulaPrefix(value.charAt(1))) return "'" + value;
        return value;
    }

    private static boolean isFormulaPrefix(char value) {
        return value == '=' || value == '+' || value == '-' || value == '@';
    }
}
