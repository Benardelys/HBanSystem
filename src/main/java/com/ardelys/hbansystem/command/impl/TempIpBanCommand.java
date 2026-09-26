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

public final class TempIpBanCommand extends BaseCommand {

    private final PunishmentManager punishmentManager;
    private final PunishmentCache cache;

    public TempIpBanCommand(@NotNull ConfigManager configManager, @NotNull PunishmentManager punishmentManager, @NotNull PunishmentCache cache) {
        super(configManager, "tempipban", "hban.tempipban");
        this.punishmentManager = punishmentManager;
        this.cache = cache;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] rawArgs) {
        ParsedArgs parsed = stripSilentFlag(rawArgs);
        String[] args = parsed.args();

        if (args.length < 3) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu|ip> <süre> <sebep> [-s]</gray>"));
            return;
        }

        String targetInput = args[0];
        String durationStr = args[1];

        DurationResult duration;
        try {
            duration = DurationParser.parse(durationStr);
        } catch (IllegalArgumentException e) {
            configManager.getMessages().sendPrefixed(sender, "general.invalid-duration", "<red>Geçersiz süre formatı!");
            return;
        }

        String ip;
        String targetName = targetInput;
        UUID targetUuid = Punishment.CONSOLE_UUID;

        if (ValidationUtil.isValidIp(targetInput)) {
            ip = ValidationUtil.cleanIp(targetInput);
        } else {
            Player onlineTarget = Bukkit.getPlayer(targetInput);
            if (onlineTarget != null && onlineTarget.getAddress() != null) {
                ip = ValidationUtil.cleanIp(onlineTarget.getAddress().getAddress().getHostAddress());
                targetName = onlineTarget.getName();
                targetUuid = onlineTarget.getUniqueId();
            } else {
                targetUuid = cache.getUuidByName(targetInput);
                ip = (targetUuid != null) ? cache.getLastIp(targetUuid) : null;
            }
        }

        if (ip == null || !ValidationUtil.isValidIp(ip)) {
            configManager.getMessages().sendPrefixed(sender, "general.invalid-ip", "<red>Geçersiz bir IP adresi veya oyuncu IP'si bulunamadı.");
            return;
        }

        String reason = (args.length > 2) ?
                String.join(" ", Arrays.copyOfRange(args, 2, args.length)) :
                (configManager.getSettings().requireReason() ? "" : configManager.getSettings().defaultBanReason());

        if (authService != null) {
            var authResult = authService.canIpBan(sender, targetUuid, targetName, false, duration.millis(), reason);
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    sender.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }
        }

        if (reason.isEmpty()) {
            reason = configManager.getSettings().defaultBanReason();
        }

        if (cache.isIpBanned(ip)) {
            configManager.getMessages().sendPrefixed(sender, "punishments.ipban.already-banned", "<red>Bu IP adresi zaten yasaklı.");
            return;
        }
        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : Punishment.CONSOLE_UUID;
        String staffName = sender.getName();
        final String finalTargetName = targetName;
        final String finalIp = ip;
        final String finalReason = reason;

        punishmentManager.ipBan(ip, targetName, targetUuid, reason, staffUuid, staffName, duration.millis(), parsed.silent()).thenAccept(p -> {
            if (p != null) {
                Map<String, String> placeholders = Map.of(
                        "target", finalTargetName,
                        "ip", finalIp,
                        "reason", finalReason,
                        "duration", DurationParser.formatRemaining(duration.millis())
                );
                configManager.getMessages().sendPrefixed(sender, "punishments.tempipban.success", "<green>Süreli IP yasağı uygulandı.", placeholders);
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
