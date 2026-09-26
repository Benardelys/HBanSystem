package com.ardelys.hbansystem.manager.punishment;

import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.config.Settings;
import com.ardelys.hbansystem.model.DurationResult;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.DurationParser;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Logger;

public final class WarningEscalationManager {

    private final ConfigManager configManager;
    private final PunishmentManager punishmentManager;
    private final Logger logger;

    public WarningEscalationManager(
            @NotNull ConfigManager configManager,
            @NotNull PunishmentManager punishmentManager,
            @NotNull Logger logger
    ) {
        this.configManager = configManager;
        this.punishmentManager = punishmentManager;
        this.logger = logger;
    }

    public void checkAndEscalate(@NotNull UUID targetUuid, @NotNull String targetName, int activeWarningCount) {
        Settings settings = configManager.getSettings();
        if (!settings.warningLadderEnabled()) {
            return;
        }

        Settings.WarningLadderRule rule = settings.warningLadderRules().get(activeWarningCount);
        if (rule == null) {
            return;
        }

        logger.info("[HBanSystem] Otomatik uyarı kademesi tetiklendi: " + targetName + " (Uyarı: " + activeWarningCount + ")");

        DurationResult duration;
        try {
            duration = DurationParser.parse(rule.duration());
        } catch (Exception e) {
            duration = DurationResult.of(3600000L);
        }

        UUID consoleUuid = Punishment.CONSOLE_UUID;
        String consoleName = "Otomatik Sistem";

        if (rule.action().equalsIgnoreCase("tempban") || rule.action().equalsIgnoreCase("ban")) {
            punishmentManager.ban(targetUuid, targetName, null, rule.reason(), consoleUuid, consoleName, duration.millis(), false);
        } else if (rule.action().equalsIgnoreCase("tempmute") || rule.action().equalsIgnoreCase("mute")) {
            punishmentManager.mute(targetUuid, targetName, rule.reason(), consoleUuid, consoleName, duration.millis(), false);
        }
    }
}
