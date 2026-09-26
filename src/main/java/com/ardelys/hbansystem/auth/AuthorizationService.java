package com.ardelys.hbansystem.auth;

import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.config.Settings;
import com.ardelys.hbansystem.hook.LuckPermsHook;
import com.ardelys.hbansystem.model.DurationResult;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import com.ardelys.hbansystem.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class AuthorizationService {

    private final ConfigManager configManager;
    private final LuckPermsHook luckPermsHook;
    private final Logger logger;

    public AuthorizationService(
            @NotNull ConfigManager configManager,
            @NotNull LuckPermsHook luckPermsHook,
            @NotNull Logger logger
    ) {
        this.configManager = configManager;
        this.luckPermsHook = luckPermsHook;
        this.logger = logger;
    }

    
    public boolean hasPermission(@NotNull CommandSender sender, @NotNull String permission) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        try {
            if (Bukkit.getServer() != null && sender.equals(Bukkit.getConsoleSender())) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        if (sender.hasPermission("hban.admin") || sender.hasPermission("abans.admin")) {
            return true;
        }

        if (permission.isEmpty() || sender.hasPermission(permission)) {
            return true;
        }

        if (permission.startsWith("hban.")) {
            String legacyPerm = "abans." + permission.substring(5);
            return sender.hasPermission(legacyPerm);
        }

        return false;
    }

    
    public boolean checkPermission(@NotNull CommandSender sender, @NotNull String permission) {
        if (hasPermission(sender, permission)) {
            return true;
        }

        Settings settings = configManager.getSettings();
        if (settings.logPermissionDenied()) {
            logger.warning("[HBanSystem] Yetkisiz komut denemesi: Oyuncu: " + sender.getName() + ", Gerekli izin: " + permission);
        }

        configManager.getMessages().sendPrefixed(
                sender,
                "general.no-permission",
                "<red>Bu işlemi gerçekleştirmek için yetkiniz bulunmuyor."
        );
        return false;
    }

    
    @NotNull
    public AuthResult canPunish(
            @NotNull CommandSender actor,
            @NotNull UUID targetUuid,
            @Nullable String targetName,
            @NotNull PunishmentType type,
            boolean isPermanent,
            long durationMillis,
            @Nullable String reason
    ) {
        if (isConsole(actor)) {
            if (configManager.getSettings().requireReason() && (reason == null || reason.trim().isEmpty())) {
                return AuthResult.deniedMissingReason(
                        configManager.getMessages().getString("general.reason-required", "%prefix%&cCeza uygulamak için geçerli bir sebep belirtmelisiniz.")
                );
            }
            return AuthResult.success();
        }

        String requiredPerm = getPermissionForType(type, isPermanent);
        if (!hasPermission(actor, requiredPerm)) {
            Settings settings = configManager.getSettings();
            if (settings.logPermissionDenied()) {
                logger.warning("[HBanSystem] Yetkisiz ceza denemesi: " + actor.getName() + " -> " + requiredPerm);
            }
            return AuthResult.deniedPermission(
                    requiredPerm,
                    configManager.getMessages().getString("general.no-permission", "%prefix%&cBu işlemi gerçekleştirmek için yetkiniz bulunmuyor.")
            );
        }

        if (configManager.getSettings().preventSelfPunishment() && actor instanceof Player p && p.getUniqueId().equals(targetUuid)) {
            return AuthResult.deniedSelfPunishment(
                    configManager.getMessages().getString("general.cannot-punish-self", "%prefix%&cKendinizi cezalandıramazsınız!")
            );
        }

        if (isImmune(actor, targetUuid, targetName, type)) {
            return AuthResult.deniedImmunity(
                    configManager.getMessages().getString("general.player-immune", "%prefix%&cBu oyuncunun ceza dokunulmazlığı bulunmaktadır!")
            );
        }

        if (!checkHierarchy(actor, targetUuid)) {
            return AuthResult.deniedHierarchy(
                    configManager.getMessages().getString("general.hierarchy-prevented", "%prefix%&cKendisinden daha üst veya eşit rütbedeki bir yetkiliyi cezalandıramazsınız!")
            );
        }

        if (durationMillis > 0 && !validateDurationLimit(actor, type, durationMillis)) {
            return AuthResult.deniedMaxDuration(
                    configManager.getMessages().getString("general.max-duration-exceeded", "%prefix%&cBu süreyle ceza uygulama yetkiniz bulunmuyor.")
            );
        }

        if (configManager.getSettings().requireReason()) {
            if (reason == null || reason.trim().isEmpty()) {
                if (!actor.hasPermission("hban.reason.bypass") && !hasPermission(actor, "hban.admin")) {
                    return AuthResult.deniedMissingReason(
                            configManager.getMessages().getString("general.reason-required", "%prefix%&cCeza uygulamak için geçerli bir sebep belirtmelisiniz.")
                    );
                }
            }
        }

        return AuthResult.success();
    }

    
    public boolean isImmune(
            @NotNull CommandSender actor,
            @NotNull UUID targetUuid,
            @Nullable String targetName,
            @NotNull PunishmentType type
    ) {
        if (targetUuid.equals(Punishment.CONSOLE_UUID)) {
            return true;
        }

        Settings settings = configManager.getSettings();
        if (!settings.immunityEnabled()) {
            return false;
        }

        if (actor.hasPermission("hban.bypass.override") ||
                (settings.adminOverrideEnabled() && settings.adminOverrideBypassImmunity() && hasPermission(actor, "hban.admin"))) {
            return false;
        }

        if (targetName != null && settings.exemptPlayers().contains(targetName)) {
            return true;
        }
        if (settings.exemptPlayers().contains(targetUuid.toString())) {
            return true;
        }

        if (hasTargetPermission(targetUuid, "hban.bypass") ||
            hasTargetPermission(targetUuid, "hban.immune") ||
            hasTargetPermission(targetUuid, "abans.bypass") ||
            hasTargetPermission(targetUuid, "abans.immune")) {
            return true;
        }

        return switch (type) {
            case BAN, TEMPBAN, IP_BAN, TEMP_IP_BAN ->
                    hasTargetPermission(targetUuid, "hban.bypass.ban") || hasTargetPermission(targetUuid, "abans.bypass.ban");
            case MUTE, TEMP_MUTE ->
                    hasTargetPermission(targetUuid, "hban.bypass.mute") || hasTargetPermission(targetUuid, "abans.bypass.mute");
            case KICK ->
                    hasTargetPermission(targetUuid, "hban.bypass.kick") || hasTargetPermission(targetUuid, "abans.bypass.kick");
            case WARN ->
                    hasTargetPermission(targetUuid, "hban.bypass.warn") || hasTargetPermission(targetUuid, "abans.bypass.warn");
        };
    }

    
    public boolean checkHierarchy(@NotNull CommandSender actor, @NotNull UUID targetUuid) {
        if (isConsole(actor)) {
            return true;
        }

        Settings settings = configManager.getSettings();
        if (!settings.staffHierarchyEnabled()) {
            return true;
        }

        if (settings.adminOverrideEnabled() && settings.adminOverrideBypassHierarchy() && hasPermission(actor, "hban.admin")) {
            return true;
        }

        if (actor instanceof Player staffPlayer) {
            if (settings.staffHierarchyUseLuckPerms() && luckPermsHook.isAvailable()) {
                int staffWeight = luckPermsHook.getUserWeight(staffPlayer.getUniqueId());
                int targetWeight = luckPermsHook.getUserWeight(targetUuid);

                if (staffWeight <= targetWeight || (staffWeight - targetWeight) < settings.staffHierarchyMinWeightDiff()) {
                    return false;
                }
            } else {
                try {
                    if (Bukkit.getServer() != null) {
                        Player onlineTarget = Bukkit.getPlayer(targetUuid);
                        if (onlineTarget != null && hasPermission(onlineTarget, "hban.admin") && !hasPermission(staffPlayer, "hban.admin")) {
                            return false;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return true;
    }

    
    public boolean validateDurationLimit(@NotNull CommandSender actor, @NotNull PunishmentType type, long requestedMillis) {
        if (isConsole(actor) || hasPermission(actor, "hban.admin") || actor.hasPermission("hban.duration.unlimited")) {
            return true;
        }

        Settings settings = configManager.getSettings();
        if (!settings.durationLimitsEnabled() || settings.durationLimits().isEmpty()) {
            return true;
        }

        long maxAllowedMillis = -1L;
        boolean hasAnyLimitConfigured = false;

        if (actor instanceof Player p && luckPermsHook.isAvailable()) {
            String metaKey = (type == PunishmentType.TEMP_MUTE) ? "max-tempmute" : "max-tempban";
            var metaVal = luckPermsHook.getMeta(p.getUniqueId(), metaKey);
            if (metaVal.isPresent()) {
                try {
                    DurationResult parsed = DurationParser.parse(metaVal.get());
                    maxAllowedMillis = Math.max(maxAllowedMillis, parsed.millis());
                    hasAnyLimitConfigured = true;
                } catch (Exception ignored) {}
            }
        }

        for (Map.Entry<String, String> entry : settings.durationLimits().entrySet()) {
            String perm = entry.getKey();
            if (actor.hasPermission(perm)) {
                try {
                    DurationResult parsed = DurationParser.parse(entry.getValue());
                    maxAllowedMillis = Math.max(maxAllowedMillis, parsed.millis());
                    hasAnyLimitConfigured = true;
                } catch (Exception ignored) {}
            }
        }

        if (hasAnyLimitConfigured && maxAllowedMillis > 0) {
            return requestedMillis <= maxAllowedMillis;
        }

        return true;
    }

    
    @NotNull
    public String getPermissionForType(@NotNull PunishmentType type, boolean isPermanent) {
        Settings settings = configManager.getSettings();
        return switch (type) {
            case BAN -> isPermanent ?
                    settings.getPermission("ban", "hban.ban") :
                    settings.getPermission("tempban", "hban.tempban");
            case TEMPBAN -> settings.getPermission("tempban", "hban.tempban");
            case IP_BAN -> isPermanent ?
                    settings.getPermission("ipban", "hban.ipban") :
                    settings.getPermission("tempipban", "hban.tempipban");
            case TEMP_IP_BAN -> settings.getPermission("tempipban", "hban.tempipban");
            case MUTE -> isPermanent ?
                    settings.getPermission("mute", "hban.mute") :
                    settings.getPermission("tempmute", "hban.tempmute");
            case TEMP_MUTE -> settings.getPermission("tempmute", "hban.tempmute");
            case KICK -> settings.getPermission("kick", "hban.kick");
            case WARN -> settings.getPermission("warn", "hban.warn");
        };
    }

    
    private boolean hasTargetPermission(@NotNull UUID targetUuid, @NotNull String permission) {
        try {
            if (Bukkit.getServer() != null) {
                Player online = Bukkit.getPlayer(targetUuid);
                if (online != null) {
                    return online.hasPermission(permission);
                }
            }
        } catch (Throwable ignored) {
        }
        if (luckPermsHook.isAvailable()) {
            return luckPermsHook.hasPermission(targetUuid, permission);
        }
        return false;
    }

    public boolean isConsole(@NotNull CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        try {
            return Bukkit.getServer() != null && sender.equals(Bukkit.getConsoleSender());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NotNull
    public AuthResult canBan(@NotNull CommandSender actor, @NotNull UUID target, @Nullable String name, boolean perm, long dur, @Nullable String reason) {
        return canPunish(actor, target, name, perm ? PunishmentType.BAN : PunishmentType.TEMPBAN, perm, dur, reason);
    }

    @NotNull
    public AuthResult canIpBan(@NotNull CommandSender actor, @NotNull UUID target, @Nullable String name, boolean perm, long dur, @Nullable String reason) {
        return canPunish(actor, target, name, perm ? PunishmentType.IP_BAN : PunishmentType.TEMP_IP_BAN, perm, dur, reason);
    }

    @NotNull
    public AuthResult canMute(@NotNull CommandSender actor, @NotNull UUID target, @Nullable String name, boolean perm, long dur, @Nullable String reason) {
        return canPunish(actor, target, name, perm ? PunishmentType.MUTE : PunishmentType.TEMP_MUTE, perm, dur, reason);
    }

    @NotNull
    public AuthResult canKick(@NotNull CommandSender actor, @NotNull UUID target, @Nullable String name, @Nullable String reason) {
        return canPunish(actor, target, name, PunishmentType.KICK, false, -1L, reason);
    }

    @NotNull
    public AuthResult canWarn(@NotNull CommandSender actor, @NotNull UUID target, @Nullable String name, @Nullable String reason) {
        return canPunish(actor, target, name, PunishmentType.WARN, false, -1L, reason);
    }

    @NotNull
    public AuthResult canUnban(@NotNull CommandSender actor, @Nullable Punishment punishment) {
        if (isConsole(actor)) {
            return AuthResult.success();
        }
        String perm = configManager.getSettings().getPermission("unban", "hban.unban");
        if (!hasPermission(actor, perm)) {
            if (configManager.getSettings().logPermissionDenied()) {
                logger.warning("[HBanSystem] Yetkisiz yasak kaldırma denemesi: " + actor.getName() + " -> " + perm);
            }
            return AuthResult.deniedPermission(perm, configManager.getMessages().getString("general.no-permission", "%prefix%&cBu işlemi gerçekleştirmek için yetkiniz bulunmuyor."));
        }
        if (punishment != null && punishment.getStaffUuid() != null && !punishment.getStaffUuid().equals(Punishment.CONSOLE_UUID)) {
            if (!checkHierarchy(actor, punishment.getStaffUuid())) {
                return AuthResult.deniedHierarchy(configManager.getMessages().getString("general.hierarchy-prevented", "%prefix%&cKendisinden daha üst veya eşit rütbedeki bir yetkilinin cezasını kaldıramazsınız!"));
            }
        }
        return AuthResult.success();
    }

    @NotNull
    public AuthResult canUnban(@NotNull CommandSender actor) {
        return canUnban(actor, (Punishment) null);
    }

    @NotNull
    public AuthResult canUnmute(@NotNull CommandSender actor, @Nullable Punishment punishment) {
        if (isConsole(actor)) {
            return AuthResult.success();
        }
        String perm = configManager.getSettings().getPermission("unmute", "hban.unmute");
        if (!hasPermission(actor, perm)) {
            if (configManager.getSettings().logPermissionDenied()) {
                logger.warning("[HBanSystem] Yetkisiz susturma kaldırma denemesi: " + actor.getName() + " -> " + perm);
            }
            return AuthResult.deniedPermission(perm, configManager.getMessages().getString("general.no-permission", "%prefix%&cBu işlemi gerçekleştirmek için yetkiniz bulunmuyor."));
        }
        if (punishment != null && punishment.getStaffUuid() != null && !punishment.getStaffUuid().equals(Punishment.CONSOLE_UUID)) {
            if (!checkHierarchy(actor, punishment.getStaffUuid())) {
                return AuthResult.deniedHierarchy(configManager.getMessages().getString("general.hierarchy-prevented", "%prefix%&cKendisinden daha üst veya eşit rütbedeki bir yetkilinin cezasını kaldıramazsınız!"));
            }
        }
        return AuthResult.success();
    }

    @NotNull
    public AuthResult canUnmute(@NotNull CommandSender actor) {
        return canUnmute(actor, (Punishment) null);
    }

    @NotNull
    public AuthResult canUnIpBan(@NotNull CommandSender actor) {
        if (isConsole(actor)) {
            return AuthResult.success();
        }
        String perm = configManager.getSettings().getPermission("unipban", "hban.unipban");
        if (!hasPermission(actor, perm)) {
            if (configManager.getSettings().logPermissionDenied()) {
                logger.warning("[HBanSystem] Yetkisiz IP yasağı kaldırma denemesi: " + actor.getName() + " -> " + perm);
            }
            return AuthResult.deniedPermission(perm, configManager.getMessages().getString("general.no-permission", "%prefix%&cBu işlemi gerçekleştirmek için yetkiniz bulunmuyor."));
        }
        return AuthResult.success();
    }

    @NotNull
    public AuthResult canUnwarn(@NotNull CommandSender actor) {
        if (isConsole(actor)) {
            return AuthResult.success();
        }
        String perm = configManager.getSettings().getPermission("unwarn", "hban.unwarn");
        if (!hasPermission(actor, perm)) {
            if (configManager.getSettings().logPermissionDenied()) {
                logger.warning("[HBanSystem] Yetkisiz uyarı kaldırma denemesi: " + actor.getName() + " -> " + perm);
            }
            return AuthResult.deniedPermission(perm, configManager.getMessages().getString("general.no-permission", "%prefix%&cBu işlemi gerçekleştirmek için yetkiniz bulunmuyor."));
        }
        return AuthResult.success();
    }

    @NotNull
    public AuthResult canAccessSecurity(@NotNull CommandSender actor) {
        if (isConsole(actor)) {
            return AuthResult.success();
        }
        String perm = configManager.getSettings().getPermission("security", "hban.security");
        if (!hasPermission(actor, perm) && !hasPermission(actor, "hban.security.view") && !hasPermission(actor, "hban.admin")) {
            if (configManager.getSettings().logPermissionDenied()) {
                logger.warning("[HBanSystem] Yetkisiz güvenlik görüntüleme denemesi: " + actor.getName());
            }
            return AuthResult.deniedPermission(perm, configManager.getMessages().getString("general.no-permission", "%prefix%&cBu işlemi gerçekleştirmek için yetkiniz bulunmuyor."));
        }
        return AuthResult.success();
    }

    @NotNull
    public AuthResult canAccessSecurityDebug(@NotNull CommandSender actor) {
        if (isConsole(actor)) {
            return AuthResult.success();
        }
        if (!hasPermission(actor, "hban.security.debug") && !hasPermission(actor, "hban.admin")) {
            if (configManager.getSettings().logPermissionDenied()) {
                logger.warning("[HBanSystem] Yetkisiz güvenlik hata ayıklama denemesi: " + actor.getName());
            }
            return AuthResult.deniedPermission("hban.security.debug", configManager.getMessages().getString("general.no-permission", "%prefix%&cBu işlemi gerçekleştirmek için yetkiniz bulunmuyor."));
        }
        return AuthResult.success();
    }

    @NotNull
    public AuthResult canAccessDatabase(@NotNull CommandSender actor) {
        if (isConsole(actor)) {
            return AuthResult.success();
        }
        if (!hasPermission(actor, "hban.database") && !hasPermission(actor, "hban.admin")) {
            if (configManager.getSettings().logPermissionDenied()) {
                logger.warning("[HBanSystem] Yetkisiz veritabanı komut denemesi: " + actor.getName());
            }
            return AuthResult.deniedPermission("hban.database", configManager.getMessages().getString("general.no-permission", "%prefix%&cBu işlemi gerçekleştirmek için yetkiniz bulunmuyor."));
        }
        return AuthResult.success();
    }
}
