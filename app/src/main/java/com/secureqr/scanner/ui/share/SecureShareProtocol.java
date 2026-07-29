package com.secureqr.scanner.ui.share;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Temporary, offline ECDH exchange. Receiver private keys never enter a QR code. */
public final class SecureShareProtocol {
    public static final String DIRECT_PREFIX = "keyscan://secure-share/direct?data=";
    static final String REQUEST_PREFIX = "keyscan://secure-share/request?data=";
    static final String RESPONSE_PREFIX = "keyscan://secure-share/response?data=";
    private static final byte[] INFO = "KeyScan-SecureShare-v1".getBytes(StandardCharsets.UTF_8);
    private static final Map<String, ReceiverSession> SESSIONS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureShareProtocol() { }

    static String createReceiverRequest(long lifetimeMs) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        String sessionId = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + lifetimeMs;
        SESSIONS.put(sessionId, new ReceiverSession(pair.getPrivate(), expiresAt));
        JSONObject json = new JSONObject()
                .put("v", 1)
                .put("sid", sessionId)
                .put("exp", expiresAt)
                .put("pub", encode(pair.getPublic().getEncoded()));
        return REQUEST_PREFIX + encode(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String encryptResponse(String requestQr, JSONObject passwordPayload) throws Exception {
        JSONObject request = decodeEnvelope(requestQr, REQUEST_PREFIX);
        requireVersionAndExpiry(request);
        String sessionId = request.getString("sid");
        PublicKey receiverPublic = KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(decode(request.getString("pub"))));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair senderPair = generator.generateKeyPair();
        byte[] secret = agree(senderPair.getPrivate(), receiverPublic);
        byte[] key = derive(secret, sessionId);
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        long expiresAt = request.getLong("exp");
        JSONObject header = new JSONObject()
                .put("v", 1).put("sid", sessionId).put("exp", expiresAt)
                .put("shareId", UUID.randomUUID().toString())
                .put("pub", encode(senderPair.getPublic().getEncoded()))
                .put("nonce", encode(nonce));
        byte[] aad = aad(header);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        header.put("cipher", encode(cipher.doFinal(passwordPayload.toString().getBytes(StandardCharsets.UTF_8))));
        return RESPONSE_PREFIX + encode(header.toString().getBytes(StandardCharsets.UTF_8));
    }

    static JSONObject decryptResponse(String responseQr) throws Exception {
        JSONObject response = decodeEnvelope(responseQr, RESPONSE_PREFIX);
        requireVersionAndExpiry(response);
        String sessionId = response.getString("sid");
        ReceiverSession session = SESSIONS.remove(sessionId);
        if (session == null || session.consumed || System.currentTimeMillis() > session.expiresAt) {
            throw new IllegalStateException("session");
        }
        session.consumed = true;
        PublicKey senderPublic = KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(decode(response.getString("pub"))));
        byte[] key = derive(agree(session.privateKey, senderPublic), sessionId);
        byte[] nonce = decode(response.getString("nonce"));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad(response));
        byte[] plain = cipher.doFinal(decode(response.getString("cipher")));
        return new JSONObject(new String(plain, StandardCharsets.UTF_8));
    }

    static boolean isRequest(String value) { return value != null && value.startsWith(REQUEST_PREFIX); }
    static boolean isResponse(String value) { return value != null && value.startsWith(RESPONSE_PREFIX); }
    public static boolean isDirect(String value) { return value != null && value.startsWith(DIRECT_PREFIX); }

    /**
     * Creates a bearer QR: possession during its validity is the authorization.
     * The AES key is carried in the envelope, so this is encrypted transport encoding,
     * not receiver-bound encryption.
     */
    public static String createDirect(JSONObject payload, long lifetimeMs) throws Exception {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(key);
        RANDOM.nextBytes(nonce);
        long expiresAt = System.currentTimeMillis() + lifetimeMs;
        String shareId = UUID.randomUUID().toString();
        String aadValue = "1|" + shareId + "|" + expiresAt;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aadValue.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject()
                .put("v", 1).put("id", shareId).put("exp", expiresAt)
                .put("k", encode(key)).put("n", encode(nonce))
                .put("c", encode(cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8))));
        return DIRECT_PREFIX + encode(envelope.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static JSONObject decryptDirect(String qr) throws Exception {
        JSONObject envelope = decodeEnvelope(qr, DIRECT_PREFIX);
        requireVersionAndExpiry(envelope);
        String aadValue = "1|" + envelope.getString("id") + "|" + envelope.getLong("exp");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decode(envelope.getString("k")), "AES"),
                new GCMParameterSpec(128, decode(envelope.getString("n"))));
        cipher.updateAAD(aadValue.getBytes(StandardCharsets.UTF_8));
        JSONObject payload = new JSONObject(new String(
                cipher.doFinal(decode(envelope.getString("c"))), StandardCharsets.UTF_8));
        payload.put("_shareId", envelope.getString("id"));
        return payload;
    }

    private static JSONObject decodeEnvelope(String value, String prefix) throws Exception {
        if (value == null || !value.startsWith(prefix)) throw new IllegalArgumentException("type");
        return new JSONObject(new String(decode(value.substring(prefix.length())), StandardCharsets.UTF_8));
    }

    private static void requireVersionAndExpiry(JSONObject json) {
        if (json.optInt("v", 0) != 1) throw new IllegalArgumentException("version");
        if (System.currentTimeMillis() > json.optLong("exp", 0)) throw new IllegalStateException("expired");
    }

    private static byte[] agree(PrivateKey ownPrivate, PublicKey peerPublic) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ownPrivate);
        agreement.doPhase(peerPublic, true);
        return agreement.generateSecret();
    }

    private static byte[] derive(byte[] secret, String sessionId) throws Exception {
        byte[] salt = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(secret);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(INFO);
        mac.update((byte) 1);
        byte[] output = mac.doFinal();
        byte[] key = new byte[32];
        System.arraycopy(output, 0, key, 0, key.length);
        return key;
    }

    private static byte[] aad(JSONObject header) {
        String value = header.optInt("v") + "|" + header.optString("sid") + "|"
                + header.optLong("exp") + "|" + header.optString("shareId") + "|"
                + header.optString("pub") + "|" + header.optString("nonce");
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] decode(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static final class ReceiverSession {
        final PrivateKey privateKey;
        final long expiresAt;
        volatile boolean consumed;
        ReceiverSession(PrivateKey privateKey, long expiresAt) {
            this.privateKey = privateKey;
            this.expiresAt = expiresAt;
        }
    }
}
