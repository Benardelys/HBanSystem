package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.MessageUtil;
import com.ardelys.hbansystem.util.PaginationUtil;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HistoryCommand extends BaseCommand {

    private final PunishmentRepository punishmentRepository;
    private final PunishmentCache cache;

    public HistoryCommand(
            @NotNull ConfigManager configManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull PunishmentCache cache
    ) {
        super(configManager, "history", "hban.history");
        this.punishmentRepository = punishmentRepository;
        this.cache = cache;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu> [sayfa]</gray>"));
            return;
        }

        String target = args[0];
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }
        final int targetPage = page;

        UUID uuid = ValidationUtil.parseUuidSafely(target);
        if (uuid == null) {
            uuid = cache.getUuidByName(target);
        }

        if (uuid == null) {
            final String finalTarget = target;
            final int finalPage = targetPage;
            punishmentRepository.findByTargetIp(target).thenAccept(ipList -> {
                if (!ipList.isEmpty()) {
                    showHistory(sender, ipList.get(0).getTargetUuid(), finalTarget, finalPage);
                } else {
                    configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
                }
            });
            return;
        }

        showHistory(sender, uuid, target, targetPage);
    }

    private void showHistory(CommandSender sender, UUID uuid, String targetName, int page) {
        punishmentRepository.findByTargetUuid(uuid).thenAccept(list -> {
            if (list.isEmpty()) {
                configManager.getMessages().sendPrefixed(sender, "info.history-empty", "<green>Bu oyuncuya ait herhangi bir ceza kaydı bulunmuyor.");
                return;
            }

            int pageSize = 6;
            int totalPages = PaginationUtil.getTotalPages(list.size(), pageSize);
            int validPage = Math.max(1, Math.min(page, totalPages));
            List<Punishment> pageItems = PaginationUtil.getPage(list, validPage, pageSize);

            Map<String, String> headerHolders = Map.of(
                    "player", targetName,
                    "page", String.valueOf(validPage),
                    "total", String.valueOf(totalPages)
            );
            sender.sendMessage(configManager.getMessages().getComponent("info.history-header", "<gradient:#4facfe:#00f2fe>=== Ceza Geçmişi ===</gradient>", headerHolders, false));

            for (Punishment p : pageItems) {
                String durationStr = p.isPermanent() ? "Kalıcı" : (p.getType().isTemporary() ? DurationParser.formatRemaining(p.getRemainingMillis()) : "-");
                String statusStr = p.isActive() ? "<red>Aktif</red>" : "<gray>Geçersiz</gray>";

                Map<String, String> placeholders = Map.of(
                        "id", String.valueOf(p.getId()),
                        "type", p.getType().name(),
                        "reason", p.getReason(),
                        "staff", p.getStaffName(),
                        "date", MessageUtil.formatDate(p.getCreatedAt()),
                        "duration", durationStr,
                        "status", statusStr
                );
                sender.sendMessage(configManager.getMessages().getComponent("info.history-entry", "#{id} | {type} | {reason} | {staff} | {date} | {duration} | {status}", placeholders, false));
            }

            if (validPage < totalPages) {
                Map<String, String> footerHolders = Map.of(
                        "player", targetName,
                        "nextpage", String.valueOf(validPage + 1)
                );
                sender.sendMessage(configManager.getMessages().getComponent("info.history-footer", "<gray>Sonraki sayfa: /history {player} {nextpage}", footerHolders, false));
            }
        });
    }
}
