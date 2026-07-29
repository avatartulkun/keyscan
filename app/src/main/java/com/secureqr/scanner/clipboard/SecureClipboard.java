package com.secureqr.scanner.clipboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import com.secureqr.scanner.security.SecuritySettings;

public final class SecureClipboard {
    private SecureClipboard() {
    }

    public static void copySensitive(Context context, String label, String text) {
        String safeText = putSensitive(context, label, text);
        if (safeText == null) return;
        long timeoutMs = SecuritySettings.clipboardTimeoutSeconds(context) * 1000L;
        if (timeoutMs > 0) {
            Context appContext = context.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> clearIfMatches(appContext, safeText),
                    timeoutMs
            );
        }
    }

    public static void copySensitive(Context context, String label, String text, long clearAfterMs) {
        String copied = putSensitive(context, label, text);
        if (context == null || copied == null || clearAfterMs <= 0) return;
        Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> clearIfMatches(appContext, copied),
                clearAfterMs
        );
    }

    private static String putSensitive(Context context, String label, String text) {
        if (context == null) return null;
        String safeText = text == null ? "" : text;
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return null;
        ClipData clip = ClipData.newPlainText(label == null ? "KeyScan" : label, safeText);
        markSensitive(clip);
        manager.setPrimaryClip(clip);
        ClipboardImportSession.markInternalCopy(safeText);
        return safeText;
    }

    public static void clearIfMatches(Context context, String text) {
        if (context == null || text == null) return;
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) return;
        try {
            ClipData current = manager.getPrimaryClip();
            if (current == null || current.getItemCount() == 0) return;
            CharSequence value = current.getItemAt(0).coerceToText(context);
            if (text.contentEquals(value == null ? "" : value)) {
                manager.setPrimaryClip(ClipData.newPlainText("KeyScan", ""));
            }
        } catch (Exception ignored) {
        }
    }

    private static void markSensitive(ClipData clip) {
        if (clip == null || Build.VERSION.SDK_INT < 24) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        extras.putBoolean("androidx.core.content.extra.IS_SENSITIVE", true);
        ClipDescription description = clip.getDescription();
        if (description != null) {
            description.setExtras(extras);
        }
    }
}
