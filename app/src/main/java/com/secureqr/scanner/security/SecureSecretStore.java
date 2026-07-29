package com.secureqr.scanner.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureSecretStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "keyscan_secure_secret_store";
    private static final String SECURE_PREFIX = "__secure_";
    private static final String MIGRATED_PREFIX = "__secure_migrated_";

    private SecureSecretStore() {
    }

    public static boolean hasSecret(Context context, String prefsName, String key) {
        return prefs(context, prefsName).contains(secureKey(key));
    }

    public static String getSecret(Context context, String prefsName, String key) {
        SharedPreferences prefs = prefs(context, prefsName);
        String stored = prefs.getString(secureKey(key), "");
        if (stored == null || stored.isEmpty()) return "";
        try {
            JSONObject object = new JSONObject(stored);
            byte[] iv = Base64.decode(object.getString("iv"), Base64.NO_WRAP);
            byte[] data = Base64.decode(object.getString("data"), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(data);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean putSecret(Context context, String prefsName, String key, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            JSONObject object = new JSONObject();
            object.put("version", 1);
            object.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
            object.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
            prefs(context, prefsName).edit().putString(secureKey(key), object.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void removeSecret(Context context, String prefsName, String key) {
        prefs(context, prefsName).edit().remove(secureKey(key)).apply();
    }

    public static MigrationResult migrateLegacyString(Context context, String prefsName, String key) {
        SharedPreferences prefs = prefs(context, prefsName);
        if (hasSecret(context, prefsName, key)) {
            return new MigrationResult(key, "already_secure");
        }
        String legacy = prefs.getString(key, null);
        if (legacy == null) {
            return new MigrationResult(key, "missing");
        }
        if (!putSecret(context, prefsName, key, legacy)) {
            return new MigrationResult(key, "failed_kept_legacy");
        }
        String verify = getSecret(context, prefsName, key);
        if (!legacy.equals(verify)) {
            prefs.edit().remove(secureKey(key)).apply();
            return new MigrationResult(key, "failed_kept_legacy");
        }
        prefs.edit()
                .remove(key)
                .putBoolean(MIGRATED_PREFIX + key, true)
                .apply();
        return new MigrationResult(key, "migrated");
    }

    public static String secureKey(String key) {
        return SECURE_PREFIX + key;
    }

    private static SharedPreferences prefs(Context context, String prefsName) {
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false);
        }
        generator.init(builder.build());
        return generator.generateKey();
    }

    public static final class MigrationResult {
        public final String key;
        public final String status;

        MigrationResult(String key, String status) {
            this.key = key;
            this.status = status;
        }
    }
}
