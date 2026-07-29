package com.secureqr.scanner.clipboard;

import android.content.Context;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordNote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ClipboardSensitiveClassifier {
    private static final int MAX_TEXT_LENGTH = 64 * 1024;
    private static final Pattern LICENSE_KEY = Pattern.compile("(?i)\\b[A-Z0-9]{4,8}(-[A-Z0-9]{4,8}){2,8}\\b");
    private static final Pattern API_KEY = Pattern.compile("(?i)\\b(api[_-]?key|access[_-]?token|bearer|secret|client[_-]?secret|token)\\b");
    private static final Pattern RECOVERY_CODE = Pattern.compile("(?i)\\b(recovery|backup|restore)[ _-]?(code|key|secret)\\b");
    private static final Pattern CONNECTION = Pattern.compile("(?i)\\b(postgres|postgresql|mysql|mongodb|redis|jdbc|sftp|ftp|webdav)://");
    private static final Pattern WIFI = Pattern.compile("(?i)^WIFI:.*;");
    private static final Pattern PASSWORD_PAIR = Pattern.compile("(?i)\\b(password|passwd|pwd|pass)\\s*[:=]");
    private static final Pattern HIGH_ENTROPY = Pattern.compile("^[A-Za-z0-9_\\-+/=:.]{24,}$");

    private ClipboardSensitiveClassifier() {
    }

    public static Result classify(String raw) {
        return classify(null, raw);
    }

    public static Result classify(Context context, String raw) {
        if (raw == null) return Result.notSensitive();
        String text = raw.trim();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) return Result.notSensitive();
        String lower = text.toLowerCase(Locale.US);
        if (text.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----")
                || text.startsWith("-----BEGIN RSA PRIVATE KEY-----")
                || text.startsWith("-----BEGIN PRIVATE KEY-----")) {
            return result(Category.SSH_PRIVATE_KEY, PasswordNote.TYPE_SERVER, 95, text,
                    label(context, R.string.clipboard_label_ssh_private_key, "SSH private key"), "server");
        }
        if (text.startsWith("ssh-rsa ") || text.startsWith("ssh-ed25519 ") || text.startsWith("ecdsa-sha2-")) {
            return result(Category.SSH_PUBLIC_KEY, PasswordNote.TYPE_SERVER, 85, text,
                    label(context, R.string.clipboard_label_ssh_public_key, "SSH public key"), "server");
        }
        if (lower.startsWith("otpauth://")) {
            return result(Category.OTP_URI, PasswordNote.TYPE_SECURE_NOTE, 95, text,
                    label(context, R.string.clipboard_label_otp_secret, "TOTP secret"), "secure");
        }
        if (WIFI.matcher(text).find()) {
            return result(Category.WIFI, PasswordNote.TYPE_SECURE_NOTE, 90, text,
                    label(context, R.string.clipboard_label_wifi_credentials, "Wi-Fi credentials"), "secure");
        }
        if (CONNECTION.matcher(text).find()) {
            return result(Category.CONNECTION_STRING, PasswordNote.TYPE_SERVER, 90, text,
                    label(context, R.string.clipboard_label_connection_credentials, "Service connection credentials"), "server");
        }
        if (API_KEY.matcher(text).find()) {
            return result(Category.API_KEY, PasswordNote.TYPE_SECURE_NOTE, 85, text,
                    label(context, R.string.clipboard_label_api_token, "API key / token"), "secure");
        }
        if (RECOVERY_CODE.matcher(text).find()) {
            return result(Category.RECOVERY_CODE, PasswordNote.TYPE_SECURE_NOTE, 82, text,
                    label(context, R.string.clipboard_label_recovery_code, "Recovery code"), "secure");
        }
        if (PASSWORD_PAIR.matcher(text).find()) {
            return result(Category.PASSWORD_TEXT, PasswordNote.TYPE_SECURE_NOTE, 75, text,
                    label(context, R.string.clipboard_label_password_text, "Password text"), "secure");
        }
        if (LICENSE_KEY.matcher(text).find()) {
            return result(Category.LICENSE_KEY, PasswordNote.TYPE_SOFTWARE_LICENSE, 80, text,
                    label(context, R.string.clipboard_label_license_key, "Software license key"), "license");
        }
        if (looksLikeHighEntropySecret(text)) {
            return result(Category.UNKNOWN_SECRET, PasswordNote.TYPE_SECURE_NOTE, 70, text,
                    label(context, R.string.clipboard_label_possible_secret, "Possible secret"), "secure");
        }
        return Result.notSensitive();
    }

    private static String label(Context context, int resource, String fallback) {
        return context == null ? fallback : context.getString(resource);
    }

    private static boolean looksLikeHighEntropySecret(String text) {
        if (!HIGH_ENTROPY.matcher(text).matches()) return false;
        int classes = 0;
        if (Pattern.compile("[a-z]").matcher(text).find()) classes++;
        if (Pattern.compile("[A-Z]").matcher(text).find()) classes++;
        if (Pattern.compile("[0-9]").matcher(text).find()) classes++;
        if (Pattern.compile("[_\\-+/=:.]").matcher(text).find()) classes++;
        return classes >= 2;
    }

    private static Result result(Category category, String noteType, int confidence, String text, String label, String group) {
        List<String> alternatives = new ArrayList<>();
        alternatives.add(noteType);
        if (!PasswordNote.TYPE_SECURE_NOTE.equals(noteType)) alternatives.add(PasswordNote.TYPE_SECURE_NOTE);
        if (!PasswordNote.TYPE_CUSTOM.equals(noteType)) alternatives.add(PasswordNote.TYPE_CUSTOM);
        return new Result(true, category, noteType, confidence, mask(text), label, alternatives, group);
    }

    private static String mask(String text) {
        String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() <= 8) return "\u2022\u2022\u2022\u2022";
        String prefix = singleLine.substring(0, Math.min(4, singleLine.length()));
        String suffix = singleLine.substring(Math.max(0, singleLine.length() - 4));
        return prefix + "\u2022\u2022\u2022\u2022" + suffix;
    }

    public enum Category {
        LICENSE_KEY,
        API_KEY,
        ACCESS_TOKEN,
        SSH_PRIVATE_KEY,
        SSH_PUBLIC_KEY,
        RECOVERY_CODE,
        OTP_URI,
        CONNECTION_STRING,
        WIFI,
        PASSWORD_TEXT,
        UNKNOWN_SECRET,
        NONE
    }

    public static final class Result {
        public final boolean sensitive;
        public final Category category;
        public final String suggestedNoteType;
        public final int confidence;
        public final String maskedPreview;
        public final String displayLabel;
        public final List<String> alternativeNoteTypes;
        public final String group;

        private Result(boolean sensitive, Category category, String suggestedNoteType, int confidence,
                       String maskedPreview, String displayLabel, List<String> alternativeNoteTypes, String group) {
            this.sensitive = sensitive;
            this.category = category;
            this.suggestedNoteType = suggestedNoteType;
            this.confidence = confidence;
            this.maskedPreview = maskedPreview;
            this.displayLabel = displayLabel;
            this.alternativeNoteTypes = Collections.unmodifiableList(alternativeNoteTypes);
            this.group = group;
        }

        private static Result notSensitive() {
            return new Result(false, Category.NONE, PasswordNote.TYPE_SECURE_NOTE, 0, "", "", Collections.emptyList(), "");
        }
    }
}
