package com.ardelys.hbansystem.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record Settings(
        @NotNull String globalPrefix,
        @NotNull String brandingName,
        @NotNull String brandingPrefix,
        @NotNull String brandingAuthor,
        @NotNull String serverScope,
        @NotNull String silentFlag,
        @NotNull String defaultBanReason,
        @NotNull String defaultMuteReason,
        @NotNull String defaultKickReason,
        @NotNull String defaultWarnReason,
        @NotNull String defaultUnbanReason,
        @NotNull String defaultUnmuteReason,
        @NotNull Set<String> blockedMuteCommands,
        boolean immunityEnabled,
        boolean checkLuckPermsHierarchy,
        @NotNull Set<String> exemptPlayers,
        boolean staffHierarchyEnabled,
        boolean staffHierarchyUseLuckPerms,
        int staffHierarchyMinWeightDiff,
        boolean preventSelfPunishment,
        boolean adminOverrideEnabled,
        boolean adminOverrideBypassImmunity,
        boolean adminOverrideBypassHierarchy,
        boolean durationLimitsEnabled,
        @NotNull Map<String, String> durationLimits,
        boolean requireReason,
        boolean logPermissionDenied,
        boolean warningLadderEnabled,
        @NotNull Map<Integer, WarningLadderRule> warningLadderRules,
        boolean allowLoginOnDbError,
        boolean notifyStaffOnError,
        boolean guiEnabled,
        int guiRows,
        @NotNull Map<String, String> permissionsMap
) {
    public record WarningLadderRule(
            @NotNull String action,
            @NotNull String duration,
            @NotNull String reason
    ) {}

    public static Settings fromConfigs(
            @NotNull FileConfiguration config,
            @NotNull FileConfiguration permsConfig
    ) {
        String globalPrefix = config.getString("prefix", config.getString("abans-prefix", "&8[&6HBanSystem&8] "));
        String brandingName = config.getString("branding.name", "HBanSystem");
        String brandingPrefix = config.getString("branding.prefix", globalPrefix);
        String brandingAuthor = config.getString("branding.author", "Ardelys");

        String serverScope = config.getString("server.scope", "global");
        String silentFlag = config.getString("punishments.silent-flag", "-s");

        String defaultBanReason = config.getString("punishments.defaults.ban-reason", "Kurallara aykırı davranış");
        String defaultMuteReason = config.getString("punishments.defaults.mute-reason", "Sohbet kurallarına uymama");
        String defaultKickReason = config.getString("punishments.defaults.kick-reason", "Sunucudan uzaklaştırıldınız");
        String defaultWarnReason = config.getString("punishments.defaults.warn-reason", "Uyarı kuralları ihlali");
        String defaultUnbanReason = config.getString("punishments.defaults.unban-reason", "Yetkili tarafından kaldırıldı");
        String defaultUnmuteReason = config.getString("punishments.defaults.unmute-reason", "Yetkili tarafından kaldırıldı");

        List<String> rawBlocked = config.getStringList("punishments.blocked-commands-while-muted");
        Set<String> blockedMuteCommands = new HashSet<>();
        for (String c : rawBlocked) {
            blockedMuteCommands.add(c.toLowerCase(Locale.ROOT));
        }

        boolean immunityEnabled = config.getBoolean("immunity.enabled", true);
        boolean checkLuckPermsHierarchy = config.getBoolean("immunity.check-luckperms-hierarchy", true);
        List<String> rawExempt = config.getStringList("immunity.exempt-players");
        Set<String> exemptPlayers = new HashSet<>(rawExempt);

        boolean staffHierarchyEnabled = config.getBoolean("staff-hierarchy.enabled", true);
        boolean staffHierarchyUseLuckPerms = config.getBoolean("staff-hierarchy.use-luckperms-weight", true);
        int staffHierarchyMinWeightDiff = config.getInt("staff-hierarchy.minimum-weight-difference", 1);

        boolean preventSelfPunishment = config.getBoolean("protection.prevent-self-punishment", true);

        boolean adminOverrideEnabled = config.getBoolean("authorization.admin-override.enabled", true);
        boolean adminOverrideBypassImmunity = config.getBoolean("authorization.admin-override.bypass-immunity", true);
        boolean adminOverrideBypassHierarchy = config.getBoolean("authorization.admin-override.bypass-hierarchy", true);

        boolean durationLimitsEnabled = config.getBoolean("duration-limits.enabled", true);
        Map<String, String> durationLimits = new HashMap<>();
        ConfigurationSection durSec = config.getConfigurationSection("duration-limits.limits");
        if (durSec != null) {
            for (String key : durSec.getKeys(false)) {
                durationLimits.put(key, durSec.getString(key));
            }
        }

        boolean requireReason = config.getBoolean("punishments.require-reason", true);
        boolean logPermissionDenied = config.getBoolean("logging.permission-denied", true);

        boolean warningLadderEnabled = config.getBoolean("warning-ladder.enabled", false);
        Map<Integer, WarningLadderRule> ladderRules = new HashMap<>();
        ConfigurationSection thresholdsSec = config.getConfigurationSection("warning-ladder.thresholds");
        if (thresholdsSec != null) {
            for (String key : thresholdsSec.getKeys(false)) {
                try {
                    int count = Integer.parseInt(key);
                    String action = thresholdsSec.getString(key + ".action", "tempmute");
                    String duration = thresholdsSec.getString(key + ".duration", "1h");
                    String reason = thresholdsSec.getString(key + ".reason", "Uyarı Limiti Aşıldı");
                    ladderRules.put(count, new WarningLadderRule(action, duration, reason));
                } catch (NumberFormatException ignored) {}
            }
        }

        boolean allowLoginOnDbError = config.getBoolean("fail-safe.allow-login-on-db-error", true);
        boolean notifyStaffOnError = config.getBoolean("fail-safe.notify-staff-on-error", true);

        boolean guiEnabled = config.getBoolean("gui.enabled", true);
        int guiRows = config.getInt("gui.rows", 6);

        Map<String, String> permissionsMap = new HashMap<>();
        ConfigurationSection permsSec = permsConfig.getConfigurationSection("permissions");
        if (permsSec != null) {
            for (String k : permsSec.getKeys(false)) {
                permissionsMap.put(k, permsSec.getString(k));
            }
        }

        return new Settings(
                globalPrefix, brandingName, brandingPrefix, brandingAuthor,
                serverScope, silentFlag, defaultBanReason, defaultMuteReason, defaultKickReason,
                defaultWarnReason, defaultUnbanReason, defaultUnmuteReason,
                blockedMuteCommands, immunityEnabled, checkLuckPermsHierarchy,
                exemptPlayers, staffHierarchyEnabled, staffHierarchyUseLuckPerms,
                staffHierarchyMinWeightDiff, preventSelfPunishment, adminOverrideEnabled,
                adminOverrideBypassImmunity, adminOverrideBypassHierarchy,
                durationLimitsEnabled, durationLimits, requireReason, logPermissionDenied,
                warningLadderEnabled, ladderRules, allowLoginOnDbError, notifyStaffOnError,
                guiEnabled, guiRows, permissionsMap
        );
    }

    public String getPermission(String key, String fallback) {
        return permissionsMap.getOrDefault(key, fallback);
    }

    public boolean isCommandBlockedWhileMuted(String command) {
        if (command == null || command.isEmpty()) return false;
        String cmd = command.toLowerCase(Locale.ROOT);
        if (!cmd.startsWith("/")) cmd = "/" + cmd;
        String base = cmd.split(" ")[0];
        return blockedMuteCommands.contains(base);
    }
}
