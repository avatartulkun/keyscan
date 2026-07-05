package com.secureqr.scanner.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.secureqr.scanner.R;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class PinLockHelper {
    public static final String[] SECURITY_QUESTIONS = {
            "What was the name of your primary school?",
            "What is your mother's name?",
            "What is your father's name?",
            "What city were you born in?",
            "What was the name of your first pet?"
    };

    private static final String PREFS = "secureqr_settings";
    private static final String KEY_PASSWORD_HASH = "password_forge_pin_hash";
    private static final String KEY_PASSWORD_HINT = "password_forge_pin_hint";
    private static final String KEY_SECURITY_QUESTION = "password_forge_security_question";
    private static final String KEY_SECURITY_ANSWER_HASH = "password_forge_security_answer_hash";
    private static final String KEY_FAILED_COUNT = "password_forge_failed_count";
    private static final String KEY_LOCKED_UNTIL = "password_forge_locked_until";
    private static final long LOCK_MS = 60 * 1000L;

    private PinLockHelper() {
    }

    public static String[] securityQuestions(Context context) {
        return new String[]{
                context.getString(R.string.security_question_school),
                context.getString(R.string.security_question_mother),
                context.getString(R.string.security_question_father),
                context.getString(R.string.security_question_birth_city),
                context.getString(R.string.security_question_first_pet)
        };
    }

    public static boolean isConfigured(Context context) {
        SharedPreferences preferences = prefs(context);
        return !preferences.getString(KEY_PASSWORD_HASH, "").isEmpty()
                && !preferences.getString(KEY_SECURITY_QUESTION, "").isEmpty()
                && !preferences.getString(KEY_SECURITY_ANSWER_HASH, "").isEmpty();
    }

    public static boolean isPinSet(Context context) {
        return isConfigured(context);
    }

    public static void savePin(Context context, String password) {
        prefs(context).edit().putString(KEY_PASSWORD_HASH, hashPassword(password)).apply();
    }

    public static void savePasswordAndHint(Context context, String password, String hint) {
        prefs(context).edit()
                .putString(KEY_PASSWORD_HASH, hashPassword(password))
                .putString(KEY_PASSWORD_HINT, hint == null ? "" : hint.trim())
                .putInt(KEY_FAILED_COUNT, 0)
                .putLong(KEY_LOCKED_UNTIL, 0)
                .apply();
    }

    public static void saveCredentials(Context context, String password, String question, String answer) {
        saveCredentials(context, password, "", question, answer);
    }

    public static void saveCredentials(Context context, String password, String hint, String question, String answer) {
        prefs(context).edit()
                .putString(KEY_PASSWORD_HASH, hashPassword(password))
                .putString(KEY_PASSWORD_HINT, hint == null ? "" : hint.trim())
                .putString(KEY_SECURITY_QUESTION, question)
                .putString(KEY_SECURITY_ANSWER_HASH, hashAnswer(answer))
                .putInt(KEY_FAILED_COUNT, 0)
                .putLong(KEY_LOCKED_UNTIL, 0)
                .apply();
    }

    public static boolean verifyPin(Context context, String password) {
        String saved = prefs(context).getString(KEY_PASSWORD_HASH, "");
        return !saved.isEmpty() && saved.equals(hashPassword(password));
    }

    public static boolean verifySecurityAnswer(Context context, String answer) {
        String saved = prefs(context).getString(KEY_SECURITY_ANSWER_HASH, "");
        return !saved.isEmpty() && saved.equals(hashAnswer(answer));
    }

    public static String securityQuestion(Context context) {
        return prefs(context).getString(KEY_SECURITY_QUESTION, SECURITY_QUESTIONS[0]);
    }

    public static String passwordHint(Context context) {
        return prefs(context).getString(KEY_PASSWORD_HINT, "");
    }

    public static boolean isValidPin(String password) {
        if (password == null) return false;
        String value = password.trim();
        if (value.matches("\\d+")) {
            return value.length() >= 4 && value.length() <= 6;
        }
        return value.length() >= 4;
    }

    public static long remainingLockMs(Context context) {
        long lockedUntil = prefs(context).getLong(KEY_LOCKED_UNTIL, 0);
        return Math.max(0, lockedUntil - System.currentTimeMillis());
    }

    public static void recordFailedAttempt(Context context) {
        SharedPreferences preferences = prefs(context);
        int failed = preferences.getInt(KEY_FAILED_COUNT, 0) + 1;
        SharedPreferences.Editor editor = preferences.edit();
        if (failed >= 5) {
            editor.putInt(KEY_FAILED_COUNT, 0)
                    .putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + LOCK_MS);
        } else {
            editor.putInt(KEY_FAILED_COUNT, failed);
        }
        editor.apply();
    }

    public static void clearFailedAttempts(Context context) {
        prefs(context).edit()
                .putInt(KEY_FAILED_COUNT, 0)
                .putLong(KEY_LOCKED_UNTIL, 0)
                .apply();
    }

    public static int failedCount(Context context) {
        return prefs(context).getInt(KEY_FAILED_COUNT, 0);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String hashPassword(String password) {
        return hash("KeyScanPasswordForge:" + password);
    }

    private static String hashAnswer(String answer) {
        return hash("KeyScanPasswordForgeAnswer:" + normalizeAnswer(answer));
    }

    private static String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }
}

