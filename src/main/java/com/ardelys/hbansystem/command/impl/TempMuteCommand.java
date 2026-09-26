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

public final class TempMuteCommand extends BaseCommand {

    private final PunishmentManager punishmentManager;
    private final PunishmentCache cache;

    public TempMuteCommand(@NotNull ConfigManager configManager, @NotNull PunishmentManager punishmentManager, @NotNull PunishmentCache cache) {
        super(configManager, "tempmute", "hban.tempmute");
        this.punishmentManager = punishmentManager;
        this.cache = cache;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] rawArgs) {
        ParsedArgs parsed = stripSilentFlag(rawArgs);
        String[] args = parsed.args();

        if (args.length < 3) {
            sender.sendMessage(configManager.getMessages().getComponent("general.invalid-duration", "<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu> <süre> <sebep> [-s]</gray>"));
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

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String reason = sb.toString().trim();

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

        if (targetUuid == null) {
            final boolean silent = parsed.silent();
            final DurationResult finalDuration = duration;
            final String finalReason = reason;

            punishmentManager.getPlayerDataRepository().findByName(targetInput).thenAccept(optData -> {
                if (optData.isEmpty()) {
                    configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
                    return;
                }
                var data = optData.get();
                processTempMute(sender, data.uuid(), data.lastKnownName(), finalDuration, finalReason, silent);
            });
            return;
        }

        processTempMute(sender, targetUuid, targetName, duration, reason, parsed.silent());
    }

    private void processTempMute(CommandSender sender, UUID targetUuid, String targetName, DurationResult duration, String reason, boolean silent) {
        if (authService != null) {
            var authResult = authService.canMute(sender, targetUuid, targetName, false, duration.millis(), reason);
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

        if (cache.isMuted(targetUuid)) {
            configManager.getMessages().sendPrefixed(sender, "punishments.mute.already-muted", "<red>Bu oyuncunun zaten aktif bir susturması bulunuyor.");
            return;
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : Punishment.CONSOLE_UUID;
        String staffName = sender.getName();

        punishmentManager.mute(targetUuid, targetName, reason, staffUuid, staffName, duration.millis(), silent).thenAccept(p -> {
            if (p != null) {
                Map<String, String> placeholders = Map.of(
                        "player", targetName,
                        "reason", reason,
                        "duration", DurationParser.formatRemaining(duration.millis())
                );
                configManager.getMessages().sendPrefixed(sender, "punishments.tempmute.success", "<green>Oyuncu süreli susturuldu.", placeholders);
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
