package com.ardelys.hbansystem.manager.punishment;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PlayerDataRepository;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.event.*;
import com.ardelys.hbansystem.hook.LuckPermsHook;
import com.ardelys.hbansystem.hook.discord.DiscordWebhookService;
import com.ardelys.hbansystem.manager.log.StaffLogger;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.ValidationUtil;
import com.ardelys.hbansystem.web.sync.WebSyncEvent;
import com.ardelys.hbansystem.web.sync.WebSyncService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PunishmentManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PunishmentRepository punishmentRepository;
    private final PlayerDataRepository playerDataRepository;
    private final PunishmentCache cache;
    private final LuckPermsHook luckPermsHook;
    private final DiscordWebhookService discordWebhookService;
    private final StaffLogger staffLogger;
    private WarningEscalationManager warningEscalationManager;
    private com.ardelys.hbansystem.auth.AuthorizationService authorizationService;
    private com.ardelys.hbansystem.web.WebServer webServer;
    private WebSyncService webSyncService;

    public PunishmentManager(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigManager configManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull PlayerDataRepository playerDataRepository,
            @NotNull PunishmentCache cache,
            @NotNull LuckPermsHook luckPermsHook,
            @NotNull DiscordWebhookService discordWebhookService,
            @NotNull StaffLogger staffLogger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.punishmentRepository = punishmentRepository;
        this.playerDataRepository = playerDataRepository;
        this.cache = cache;
        this.luckPermsHook = luckPermsHook;
        this.discordWebhookService = discordWebhookService;
        this.staffLogger = staffLogger;
    }

    public void setWebServer(@Nullable com.ardelys.hbansystem.web.WebServer webServer) {
        this.webServer = webServer;
    }

    public void setWebSyncService(@Nullable WebSyncService webSyncService) {
        this.webSyncService = webSyncService;
    }

    public void setAuthorizationService(@Nullable com.ardelys.hbansystem.auth.AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Nullable
    public com.ardelys.hbansystem.auth.AuthorizationService getAuthorizationService() {
        return authorizationService;
    }

    public void setWarningEscalationManager(@NotNull WarningEscalationManager warningEscalationManager) {
        this.warningEscalationManager = warningEscalationManager;
    }

    public boolean checkImmunity(@NotNull CommandSender staff, @NotNull UUID targetUuid, @NotNull String targetName) {
        if (authorizationService != null) {
            return authorizationService.isImmune(staff, targetUuid, targetName, PunishmentType.BAN) ||
                   !authorizationService.checkHierarchy(staff, targetUuid);
        }

        if (!configManager.getSettings().immunityEnabled()) {
            return false;
        }

        if (configManager.getSettings().exemptPlayers().contains(targetName) ||
                configManager.getSettings().exemptPlayers().contains(targetUuid.toString())) {
            return true;
        }

        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        String immunePerm = configManager.getSettings().getPermission("immune", "hban.immune");
        if (onlineTarget != null && (onlineTarget.hasPermission(immunePerm) || onlineTarget.hasPermission("abans.immune"))) {
            return true;
        }

        if (staff instanceof Player staffPlayer) {
            if (configManager.getSettings().checkLuckPermsHierarchy() && luckPermsHook.isAvailable()) {
                if (!luckPermsHook.canPunish(staffPlayer.getUniqueId(), targetUuid)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void triggerWebSync(@NotNull String action, @NotNull Punishment p) {
        if (webServer != null) {
            webServer.invalidateCache();
        }
        if (webSyncService != null) {
            try {
                webSyncService.enqueueEvent(WebSyncEvent.fromPunishment(action, p));
            } catch (Exception ignored) {}
        }
    }

    @NotNull
    public CompletableFuture<Punishment> ban(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @Nullable String ip,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            boolean silent,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        boolean temp = durationMillis > 0;
        PunishmentType type = temp ? PunishmentType.TEMPBAN : PunishmentType.BAN;
        long now = System.currentTimeMillis();
        long expires = temp ? (now + durationMillis) : -1L;

        Punishment punishment = Punishment.builder()
                .targetUuid(targetUuid)
                .targetName(targetName)
                .targetIp(ip)
                .type(type)
                .reason(reason)
                .staffUuid(staffUuid)
                .staffName(staffName)
                .createdAt(now)
                .expiresAt(expires)
                .serverScope(configManager.getSettings().serverScope())
                .active(true)
                .build();

        BanEvent event = new BanEvent(punishment, source, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(null);
        }

        return punishmentRepository.save(punishment).thenApply(saved -> {
            cache.addActive(saved);
            triggerWebSync("PUNISHMENT_CREATED", saved);
            staffLogger.logAction(staffName, "BAN", targetName + " yasaklandı. Sebep: " + reason + " Süre: " + (temp ? DurationParser.formatRemaining(durationMillis) : "Kalıcı") + " [Kaynak: " + source + "]");
            discordWebhookService.sendPunishmentEmbed(saved);

            broadcastPunishment(saved, silent);
            kickIfOnline(targetUuid, saved);
            return saved;
        });
    }

    @NotNull
    public CompletableFuture<Punishment> ban(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @Nullable String ip,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            boolean silent
    ) {
        var src = staffUuid.equals(Punishment.CONSOLE_UUID) ?
                com.ardelys.hbansystem.model.PunishmentSource.CONSOLE :
                com.ardelys.hbansystem.model.PunishmentSource.PLAYER;
        return ban(targetUuid, targetName, ip, reason, staffUuid, staffName, durationMillis, silent, src);
    }

    @NotNull
    public CompletableFuture<Boolean> unban(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            boolean silent,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        Optional<Punishment> activeBan = cache.getActiveBan(targetUuid);
        if (activeBan.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        Punishment punishment = activeBan.get();
        UnbanEvent event = new UnbanEvent(punishment, source, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(false);
        }

        return punishmentRepository.revoke(punishment.getId(), staffUuid, staffName, reason).thenApply(success -> {
            if (success) {
                cache.removeBan(targetUuid);
                Punishment revoked = punishment.withRevocation(staffUuid, staffName, reason);
                triggerWebSync("PUNISHMENT_REVOKED", revoked);
                staffLogger.logAction(staffName, "UNBAN", targetName + " yasağı kaldırıldı. Sebep: " + reason + " [Kaynak: " + source + "]");
                discordWebhookService.sendRevocationEmbed(revoked);
                broadcastRevocation(punishment, staffName, reason, silent);
            }
            return success;
        });
    }

    @NotNull
    public CompletableFuture<Boolean> unban(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            boolean silent
    ) {
        var src = staffUuid.equals(Punishment.CONSOLE_UUID) ?
                com.ardelys.hbansystem.model.PunishmentSource.CONSOLE :
                com.ardelys.hbansystem.model.PunishmentSource.PLAYER;
        return unban(targetUuid, targetName, staffUuid, staffName, reason, silent, src);
    }

    @NotNull
    public CompletableFuture<Punishment> mute(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            boolean silent,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        boolean temp = durationMillis > 0;
        PunishmentType type = temp ? PunishmentType.TEMP_MUTE : PunishmentType.MUTE;
        long now = System.currentTimeMillis();
        long expires = temp ? (now + durationMillis) : -1L;

        Punishment punishment = Punishment.builder()
                .targetUuid(targetUuid)
                .targetName(targetName)
                .type(type)
                .reason(reason)
                .staffUuid(staffUuid)
                .staffName(staffName)
                .createdAt(now)
                .expiresAt(expires)
                .serverScope(configManager.getSettings().serverScope())
                .active(true)
                .build();

        MuteEvent event = new MuteEvent(punishment, source, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(null);
        }

        return punishmentRepository.save(punishment).thenApply(saved -> {
            cache.addActive(saved);
            triggerWebSync("PUNISHMENT_CREATED", saved);
            staffLogger.logAction(staffName, "MUTE", targetName + " susturuldu. Sebep: " + reason + " Süre: " + (temp ? DurationParser.formatRemaining(durationMillis) : "Kalıcı") + " [Kaynak: " + source + "]");
            discordWebhookService.sendPunishmentEmbed(saved);
            broadcastPunishment(saved, silent);
            return saved;
        });
    }

    @NotNull
    public CompletableFuture<Punishment> mute(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            boolean silent
    ) {
        var src = staffUuid.equals(Punishment.CONSOLE_UUID) ?
                com.ardelys.hbansystem.model.PunishmentSource.CONSOLE :
                com.ardelys.hbansystem.model.PunishmentSource.PLAYER;
        return mute(targetUuid, targetName, reason, staffUuid, staffName, durationMillis, silent, src);
    }

    @NotNull
    public CompletableFuture<Boolean> unmute(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            boolean silent,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        Optional<Punishment> activeMute = cache.getActiveMute(targetUuid);
        if (activeMute.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        Punishment punishment = activeMute.get();
        UnmuteEvent event = new UnmuteEvent(punishment, source, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(false);
        }

        return punishmentRepository.revoke(punishment.getId(), staffUuid, staffName, reason).thenApply(success -> {
            if (success) {
                cache.removeMute(targetUuid);
                Punishment revoked = punishment.withRevocation(staffUuid, staffName, reason);
                triggerWebSync("PUNISHMENT_REVOKED", revoked);
                staffLogger.logAction(staffName, "UNMUTE", targetName + " susturması kaldırıldı. [Kaynak: " + source + "]");
                discordWebhookService.sendRevocationEmbed(revoked);
                broadcastRevocation(punishment, staffName, reason, silent);
            }
            return success;
        });
    }

    @NotNull
    public CompletableFuture<Boolean> unmute(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            boolean silent
    ) {
        var src = staffUuid.equals(Punishment.CONSOLE_UUID) ?
                com.ardelys.hbansystem.model.PunishmentSource.CONSOLE :
                com.ardelys.hbansystem.model.PunishmentSource.PLAYER;
        return unmute(targetUuid, targetName, staffUuid, staffName, reason, silent, src);
    }

    @NotNull
    public CompletableFuture<Punishment> kick(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            boolean silent,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        Punishment punishment = Punishment.builder()
                .targetUuid(targetUuid)
                .targetName(targetName)
                .type(PunishmentType.KICK)
                .reason(reason)
                .staffUuid(staffUuid)
                .staffName(staffName)
                .createdAt(System.currentTimeMillis())
                .active(false)
                .serverScope(configManager.getSettings().serverScope())
                .build();

        KickEvent event = new KickEvent(punishment, source, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(null);
        }

        return punishmentRepository.save(punishment).thenApply(saved -> {
            staffLogger.logAction(staffName, "KICK", targetName + " atıldı. Sebep: " + reason + " [Kaynak: " + source + "]");
            triggerWebSync("PUNISHMENT_CREATED", saved);
            discordWebhookService.sendPunishmentEmbed(saved);
            broadcastPunishment(saved, silent);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(targetUuid);
                if (p != null && p.isOnline()) {
                    Map<String, String> placeholders = Map.of(
                            "staff", staffName,
                            "reason", reason
                    );
                    Component kickScreen = configManager.getMessages().getComponent("screens.kick", "<gold>Sunucudan atıldınız.</gold>", placeholders, false);
                    p.kick(kickScreen);
                }
            });

            return saved;
        });
    }

    @NotNull
    public CompletableFuture<Punishment> kick(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            boolean silent
    ) {
        var src = staffUuid.equals(Punishment.CONSOLE_UUID) ?
                com.ardelys.hbansystem.model.PunishmentSource.CONSOLE :
                com.ardelys.hbansystem.model.PunishmentSource.PLAYER;
        return kick(targetUuid, targetName, reason, staffUuid, staffName, silent, src);
    }

    @NotNull
    public CompletableFuture<Punishment> warn(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            boolean silent,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        Punishment punishment = Punishment.builder()
                .targetUuid(targetUuid)
                .targetName(targetName)
                .type(PunishmentType.WARN)
                .reason(reason)
                .staffUuid(staffUuid)
                .staffName(staffName)
                .createdAt(System.currentTimeMillis())
                .active(true)
                .serverScope(configManager.getSettings().serverScope())
                .build();

        WarningEvent event = new WarningEvent(punishment, source, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(null);
        }

        return punishmentRepository.save(punishment).thenApply(saved -> {
            staffLogger.logAction(staffName, "WARN", targetName + " uyarıldı. Sebep: " + reason + " [Kaynak: " + source + "]");
            triggerWebSync("PUNISHMENT_CREATED", saved);
            discordWebhookService.sendPunishmentEmbed(saved);
            broadcastPunishment(saved, silent);

            Player p = Bukkit.getPlayer(targetUuid);
            if (p != null && p.isOnline()) {
                Map<String, String> placeholders = Map.of(
                        "staff", staffName,
                        "reason", reason,
                        "id", String.valueOf(saved.getId())
                );
                configManager.getMessages().sendPrefixed(p, "punishments.warn.notification", "<red>Uyarıldınız!", placeholders);
            }

            punishmentRepository.findByTargetUuid(targetUuid).thenAccept(history -> {
                long activeWarns = history.stream().filter(h -> h.getType() == PunishmentType.WARN && h.isActive()).count();
                if (warningEscalationManager != null) {
                    warningEscalationManager.checkAndEscalate(targetUuid, targetName, (int) activeWarns);
                }
            });

            return saved;
        });
    }

    @NotNull
    public CompletableFuture<Punishment> warn(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            boolean silent
    ) {
        var src = staffUuid.equals(Punishment.CONSOLE_UUID) ?
                com.ardelys.hbansystem.model.PunishmentSource.CONSOLE :
                com.ardelys.hbansystem.model.PunishmentSource.PLAYER;
        return warn(targetUuid, targetName, reason, staffUuid, staffName, silent, src);
    }

    @NotNull
    public CompletableFuture<Boolean> unwarn(
            long punishmentId,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    ) {
        return punishmentRepository.findById(punishmentId).thenCompose(opt -> {
            if (opt.isEmpty() || opt.get().getType() != PunishmentType.WARN || !opt.get().isActive()) {
                return CompletableFuture.completedFuture(false);
            }
            Punishment p = opt.get();
            return punishmentRepository.revoke(punishmentId, staffUuid, staffName, reason).thenApply(success -> {
                if (success) {
                    staffLogger.logAction(staffName, "UNWARN", p.getTargetName() + " uyarısı (#" + punishmentId + ") kaldırıldı.");
                    triggerWebSync("PUNISHMENT_REVOKED", p.withRevocation(staffUuid, staffName, reason));
                }
                return success;
            });
        });
    }

    @NotNull
    public CompletableFuture<Punishment> ipBan(
            @NotNull String ip,
            @NotNull String targetName,
            @NotNull UUID targetUuid,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            boolean silent
    ) {
        String cleanIp = ValidationUtil.cleanIp(ip);
        boolean temp = durationMillis > 0;
        PunishmentType type = temp ? PunishmentType.TEMP_IP_BAN : PunishmentType.IP_BAN;
        long now = System.currentTimeMillis();
        long expires = temp ? (now + durationMillis) : -1L;

        Punishment punishment = Punishment.builder()
                .targetUuid(targetUuid)
                .targetName(targetName)
                .targetIp(cleanIp)
                .type(type)
                .reason(reason)
                .staffUuid(staffUuid)
                .staffName(staffName)
                .createdAt(now)
                .expiresAt(expires)
                .serverScope(configManager.getSettings().serverScope())
                .active(true)
                .build();

        BanEvent event = new BanEvent(punishment, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(null);
        }

        return punishmentRepository.save(punishment).thenApply(saved -> {
            cache.addActive(saved);
            triggerWebSync("PUNISHMENT_CREATED", saved);
            staffLogger.logAction(staffName, "IP_BAN", cleanIp + " (" + targetName + ") IP yasaklandı. Sebep: " + reason);
            discordWebhookService.sendPunishmentEmbed(saved);
            broadcastPunishment(saved, silent);

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String pIp = p.getAddress() != null ? ValidationUtil.cleanIp(p.getAddress().getAddress().getHostAddress()) : "";
                    if (pIp.equals(cleanIp)) {
                        Map<String, String> placeholders = Map.of(
                                "staff", staffName,
                                "reason", reason,
                                "duration", temp ? DurationParser.formatRemaining(durationMillis) : "Kalıcı",
                                "id", String.valueOf(saved.getId())
                        );
                        Component ipBanScreen = configManager.getMessages().getComponent("screens.ipban", "<red>IP Adresiniz engellendi!</red>", placeholders, false);
                        p.kick(ipBanScreen);
                    }
                }
            });

            return saved;
        });
    }

    @NotNull
    public CompletableFuture<Boolean> unIpBan(
            @NotNull String ip,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            boolean silent
    ) {
        String cleanIp = ValidationUtil.cleanIp(ip);
        Optional<Punishment> activeBan = cache.getActiveIpBan(cleanIp);
        if (activeBan.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        Punishment punishment = activeBan.get();
        UnbanEvent event = new UnbanEvent(punishment, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(false);
        }

        return punishmentRepository.revoke(punishment.getId(), staffUuid, staffName, reason).thenApply(success -> {
            if (success) {
                cache.removeIpBan(cleanIp);
                Punishment revoked = punishment.withRevocation(staffUuid, staffName, reason);
                triggerWebSync("PUNISHMENT_REVOKED", revoked);
                staffLogger.logAction(staffName, "UN_IPBAN", cleanIp + " IP yasağı kaldırıldı.");
                discordWebhookService.sendRevocationEmbed(revoked);
                broadcastRevocation(punishment, staffName, reason, silent);
            }
            return success;
        });
    }

    private void kickIfOnline(@NotNull UUID targetUuid, @NotNull Punishment punishment) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(targetUuid);
            if (p != null && p.isOnline()) {
                Map<String, String> placeholders = Map.of(
                        "staff", punishment.getStaffName(),
                        "reason", punishment.getReason(),
                        "duration", punishment.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(punishment.getRemainingMillis()),
                        "id", String.valueOf(punishment.getId())
                );
                Component banScreen = configManager.getMessages().getComponent("screens.ban", "<red>Sunucudan yasaklandınız!</red>", placeholders, false);
                p.kick(banScreen);
            }
        });
    }

    private void broadcastPunishment(@NotNull Punishment p, boolean silent) {
        Map<String, String> placeholders = Map.of(
                "player", p.getTargetName(),
                "target", p.getTargetName(),
                "staff", p.getStaffName(),
                "reason", p.getReason(),
                "duration", p.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(p.getRemainingMillis()),
                "id", String.valueOf(p.getId())
        );

        String pathPrefix = switch (p.getType()) {
            case BAN -> "punishments.ban";
            case TEMPBAN -> "punishments.tempban";
            case IP_BAN -> "punishments.ipban";
            case TEMP_IP_BAN -> "punishments.tempipban";
            case MUTE -> "punishments.mute";
            case TEMP_MUTE -> "punishments.tempmute";
            case KICK -> "punishments.kick";
            case WARN -> "punishments.warn";
        };

        if (silent) {
            Component silentComp = configManager.getMessages().getComponent(pathPrefix + ".silent-broadcast", "<gray>[Sessiz] Ceza uygulandı.", placeholders, false);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("hban.admin") || player.hasPermission("abans.admin") ||
                    player.hasPermission("hban.history") || player.hasPermission("abans.history")) {
                    player.sendMessage(silentComp);
                }
            }
            Bukkit.getConsoleSender().sendMessage(silentComp);
        } else {
            Component broadcastComp = configManager.getMessages().getComponent(pathPrefix + ".broadcast", "<red>Ceza uygulandı.", placeholders, false);
            Bukkit.broadcast(broadcastComp);
        }
    }

    private void broadcastRevocation(@NotNull Punishment p, @NotNull String staffName, @NotNull String reason, boolean silent) {
        Map<String, String> placeholders = Map.of(
                "player", p.getTargetName(),
                "target", p.getTargetName(),
                "staff", staffName,
                "reason", reason,
                "id", String.valueOf(p.getId())
        );

        String key = p.getType().isBan() ? "punishments.unban.broadcast" : "punishments.unmute.broadcast";
        Component comp = configManager.getMessages().getComponent(key, "<green>Ceza kaldırıldı.", placeholders, false);

        if (silent) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("hban.admin") || player.hasPermission("abans.admin") ||
                    player.hasPermission("hban.history") || player.hasPermission("abans.history")) {
                    player.sendMessage(comp);
                }
            }
            Bukkit.getConsoleSender().sendMessage(comp);
        } else {
            Bukkit.broadcast(comp);
        }
    }

    @NotNull
    public PunishmentRepository getPunishmentRepository() {
        return punishmentRepository;
    }

    @NotNull
    public PlayerDataRepository getPlayerDataRepository() {
        return playerDataRepository;
    }

    @NotNull
    public PunishmentCache getCache() {
        return cache;
    }
}
