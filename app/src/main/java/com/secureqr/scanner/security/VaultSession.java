package com.secureqr.scanner.security;

import android.content.Context;

public final class VaultSession {
    private static boolean unlocked;
    private static long lastAccessTime;

    private VaultSession() {
    }

    public static synchronized boolean isUnlocked(Context context) {
        if (!unlocked) return false;
        if (VaultLockManager.isExpired(context, lastAccessTime)) {
            lock();
            return false;
        }
        return true;
    }

    public static synchronized void unlock(Context context) {
        unlocked = true;
        touch(context);
    }

    public static synchronized void touch(Context context) {
        lastAccessTime = System.currentTimeMillis();
    }

    public static synchronized void lock() {
        unlocked = false;
        lastAccessTime = 0L;
        RecentAuthSession.clear();
    }

    public static synchronized long lastAccessTime() {
        return lastAccessTime;
    }
}
