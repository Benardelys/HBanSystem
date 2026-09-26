package com.ardelys.hbansystem.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.regex.Pattern;

public final class ValidationUtil {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$"
    );

    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{2,16}$");

    private ValidationUtil() {}

    public static boolean isValidIp(@Nullable String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String clean = cleanIp(ip);
        if (IPV4_PATTERN.matcher(clean).matches()) {
            return true;
        }
        if (IPV6_PATTERN.matcher(clean).matches()) {
            return true;
        }
        if (clean.contains(":") && !clean.contains(".")) {
            String[] parts = clean.split(":", -1);
            return parts.length >= 3 && parts.length <= 8;
        }
        return false;
    }

    @NotNull
    public static String cleanIp(@Nullable String ip) {
        if (ip == null || ip.isBlank()) {
            return "";
        }
        String clean = ip.trim();
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.startsWith("[") && clean.contains("]")) {
            int endBracket = clean.indexOf(']');
            clean = clean.substring(1, endBracket);
        } else if (clean.contains(".") && clean.contains(":")) {
            int portIdx = clean.lastIndexOf(':');
            clean = clean.substring(0, portIdx);
        }
        return clean;
    }

    public static boolean isValidPlayerName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return PLAYER_NAME_PATTERN.matcher(name).matches();
    }

    @Nullable
    public static UUID parseUuidSafely(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            if (input.length() == 32) {
                return UUID.fromString(input.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"
                ));
            }
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
