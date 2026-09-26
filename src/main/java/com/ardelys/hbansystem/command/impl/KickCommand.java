package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.model.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class KickCommand extends BaseCommand {

    private final PunishmentManager punishmentManager;

    public KickCommand(@NotNull ConfigManager configManager, @NotNull PunishmentManager punishmentManager) {
        super(configManager, "kick", "hban.kick");
        this.punishmentManager = punishmentManager;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] rawArgs) {
        ParsedArgs parsed = stripSilentFlag(rawArgs);
        String[] args = parsed.args();

        if (args.length < 1) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu> <sebep> [-s]</gray>"));
            return;
        }

        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu çevrim içi değil.");
            return;
        }

        String reason = (args.length > 1) ?
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)) :
                (configManager.getSettings().requireReason() ? "" : configManager.getSettings().defaultKickReason());

        if (authService != null) {
            var authResult = authService.canKick(sender, targetPlayer.getUniqueId(), targetPlayer.getName(), reason);
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    sender.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }
        } else {
            if (sender instanceof Player p && p.getUniqueId().equals(targetPlayer.getUniqueId())) {
                configManager.getMessages().sendPrefixed(sender, "general.cannot-punish-self", "<red>Kendinizi sunucudan atamazsınız!");
                return;
            }
            if (punishmentManager.checkImmunity(sender, targetPlayer.getUniqueId(), targetPlayer.getName())) {
                configManager.getMessages().sendPrefixed(sender, "general.player-immune", "<red>Bu oyuncunun ceza dokunulmazlığı bulunmaktadır!");
                return;
            }
        }

        final String finalReason = reason.isEmpty() ? configManager.getSettings().defaultKickReason() : reason;

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : Punishment.CONSOLE_UUID;
        String staffName = sender.getName();

        punishmentManager.kick(targetPlayer.getUniqueId(), targetPlayer.getName(), finalReason, staffUuid, staffName, parsed.silent()).thenAccept(k -> {
            if (k != null) {
                Map<String, String> placeholders = Map.of(
                        "player", targetPlayer.getName(),
                        "reason", finalReason
                );
                configManager.getMessages().sendPrefixed(sender, "punishments.kick.success", "<green>Oyuncu sunucudan atıldı.", placeholders);
            }
        });
    }
}
