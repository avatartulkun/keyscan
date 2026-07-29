package com.secureqr.scanner.security;

public final class RecentAuthSession {
    private static final long VALID_MS = 60_000L;
    private static long authenticatedAt;

    private RecentAuthSession() {
    }

    public static synchronized void markAuthenticated() {
        authenticatedAt = System.currentTimeMillis();
    }

    public static synchronized boolean isAuthenticated() {
        return getRemainingTime() > 0;
    }

    public static synchronized long getRemainingTime() {
        if (authenticatedAt <= 0) return 0;
        long remaining = VALID_MS - (System.currentTimeMillis() - authenticatedAt);
        if (remaining <= 0) {
            authenticatedAt = 0;
            return 0;
        }
        return remaining;
    }

    public static synchronized void clear() {
        authenticatedAt = 0;
    }
}
