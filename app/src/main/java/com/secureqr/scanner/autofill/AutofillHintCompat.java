package com.secureqr.scanner.autofill;

import android.view.View;

public final class AutofillHintCompat {
    private static final String[] NEW_USERNAME_HINTS = {
            "newUsername",
            "new-username",
            "new_username"
    };
    private static final String[] NEW_PASSWORD_HINTS = {
            "newPassword",
            "new-password",
            "new_password"
    };

    private AutofillHintCompat() {
    }

    public static boolean isUsername(String[] hints) {
        return contains(hints, View.AUTOFILL_HINT_USERNAME)
                || contains(hints, View.AUTOFILL_HINT_EMAIL_ADDRESS)
                || isNewUsername(hints);
    }

    public static boolean isNewUsername(String[] hints) {
        return containsAny(hints, NEW_USERNAME_HINTS);
    }

    public static boolean isPassword(String[] hints) {
        return contains(hints, View.AUTOFILL_HINT_PASSWORD)
                || isNewPassword(hints);
    }

    public static boolean isNewPassword(String[] hints) {
        return containsAny(hints, NEW_PASSWORD_HINTS);
    }

    public static boolean textHasNewUsername(String text) {
        return containsAny(text, NEW_USERNAME_HINTS);
    }

    public static boolean textHasNewPassword(String text) {
        return containsAny(text, NEW_PASSWORD_HINTS);
    }

    public static boolean contains(String[] hints, String target) {
        if (hints == null || target == null) return false;
        for (String hint : hints) {
            if (target.equalsIgnoreCase(hint)) return true;
        }
        return false;
    }

    private static boolean containsAny(String[] hints, String[] targets) {
        if (hints == null || targets == null) return false;
        for (String target : targets) {
            if (contains(hints, target)) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, String[] targets) {
        if (text == null || targets == null) return false;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String target : targets) {
            if (target != null && lower.contains(target.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }
}
