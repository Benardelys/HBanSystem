package com.ardelys.hbansystem.manager.security;

import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.config.SecuritySettings;
import com.ardelys.hbansystem.model.SecurityEvidence;
import com.ardelys.hbansystem.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SecurityAlertManager {

    private final ConfigManager configManager;
    private final Map<String, Long> alertCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, GroupedAlertData> recentAlertsByPlayer = new ConcurrentHashMap<>();

    private record GroupedAlertData(long firstTimestamp, AtomicInteger count, SecurityEvidence lastEvidence) {}

    public SecurityAlertManager(@NotNull ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void dispatchAlert(@NotNull SecurityEvidence evidence) {
        SecuritySettings settings = configManager.getSecuritySettings();
        UUID uuid = evidence.playerUuid();
        String cooldownKey = uuid + ":" + evidence.detectionType();
        long now = System.currentTimeMillis();

        if (settings.duplicateSuppression()) {
            Long lastTime = alertCooldowns.get(cooldownKey);
            if (lastTime != null && (now - lastTime) < (settings.alertCooldownSeconds() * 1000L)) {
                return;
            }
        }
        alertCooldowns.put(cooldownKey, now);

        GroupedAlertData group = recentAlertsByPlayer.compute(uuid, (key, existing) -> {
            if (existing == null || (now - existing.firstTimestamp() > 5000L)) {
                return new GroupedAlertData(now, new AtomicInteger(1), evidence);
            }
            existing.count().incrementAndGet();
            return new GroupedAlertData(existing.firstTimestamp(), existing.count(), evidence);
        });

        int occurrences = group.count().get();
        if (occurrences > 1 && occurrences <= 4) {
            if (occurrences == 2) {
                broadcastGroupedAlert(evidence, occurrences);
            }
            return;
        }

        broadcastSingleAlert(evidence, settings);
    }

    private void broadcastSingleAlert(SecurityEvidence evidence, SecuritySettings settings) {
        String levelColor = switch (evidence.level()) {
            case SAFE -> "<green>";
            case LOW -> "<dark_green>";
            case MEDIUM -> "<yellow>";
            case HIGH -> "<gold>";
            case VERY_HIGH -> "<red>";
            case CRITICAL -> "<dark_red><bold>";
        };

        String prefix = (settings.securityPrefix() == null || settings.securityPrefix().isBlank())
                ? configManager.getPrefix()
                : settings.securityPrefix();

        Map<String, String> placeholders = Map.of(
                "prefix", prefix,
                "player", evidence.playerName(),
                "type", evidence.detectionType(),
                "level", evidence.level().getDisplayName(),
                "level_color", levelColor,
                "confidence", String.valueOf(evidence.confidenceScore()),
                "action", evidence.actionTaken().getDisplayName()
        );

        String template = settings.alertFormat();
        if (template.isBlank()) {
            template = prefix + "<yellow>{player}</yellow> için tespit: <aqua>{type}</aqua> ({level} - %{confidence})";
        } else {
            template = template.replace("%prefix%", prefix);
        }

        Component alertComp = MessageUtil.parseWithPlaceholders(template, placeholders);
        sendToStaff(alertComp);
    }

    private void broadcastGroupedAlert(SecurityEvidence evidence, int count) {
        SecuritySettings settings = configManager.getSecuritySettings();
        String prefix = (settings.securityPrefix() == null || settings.securityPrefix().isBlank())
                ? configManager.getPrefix()
                : settings.securityPrefix();

        Component groupedComp = MessageUtil.parse(
                prefix + "<yellow>" + evidence.playerName() +
                        "</yellow> için son 5 saniyede <aqua>" + count + " adet</aqua> şüpheli aktivite saptandı. (Son Tespit: " +
                        evidence.detectionType() + " | Skor: %" + evidence.confidenceScore() + ")"
        );
        sendToStaff(groupedComp);
    }

    private void sendToStaff(Component component) {
        String alertPerm = configManager.getSettings().getPermission("alerts", "hban.alerts");
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(alertPerm) || staff.hasPermission("abans.alerts") || staff.hasPermission("hban.admin") || staff.hasPermission("abans.admin")) {
                staff.sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    public void cleanup(@NotNull UUID uuid) {
        recentAlertsByPlayer.remove(uuid);
    }
}
