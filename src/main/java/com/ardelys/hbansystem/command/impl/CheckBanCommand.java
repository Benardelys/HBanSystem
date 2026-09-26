package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public final class CheckBanCommand extends BaseCommand {

    private final PunishmentCache cache;

    public CheckBanCommand(@NotNull ConfigManager configManager, @NotNull PunishmentCache cache) {
        super(configManager, "check", "hban.check");
        this.cache = cache;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/" + label + " <oyuncu></gray>"));
            return;
        }

        String target = args[0];
        UUID uuid = ValidationUtil.parseUuidSafely(target);
        if (uuid == null) {
            uuid = cache.getUuidByName(target);
        }

        if (uuid == null) {
            configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
            return;
        }

        Optional<Punishment> activeBan = cache.getActiveBan(uuid);
        sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== [HBanSystem] " + target + " Yasak Durumu ===</gradient>"));
        if (activeBan.isPresent()) {
            Punishment b = activeBan.get();
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Durum: <red>Aktif Yasaklı</red>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Ceza ID: <yellow>#" + b.getId() + "</yellow>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Sebep: <yellow>" + b.getReason() + "</yellow>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Yetkili: <gold>" + b.getStaffName() + "</gold>"));
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Kalan Süre: <aqua>" + (b.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(b.getRemainingMillis())) + "</aqua>"));
        } else {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Durum: <green>Temiz (Yasak Yok)</green>"));
        }
    }
}
