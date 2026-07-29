package com.secureqr.scanner.clipboard;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

public final class ClipboardImportSession {
    private static final Object LOCK = new Object();
    private static final Set<String> ignoredDigests = new HashSet<>();
    private static String internalCopyDigest;
    private static String pendingText;
    private static ClipboardSensitiveClassifier.Result pendingResult;
    private static String pendingDigest;
    private static boolean pendingFromShare;

    private ClipboardImportSession() {
    }

    public static boolean begin(String text, ClipboardSensitiveClassifier.Result result, boolean fromShare) {
        if (text == null || result == null || !result.sensitive) return false;
        String digest = sha256(text);
        synchronized (LOCK) {
            if (digest.equals(internalCopyDigest) || ignoredDigests.contains(digest)) return false;
            pendingText = text;
            pendingResult = result;
            pendingDigest = digest;
            pendingFromShare = fromShare;
            return true;
        }
    }

    public static Pending current() {
        synchronized (LOCK) {
            if (pendingText == null || pendingResult == null) return null;
            return new Pending(pendingText, pendingResult, pendingDigest, pendingFromShare);
        }
    }

    public static void ignorePending() {
        synchronized (LOCK) {
            if (pendingDigest != null) ignoredDigests.add(pendingDigest);
            clearPendingLocked();
        }
    }

    public static void clearPending() {
        synchronized (LOCK) {
            clearPendingLocked();
        }
    }

    public static void markInternalCopy(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (LOCK) {
            internalCopyDigest = sha256(text);
        }
    }

    public static boolean isInternalCopy(String text) {
        if (text == null || text.isEmpty()) return false;
        synchronized (LOCK) {
            return sha256(text).equals(internalCopyDigest);
        }
    }

    private static void clearPendingLocked() {
        pendingText = null;
        pendingResult = null;
        pendingDigest = null;
        pendingFromShare = false;
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    public static final class Pending {
        public final String text;
        public final ClipboardSensitiveClassifier.Result result;
        public final String digest;
        public final boolean fromShare;

        private Pending(String text, ClipboardSensitiveClassifier.Result result, String digest, boolean fromShare) {
            this.text = text;
            this.result = result;
            this.digest = digest;
            this.fromShare = fromShare;
        }
    }
}
