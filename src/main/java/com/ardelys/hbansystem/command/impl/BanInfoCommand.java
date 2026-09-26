package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public final class BanInfoCommand extends BaseCommand {

    private final PunishmentRepository punishmentRepository;

    public BanInfoCommand(@NotNull ConfigManager configManager, @NotNull PunishmentRepository punishmentRepository) {
        super(configManager, "baninfo", "hban.baninfo");
        this.punishmentRepository = punishmentRepository;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <ceza-id></gray>"));
            return;
        }

        long id;
        try {
            id = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(configManager.getMessages().parseComponent("<red>Geçersiz ceza ID numarası.</red>"));
            return;
        }

        punishmentRepository.findById(id).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sender.sendMessage(configManager.getMessages().parseComponent("<red>#" + id + " numaralı ceza bulunamadı.</red>"));
                return;
            }

            Punishment p = opt.get();
            String durationStr = p.isPermanent() ? "Kalıcı" : (p.getType().isTemporary() ? DurationParser.formatRemaining(p.getRemainingMillis()) : "-");
            String statusStr = p.isActive() ? "<red>Aktif</red>" : "<gray>Geçersiz / Kaldırılmış</gray>";

            sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== Ceza Detayı: #" + p.getId() + " ===</gradient>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Hedef: <yellow>" + p.getTargetName() + "</yellow> <dark_gray>(" + p.getTargetUuid() + ")</dark_gray>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Tür: <aqua>" + p.getType().getDisplayName() + "</aqua>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Sebep: <white>" + p.getReason() + "</white>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Yetkili: <gold>" + p.getStaffName() + "</gold>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Tarih: <white>" + MessageUtil.formatDate(p.getCreatedAt()) + "</white>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Süre: <yellow>" + durationStr + "</yellow>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Durum: " + statusStr));

            if (p.getRevokedAt() != null) {
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>Kaldıran: <gold>" + (p.getRevokedByStaffName() != null ? p.getRevokedByStaffName() : "-") + "</gold>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>Kaldırma Sebebi: <white>" + (p.getRevocationReason() != null ? p.getRevocationReason() : "-") + "</white>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>Kaldırma Tarihi: <white>" + MessageUtil.formatDate(p.getRevokedAt()) + "</white>"));
            }
        });
    }
}
