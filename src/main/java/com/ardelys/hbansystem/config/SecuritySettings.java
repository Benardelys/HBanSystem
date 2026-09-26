package com.ardelys.hbansystem.config;

import com.ardelys.hbansystem.model.SecurityAction;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record SecuritySettings(
        boolean enabled,
        @NotNull String securityPrefix,
        int evidenceRetentionDays,
        int lowThreshold,
        int mediumThreshold,
        int highThreshold,
        int veryHighThreshold,
        int criticalThreshold,
        @NotNull SecurityAction lowAction,
        @NotNull SecurityAction mediumAction,
        @NotNull SecurityAction highAction,
        @NotNull SecurityAction veryHighAction,
        @NotNull SecurityAction criticalAction,
        @NotNull String tempbanDuration,
        @NotNull String tempbanReason,
        @NotNull String kickReason,
        @NotNull String alertFormat,
        int alertCooldownSeconds,
        boolean duplicateSuppression,
        @NotNull Set<String> whitelistBrands
) {
    public SecuritySettings(
            boolean enabled,
            int evidenceRetentionDays,
            int lowThreshold,
            int mediumThreshold,
            int highThreshold,
            int veryHighThreshold,
            int criticalThreshold,
            @NotNull SecurityAction lowAction,
            @NotNull SecurityAction mediumAction,
            @NotNull SecurityAction highAction,
            @NotNull SecurityAction veryHighAction,
            @NotNull SecurityAction criticalAction,
            @NotNull String tempbanDuration,
            @NotNull String tempbanReason,
            @NotNull String kickReason,
            @NotNull String alertFormat,
            int alertCooldownSeconds,
            boolean duplicateSuppression,
            @NotNull Set<String> whitelistBrands
    ) {
        this(
                enabled, "&8[&cHBanSystem Security&8] ", evidenceRetentionDays,
                lowThreshold, mediumThreshold, highThreshold, veryHighThreshold, criticalThreshold,
                lowAction, mediumAction, highAction, veryHighAction, criticalAction,
                tempbanDuration, tempbanReason, kickReason, alertFormat,
                alertCooldownSeconds, duplicateSuppression, whitelistBrands
        );
    }

    public SecuritySettings(
            boolean enabled,
            int lowThreshold,
            int mediumThreshold,
            int highThreshold,
            int criticalThreshold,
            @NotNull SecurityAction lowAction,
            @NotNull SecurityAction mediumAction,
            @NotNull SecurityAction highAction,
            @NotNull SecurityAction criticalAction,
            @NotNull String tempbanDuration,
            @NotNull String tempbanReason,
            @NotNull String kickReason,
            @NotNull String alertFormat,
            int alertCooldownSeconds,
            boolean duplicateSuppression,
            @NotNull Set<String> whitelistBrands
    ) {
        this(
                enabled, 30, lowThreshold, mediumThreshold, highThreshold, 80, criticalThreshold,
                lowAction, mediumAction, highAction, SecurityAction.KICK, criticalAction,
                tempbanDuration, tempbanReason, kickReason, alertFormat,
                alertCooldownSeconds, duplicateSuppression, whitelistBrands
        );
    }

    public static SecuritySettings fromConfig(@NotNull FileConfiguration config) {
        boolean enabled = config.getBoolean("enabled", true);
        String securityPrefix = config.getString("security-prefix", "&8[&cHBanSystem Security&8] ");
        int evidenceRetentionDays = config.getInt("evidence-retention-days", 30);
        int lowThreshold = config.getInt("thresholds.low", 20);
        int mediumThreshold = config.getInt("thresholds.medium", 40);
        int highThreshold = config.getInt("thresholds.high", 60);
        int veryHighThreshold = config.getInt("thresholds.very-high", 80);
        int criticalThreshold = config.getInt("thresholds.critical", 95);

        SecurityAction lowAction = parseAction(config.getString("actions.low", "LOG"));
        SecurityAction mediumAction = parseAction(config.getString("actions.medium", "STAFF_ALERT"));
        SecurityAction highAction = parseAction(config.getString("actions.high", "STAFF_ALERT"));
        SecurityAction veryHighAction = parseAction(config.getString("actions.very-high", "KICK"));
        SecurityAction criticalAction = parseAction(config.getString("actions.critical", "TEMPBAN"));

        String tempbanDuration = config.getString("tempban-duration", "7d");
        String tempbanReason = config.getString("tempban-reason", "Şüpheli Enjeksiyon / Hile Protokolü Tespiti");
        String kickReason = config.getString("kick-reason", "Protokol tutarsızlığı tespit edildi.");

        String alertFormat = config.getString("alerts.format", "");
        int cooldown = config.getInt("alerts.cooldown-seconds", 15);
        boolean duplicateSuppression = config.getBoolean("alerts.duplicate-suppression", true);

        List<String> rawBrands = config.getStringList("whitelist-brands");
        Set<String> brands = new HashSet<>();
        for (String b : rawBrands) {
            brands.add(b.toLowerCase(Locale.ROOT));
        }

        return new SecuritySettings(
                enabled, securityPrefix, evidenceRetentionDays,
                lowThreshold, mediumThreshold, highThreshold, veryHighThreshold, criticalThreshold,
                lowAction, mediumAction, highAction, veryHighAction, criticalAction,
                tempbanDuration, tempbanReason, kickReason, alertFormat,
                cooldown, duplicateSuppression, brands
        );
    }

    private static SecurityAction parseAction(String name) {
        if (name == null) return SecurityAction.LOG;
        try {
            return SecurityAction.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SecurityAction.LOG;
        }
    }

    public boolean isBrandWhitelisted(String brand) {
        if (brand == null || brand.isBlank()) {
            return false;
        }
        String clean = brand.toLowerCase(Locale.ROOT).trim();
        for (String white : whitelistBrands) {
            if (clean.contains(white)) {
                return true;
            }
        }
        return false;
    }
}
