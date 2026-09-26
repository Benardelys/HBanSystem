package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class UnIpBanCommand extends BaseCommand {

    private final PunishmentManager punishmentManager;
    private final PunishmentCache cache;

    public UnIpBanCommand(@NotNull ConfigManager configManager, @NotNull PunishmentManager punishmentManager, @NotNull PunishmentCache cache) {
        super(configManager, "unipban", "hban.unipban");
        this.punishmentManager = punishmentManager;
        this.cache = cache;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] rawArgs) {
        ParsedArgs parsed = stripSilentFlag(rawArgs);
        String[] args = parsed.args();

        if (args.length < 1) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu|ip> [sebep] [-s]</gray>"));
            return;
        }

        String targetInput = args[0];
        String ip;

        if (ValidationUtil.isValidIp(targetInput)) {
            ip = ValidationUtil.cleanIp(targetInput);
        } else {
            UUID uuid = cache.getUuidByName(targetInput);
            ip = (uuid != null) ? cache.getLastIp(uuid) : null;
        }

        if (ip == null || !ValidationUtil.isValidIp(ip)) {
            configManager.getMessages().sendPrefixed(sender, "general.invalid-ip", "<red>Geçersiz bir IP adresi veya oyuncu IP'si bulunamadı.");
            return;
        }

        if (!cache.isIpBanned(ip)) {
            configManager.getMessages().sendPrefixed(sender, "punishments.unipban.not-banned", "<red>Bu IP adresinin aktif bir yasağı bulunmuyor.");
            return;
        }

        if (authService != null) {
            var authResult = authService.canUnIpBan(sender);
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    sender.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }
        }

        String reason = (args.length > 1) ?
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)) :
                configManager.getSettings().defaultUnbanReason();

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : Punishment.CONSOLE_UUID;
        String staffName = sender.getName();

        punishmentManager.unIpBan(ip, staffUuid, staffName, reason, parsed.silent()).thenAccept(success -> {
            if (success) {
                Map<String, String> placeholders = Map.of(
                        "target", targetInput,
                        "ip", ip
                );
                configManager.getMessages().sendPrefixed(sender, "punishments.unipban.success", "<green>IP yasağı kaldırıldı.", placeholders);
            } else {
                configManager.getMessages().sendPrefixed(sender, "punishments.unipban.not-banned", "<red>Bu IP adresinin aktif bir yasağı bulunmuyor.");
            }
        });
    }
}
