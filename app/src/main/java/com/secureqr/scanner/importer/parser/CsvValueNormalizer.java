package com.secureqr.scanner.importer.parser;

/** Restores KeyScan's own CSV spreadsheet-protection prefix without altering ordinary apostrophes. */
public final class CsvValueNormalizer {
    private CsvValueNormalizer() { }

    public static String restore(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '\'') return value == null ? "" : value;
        if (value.length() >= 3 && value.charAt(1) == '\'' && isFormulaPrefix(value.charAt(2))) {
            return value.substring(1);
        }
        if (isFormulaPrefix(value.charAt(1))) return value.substring(1);
        return value;
    }

    private static boolean isFormulaPrefix(char value) {
        return value == '=' || value == '+' || value == '-' || value == '@';
    }
}
