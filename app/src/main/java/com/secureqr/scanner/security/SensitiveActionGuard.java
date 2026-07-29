package com.secureqr.scanner.security;

import androidx.fragment.app.FragmentActivity;

public final class SensitiveActionGuard {
    private SensitiveActionGuard() {
    }

    public static void requireRecentAuth(FragmentActivity activity, String reason, Runnable onSuccess) {
        VaultAccessManager.requireUnlocked(activity, reason, onSuccess);
    }
    public static void requireAuthentication(FragmentActivity activity,String reason,Runnable onSuccess){VaultAccessManager.requireAuthentication(activity,reason,onSuccess);}
}
