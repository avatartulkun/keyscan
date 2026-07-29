package com.secureqr.scanner.backup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Stream format for v5 local backup containers. Header contains no user data. */
final class BackupStreamCipher {
    private static final byte[] MAGIC_V5 = new byte[]{'K','S','B','5','A','E','1',0};
    private static final byte[] MAGIC_V6 = new byte[]{'K','S','B','6','A','E','1',0};
    private static final int SALT_SIZE = 16;
    private static final int IV_SIZE = 12;
    private static final int ITERATIONS = 240_000;

    private BackupStreamCipher() { }

    static CipherOutputStream encrypting(OutputStream destination, String password) throws Exception {
        byte[] salt = random(SALT_SIZE); byte[] iv = random(IV_SIZE);
        DataOutputStream header = new DataOutputStream(destination); header.write(MAGIC_V5); header.write(salt); header.write(iv); header.flush();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), new GCMParameterSpec(128, iv));
        return new CipherOutputStream(destination, cipher);
    }

    static CipherOutputStream encryptingV6(OutputStream destination, String databaseKey, String rootKey) throws Exception {
        byte[] wrapSalt = random(SALT_SIZE);
        byte[] wrapIv = random(IV_SIZE);
        Cipher wrapCipher = Cipher.getInstance("AES/GCM/NoPadding");
        wrapCipher.init(Cipher.ENCRYPT_MODE, derive(rootKey, wrapSalt), new GCMParameterSpec(128, wrapIv));
        byte[] wrappedDatabaseKey = wrapCipher.doFinal(databaseKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] payloadSalt = random(SALT_SIZE);
        byte[] payloadIv = random(IV_SIZE);
        DataOutputStream header = new DataOutputStream(destination);
        header.write(MAGIC_V6);
        header.write(wrapSalt);
        header.write(wrapIv);
        header.writeInt(wrappedDatabaseKey.length);
        header.write(wrappedDatabaseKey);
        header.write(payloadSalt);
        header.write(payloadIv);
        header.flush();

        Cipher payloadCipher = Cipher.getInstance("AES/GCM/NoPadding");
        payloadCipher.init(Cipher.ENCRYPT_MODE, derive(databaseKey, payloadSalt),
                new GCMParameterSpec(128, payloadIv));
        return new CipherOutputStream(destination, payloadCipher);
    }

    static CipherInputStream decrypting(InputStream source, String password) throws Exception {
        DataInputStream header = new DataInputStream(source); byte[] magic = new byte[MAGIC_V5.length]; header.readFully(magic);
        if (!matches(magic, MAGIC_V5)) throw new IllegalArgumentException("Not a legacy v5 backup container");
        byte[] salt = new byte[SALT_SIZE]; byte[] iv = new byte[IV_SIZE]; header.readFully(salt); header.readFully(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), new GCMParameterSpec(128, iv));
        return new CipherInputStream(source, cipher);
    }

    static CipherInputStream decryptingV6(InputStream source, String rootKey) throws Exception {
        DataInputStream header = new DataInputStream(source);
        byte[] magic = new byte[MAGIC_V6.length];
        header.readFully(magic);
        if (!matches(magic, MAGIC_V6)) throw new IllegalArgumentException("Not a secure v6 backup container");
        byte[] wrapSalt = new byte[SALT_SIZE];
        byte[] wrapIv = new byte[IV_SIZE];
        header.readFully(wrapSalt);
        header.readFully(wrapIv);
        int wrappedLength = header.readInt();
        if (wrappedLength < 16 || wrappedLength > 1024) throw new IllegalArgumentException("Invalid wrapped key");
        byte[] wrappedDatabaseKey = new byte[wrappedLength];
        header.readFully(wrappedDatabaseKey);

        Cipher wrapCipher = Cipher.getInstance("AES/GCM/NoPadding");
        wrapCipher.init(Cipher.DECRYPT_MODE, derive(rootKey, wrapSalt), new GCMParameterSpec(128, wrapIv));
        String databaseKey = new String(wrapCipher.doFinal(wrappedDatabaseKey),
                java.nio.charset.StandardCharsets.UTF_8);

        byte[] payloadSalt = new byte[SALT_SIZE];
        byte[] payloadIv = new byte[IV_SIZE];
        header.readFully(payloadSalt);
        header.readFully(payloadIv);
        Cipher payloadCipher = Cipher.getInstance("AES/GCM/NoPadding");
        payloadCipher.init(Cipher.DECRYPT_MODE, derive(databaseKey, payloadSalt),
                new GCMParameterSpec(128, payloadIv));
        return new CipherInputStream(source, payloadCipher);
    }

    static boolean isV5Container(InputStream source) throws Exception {
        if (!source.markSupported()) return false;
        source.mark(MAGIC_V5.length); byte[] value = new byte[MAGIC_V5.length]; int read = source.read(value); source.reset();
        return read == MAGIC_V5.length && (matches(value, MAGIC_V5) || matches(value, MAGIC_V6));
    }

    static boolean isV6Container(InputStream source) throws Exception {
        if (!source.markSupported()) return false;
        source.mark(MAGIC_V6.length); byte[] value = new byte[MAGIC_V6.length]; int read = source.read(value); source.reset();
        return read == MAGIC_V6.length && matches(value, MAGIC_V6);
    }

    private static SecretKeySpec derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec((password == null ? "" : password).toCharArray(), salt, ITERATIONS, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }
    private static boolean matches(byte[] left, byte[] right) {
        if (left == null || right == null || left.length != right.length) return false;
        int difference = 0;
        for (int i = 0; i < left.length; i++) difference |= left[i] ^ right[i];
        return difference == 0;
    }
    private static byte[] random(int size) { byte[] value = new byte[size]; new SecureRandom().nextBytes(value); return value; }
}
