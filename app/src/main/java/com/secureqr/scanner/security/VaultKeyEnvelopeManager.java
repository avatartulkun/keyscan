package com.secureqr.scanner.security;

import android.content.Context;
import android.util.Base64;

import com.secureqr.scanner.utils.CryptoHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Maintains the two-level vault key model:
 * key0 is the stable random SQLCipher key; key1 is deterministically derived
 * from the user's PIN and data-protection key and only wraps key0.
 */
public final class VaultKeyEnvelopeManager {
    static final String KEY_ROOT_KEY = "vault_root_key_v1";
    static final String KEY_DATABASE_ENVELOPE = "database_key_envelope_v1";
    private static final String DOMAIN = "KeyScanVaultRoot:v1";
    private static final int ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;

    private VaultKeyEnvelopeManager() { }

    public static String deriveRootKey(String pin, String dataProtectionKey) {
        if (pin == null || pin.isEmpty() || dataProtectionKey == null || dataProtectionKey.isEmpty()) return "";
        try {
            byte[] salt = MessageDigest.getInstance("SHA-256")
                    .digest(DOMAIN.getBytes(StandardCharsets.UTF_8));
            char[] material = (DOMAIN + "\nPIN=" + pin + "\nDEK=" + dataProtectionKey).toCharArray();
            PBEKeySpec spec = new PBEKeySpec(material, salt, ITERATIONS, KEY_BITS);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.encodeToString(key, Base64.NO_WRAP);
        } catch (Exception error) {
            return "";
        }
    }

    public static String generateDatabaseKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.encodeToString(key, Base64.NO_WRAP);
    }

    public static boolean create(Context context, String pin, String dataProtectionKey, String databaseKey) {
        String rootKey = deriveRootKey(pin, dataProtectionKey);
        if (rootKey.isEmpty() || databaseKey == null || databaseKey.isEmpty()) return false;
        try {
            String envelope = CryptoHelper.encrypt(databaseKey, rootKey);
            if (!SecureSecretStore.putSecret(context, SecuritySettings.PREFS, KEY_ROOT_KEY, rootKey)) return false;
            SecuritySettings.prefs(context).edit().putString(KEY_DATABASE_ENVELOPE, envelope).apply();
            return databaseKey.equals(unwrapDatabaseKey(context));
        } catch (Exception error) {
            return false;
        }
    }

    public static String rootKey(Context context) {
        return SecureSecretStore.getSecret(context, SecuritySettings.PREFS, KEY_ROOT_KEY);
    }

    public static String unwrapDatabaseKey(Context context) {
        String rootKey = rootKey(context);
        String envelope = SecuritySettings.prefs(context).getString(KEY_DATABASE_ENVELOPE, "");
        if (rootKey.isEmpty() || envelope == null || envelope.isEmpty()) return "";
        try {
            return CryptoHelper.decrypt(envelope, rootKey);
        } catch (Exception error) {
            return "";
        }
    }

    public static boolean rewrap(Context context, String pin, String dataProtectionKey) {
        String databaseKey = unwrapDatabaseKey(context);
        if (databaseKey.isEmpty()) return false;
        return create(context, pin, dataProtectionKey, databaseKey);
    }

    public static boolean isInitialized(Context context) {
        return !rootKey(context).isEmpty()
                && !SecuritySettings.prefs(context).getString(KEY_DATABASE_ENVELOPE, "").isEmpty();
    }
}
