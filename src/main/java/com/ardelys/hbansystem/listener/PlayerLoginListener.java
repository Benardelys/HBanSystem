package com.ardelys.hbansystem.listener;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PlayerDataRepository;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.model.PlayerData;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.ValidationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerLoginListener implements Listener {

    private final ConfigManager configManager;
    private final PunishmentRepository punishmentRepository;
    private final PlayerDataRepository playerDataRepository;
    private final PunishmentCache cache;
    private final Logger logger;

    public PlayerLoginListener(
            @NotNull ConfigManager configManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull PlayerDataRepository playerDataRepository,
            @NotNull PunishmentCache cache,
            @NotNull Logger logger
    ) {
        this.configManager = configManager;
        this.punishmentRepository = punishmentRepository;
        this.playerDataRepository = playerDataRepository;
        this.cache = cache;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        InetAddress address = event.getAddress();
        String ip = address != null ? ValidationUtil.cleanIp(address.getHostAddress()) : "";

        Optional<Punishment> cachedBan = cache.getActiveBan(uuid);
        if (cachedBan.isPresent()) {
            disallowLogin(event, cachedBan.get());
            return;
        }

        if (!ip.isEmpty()) {
            Optional<Punishment> cachedIpBan = cache.getActiveIpBan(ip);
            if (cachedIpBan.isPresent()) {
                disallowIpLogin(event, cachedIpBan.get());
                return;
            }
        }

        try {
            long now = System.currentTimeMillis();
            playerDataRepository.saveOrUpdate(new PlayerData(uuid, name, ip, now, now));
            cache.updatePlayerInfo(uuid, name, ip);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[HBanSystem] Veritabanı sorgusu başarısız oldu (Oyuncu: " + name + "): " + e.getMessage());
            if (!configManager.getSettings().allowLoginOnDbError()) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("Sunucu veritabanı geçici olarak meşgul. Lütfen az sonra tekrar deneyin.")
                );
                return;
            }
            if (configManager.getSettings().notifyStaffOnError()) {
                notifyStaffOfDbError(name);
            }
        }
    }

    private void disallowLogin(AsyncPlayerPreLoginEvent event, Punishment punishment) {
        Map<String, String> placeholders = Map.of(
                "staff", punishment.getStaffName(),
                "reason", punishment.getReason(),
                "duration", punishment.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(punishment.getRemainingMillis()),
                "id", String.valueOf(punishment.getId())
        );
        Component kickComp = configManager.getMessages().getComponent("screens.ban", "<red>Sunucudan yasaklandınız!</red>", placeholders, false);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickComp);
    }

    private void disallowIpLogin(AsyncPlayerPreLoginEvent event, Punishment punishment) {
        Map<String, String> placeholders = Map.of(
                "staff", punishment.getStaffName(),
                "reason", punishment.getReason(),
                "duration", punishment.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(punishment.getRemainingMillis()),
                "id", String.valueOf(punishment.getId())
        );
        Component kickComp = configManager.getMessages().getComponent("screens.ipban", "<red>IP adresiniz engellendi!</red>", placeholders, false);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickComp);
    }

    private void notifyStaffOfDbError(String playerName) {
        Component msg = Component.text("§c[HBanSystem UYARI] Veritabanı hatası sebebiyle " + playerName + " için güvenli giriş uygulandı.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("hban.admin") || p.hasPermission("abans.admin")) {
                p.sendMessage(msg);
            }
        }
    }
}
