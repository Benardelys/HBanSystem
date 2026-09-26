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

public final class UnmuteCommand extends BaseCommand {

    private final PunishmentManager punishmentManager;
    private final PunishmentCache cache;

    public UnmuteCommand(@NotNull ConfigManager configManager, @NotNull PunishmentManager punishmentManager, @NotNull PunishmentCache cache) {
        super(configManager, "unmute", "hban.unmute");
        this.punishmentManager = punishmentManager;
        this.cache = cache;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] rawArgs) {
        ParsedArgs parsed = stripSilentFlag(rawArgs);
        String[] args = parsed.args();

        if (args.length < 1) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu> [sebep] [-s]</gray>"));
            return;
        }

        String targetInput = args[0];
        String reason = (args.length > 1) ?
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)) :
                configManager.getSettings().defaultUnmuteReason();

        UUID targetUuid = ValidationUtil.parseUuidSafely(targetInput);
        if (targetUuid == null) {
            targetUuid = cache.getUuidByName(targetInput);
        }

        if (targetUuid == null) {
            final boolean silent = parsed.silent();
            final String finalReason = reason;

            punishmentManager.getPlayerDataRepository().findByName(targetInput).thenAccept(optData -> {
                if (optData.isEmpty()) {
                    configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
                    return;
                }
                var data = optData.get();
                processUnmute(sender, data.uuid(), data.lastKnownName(), finalReason, silent);
            });
            return;
        }

        String targetName = cache.getNameByUuid(targetUuid);
        processUnmute(sender, targetUuid, targetName != null ? targetName : targetInput, reason, parsed.silent());
    }

    private void processUnmute(CommandSender sender, UUID targetUuid, String targetName, String reason, boolean silent) {
        if (!cache.isMuted(targetUuid)) {
            configManager.getMessages().sendPrefixed(sender, "punishments.unmute.not-muted", "<red>Bu oyuncunun aktif bir susturması bulunmuyor.");
            return;
        }

        if (authService != null) {
            var activeMuteOpt = cache.getActiveMute(targetUuid);
            var authResult = authService.canUnmute(sender, activeMuteOpt.orElse(null));
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    sender.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : Punishment.CONSOLE_UUID;
        String staffName = sender.getName();

        punishmentManager.unmute(targetUuid, targetName, staffUuid, staffName, reason, silent).thenAccept(success -> {
            if (success) {
                Map<String, String> placeholders = Map.of(
                        "player", targetName,
                        "reason", reason
                );
                configManager.getMessages().sendPrefixed(sender, "punishments.unmute.success", "<green>Oyuncunun susturması kaldırıldı.", placeholders);
            } else {
                configManager.getMessages().sendPrefixed(sender, "punishments.unmute.not-muted", "<red>Bu oyuncunun aktif bir susturması bulunuyor.");
            }
        });
    }
}
