package com.secureqr.scanner.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.secureqr.scanner.utils.PinLockHelper;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class DatabaseKeyManager {
    public static final String STATUS_OK = DatabaseOpenState.NORMAL.name();
    public static final String STATUS_NOT_INITIALIZED = DatabaseOpenState.NOT_INITIALIZED.name();
    public static final String STATUS_DATABASE_KEY_ERROR = DatabaseOpenState.DATABASE_KEY_ERROR.name();
    public static final String STATUS_LEGACY_OK = DatabaseOpenState.LEGACY_COMPATIBLE.name();
    private static final String KEY_DATABASE_KEY = "database_key";
    private static final String KEY_DATABASE_KEY_SALT = "database_key_salt";
    private static final String KEY_DATABASE_PROTECTION_ENABLED = "database_protection_enabled";
    private static final String KEY_DATABASE_LAST_OPENED = "database_last_opened";
    private static final String KEY_DATABASE_KEY_STATUS = "database_key_status";
    private static final String KEY_DATABASE_KEY_ERROR = "database_key_error";
    private static final String KEY_DATABASE_KEY_VERSION = "database_key_version";
    private static final String DATABASE_KEY_VERSION = "v2-envelope";
    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final int KDF_ITERATIONS = 180_000;
    private static final int KDF_BITS = 256;

    private DatabaseKeyManager() {
    }

    public static synchronized String getDatabaseKey(Context context) {
        String enveloped = VaultKeyEnvelopeManager.unwrapDatabaseKey(context);
        if (!enveloped.isEmpty()) return enveloped;
        String stored = SecureSecretStore.getSecret(context, SecuritySettings.PREFS, KEY_DATABASE_KEY);
        if (!stored.isEmpty()) {
            return stored;
        }
        return "";
    }

    public static synchronized boolean initializeDatabaseKey(Context context, String pin) {
        String dataKey = SecuritySettings.getDataEncryptionKey(context);
        if (pin == null || pin.isEmpty() || dataKey == null || dataKey.isEmpty()) return false;
        String existing = SecureSecretStore.getSecret(context, SecuritySettings.PREFS, KEY_DATABASE_KEY);
        String databaseKey = existing.isEmpty() ? VaultKeyEnvelopeManager.generateDatabaseKey() : existing;
        if (!VaultKeyEnvelopeManager.create(context, pin, dataKey, databaseKey)) return false;
        SecureSecretStore.removeSecret(context, SecuritySettings.PREFS, KEY_DATABASE_KEY);
        markDatabaseOpened(context);
        return databaseKey.equals(getDatabaseKey(context));
    }

    public static boolean isDatabaseProtectionEnabled(Context context) {
        return SecuritySettings.prefs(context).getBoolean(KEY_DATABASE_PROTECTION_ENABLED, false)
                && (VaultKeyEnvelopeManager.isInitialized(context)
                || SecureSecretStore.hasSecret(context, SecuritySettings.PREFS, KEY_DATABASE_KEY));
    }

    public static synchronized boolean ensurePersistentDatabaseKey(Context context) {
        return !getDatabaseKey(context).isEmpty();
    }

    public static synchronized boolean reprotectDatabaseKey(Context context) {
        if (getDatabaseKey(context).isEmpty()) return false;
        SecuritySettings.prefs(context).edit()
                .putBoolean(KEY_DATABASE_PROTECTION_ENABLED, true)
                .putString(KEY_DATABASE_KEY_STATUS, DatabaseOpenState.NORMAL.name())
                .putString(KEY_DATABASE_KEY_VERSION, DATABASE_KEY_VERSION)
                .putLong(SecuritySettings.KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
        return true;
    }

    public static synchronized boolean rewrapDatabaseKey(Context context, String pin, String dataProtectionKey) {
        if (!VaultKeyEnvelopeManager.rewrap(context, pin, dataProtectionKey)) return false;
        return reprotectDatabaseKey(context);
    }

    /** key1 used to unwrap key0 and to authenticate current secure backup containers. */
    public static String getBackupRootKey(Context context) {
        return VaultKeyEnvelopeManager.rootKey(context);
    }

    public static void markDatabaseOpened(Context context) {
        SecuritySettings.prefs(context).edit()
                .putBoolean(KEY_DATABASE_PROTECTION_ENABLED, true)
                .putString(KEY_DATABASE_KEY_STATUS, DatabaseOpenState.NORMAL.name())
                .putString(KEY_DATABASE_KEY_VERSION, DATABASE_KEY_VERSION)
                .remove(KEY_DATABASE_KEY_ERROR)
                .putLong(KEY_DATABASE_LAST_OPENED, System.currentTimeMillis())
                .putLong(SecuritySettings.KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
    }

    public static void markLegacyDatabaseOpened(Context context) {
        SecuritySettings.prefs(context).edit()
                .putString(KEY_DATABASE_KEY_STATUS, DatabaseOpenState.LEGACY_COMPATIBLE.name())
                .putString(KEY_DATABASE_KEY_VERSION, "legacy")
                .remove(KEY_DATABASE_KEY_ERROR)
                .putLong(KEY_DATABASE_LAST_OPENED, System.currentTimeMillis())
                .putLong(SecuritySettings.KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
    }

    public static void markDatabaseKeyError(Context context, DatabaseOpenState state, Throwable error) {
        String reason = error == null ? "unknown" : error.getClass().getSimpleName();
        SecuritySettings.prefs(context).edit()
                .putString(KEY_DATABASE_KEY_STATUS, state == null ? DatabaseOpenState.DATABASE_ACCESS_ERROR.name() : state.name())
                .putString(KEY_DATABASE_KEY_ERROR, reason)
                .putLong(SecuritySettings.KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
    }

    public static String databaseKeyStatus(Context context) {
        return SecuritySettings.prefs(context).getString(KEY_DATABASE_KEY_STATUS, STATUS_NOT_INITIALIZED);
    }

    public static DatabaseOpenState databaseOpenState(Context context) {
        String value = databaseKeyStatus(context);
        try {
            return DatabaseOpenState.valueOf(value);
        } catch (Exception ignored) {
            return DatabaseOpenState.NOT_INITIALIZED;
        }
    }

    private static String initializeFromMaterial(Context context, String pinMaterial) {
        String dataKey = SecuritySettings.getDataEncryptionKey(context);
        if (pinMaterial == null || pinMaterial.isEmpty() || dataKey == null || dataKey.isEmpty()) return "";
        String salt = databaseSalt(context);
        String derived = derive(pinMaterial, dataKey, salt);
        if (derived.isEmpty()) return "";
        if (!SecureSecretStore.putSecret(context, SecuritySettings.PREFS, KEY_DATABASE_KEY, derived)) return "";
        SecuritySettings.prefs(context).edit()
                .putBoolean(KEY_DATABASE_PROTECTION_ENABLED, true)
                .putString(KEY_DATABASE_KEY_STATUS, DatabaseOpenState.NORMAL.name())
                .putString(KEY_DATABASE_KEY_VERSION, DATABASE_KEY_VERSION)
                .putLong(SecuritySettings.KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
        return derived;
    }

    private static String databaseSalt(Context context) {
        SharedPreferences prefs = SecuritySettings.prefs(context);
        String saved = prefs.getString(KEY_DATABASE_KEY_SALT, "");
        if (saved != null && !saved.isEmpty()) return saved;
        byte[] salt = new byte[24];
        new SecureRandom().nextBytes(salt);
        saved = Base64.encodeToString(salt, Base64.NO_WRAP);
        prefs.edit().putString(KEY_DATABASE_KEY_SALT, saved).apply();
        return saved;
    }

    private static String derive(String pinMaterial, String dataEncryptionKey, String salt) {
        try {
            String input = "KeyScanDatabaseKey:v1\nPIN=" + pinMaterial + "\nDEK=" + dataEncryptionKey;
            PBEKeySpec spec = new PBEKeySpec(
                    input.toCharArray(),
                    Base64.decode(salt, Base64.NO_WRAP),
                    KDF_ITERATIONS,
                    KDF_BITS
            );
            byte[] key = SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
            return Base64.encodeToString(key, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
