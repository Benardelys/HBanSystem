package com.ardelys.hbansystem.listener;

import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.manager.security.SecurityManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public final class SecurityChannelListener implements Listener, PluginMessageListener {

    private final ConfigManager configManager;
    private final SecurityManager securityManager;

    public SecurityChannelListener(
            @NotNull ConfigManager configManager,
            @NotNull SecurityManager securityManager
    ) {
        this.configManager = configManager;
        this.securityManager = securityManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        securityManager.onPlayerJoin(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        String channel = event.getChannel();
        securityManager.onChannelRegister(player, channel);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        securityManager.onPluginMessageReceived(player, channel, message);
    }
}
