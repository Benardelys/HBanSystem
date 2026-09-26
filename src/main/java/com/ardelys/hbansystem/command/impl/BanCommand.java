package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.model.DurationResult;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class BanCommand extends BaseCommand {

    private final PunishmentManager punishmentManager;
    private final PunishmentCache cache;

    public BanCommand(@NotNull ConfigManager configManager, @NotNull PunishmentManager punishmentManager, @NotNull PunishmentCache cache) {
        super(configManager, "ban", "hban.ban");
        this.punishmentManager = punishmentManager;
        this.cache = cache;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] rawArgs) {
        ParsedArgs parsed = stripSilentFlag(rawArgs);
        String[] args = parsed.args();

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu> [süre] <sebep> [-s]</gray>"));
            return;
        }

        String targetInput = args[0];
        UUID targetUuid = null;
        String targetName = targetInput;
        String ip = null;

        Player onlineTarget = Bukkit.getPlayer(targetInput);
        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
            if (onlineTarget.getAddress() != null) {
                ip = ValidationUtil.cleanIp(onlineTarget.getAddress().getAddress().getHostAddress());
            }
        } else {
            UUID parsedUuid = ValidationUtil.parseUuidSafely(targetInput);
            if (parsedUuid != null) {
                targetUuid = parsedUuid;
                String cachedName = cache.getNameByUuid(targetUuid);
                if (cachedName != null) targetName = cachedName;
            } else {
                targetUuid = cache.getUuidByName(targetInput);
            }
            if (targetUuid != null) {
                ip = cache.getLastIp(targetUuid);
            }
        }

        if (targetUuid == null) {
            final boolean silent = parsed.silent();
            final String[] finalArgs = args;
            final String finalTargetInput = targetInput;

            punishmentManager.getPlayerDataRepository().findByName(targetInput).thenAccept(optData -> {
                if (optData.isEmpty()) {
                    configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
                    return;
                }
                var data = optData.get();
                processBan(sender, data.uuid(), data.lastKnownName(), data.lastIp(), finalArgs, silent);
            });
            return;
        }

        processBan(sender, targetUuid, targetName, ip, args, parsed.silent());
    }

    private void processBan(CommandSender sender, UUID targetUuid, String targetName, String ip, String[] args, boolean silent) {
        DurationResult duration;
        int reasonStartIndex = 1;
        try {
            duration = DurationParser.parse(args[1]);
            reasonStartIndex = 2;
        } catch (IllegalArgumentException e) {
            duration = DurationResult.PERMANENT;
        }

        String reason;
        if (args.length > reasonStartIndex) {
            StringBuilder sb = new StringBuilder();
            for (int i = reasonStartIndex; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            reason = sb.toString().trim();
        } else {
            reason = configManager.getSettings().requireReason() ? "" : configManager.getSettings().defaultBanReason();
        }

        if (authService != null) {
            var authResult = authService.canBan(sender, targetUuid, targetName, duration.isPermanent(), duration.millis(), reason);
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    sender.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }
        } else {
            if (sender instanceof Player p && p.getUniqueId().equals(targetUuid)) {
                configManager.getMessages().sendPrefixed(sender, "general.cannot-punish-self", "<red>Kendinizi cezalandıramazsınız!");
                return;
            }
            if (punishmentManager.checkImmunity(sender, targetUuid, targetName)) {
                configManager.getMessages().sendPrefixed(sender, "general.player-immune", "<red>Bu oyuncunun ceza dokunulmazlığı bulunmaktadır!");
                return;
            }
        }

        final String finalReason = reason.isEmpty() ? configManager.getSettings().defaultBanReason() : reason;

        if (cache.isBanned(targetUuid)) {
            configManager.getMessages().sendPrefixed(sender, "punishments.ban.already-banned", "<red>Bu oyuncunun zaten aktif bir yasağı bulunuyor.");
            return;
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : Punishment.CONSOLE_UUID;
        String staffName = sender.getName();

        punishmentManager.ban(targetUuid, targetName, ip, finalReason, staffUuid, staffName, duration.millis(), silent).thenAccept(p -> {
            if (p != null) {
                Map<String, String> placeholders = Map.of(
                        "player", targetName,
                        "reason", finalReason,
                        "duration", p.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(p.getRemainingMillis())
                );
                configManager.getMessages().sendPrefixed(sender, "punishments.ban.success", "<green>Oyuncu yasaklandı.", placeholders);
            }
        });
    }

    @Override
    protected List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return super.tabComplete(sender, alias, args);
        } else if (args.length == 2) {
            return completeDurations(args[1]);
        }
        return Collections.emptyList();
    }
}
