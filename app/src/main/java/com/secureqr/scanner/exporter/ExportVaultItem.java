package com.secureqr.scanner.exporter;

public final class ExportVaultItem {
    public final String title;
    public final String type;
    public final String category;
    public final String fields;
    public final String notes;
    public final long createdTime;
    public final long updatedTime;

    public ExportVaultItem(String title, String type, String category, String fields, String notes, long createdTime, long updatedTime) {
        this.title = clean(title);
        this.type = clean(type);
        this.category = clean(category);
        this.fields = clean(fields);
        this.notes = clean(notes);
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
