package com.ardelys.hbansystem.listener;

import com.ardelys.hbansystem.manager.security.SecurityManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public final class PlayerQuitListener implements Listener {

    private final SecurityManager securityManager;

    public PlayerQuitListener(@NotNull SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        securityManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
