package com.secureqr.scanner.importer.session;

import com.secureqr.scanner.importer.model.ImportedOtp;
import com.secureqr.scanner.importer.model.ImportedPassword;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, process-memory-only storage for sensitive import previews. */
public final class ImportSessionManager {
    private static final ConcurrentHashMap<String, ImportSession> SESSIONS = new ConcurrentHashMap<>();

    private ImportSessionManager() { }

    public static String create(List<ImportedPassword> passwords, List<ImportedOtp> otps) {
        String id = UUID.randomUUID().toString();
        SESSIONS.put(id, new ImportSession(passwords, otps));
        return id;
    }

    public static ImportSession get(String sessionId) {
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }

    public static void clear(String sessionId) {
        ImportSession session = sessionId == null ? null : SESSIONS.remove(sessionId);
        if (session != null) session.wipe();
    }

    public static void wipe(List<ImportedPassword> passwords, List<ImportedOtp> otps) {
        if (passwords != null) {
            for (ImportedPassword item : passwords) wipePassword(item);
            passwords.clear();
        }
        if (otps != null) {
            for (ImportedOtp item : otps) wipeOtp(item);
            otps.clear();
        }
    }

    public static final class ImportSession {
        private final ArrayList<ImportedPassword> passwords;
        private final ArrayList<ImportedOtp> otps;

        private ImportSession(List<ImportedPassword> passwords, List<ImportedOtp> otps) {
            this.passwords = copyPasswords(passwords);
            this.otps = copyOtps(otps);
        }

        public List<ImportedPassword> passwords() { return new ArrayList<>(passwords); }
        public List<ImportedOtp> otps() { return new ArrayList<>(otps); }

        private void wipe() {
            ImportSessionManager.wipe(passwords, otps);
        }
    }

    private static ArrayList<ImportedPassword> copyPasswords(List<ImportedPassword> source) {
        ArrayList<ImportedPassword> result = new ArrayList<>();
        if (source == null) return result;
        for (ImportedPassword value : source) {
            if (value == null) continue;
            ImportedPassword copy = new ImportedPassword();
            copy.title = value.title; copy.websiteDomain = value.websiteDomain; copy.appPackageName = value.appPackageName;
            copy.username = value.username; copy.account = value.account; copy.password = value.password;
            copy.notes = value.notes; copy.folderName = value.folderName; copy.sourceFormat = value.sourceFormat;
            result.add(copy);
        }
        return result;
    }

    private static ArrayList<ImportedOtp> copyOtps(List<ImportedOtp> source) {
        ArrayList<ImportedOtp> result = new ArrayList<>();
        if (source == null) return result;
        for (ImportedOtp value : source) {
            if (value == null) continue;
            ImportedOtp copy = new ImportedOtp();
            copy.issuer = value.issuer; copy.account = value.account; copy.secret = value.secret;
            copy.algorithm = value.algorithm; copy.digits = value.digits; copy.period = value.period; copy.sourceFormat = value.sourceFormat;
            result.add(copy);
        }
        return result;
    }

    private static void wipePassword(ImportedPassword item) {
        if (item == null) return;
        item.password = null; item.username = null; item.account = null; item.notes = null;
        item.websiteDomain = null; item.title = null; item.folderName = null; item.appPackageName = null;
    }

    private static void wipeOtp(ImportedOtp item) {
        if (item == null) return;
        item.secret = null; item.account = null; item.issuer = null; item.algorithm = null;
    }
}
