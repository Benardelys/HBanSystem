package com.ardelys.hbansystem.listener;

import com.ardelys.hbansystem.auth.AuthorizationService;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.gui.PunishmentHistoryGui;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class InventoryListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final AuthorizationService authorizationService;
    private final PunishmentManager punishmentManager;

    public InventoryListener(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigManager configManager,
            @NotNull AuthorizationService authorizationService,
            @NotNull PunishmentManager punishmentManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.authorizationService = authorizationService;
        this.punishmentManager = punishmentManager;
    }

    public InventoryListener() {
        this(null, null, null, null);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PunishmentHistoryGui gui)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }

        if (slot == 49 || current.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (authorizationService == null) {
            return;
        }

        UUID targetUuid = gui.getTargetUuid();
        String targetName = gui.getTargetName();

        if (slot == 19 && current.getType() == Material.RED_CONCRETE) {
            if (!authorizationService.hasPermission(player, "hban.unban")) {
                configManager.getMessages().sendPrefixed(
                        player,
                        "general.no-permission",
                        "<red>Bu oyuncunun yasağını kaldırmak için hban.unban yetkiniz bulunmuyor."
                );
                return;
            }

            var authResult = authorizationService.canUnban(player);
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    player.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }

            punishmentManager.unban(targetUuid, targetName, player.getUniqueId(), player.getName(), "GUI üzerinden kaldırıldı", false)
                    .thenAccept(success -> {
                        if (success) {
                            configManager.getMessages().sendPrefixed(player, "punishments.unban.success", "<green>Yasak GUI üzerinden kaldırıldı.");
                            player.closeInventory();
                        }
                    });
            return;
        }

        if (slot == 21 && current.getType() == Material.ORANGE_CONCRETE) {
            if (!authorizationService.hasPermission(player, "hban.unmute")) {
                configManager.getMessages().sendPrefixed(
                        player,
                        "general.no-permission",
                        "<red>Bu oyuncunun susturmasını kaldırmak için hban.unmute yetkiniz bulunmuyor."
                );
                return;
            }

            var authResult = authorizationService.canUnmute(player);
            if (!authResult.allowed()) {
                if (authResult.denialMessage() != null) {
                    player.sendMessage(configManager.getMessages().parseComponent(authResult.denialMessage()));
                }
                return;
            }

            punishmentManager.unmute(targetUuid, targetName, player.getUniqueId(), player.getName(), "GUI üzerinden kaldırıldı", false)
                    .thenAccept(success -> {
                        if (success) {
                            configManager.getMessages().sendPrefixed(player, "punishments.unmute.success", "<green>Susturma GUI üzerinden kaldırıldı.");
                            player.closeInventory();
                        }
                    });
            return;
        }

        if (slot == 31 && current.getType() == Material.SHIELD) {
            if (!authorizationService.hasPermission(player, "hban.security.view") &&
                !authorizationService.hasPermission(player, "hban.security")) {
                configManager.getMessages().sendPrefixed(
                        player,
                        "general.no-permission",
                        "<red>Güvenlik verilerini görüntülemek için hban.security.view yetkiniz bulunmuyor."
                );
                return;
            }
            player.closeInventory();
            player.performCommand("hban security " + targetName);
        }
    }
}
