package com.ardelys.hbansystem.manager.security;

import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.config.SecuritySettings;
import com.ardelys.hbansystem.hook.discord.DiscordWebhookService;
import com.ardelys.hbansystem.manager.log.StaffLogger;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.model.DurationResult;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.SecurityAction;
import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import com.ardelys.hbansystem.model.SecurityEvidence;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.ValidationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SecurityActionManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PunishmentManager punishmentManager;
    private final DiscordWebhookService discordWebhookService;
    private final StaffLogger staffLogger;
    private final SecurityAlertManager alertManager;

    private final Map<UUID, Long> lastPunishmentActionTime = new ConcurrentHashMap<>();
    private final Map<UUID, SecurityAction> lastExecutedAction = new ConcurrentHashMap<>();

    public SecurityActionManager(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigManager configManager,
            @NotNull PunishmentManager punishmentManager,
            @NotNull DiscordWebhookService discordWebhookService,
            @NotNull StaffLogger staffLogger,
            @NotNull SecurityAlertManager alertManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.punishmentManager = punishmentManager;
        this.discordWebhookService = discordWebhookService;
        this.staffLogger = staffLogger;
        this.alertManager = alertManager;
    }

    public void handleAction(
            @NotNull Player player,
            @NotNull SecurityEvidence evidence,
            @NotNull SecurityAction action
    ) {
        UUID uuid = player.getUniqueId();
        SecuritySettings settings = configManager.getSecuritySettings();

        if (action.isAlert() || action == SecurityAction.KICK || action == SecurityAction.TEMPBAN || action == SecurityAction.BAN) {
            alertManager.dispatchAlert(evidence);
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastPunishmentActionTime.get(uuid);
        SecurityAction lastAction = lastExecutedAction.get(uuid);

        if (lastTime != null && (now - lastTime) < 10_000L) {
            if (lastAction == action || lastAction == SecurityAction.KICK || lastAction == SecurityAction.TEMPBAN || lastAction == SecurityAction.BAN) {
                return;
            }
        }

        if (punishmentManager.getCache().isBanned(uuid)) {
            return;
        }

        lastPunishmentActionTime.put(uuid, now);
        lastExecutedAction.put(uuid, action);

        UUID consoleUuid = Punishment.CONSOLE_UUID;
        String consoleName = "HBanSystem Güvenlik";
        String ip = player.getAddress() != null ? ValidationUtil.cleanIp(player.getAddress().getAddress().getHostAddress()) : null;

        switch (action) {
            case KICK -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(Component.text(settings.kickReason()));
                        staffLogger.logAction(consoleName, "GÜVENLİK ATMA", player.getName() + " güvenlik eylemi ile atıldı: " + evidence.explanation());
                    }
                });
            }
            case TEMPBAN -> {
                DurationResult duration = DurationParser.parse(settings.tempbanDuration());
                punishmentManager.ban(
                        uuid,
                        player.getName(),
                        ip,
                        settings.tempbanReason(),
                        consoleUuid,
                        consoleName,
                        duration.millis(),
                        false
                );
            }
            case BAN -> {
                punishmentManager.ban(
                        uuid,
                        player.getName(),
                        ip,
                        settings.tempbanReason(),
                        consoleUuid,
                        consoleName,
                        -1L,
                        false
                );
            }
            case LOG, ALERT, STAFF_ALERT, NONE -> {}
        }
    }

    public void cleanup(@NotNull UUID uuid) {
        lastPunishmentActionTime.remove(uuid);
        lastExecutedAction.remove(uuid);
    }
}
