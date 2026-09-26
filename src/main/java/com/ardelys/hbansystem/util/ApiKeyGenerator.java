package com.ardelys.hbansystem.util;

import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;

public final class ApiKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "hb_sec_";

    public static @NotNull String generateKey() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(PREFIX);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
