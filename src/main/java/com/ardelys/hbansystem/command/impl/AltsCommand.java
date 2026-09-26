package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PlayerDataRepository;
import com.ardelys.hbansystem.model.PlayerData;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public final class AltsCommand extends BaseCommand {

    private final PlayerDataRepository playerDataRepository;
    private final PunishmentCache cache;

    public AltsCommand(
            @NotNull ConfigManager configManager,
            @NotNull PlayerDataRepository playerDataRepository,
            @NotNull PunishmentCache cache
    ) {
        super(configManager, "alts", "hban.alts");
        this.playerDataRepository = playerDataRepository;
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
            playerDataRepository.findByName(target).thenAccept(opt -> {
                if (opt.isEmpty()) {
                    configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
                    return;
                }
                findAndDisplayAlts(sender, opt.get().lastIp(), opt.get().lastKnownName());
            });
            return;
        }

        String ip = cache.getLastIp(uuid);
        String name = cache.getNameByUuid(uuid);
        findAndDisplayAlts(sender, ip, name != null ? name : target);
    }

    private void findAndDisplayAlts(CommandSender sender, String ip, String targetName) {
        if (ip == null || ip.isBlank()) {
            sender.sendMessage(configManager.getMessages().parseComponent("<yellow>[HBanSystem]</yellow> <gray>" + targetName + " için kayıtlı IP adresi bulunamadı.</gray>"));
            return;
        }

        playerDataRepository.findByIp(ip).thenAccept(list -> {
            if (list.isEmpty()) {
                configManager.getMessages().sendPrefixed(sender, "info.alts-empty", "<green>Bu oyuncuyla eşleşen başka bir hesap bulunamadı.");
                return;
            }

            sender.sendMessage(configManager.getMessages().parseComponent(
                    "<gradient:#4facfe:#00f2fe>=== [HBanSystem] " + targetName + " Yan Hesapları (" + list.size() + " Adet) ===</gradient>"
            ));

            for (PlayerData data : list) {
                String status = "";
                if (cache.isBanned(data.uuid())) {
                    status += " <red>[YASAKLI]</red>";
                }
                if (cache.isMuted(data.uuid())) {
                    status += " <gold>[SUSTURULDU]</gold>";
                }
                if (status.isEmpty()) {
                    status = " <green>[TEMİZ]</green>";
                }

                sender.sendMessage(configManager.getMessages().parseComponent(
                        "<gray>• <yellow>" + data.lastKnownName() + "</yellow> <dark_gray>(" + data.uuid() + ")</dark_gray>" + status
                ));
            }
        });
    }
}
