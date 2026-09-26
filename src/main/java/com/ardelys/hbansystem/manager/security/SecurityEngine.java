package com.ardelys.hbansystem.manager.security;

import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.config.SecuritySettings;
import com.ardelys.hbansystem.database.repository.SecurityRepository;
import com.ardelys.hbansystem.event.SecurityDetectionEvent;
import com.ardelys.hbansystem.hook.discord.DiscordWebhookService;
import com.ardelys.hbansystem.manager.log.StaffLogger;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.manager.security.detector.*;
import com.ardelys.hbansystem.model.*;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Logger;

public final class SecurityEngine {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SecurityRepository securityRepository;
    private final PunishmentManager punishmentManager;
    private final DiscordWebhookService discordWebhookService;
    private final StaffLogger staffLogger;
    private final Logger logger;

    private final DetectionManager detectionManager;
    private final ConfidenceManager confidenceManager;
    private final SecurityAlertManager alertManager;
    private final SecurityActionManager actionManager;

    private final InjectorDetector injectorDetector;
    private final ProtocolDetector protocolDetector;
    private final PacketAnomalyDetector packetAnomalyDetector;
    private final ChannelDetector channelDetector;
    private final BehaviorDetector behaviorDetector;
    private final ClientIntegrityDetector clientIntegrityDetector;

    private final List<ISecurityDetector> allDetectors = new ArrayList<>();
    private BukkitTask maintenanceTask;
    private BukkitTask retentionTask;

    public SecurityEngine(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigManager configManager,
            @NotNull SecurityRepository securityRepository,
            @NotNull PunishmentManager punishmentManager,
            @NotNull DiscordWebhookService discordWebhookService,
            @NotNull StaffLogger staffLogger,
            @NotNull Logger logger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.securityRepository = securityRepository;
        this.punishmentManager = punishmentManager;
        this.discordWebhookService = discordWebhookService;
        this.staffLogger = staffLogger;
        this.logger = logger;

        this.detectionManager = new DetectionManager();
        this.confidenceManager = new ConfidenceManager(60_000L, 30_000L);
        this.alertManager = new SecurityAlertManager(configManager);
        this.actionManager = new SecurityActionManager(plugin, configManager, punishmentManager, discordWebhookService, staffLogger, alertManager);

        this.injectorDetector = new InjectorDetector(logger);
        this.protocolDetector = new ProtocolDetector(logger);
        this.packetAnomalyDetector = new PacketAnomalyDetector(logger);
        this.channelDetector = new ChannelDetector(logger);
        this.behaviorDetector = new BehaviorDetector(logger);
        this.clientIntegrityDetector = new ClientIntegrityDetector(logger);

        this.allDetectors.addAll(List.of(
                injectorDetector, protocolDetector, packetAnomalyDetector,
                channelDetector, behaviorDetector, clientIntegrityDetector
        ));
    }

    public synchronized void start() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }
        this.maintenanceTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                detectionManager.cleanupStaleContexts();
                for (DetectionContext ctx : detectionManager.getAllActiveContexts()) {
                    confidenceManager.calculateConfidence(ctx);
                }
            } catch (Throwable t) {
                logger.warning("[HBanSystem Security] Bakım görevi sırasında hata: " + t.getMessage());
            }
        }, 200L, 200L);

        long retentionDays = configManager.getSecuritySettings().evidenceRetentionDays();
        if (retentionDays > 0) {
            if (retentionTask != null) {
                retentionTask.cancel();
            }
            this.retentionTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                try {
                    long cutoff = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(retentionDays);
                    securityRepository.deleteExpiredEvidence(cutoff).thenAccept(deleted -> {
                        if (deleted > 0) {
                            logger.info("[HBanSystem Security] " + deleted + " adet süresi geçmiş güvenlik kanıtı temizlendi.");
                        }
                    });
                } catch (Throwable t) {
                    logger.warning("[HBanSystem Security] Kanıt temizleme sırasında hata: " + t.getMessage());
                }
            }, 1200L, 72000L);
        }
    }

    public synchronized void stop() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        if (retentionTask != null) {
            retentionTask.cancel();
            retentionTask = null;
        }
        detectionManager.clearAll();
    }

    public void onPlayerJoin(@NotNull Player player) {
        DetectionContext context = detectionManager.getOrCreateContext(player);
        behaviorDetector.onPlayerJoin(player.getUniqueId());

        try {
            String brand = player.getClientBrandName();
            if (brand != null) {
                onBrandReceived(player, brand);
            }
        } catch (Throwable ignored) {}
    }

    public void onPlayerQuit(@NotNull UUID uuid) {
        packetAnomalyDetector.cleanup(uuid);
        behaviorDetector.cleanup(uuid);
        actionManager.cleanup(uuid);
        alertManager.cleanup(uuid);
        detectionManager.removeContext(uuid);
    }

    public void onBrandReceived(@NotNull Player player, @NotNull String rawBrand) {
        DetectionContext context = detectionManager.getOrCreateContext(player);
        DetectionResult result = clientIntegrityDetector.analyzeBrand(context, rawBrand, configManager.getSecuritySettings());
        if (result.suspicious() && result.evidence() != null) {
            processEvidence(player, result.evidence());
        }
    }

    public void onChannelRegister(@NotNull Player player, @NotNull String channel) {
        DetectionContext context = detectionManager.getOrCreateContext(player);

        DetectionResult channelRes = channelDetector.validateChannel(context, channel);
        if (channelRes.suspicious() && channelRes.evidence() != null) {
            processEvidence(player, channelRes.evidence());
        }

        DetectionResult injectorRes = injectorDetector.analyzeChannelPayload(context, channel);
        if (injectorRes.suspicious() && injectorRes.evidence() != null) {
            processEvidence(player, injectorRes.evidence());
        }
    }

    public void onPluginMessageReceived(@NotNull Player player, @NotNull String channel, byte[] message) {
        DetectionContext context = detectionManager.getOrCreateContext(player);

        DetectionResult anomalyRes = packetAnomalyDetector.analyzePayloadPacket(context, message.length);
        if (anomalyRes.suspicious() && anomalyRes.evidence() != null) {
            processEvidence(player, anomalyRes.evidence());
            return;
        }

        DetectionResult channelRes = channelDetector.validateChannel(context, channel);
        if (channelRes.suspicious() && channelRes.evidence() != null) {
            processEvidence(player, channelRes.evidence());
        }

        DetectionResult injectorRes = injectorDetector.analyzeChannelPayload(context, channel);
        if (injectorRes.suspicious() && injectorRes.evidence() != null) {
            processEvidence(player, injectorRes.evidence());
        }
    }

    public void processEvidence(@NotNull Player player, @NotNull DetectionEvidence evidence) {
        SecuritySettings settings = configManager.getSecuritySettings();
        if (!settings.enabled()) {
            return;
        }

        DetectionContext context = detectionManager.getOrCreateContext(player);
        context.addEvidence(evidence);

        int aggregateConfidence = confidenceManager.calculateConfidence(context);
        SecurityDetectionLevel level = confidenceManager.getDetectionLevel(aggregateConfidence);

        SecurityAction action = switch (level) {
            case CRITICAL -> settings.criticalAction();
            case VERY_HIGH -> settings.veryHighAction();
            case HIGH -> settings.highAction();
            case MEDIUM -> settings.mediumAction();
            case LOW -> settings.lowAction();
            case SAFE -> SecurityAction.NONE;
        };

        String ip = player.getAddress() != null ? ValidationUtil.cleanIp(player.getAddress().getAddress().getHostAddress()) : null;

        SecurityEvidence evidenceRecord = new SecurityEvidence(
                0L,
                player.getUniqueId(),
                player.getName(),
                ip,
                evidence.category() + " (" + evidence.detector() + ")",
                level,
                aggregateConfidence,
                System.currentTimeMillis(),
                Bukkit.getMinecraftVersion(),
                context.getClientBrand(),
                evidence.evidence(),
                evidence.evidence(),
                action
        );

        SecurityDetectionEvent event = new SecurityDetectionEvent(evidenceRecord, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        securityRepository.save(evidenceRecord).thenAccept(saved -> {
            discordWebhookService.sendSecurityEmbed(saved);
        });

        staffLogger.logAction("GÜVENLİK SİSTEMİ", "TESPİT", String.format(
                "[%s] %s (Seviye: %s, Güven: %%%d, Eylem: %s)",
                evidence.detector(), player.getName(), level.name(), aggregateConfidence, action.name()
        ));

        actionManager.handleAction(player, evidenceRecord, action);
    }

    @NotNull
    public DetectionManager getDetectionManager() {
        return detectionManager;
    }

    @NotNull
    public ConfidenceManager getConfidenceManager() {
        return confidenceManager;
    }

    @NotNull
    public List<ISecurityDetector> getAllDetectors() {
        return Collections.unmodifiableList(allDetectors);
    }
}
