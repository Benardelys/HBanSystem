package com.ardelys.hbansystem.web.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class HmacUtil {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String HASH_ALGO = "SHA-256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private HmacUtil() {}

    @NotNull
    public static String generateNonce() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    @NotNull
    public static String sha256Hex(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        }
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGO);
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }

    @NotNull
    public static String buildCanonicalRequest(
            @NotNull String method,
            @NotNull String path,
            long timestamp,
            @NotNull String nonce,
            @NotNull String bodyHash
    ) {
        return method.toUpperCase() + "\n" +
                path + "\n" +
                timestamp + "\n" +
                nonce + "\n" +
                bodyHash;
    }

    @NotNull
    public static String computeHmac(@NotNull String secret, @NotNull String canonicalData) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(canonicalData.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 hesaplanamadı", e);
        }
    }

    public static boolean verifySignature(
            @NotNull String secret,
            @NotNull String canonicalData,
            @NotNull String signatureToVerify
    ) {
        if (secret.isEmpty() || signatureToVerify.isEmpty()) {
            return false;
        }
        String calculated = computeHmac(secret, canonicalData);
        byte[] a = calculated.getBytes(StandardCharsets.UTF_8);
        byte[] b = signatureToVerify.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    @NotNull
    public static String resolveSecret(@Nullable String configuredSecret, @NotNull String envVarName) {
        String env = System.getenv(envVarName);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return configuredSecret != null ? configuredSecret.trim() : "";
    }
}
