package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.model.Punishment;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public final class UnwarnCommand extends BaseCommand {

    private final PunishmentManager punishmentManager;

    public UnwarnCommand(@NotNull ConfigManager configManager, @NotNull PunishmentManager punishmentManager) {
        super(configManager, "unwarn", "hban.unwarn");
        this.punishmentManager = punishmentManager;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu> <ceza-id></gray>"));
            return;
        }

        long punishmentId;
        try {
            punishmentId = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            configManager.getMessages().sendPrefixed(sender, "general.error-occurred", "<red>Geçersiz ceza ID'si girdiniz.");
            return;
        }

        if (authService != null) {
            var authResult = authService.canUnwarn(sender);
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    sender.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : Punishment.CONSOLE_UUID;
        String staffName = sender.getName();
        String reason = "Yetkili tarafından kaldırıldı";

        punishmentManager.unwarn(punishmentId, staffUuid, staffName, reason).thenAccept(success -> {
            if (success) {
                Map<String, String> placeholders = Map.of("id", String.valueOf(punishmentId));
                configManager.getMessages().sendPrefixed(sender, "punishments.unwarn.success", "<green>Uyarı başarıyla kaldırıldı.", placeholders);
            } else {
                Map<String, String> placeholders = Map.of("id", String.valueOf(punishmentId));
                configManager.getMessages().sendPrefixed(sender, "punishments.unwarn.not-found", "<red>Aktif uyarı bulunamadı.", placeholders);
            }
        });
    }
}
