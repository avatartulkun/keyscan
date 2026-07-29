package com.secureqr.scanner.security;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

public final class SensitiveWindowGuard {
    private SensitiveWindowGuard() {
    }

    public static boolean enable(Activity activity) {
        if (activity == null) return false;
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        return false;
    }

    public static void restore(Activity activity, boolean wasSecure) {
        if (activity == null) return;
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
}
