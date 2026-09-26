package com.ardelys.hbansystem.manager.security;

import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.SecurityRepository;
import com.ardelys.hbansystem.hook.discord.DiscordWebhookService;
import com.ardelys.hbansystem.manager.log.StaffLogger;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.model.SecurityAction;
import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Logger;

public final class SecurityManager {

    private final SecurityEngine engine;

    public SecurityManager(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigManager configManager,
            @NotNull SecurityRepository securityRepository,
            @NotNull PunishmentManager punishmentManager,
            @NotNull DiscordWebhookService discordWebhookService,
            @NotNull StaffLogger staffLogger,
            @NotNull Logger logger
    ) {
        this.engine = new SecurityEngine(
                plugin, configManager, securityRepository, punishmentManager,
                discordWebhookService, staffLogger, logger
        );
    }

    public void start() {
        engine.start();
    }

    public void stop() {
        engine.stop();
    }

    public void onPlayerJoin(@NotNull Player player) {
        engine.onPlayerJoin(player);
    }

    public void onPlayerQuit(@NotNull UUID uuid) {
        engine.onPlayerQuit(uuid);
    }

    public void onBrandReceived(@NotNull Player player, @NotNull String rawBrand) {
        engine.onBrandReceived(player, rawBrand);
    }

    public void onChannelRegister(@NotNull Player player, @NotNull String channel) {
        engine.onChannelRegister(player, channel);
    }

    public void onPluginMessageReceived(@NotNull Player player, @NotNull String channel, byte[] message) {
        engine.onPluginMessageReceived(player, channel, message);
    }

    public void processSignal(
            @NotNull Player player,
            @NotNull String detectionType,
            int confidence,
            @NotNull String protocolDetails,
            @NotNull String explanation
    ) {
        DetectionEvidence evidence = DetectionEvidence.builder()
                .detector("LegacySignal")
                .category(detectionType)
                .weight(confidence)
                .severity(SecurityDetectionLevel.fromConfidence(confidence))
                .evidence(explanation + " | " + protocolDetails)
                .reliability(0.80)
                .recommendedAction(confidence >= 80 ? SecurityAction.KICK : SecurityAction.LOG)
                .build();

        engine.processEvidence(player, evidence);
    }

    @NotNull
    public SecurityEngine getEngine() {
        return engine;
    }
}
