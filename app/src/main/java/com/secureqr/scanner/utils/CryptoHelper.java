package com.secureqr.scanner.utils;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoHelper {
    private static final int SALT_SIZE = 16;
    private static final int IV_SIZE = 12;
    private static final int KEY_SIZE = 256;
    private static final int TAG_SIZE = 128;
    private static final int ITERATIONS = 120_000;

    private CryptoHelper() {
    }

    public static String encrypt(String plaintext, String password) throws Exception {
        return encrypt(plaintext, password, "AES-GCM");
    }

    public static String decrypt(String ciphertext, String password) throws Exception {
        if (ciphertext != null && ciphertext.startsWith("CQR1:")) {
            String[] parts = ciphertext.split(":", 3);
            return decryptWithAlgorithm(parts[2], password, parts[1]);
        }
        return decryptWithAlgorithm(ciphertext, password, "AES-GCM");
    }

    public static String encrypt(String plaintext, String password, String algorithm) throws Exception {
        String normalized = normalizeAlgorithm(algorithm);
        String encrypted = encryptWithAlgorithm(plaintext, password, normalized);
        return "CQR1:" + normalized + ":" + encrypted;
    }

    public static String[] supportedAlgorithms() {
        return new String[]{"AES-GCM", "AES-CBC", "DES-CBC", "RSA", "SM4 (placeholder)"};
    }

    private static String encryptWithAlgorithm(String plaintext, String password, String algorithm) throws Exception {
        if ("AES-CBC".equals(algorithm)) {
            return encryptBlockCipher(plaintext, password, "AES", "AES/CBC/PKCS5Padding", 16, 256);
        }
        if ("DES-CBC".equals(algorithm)) {
            return encryptBlockCipher(plaintext, password, "DES", "DES/CBC/PKCS5Padding", 8, 64);
        }
        if ("RSA".equals(algorithm)) {
            return encryptRsaEnvelope(plaintext, password);
        }
        if ("SM4 (placeholder)".equals(algorithm) || "SM4-\u5360\u4f4d".equals(algorithm)) {
            throw new IllegalArgumentException("SM4 encryption provider is unavailable. Please choose AES-GCM/AES-CBC/RSA/DES-CBC.");
        }
        byte[] salt = randomBytes(SALT_SIZE);
        byte[] iv = randomBytes(IV_SIZE);
        SecretKeySpec key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + encrypted.length);
        buffer.put(salt).put(iv).put(encrypted);
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
    }

    private static String decryptWithAlgorithm(String ciphertext, String password, String algorithm) throws Exception {
        if ("AES-CBC".equals(algorithm)) {
            return decryptBlockCipher(ciphertext, password, "AES", "AES/CBC/PKCS5Padding", 16, 256);
        }
        if ("DES-CBC".equals(algorithm)) {
            return decryptBlockCipher(ciphertext, password, "DES", "DES/CBC/PKCS5Padding", 8, 64);
        }
        if ("RSA".equals(algorithm)) {
            return decryptRsaEnvelope(ciphertext, password);
        }
        byte[] all = Base64.decode(ciphertext, Base64.NO_WRAP);
        ByteBuffer buffer = ByteBuffer.wrap(all);
        byte[] salt = new byte[SALT_SIZE];
        byte[] iv = new byte[IV_SIZE];
        byte[] encrypted = new byte[buffer.remaining() - SALT_SIZE - IV_SIZE];
        buffer.get(salt);
        buffer.get(iv);
        buffer.get(encrypted);
        SecretKeySpec key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static String encryptBlockCipher(String plaintext, String password, String keyName, String transformation, int ivSize, int keySize) throws Exception {
        byte[] salt = randomBytes(SALT_SIZE);
        byte[] iv = randomBytes(ivSize);
        SecretKeySpec key = deriveKey(password, salt, keySize, keyName);
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + encrypted.length);
        buffer.put(salt).put(iv).put(encrypted);
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
    }

    private static String decryptBlockCipher(String ciphertext, String password, String keyName, String transformation, int ivSize, int keySize) throws Exception {
        byte[] all = Base64.decode(ciphertext, Base64.NO_WRAP);
        ByteBuffer buffer = ByteBuffer.wrap(all);
        byte[] salt = new byte[SALT_SIZE];
        byte[] iv = new byte[ivSize];
        byte[] encrypted = new byte[buffer.remaining() - SALT_SIZE - ivSize];
        buffer.get(salt);
        buffer.get(iv);
        buffer.get(encrypted);
        SecretKeySpec key = deriveKey(password, salt, keySize, keyName);
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static String encryptRsaEnvelope(String plaintext, String password) throws Exception {
        KeyPair pair = deterministicRsaPair(password);
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKeySpec dataKey = new SecretKeySpec(keyGenerator.generateKey().getEncoded(), "AES");
        byte[] iv = randomBytes(IV_SIZE);
        Cipher dataCipher = Cipher.getInstance("AES/GCM/NoPadding");
        dataCipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(TAG_SIZE, iv));
        byte[] encryptedData = dataCipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        Cipher keyCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        keyCipher.init(Cipher.ENCRYPT_MODE, pair.getPublic());
        byte[] encryptedKey = keyCipher.doFinal(dataKey.getEncoded());

        ByteBuffer buffer = ByteBuffer.allocate(4 + encryptedKey.length + iv.length + encryptedData.length);
        buffer.putInt(encryptedKey.length).put(encryptedKey).put(iv).put(encryptedData);
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
    }

    private static String decryptRsaEnvelope(String ciphertext, String password) throws Exception {
        KeyPair pair = deterministicRsaPair(password);
        ByteBuffer buffer = ByteBuffer.wrap(Base64.decode(ciphertext, Base64.NO_WRAP));
        int keyLength = buffer.getInt();
        byte[] encryptedKey = new byte[keyLength];
        byte[] iv = new byte[IV_SIZE];
        byte[] encryptedData = new byte[buffer.remaining() - keyLength - IV_SIZE];
        buffer.get(encryptedKey);
        buffer.get(iv);
        buffer.get(encryptedData);

        Cipher keyCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        keyCipher.init(Cipher.DECRYPT_MODE, pair.getPrivate());
        SecretKeySpec dataKey = new SecretKeySpec(keyCipher.doFinal(encryptedKey), "AES");
        Cipher dataCipher = Cipher.getInstance("AES/GCM/NoPadding");
        dataCipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(TAG_SIZE, iv));
        return new String(dataCipher.doFinal(encryptedData), StandardCharsets.UTF_8);
    }

    private static KeyPair deterministicRsaPair(String password) throws Exception {
        SecureRandom seeded = SecureRandom.getInstance("SHA1PRNG");
        seeded.setSeed(password.getBytes(StandardCharsets.UTF_8));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, seeded);
        return generator.generateKeyPair();
    }

    private static SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
        return deriveKey(password, salt, KEY_SIZE, "AES");
    }

    private static SecretKeySpec deriveKey(String password, byte[] salt, int keySize, String keyName) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, Math.max(keySize, KEY_SIZE));
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        if (keySize != KEY_SIZE) {
            byte[] resized = new byte[keySize / 8];
            System.arraycopy(key, 0, resized, 0, resized.length);
            key = resized;
        }
        return new SecretKeySpec(key, keyName);
    }

    private static String normalizeAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.trim().isEmpty()) return "AES-GCM";
        if ("SM4".equalsIgnoreCase(algorithm) || "SM4-\u5360\u4f4d".equals(algorithm)) return "SM4 (placeholder)";
        return algorithm.trim();
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}

