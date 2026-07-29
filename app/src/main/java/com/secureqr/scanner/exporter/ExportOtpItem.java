package com.secureqr.scanner.exporter;

public final class ExportOtpItem {
    public final String issuer;
    public final String account;
    public final String secret;
    public final String algorithm;
    public final int digits;
    public final int period;
    public final boolean pinned;
    public final int sortOrder;

    public ExportOtpItem(String issuer, String account, String secret, String algorithm, int digits, int period, boolean pinned, int sortOrder) {
        this.issuer = clean(issuer);
        this.account = clean(account);
        this.secret = clean(secret);
        this.algorithm = clean(algorithm).trim().isEmpty() ? "SHA1" : clean(algorithm).trim().toUpperCase(java.util.Locale.US);
        this.digits = digits <= 0 ? 6 : digits;
        this.period = period <= 0 ? 30 : period;
        this.pinned = pinned;
        this.sortOrder = sortOrder;
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
