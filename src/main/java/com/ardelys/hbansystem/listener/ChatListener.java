package com.ardelys.hbansystem.listener;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.DurationParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public final class ChatListener implements Listener {

    private final ConfigManager configManager;
    private final PunishmentCache cache;

    public ChatListener(@NotNull ConfigManager configManager, @NotNull PunishmentCache cache) {
        this.configManager = configManager;
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        String muteBypassPerm = configManager.getSettings().getPermission("bypass-mute", "hban.bypass.mute");
        if (player.hasPermission(muteBypassPerm) || player.hasPermission("abans.bypass.mute")) {
            return;
        }

        Optional<Punishment> activeMute = cache.getActiveMute(player.getUniqueId());
        if (activeMute.isPresent()) {
            event.setCancelled(true);
            Punishment p = activeMute.get();

            Map<String, String> placeholders = Map.of(
                    "staff", p.getStaffName(),
                    "reason", p.getReason(),
                    "duration", p.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(p.getRemainingMillis()),
                    "id", String.valueOf(p.getId())
            );

            Component muteMsg = configManager.getMessages().getComponent(
                    "screens.mute-chat-attempt",
                    "<red>Susturulduğunuz için mesaj gönderemezsiniz!",
                    placeholders,
                    false
            );
            player.sendMessage(muteMsg);
        }
    }
}
