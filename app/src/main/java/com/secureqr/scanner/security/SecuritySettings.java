package com.secureqr.scanner.security;

import android.content.Context;
import android.content.SharedPreferences;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.utils.PinLockHelper;

import java.util.UUID;

public final class SecuritySettings {
    public static final String PREFS = "security_settings";
    public static final String KEY_PIN_ENABLED = "pin_enabled";
    public static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    public static final String KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes";
    public static final String KEY_CLIPBOARD_TIMEOUT_SECONDS = "clipboard_timeout";
    public static final String KEY_BACKUP_PASSWORD_STATE = "backup_password_state";
    public static final String KEY_BACKUP_PASSWORD = "backup_password";
    public static final String KEY_DATA_ENCRYPTION_KEY_STATE = KEY_BACKUP_PASSWORD_STATE;
    public static final String KEY_DATA_ENCRYPTION_KEY = KEY_BACKUP_PASSWORD;
    public static final String KEY_LAST_SECURITY_CHECK = "last_security_check";
    public static final String KEY_VAULT_INITIALIZED = "vault_initialized";
    private static final String KEY_PIN_OPERATION_CREDENTIAL = "pin_operation_credential";
    private static final String LEGACY_WEBDAV_PREFS = "secureqr_settings";
    private static final String LEGACY_WEBDAV_BACKUP_PASSWORD = "webdav_backup_password";

    private SecuritySettings() {
    }

    public static boolean pinEnabled(Context context) {
        return PinLockHelper.isConfigured(context) || prefs(context).getBoolean(KEY_PIN_ENABLED, false);
    }

    public static boolean isVaultInitialized(Context context) {
        if (prefs(context).getBoolean(KEY_VAULT_INITIALIZED, false)) return true;
        if (PinLockHelper.isConfigured(context) && hasDataEncryptionKey(context)) {
            markVaultInitialized(context);
            return true;
        }
        return false;
    }

    public static boolean markVaultInitialized(Context context) {
        if (!PinLockHelper.isConfigured(context) || !hasDataEncryptionKey(context)) return false;
        prefs(context).edit()
                .putBoolean(KEY_VAULT_INITIALIZED, true)
                .putLong(KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
        return true;
    }

    public static void savePin(Context context, String pin) {
        if (PinLockHelper.isConfigured(context)) {
            PinLockHelper.savePasswordAndHint(context, pin, "");
        } else {
            String question = PinLockHelper.securityQuestions(context)[0];
            PinLockHelper.saveCredentials(context, pin, "", question, UUID.randomUUID().toString());
        }
        rememberPinForKeyOperations(context, pin);
        prefs(context).edit()
                .putBoolean(KEY_PIN_ENABLED, true)
                .putLong(KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
    }

    /**
     * Keeps the PIN encrypted by Android Keystore for operations that must
     * re-wrap the database key after biometric authentication.
     */
    public static boolean rememberPinForKeyOperations(Context context, String pin) {
        if (pin == null || !PinLockHelper.verifyPin(context, pin)) return false;
        return SecureSecretStore.putSecret(context, PREFS, KEY_PIN_OPERATION_CREDENTIAL, pin);
    }

    public static String getPinForKeyOperations(Context context) {
        String pin = SecureSecretStore.getSecret(context, PREFS, KEY_PIN_OPERATION_CREDENTIAL);
        if (pin == null || pin.isEmpty() || !PinLockHelper.verifyPin(context, pin)) return "";
        return pin;
    }

    public static int autoLockMinutes(Context context) {
        return prefs(context).getInt(KEY_AUTO_LOCK_MINUTES, 5);
    }

    public static void setAutoLockMinutes(Context context, int minutes) {
        prefs(context).edit()
                .putInt(KEY_AUTO_LOCK_MINUTES, minutes)
                .putLong(KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
    }

    public static int clipboardTimeoutSeconds(Context context) {
        return prefs(context).getInt(KEY_CLIPBOARD_TIMEOUT_SECONDS, 60);
    }

    public static void setClipboardTimeoutSeconds(Context context, int seconds) {
        prefs(context).edit()
                .putInt(KEY_CLIPBOARD_TIMEOUT_SECONDS, seconds)
                .putLong(KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                .apply();
    }

    public static boolean hasBackupPassword(Context context) {
        return hasDataEncryptionKey(context);
    }

    public static boolean hasDataEncryptionKey(Context context) {
        migrateLegacyWebDavBackupPasswordIfNeeded(context);
        return prefs(context).getBoolean(KEY_BACKUP_PASSWORD_STATE, false)
                && SecureSecretStore.hasSecret(context, PREFS, KEY_BACKUP_PASSWORD);
    }

    public static boolean saveBackupPassword(Context context, String password) {
        return saveDataEncryptionKey(context, password);
    }

    public static boolean saveDataEncryptionKey(Context context, String dataEncryptionKey) {
        boolean saved = SecureSecretStore.putSecret(context, PREFS, KEY_DATA_ENCRYPTION_KEY, dataEncryptionKey);
        if (saved) {
            prefs(context).edit()
                    .putBoolean(KEY_DATA_ENCRYPTION_KEY_STATE, true)
                    .putLong(KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                    .apply();
        }
        return saved;
    }

    public static boolean changeDataEncryptionKey(Context context, String currentKey, String nextKey) {
        return false;
    }

    public static boolean changeDataEncryptionKey(Context context, String currentKey, String nextKey, String pin) {
        String savedKey = getDataEncryptionKey(context);
        if (savedKey == null || savedKey.isEmpty()) return false;
        if (currentKey == null || !savedKey.equals(currentKey)) return false;
        if (nextKey == null || nextKey.isEmpty()) return false;
        if (pin == null || !PinLockHelper.verifyPin(context, pin)) return false;
        if (!DatabaseKeyManager.ensurePersistentDatabaseKey(context)) return false;
        boolean saved = saveDataEncryptionKey(context, nextKey);
        boolean updated = saved && DatabaseKeyManager.rewrapDatabaseKey(context, pin, nextKey)
                && verifyDatabaseOpenable(context);
        if (updated) return true;

        saveDataEncryptionKey(context, savedKey);
        DatabaseKeyManager.rewrapDatabaseKey(context, pin, savedKey);
        SecurityAuditLog.record(context, "修改数据保护密钥回滚旧状态", true);
        return false;
    }

    public static String getDataEncryptionKey(Context context) {
        migrateLegacyWebDavBackupPasswordIfNeeded(context);
        return SecureSecretStore.getSecret(context, PREFS, KEY_DATA_ENCRYPTION_KEY);
    }

    private static boolean verifyDatabaseOpenable(Context context) {
        try {
            AppDatabase database = AppDatabase.getInstance(context.getApplicationContext());
            database.getOpenHelper().getWritableDatabase();
            return true;
        } catch (RuntimeException e) {
            SecurityAuditLog.record(context, "修改数据保护密钥后数据库验证失败", false);
            return false;
        }
    }

    private static void migrateLegacyWebDavBackupPasswordIfNeeded(Context context) {
        if (SecureSecretStore.hasSecret(context, PREFS, KEY_DATA_ENCRYPTION_KEY)) return;
        String legacy = SecureSecretStore.getSecret(context, LEGACY_WEBDAV_PREFS, LEGACY_WEBDAV_BACKUP_PASSWORD);
        if (legacy == null || legacy.isEmpty()) {
            legacy = context.getSharedPreferences(LEGACY_WEBDAV_PREFS, Context.MODE_PRIVATE)
                    .getString(LEGACY_WEBDAV_BACKUP_PASSWORD, "");
        }
        if (legacy == null || legacy.isEmpty()) return;
        saveDataEncryptionKey(context, legacy);
    }

    public static long lastSecurityCheck(Context context) {
        long saved = prefs(context).getLong(KEY_LAST_SECURITY_CHECK, 0);
        if (saved == 0) {
            saved = System.currentTimeMillis();
            prefs(context).edit().putLong(KEY_LAST_SECURITY_CHECK, saved).apply();
        }
        return saved;
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
