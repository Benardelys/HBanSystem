package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class WarnCommand extends BaseCommand {

    private final PunishmentManager punishmentManager;
    private final PunishmentCache cache;

    public WarnCommand(@NotNull ConfigManager configManager, @NotNull PunishmentManager punishmentManager, @NotNull PunishmentCache cache) {
        super(configManager, "warn", "hban.warn");
        this.punishmentManager = punishmentManager;
        this.cache = cache;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] rawArgs) {
        ParsedArgs parsed = stripSilentFlag(rawArgs);
        String[] args = parsed.args();

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu> <sebep> [-s]</gray>"));
            return;
        }

        String targetInput = args[0];
        UUID targetUuid = null;
        String targetName = targetInput;

        Player onlineTarget = Bukkit.getPlayer(targetInput);
        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
        } else {
            UUID parsedUuid = ValidationUtil.parseUuidSafely(targetInput);
            if (parsedUuid != null) {
                targetUuid = parsedUuid;
                String cachedName = cache.getNameByUuid(targetUuid);
                if (cachedName != null) targetName = cachedName;
            } else {
                targetUuid = cache.getUuidByName(targetInput);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String reason = sb.toString().trim();

        if (targetUuid == null) {
            final boolean silent = parsed.silent();
            final String finalReason = reason;

            punishmentManager.getPlayerDataRepository().findByName(targetInput).thenAccept(optData -> {
                if (optData.isEmpty()) {
                    configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
                    return;
                }
                var data = optData.get();
                processWarn(sender, data.uuid(), data.lastKnownName(), finalReason, silent);
            });
            return;
        }

        processWarn(sender, targetUuid, targetName, reason, parsed.silent());
    }

    private void processWarn(CommandSender sender, UUID targetUuid, String targetName, String reason, boolean silent) {
        if (authService != null) {
            var authResult = authService.canWarn(sender, targetUuid, targetName, reason);
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    sender.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }
        } else {
            if (sender instanceof Player p && p.getUniqueId().equals(targetUuid)) {
                configManager.getMessages().sendPrefixed(sender, "general.cannot-punish-self", "<red>Kendinizi uyaramazsınız!");
                return;
            }
            if (punishmentManager.checkImmunity(sender, targetUuid, targetName)) {
                configManager.getMessages().sendPrefixed(sender, "general.player-immune", "<red>Bu oyuncunun ceza dokunulmazlığı bulunmaktadır!");
                return;
            }
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : Punishment.CONSOLE_UUID;
        String staffName = sender.getName();

        punishmentManager.warn(targetUuid, targetName, reason, staffUuid, staffName, silent).thenAccept(w -> {
            if (w != null) {
                Map<String, String> placeholders = Map.of(
                        "player", targetName,
                        "reason", reason,
                        "id", String.valueOf(w.getId())
                );
                configManager.getMessages().sendPrefixed(sender, "punishments.warn.success", "<green>Oyuncu uyarıldı.", placeholders);
            }
        });
    }
}
