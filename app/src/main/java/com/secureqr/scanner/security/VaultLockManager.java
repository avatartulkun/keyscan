package com.secureqr.scanner.security;

import android.content.Context;

public final class VaultLockManager {
    private VaultLockManager() {
    }

    public static boolean isExpired(Context context, long lastAccessTime) {
        int minutes = SecuritySettings.autoLockMinutes(context);
        if (minutes == 0) return false;
        if (lastAccessTime <= 0L) return true;
        long timeout = minutes * 60_000L;
        return System.currentTimeMillis() - lastAccessTime > timeout;
    }

    public static void lockNow() {
        VaultSession.lock();
    }

    public static void onSensitiveAccess(Context context) {
        VaultSession.touch(context);
    }
}
