package com.secureqr.scanner.importer.parser;

import com.secureqr.scanner.importer.model.ImportedPassword;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** In-memory RFC 4180-style reader for password CSV files. */
public final class CsvPasswordImporter {
    public List<ImportedPassword> parse(Reader reader) throws IOException, CsvImportException {
        List<List<String>> records = readRecords(reader);
        if (records.isEmpty()) throw new CsvImportException("Missing CSV header");

        CsvHeaderMapper headers = new CsvHeaderMapper(records.get(0));
        if (!headers.supportsPasswords()) throw new CsvImportException("Unrecognized password CSV header");

        List<ImportedPassword> result = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> row = records.get(i);
            if (isBlankRow(row)) continue;
            ImportedPassword item = new ImportedPassword();
            item.title = trim(CsvValueNormalizer.restore(headers.title(row)));
            item.websiteDomain = trim(CsvValueNormalizer.restore(headers.website(row)));
            item.username = trim(CsvValueNormalizer.restore(headers.username(row)));
            item.account = trim(CsvValueNormalizer.restore(headers.account(row)));
            item.password = CsvValueNormalizer.restore(headers.password(row)); // Password whitespace is meaningful.
            item.notes = trim(CsvValueNormalizer.restore(headers.notes(row)));
            item.folderName = trim(CsvValueNormalizer.restore(headers.folder(row)));
            item.sourceFormat = "CSV";
            result.add(item);
        }
        return result;
    }

    private List<List<String>> readRecords(Reader source) throws IOException, CsvImportException {
        PushbackReader reader = new PushbackReader(source, 1);
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        int next;
        while ((next = reader.read()) != -1) {
            char ch = (char) next;
            if (quoted) {
                if (ch == '"') {
                    int following = reader.read();
                    if (following == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        afterQuote = true;
                        if (following != -1) reader.unread(following);
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (afterQuote) {
                if (ch == ',') {
                    record.add(field.toString());
                    field.setLength(0);
                    afterQuote = false;
                } else if (ch == '\n' || ch == '\r') {
                    if (ch == '\r') consumeLineFeed(reader);
                    record.add(field.toString());
                    records.add(record);
                    record = new ArrayList<>();
                    field.setLength(0);
                    afterQuote = false;
                } else if (!Character.isWhitespace(ch)) {
                    throw new CsvImportException("Invalid text after quoted field");
                }
                continue;
            }
            if (ch == '"') {
                if (field.length() != 0) throw new CsvImportException("Invalid quote in unquoted field");
                quoted = true;
            } else if (ch == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r') consumeLineFeed(reader);
                record.add(field.toString());
                records.add(record);
                record = new ArrayList<>();
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        if (quoted) throw new CsvImportException("Unterminated quoted field");
        if (afterQuote || field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    private void consumeLineFeed(PushbackReader reader) throws IOException {
        int following = reader.read();
        if (following != '\n' && following != -1) reader.unread(following);
    }

    private boolean isBlankRow(List<String> row) {
        for (String value : row) if (value != null && !value.trim().isEmpty()) return false;
        return true;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class CsvImportException extends Exception {
        public CsvImportException(String message) {
            super(message);
        }
    }
}
