package com.secureqr.scanner.security;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.secureqr.scanner.R;

public final class AuthDebugLogger {
    private static final String TAG = "KeyScanAuth";

    private AuthDebugLogger() {
    }

    public static void logAuthTrigger(FragmentActivity activity, String caller, String reason) {
        if (!isDebuggable(activity)) return;
        debug("AUTH_TRIGGER"
                + " caller=" + safe(caller)
                + " reason=" + safe(reason)
                + " lifecycle=" + lifecycle(activity)
                + " destination=" + destination(activity)
                + " savedInstanceState=unknown"
                + " changingConfigurations=" + (activity != null && activity.isChangingConfigurations())
                + " vaultLocked=" + !RecentAuthSession.isAuthenticated()
                + " recentAuthValid=" + RecentAuthSession.isAuthenticated()
                + " configRebuild=" + ConfigurationRebuildGuard.isInProgress()
                + " configReason=" + safe(ConfigurationRebuildGuard.reason()));
    }

    public static void logActivityState(FragmentActivity activity, String caller, @Nullable Bundle savedInstanceState) {
        if (!isDebuggable(activity)) return;
        debug("AUTH_TRIGGER"
                + " caller=" + safe(caller)
                + " lifecycle=" + lifecycle(activity)
                + " destination=" + destination(activity)
                + " savedInstanceState=" + (savedInstanceState != null)
                + " changingConfigurations=" + (activity != null && activity.isChangingConfigurations())
                + " vaultLocked=" + !RecentAuthSession.isAuthenticated()
                + " recentAuthValid=" + RecentAuthSession.isAuthenticated()
                + " configRebuild=" + ConfigurationRebuildGuard.isInProgress()
                + " configReason=" + safe(ConfigurationRebuildGuard.reason()));
    }

    private static String lifecycle(FragmentActivity activity) {
        return activity == null ? "none" : activity.getLifecycle().getCurrentState().name();
    }

    private static String destination(FragmentActivity activity) {
        if (activity == null) return "none";
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        return fragment == null ? "none" : fragment.getClass().getSimpleName();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "-";
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static void debug(String message) {
        if (message == null) return;
        Log.println(Log.DEBUG, TAG, message);
    }

    private static boolean isDebuggable(FragmentActivity activity) {
        return activity != null
                && (activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
