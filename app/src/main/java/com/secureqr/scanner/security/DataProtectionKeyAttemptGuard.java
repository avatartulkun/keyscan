package com.secureqr.scanner.security;

import android.content.Context;
import android.content.SharedPreferences;

/** Keeps data-protection-key retries separate from the app unlock PIN. */
public final class DataProtectionKeyAttemptGuard {
    private static final String PREFS = "data_protection_key_attempt_guard";
    private static final String KEY_FAILED_COUNT = "failed_count";
    private static final String KEY_LOCKED_UNTIL = "locked_until";
    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_MS = 60_000L;

    private DataProtectionKeyAttemptGuard() { }

    public static long remainingLockMs(Context context) {
        return Math.max(0L, prefs(context).getLong(KEY_LOCKED_UNTIL, 0L) - System.currentTimeMillis());
    }

    public static int recordFailure(Context context) {
        SharedPreferences preferences = prefs(context);
        int failed = preferences.getInt(KEY_FAILED_COUNT, 0) + 1;
        if (failed >= MAX_ATTEMPTS) {
            preferences.edit().putInt(KEY_FAILED_COUNT, 0)
                    .putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + LOCK_MS).apply();
            return 0;
        }
        preferences.edit().putInt(KEY_FAILED_COUNT, failed).apply();
        return MAX_ATTEMPTS - failed;
    }

    public static void clearFailures(Context context) {
        prefs(context).edit().putInt(KEY_FAILED_COUNT, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
