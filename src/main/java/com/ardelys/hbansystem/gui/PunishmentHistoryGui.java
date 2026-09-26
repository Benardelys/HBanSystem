package com.ardelys.hbansystem.gui;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PlayerDataRepository;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.database.repository.SecurityRepository;
import com.ardelys.hbansystem.model.PlayerData;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import com.ardelys.hbansystem.model.SecurityEvidence;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PunishmentHistoryGui implements InventoryHolder {

    private final ConfigManager configManager;
    private final PunishmentRepository punishmentRepository;
    private final PlayerDataRepository playerDataRepository;
    private final SecurityRepository securityRepository;
    private final PunishmentCache cache;
    private final UUID targetUuid;
    private final String targetName;

    private Inventory inventory;

    public PunishmentHistoryGui(
            @NotNull ConfigManager configManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull PlayerDataRepository playerDataRepository,
            @NotNull SecurityRepository securityRepository,
            @NotNull PunishmentCache cache,
            @NotNull UUID targetUuid,
            @NotNull String targetName
    ) {
        this.configManager = configManager;
        this.punishmentRepository = punishmentRepository;
        this.playerDataRepository = playerDataRepository;
        this.securityRepository = securityRepository;
        this.cache = cache;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public CompletableFuture<Inventory> buildAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Component title = MessageUtil.parse("<gradient:#4facfe:#00f2fe>HBanSystem: " + targetName + "</gradient>");
            this.inventory = Bukkit.createInventory(this, 54, title);

            ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 54; i++) {
                inventory.setItem(i, pane);
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            if (skullMeta != null) {
                skullMeta.displayName(MessageUtil.parse("<gold><bold>" + targetName + "</bold></gold>"));
                skullMeta.lore(List.of(
                        MessageUtil.parse("<gray>UUID: <yellow>" + targetUuid + "</yellow>"),
                        MessageUtil.parse("<gray>Son IP: <yellow>" + (cache.getLastIp(targetUuid) != null ? cache.getLastIp(targetUuid) : "Gizli") + "</yellow>")
                ));
                head.setItemMeta(skullMeta);
            }
            inventory.setItem(4, head);

            Optional<Punishment> activeBan = cache.getActiveBan(targetUuid);
            if (activeBan.isPresent()) {
                Punishment b = activeBan.get();
                inventory.setItem(19, createItem(
                        Material.RED_CONCRETE,
                        "<red><bold>Aktif Yasak</bold></red>",
                        "<gray>Sebep: <yellow>" + b.getReason() + "</yellow>",
                        "<gray>Yetkili: <gold>" + b.getStaffName() + "</gold>",
                        "<gray>Kalan: <aqua>" + (b.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(b.getRemainingMillis())) + "</aqua>",
                        "<gray>Ceza ID: <yellow>#" + b.getId() + "</yellow>"
                ));
            } else {
                inventory.setItem(19, createItem(
                        Material.LIME_CONCRETE,
                        "<green><bold>Yasak Yok</bold></green>",
                        "<gray>Oyuncunun aktif bir yasağı bulunmuyor."
                ));
            }

            Optional<Punishment> activeMute = cache.getActiveMute(targetUuid);
            if (activeMute.isPresent()) {
                Punishment m = activeMute.get();
                inventory.setItem(21, createItem(
                        Material.ORANGE_CONCRETE,
                        "<gold><bold>Aktif Susturma</bold></gold>",
                        "<gray>Sebep: <yellow>" + m.getReason() + "</yellow>",
                        "<gray>Yetkili: <gold>" + m.getStaffName() + "</gold>",
                        "<gray>Kalan: <aqua>" + (m.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(m.getRemainingMillis())) + "</aqua>",
                        "<gray>Ceza ID: <yellow>#" + m.getId() + "</yellow>"
                ));
            } else {
                inventory.setItem(21, createItem(
                        Material.LIME_CONCRETE,
                        "<green><bold>Susturma Yok</bold></green>",
                        "<gray>Oyuncunun aktif bir susturması bulunmuyor."
                ));
            }

            List<Punishment> history = punishmentRepository.findByTargetUuid(targetUuid).join();
            long activeWarns = history.stream().filter(p -> p.getType() == PunishmentType.WARN && p.isActive()).count();
            inventory.setItem(23, createItem(
                    Material.YELLOW_CONCRETE,
                    "<yellow><bold>Aktif Uyarılar</bold></yellow>",
                    "<gray>Aktif Uyarı Sayısı: <yellow>" + activeWarns + "</yellow>",
                    "<gray>Toplam Ceza Kaydı: <aqua>" + history.size() + "</aqua>"
            ));

            String ip = cache.getLastIp(targetUuid);
            List<PlayerData> alts = (ip != null) ? playerDataRepository.findByIp(ip).join() : List.of();
            List<String> altLore = new ArrayList<>();
            altLore.add("<gray>Aynı IP'yi kullanan hesaplar: <yellow>" + alts.size() + "</yellow>");
            for (int i = 0; i < Math.min(alts.size(), 5); i++) {
                PlayerData alt = alts.get(i);
                altLore.add("<gray>• <yellow>" + alt.lastKnownName() + "</yellow>");
            }
            inventory.setItem(25, createItem(Material.ENDER_CHEST, "<light_purple><bold>Yan Hesaplar (Alts)</bold></light_purple>", altLore.toArray(new String[0])));

            List<SecurityEvidence> evidenceList = securityRepository.findByPlayerUuid(targetUuid).join();
            List<String> secLore = new ArrayList<>();
            secLore.add("<gray>Toplam Güvenlik Kaydı: <yellow>" + evidenceList.size() + "</yellow>");
            if (!evidenceList.isEmpty()) {
                SecurityEvidence last = evidenceList.get(0);
                secLore.add("<gray>Son Tespit: <aqua>" + last.detectionType() + "</aqua>");
                secLore.add("<gray>Seviye: <gold>" + last.level().getDisplayName() + " (%" + last.confidenceScore() + ")</gold>");
                secLore.add("<gray>Eylem: <white>" + last.actionTaken().getDisplayName() + "</white>");
            }
            inventory.setItem(31, createItem(Material.SHIELD, "<red><bold>Güvenlik & Enjeksiyon Taraması</bold></red>", secLore.toArray(new String[0])));

            int slot = 36;
            for (Punishment p : history) {
                if (slot > 44) break;
                inventory.setItem(slot++, createItem(
                        Material.PAPER,
                        "<yellow>#" + p.getId() + " - " + p.getType().getDisplayName() + "</yellow>",
                        "<gray>Sebep: <white>" + p.getReason() + "</white>",
                        "<gray>Yetkili: <gold>" + p.getStaffName() + "</gold>",
                        "<gray>Tarih: <white>" + MessageUtil.formatDate(p.getCreatedAt()) + "</white>",
                        "<gray>Durum: " + (p.isActive() ? "<red>Aktif</red>" : "<gray>Geçersiz</gray>")
                ));
            }

            inventory.setItem(49, createItem(Material.BARRIER, "<red><bold>Kapat</bold></red>"));

            return inventory;
        });
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse(name));
            if (lore.length > 0) {
                List<Component> loreList = new ArrayList<>();
                for (String l : lore) {
                    loreList.add(MessageUtil.parse(l));
                }
                meta.lore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
