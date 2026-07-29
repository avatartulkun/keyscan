package com.secureqr.scanner.security;

public final class ConfigurationRebuildGuard {
    private static final long MAX_ACTIVE_MS = 10_000L;
    private static boolean inProgress;
    private static String reason = "";
    private static long startedAt;

    private ConfigurationRebuildGuard() {
    }

    public static synchronized void begin(String value) {
        inProgress = true;
        reason = value == null ? "" : value;
        startedAt = System.currentTimeMillis();
    }

    public static synchronized boolean isInProgress() {
        if (!inProgress) return false;
        if (System.currentTimeMillis() - startedAt > MAX_ACTIVE_MS) {
            clear();
            return false;
        }
        return true;
    }

    public static synchronized String reason() {
        return isInProgress() ? reason : "";
    }

    public static synchronized void clear() {
        inProgress = false;
        reason = "";
        startedAt = 0L;
    }
}
