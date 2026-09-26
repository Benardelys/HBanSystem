package com.ardelys.hbansystem.util;

import com.ardelys.hbansystem.model.DurationResult;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(mo|s|m|h|d|w|y)");

    private static final long SECOND_MS = 1000L;
    private static final long MINUTE_MS = 60L * SECOND_MS;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;
    private static final long WEEK_MS = 7L * DAY_MS;
    private static final long MONTH_MS = 30L * DAY_MS;
    private static final long YEAR_MS = 365L * DAY_MS;

    private DurationParser() {}

    
    @NotNull
    public static DurationResult parse(@NotNull String input) {
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Süre boş olamaz.");
        }

        if (trimmed.equals("perm") || trimmed.equals("permanent") || trimmed.equals("kalici") || trimmed.equals("kalıcı")) {
            return DurationResult.PERMANENT;
        }

        Matcher matcher = DURATION_PATTERN.matcher(trimmed);
        long totalMillis = 0L;
        int lastMatchEnd = 0;
        boolean matchedAny = false;

        while (matcher.find()) {
            matchedAny = true;
            if (matcher.start() != lastMatchEnd) {
                throw new IllegalArgumentException("Geçersiz süre karakterleri tespit edildi: " + input);
            }
            lastMatchEnd = matcher.end();

            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Geçersiz sayı formatı: " + matcher.group(1));
            }

            if (amount <= 0) {
                throw new IllegalArgumentException("Süre sıfır veya negatif olamaz.");
            }

            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            long unitMultiplier = switch (unit) {
                case "s" -> SECOND_MS;
                case "m" -> MINUTE_MS;
                case "h" -> HOUR_MS;
                case "d" -> DAY_MS;
                case "w" -> WEEK_MS;
                case "mo" -> MONTH_MS;
                case "y" -> YEAR_MS;
                default -> throw new IllegalArgumentException("Bilinmeyen süre birimi: " + unit);
            };

            try {
                totalMillis = Math.addExact(totalMillis, Math.multiplyExact(amount, unitMultiplier));
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Süre değeri çok büyük.");
            }
        }

        if (!matchedAny || lastMatchEnd != trimmed.length()) {
            throw new IllegalArgumentException("Geçersiz süre formatı: " + input);
        }

        return DurationResult.of(totalMillis);
    }

    
    @NotNull
    public static String formatRemaining(long millis) {
        if (millis < 0) {
            return "Kalıcı";
        }
        if (millis == 0) {
            return "Süresi doldu";
        }

        long seconds = millis / 1000L;
        if (seconds <= 0) {
            return "1 saniyeden az";
        }

        long years = seconds / (365 * 86400);
        seconds %= (365 * 86400);

        long months = seconds / (30 * 86400);
        seconds %= (30 * 86400);

        long days = seconds / 86400;
        seconds %= 86400;

        long hours = seconds / 3600;
        seconds %= 3600;

        long minutes = seconds / 60;
        long sec = seconds % 60;

        StringBuilder sb = new StringBuilder();
        int count = 0;

        if (years > 0 && count < 2) {
            sb.append(years).append(" yıl ");
            count++;
        }
        if (months > 0 && count < 2) {
            sb.append(months).append(" ay ");
            count++;
        }
        if (days > 0 && count < 2) {
            sb.append(days).append(" gün ");
            count++;
        }
        if (hours > 0 && count < 2) {
            sb.append(hours).append(" saat ");
            count++;
        }
        if (minutes > 0 && count < 2) {
            sb.append(minutes).append(" dakika ");
            count++;
        }
        if (sec > 0 && count < 2) {
            sb.append(sec).append(" saniye ");
            count++;
        }

        return sb.toString().trim();
    }
}
