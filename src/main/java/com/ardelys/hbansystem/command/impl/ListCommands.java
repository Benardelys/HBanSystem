package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.MessageUtil;
import com.ardelys.hbansystem.util.PaginationUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class ListCommands extends BaseCommand {

    private final PunishmentRepository punishmentRepository;
    private final PunishmentType type;
    private final String listTitle;

    public ListCommands(
            @NotNull ConfigManager configManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull PunishmentType type,
            @NotNull String permKey,
            @NotNull String listTitle
    ) {
        super(configManager, permKey, "hban." + permKey);
        this.punishmentRepository = punishmentRepository;
        this.type = type;
        this.listTitle = listTitle;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        final int targetPage = page;

        punishmentRepository.getActiveByType(type).thenAccept(list -> {
            if (list.isEmpty()) {
                sender.sendMessage(configManager.getMessages().parseComponent("<green>Aktif " + listTitle.toLowerCase() + " bulunmuyor.</green>"));
                return;
            }

            int pageSize = 6;
            int totalPages = PaginationUtil.getTotalPages(list.size(), pageSize);
            int validPage = Math.max(1, Math.min(targetPage, totalPages));
            List<Punishment> pageItems = PaginationUtil.getPage(list, validPage, pageSize);

            sender.sendMessage(configManager.getMessages().parseComponent(
                    "<gradient:#4facfe:#00f2fe>=== Aktif " + listTitle + " Listesi (Sayfa " + validPage + "/" + totalPages + ") ===</gradient>"
            ));

            for (Punishment p : pageItems) {
                String duration = p.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(p.getRemainingMillis());
                sender.sendMessage(configManager.getMessages().parseComponent(
                        "<yellow>#" + p.getId() + "</yellow> | <yellow>" + p.getTargetName() + "</yellow> | <gray>" + p.getReason() + "</gray> | <gold>" + p.getStaffName() + "</gold> | <aqua>" + duration + "</aqua>"
                ));
            }

            if (validPage < totalPages) {
                sender.sendMessage(configManager.getMessages().parseComponent(
                        "<gray>Sonraki sayfa için: <yellow>/" + label + " " + (validPage + 1) + "</yellow></gray>"
                ));
            }
        });
    }
}
