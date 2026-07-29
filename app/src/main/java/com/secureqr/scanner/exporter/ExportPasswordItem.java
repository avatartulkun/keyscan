package com.secureqr.scanner.exporter;

public final class ExportPasswordItem {
    public final String title;
    public final String website;
    public final String username;
    public final String account;
    public final String password;
    public final String notes;
    public final String folder;

    public ExportPasswordItem(String title, String website, String username, String account, String password, String notes, String folder) {
        this.title = clean(title);
        this.website = clean(website);
        this.username = clean(username);
        this.account = clean(account);
        this.password = clean(password);
        this.notes = clean(notes);
        this.folder = clean(folder);
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
