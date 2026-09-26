package com.ardelys.hbansystem.hook.discord;

import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import com.ardelys.hbansystem.model.SecurityEvidence;
import com.ardelys.hbansystem.util.DurationParser;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DiscordWebhookService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Logger logger;
    private final HttpClient httpClient;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(500);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    private volatile boolean enabled;
    private volatile String webhookUrl;
    private volatile String botName;
    private volatile String avatarUrl;

    private volatile boolean eventBan;
    private volatile boolean eventUnban;
    private volatile boolean eventMute;
    private volatile boolean eventUnmute;
    private volatile boolean eventKick;
    private volatile boolean eventWarn;
    private volatile boolean eventUnIpBan;
    private volatile boolean eventSecurity;

    public DiscordWebhookService(@NotNull Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public synchronized void reload(@NotNull FileConfiguration config) {
        this.enabled = config.getBoolean("enabled", false);
        this.webhookUrl = config.getString("webhook-url", "");
        this.botName = config.getString("bot-name", "HBanSystem Moderasyon");
        this.avatarUrl = config.getString("avatar-url", "https://minotar.net/avatar/MHF_Custom/128.png");

        this.eventBan = config.getBoolean("events.ban", config.getBoolean("notifications.ban", true));
        this.eventUnban = config.getBoolean("events.unban", config.getBoolean("notifications.unban", true));
        this.eventMute = config.getBoolean("events.mute", config.getBoolean("notifications.mute", true));
        this.eventUnmute = config.getBoolean("events.unmute", config.getBoolean("notifications.unmute", true));
        this.eventKick = config.getBoolean("events.kick", config.getBoolean("notifications.kick", true));
        this.eventWarn = config.getBoolean("events.warn", config.getBoolean("notifications.warn", true));
        this.eventUnIpBan = config.getBoolean("events.unipban", true);
        this.eventSecurity = config.getBoolean("events.security", config.getBoolean("notifications.security-detection", true));

        if (enabled && !webhookUrl.isBlank() && webhookUrl.startsWith("http")) {
            startWorker();
        } else {
            stopWorker();
        }
    }

    private synchronized void startWorker() {
        if (running.get()) {
            return;
        }
        running.set(true);
        workerThread = new Thread(this::processQueue, "HBanSystem-DiscordWorker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private synchronized void stopWorker() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    public synchronized void shutdown() {
        stopWorker();
        int remaining = queue.size();
        if (remaining > 0 && webhookUrl != null && !webhookUrl.isBlank()) {
            while (!queue.isEmpty()) {
                String payload = queue.poll();
                if (payload != null) {
                    dispatchDirect(payload);
                }
            }
        }
        queue.clear();
    }

    private void processQueue() {
        while (running.get()) {
            try {
                String payload = queue.poll(1, TimeUnit.SECONDS);
                if (payload != null) {
                    int statusCode = dispatchDirect(payload);
                    if (statusCode == 429) {
                        Thread.sleep(2500L);
                    } else {
                        Thread.sleep(300L);
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                logger.log(Level.FINE, "[HBanSystem Discord] Kuyruk işlenirken hata: " + e.getMessage());
            }
        }
    }

    private int dispatchDirect(String payload) {
        if (webhookUrl == null || webhookUrl.isBlank() || !webhookUrl.startsWith("http")) {
            return 0;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("User-Agent", "HBanSystem-DiscordWebhook/1.0")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            logger.log(Level.FINE, "[HBanSystem Discord] Webhook gönderim hatası: " + e.getMessage());
            return -1;
        }
    }

    public void sendPunishmentEmbed(@NotNull Punishment punishment) {
        if (!enabled || !isEventEnabled(punishment.getType())) {
            return;
        }

        String typeDisplay = punishment.getType().getDisplayName();
        String durationStr = punishment.isPermanent() ? "Kalıcı" : DurationParser.formatRemaining(punishment.getDurationMillis());
        String dateStr = DATE_FORMATTER.format(Instant.ofEpochMilli(punishment.getCreatedAt()));
        String expiresStr = punishment.isPermanent() ? "Asla" : (punishment.getExpiresAt() > 0 ? DATE_FORMATTER.format(Instant.ofEpochMilli(punishment.getExpiresAt())) : "Bilinmiyor");

        int color = switch (punishment.getType()) {
            case BAN, IP_BAN -> 16711680;
            case TEMPBAN, TEMP_IP_BAN -> 16744448;
            case MUTE, TEMP_MUTE -> 16766720;
            case KICK -> 16753920;
            case WARN -> 16776960;
        };

        String payload = "{" +
                "\"username\":\"" + escapeJson(botName) + "\"," +
                "\"avatar_url\":\"" + escapeJson(avatarUrl) + "\"," +
                "\"embeds\":[{" +
                "\"title\":\"HBanSystem - Ceza Kaydı: " + escapeJson(typeDisplay) + "\"," +
                "\"color\":" + color + "," +
                "\"fields\":[" +
                "{\"name\":\"Oyuncu\",\"value\":\"" + escapeJson(punishment.getTargetName()) + "\",\"inline\":true}," +
                "{\"name\":\"Yetkili\",\"value\":\"" + escapeJson(punishment.getStaffName()) + "\",\"inline\":true}," +
                "{\"name\":\"Süre\",\"value\":\"" + escapeJson(durationStr) + "\",\"inline\":true}," +
                "{\"name\":\"Sebep\",\"value\":\"" + escapeJson(punishment.getReason()) + "\",\"inline\":false}," +
                "{\"name\":\"Tarih\",\"value\":\"" + escapeJson(dateStr) + "\",\"inline\":true}," +
                "{\"name\":\"Bitiş\",\"value\":\"" + escapeJson(expiresStr) + "\",\"inline\":true}," +
                "{\"name\":\"Ceza ID\",\"value\":\"#" + punishment.getId() + "\",\"inline\":true}" +
                "]," +
                "\"footer\":{\"text\":\"HBanSystem Moderasyon • Ardelys\"}," +
                "\"timestamp\":\"" + Instant.ofEpochMilli(punishment.getCreatedAt()).toString() + "\"" +
                "}]" +
                "}";

        enqueue(payload);
    }

    public void sendRevocationEmbed(@NotNull Punishment punishment) {
        if (!enabled) {
            return;
        }

        boolean isBan = punishment.getType().isBan();
        if (isBan && !eventUnban) return;
        if (!isBan && !eventUnmute) return;

        String actionName = isBan ? "Yasak Kaldırıldı" : "Susturma Kaldırıldı";
        String revokedBy = punishment.getRevokedByStaffName() != null ? punishment.getRevokedByStaffName() : "Bilinmiyor";
        String revokeReason = punishment.getRevocationReason() != null ? punishment.getRevocationReason() : "Belirtilmedi";
        String dateStr = DATE_FORMATTER.format(Instant.now());

        String payload = "{" +
                "\"username\":\"" + escapeJson(botName) + "\"," +
                "\"avatar_url\":\"" + escapeJson(avatarUrl) + "\"," +
                "\"embeds\":[{" +
                "\"title\":\"HBanSystem - Ceza Kaldırıldı: " + escapeJson(actionName) + "\"," +
                "\"color\":65280," +
                "\"fields\":[" +
                "{\"name\":\"Oyuncu\",\"value\":\"" + escapeJson(punishment.getTargetName()) + "\",\"inline\":true}," +
                "{\"name\":\"Orijinal Ceza ID\",\"value\":\"#" + punishment.getId() + "\",\"inline\":true}," +
                "{\"name\":\"Kaldıran Yetkili\",\"value\":\"" + escapeJson(revokedBy) + "\",\"inline\":true}," +
                "{\"name\":\"Kaldırma Sebebi\",\"value\":\"" + escapeJson(revokeReason) + "\",\"inline\":false}," +
                "{\"name\":\"Orijinal Sebep\",\"value\":\"" + escapeJson(punishment.getReason()) + "\",\"inline\":false}," +
                "{\"name\":\"Tarih\",\"value\":\"" + escapeJson(dateStr) + "\",\"inline\":true}" +
                "]," +
                "\"footer\":{\"text\":\"HBanSystem Moderasyon • Ardelys\"}," +
                "\"timestamp\":\"" + Instant.now().toString() + "\"" +
                "}]" +
                "}";

        enqueue(payload);
    }

    public void sendSecurityEmbed(@NotNull SecurityEvidence evidence) {
        if (!enabled || !eventSecurity) {
            return;
        }

        String payload = "{" +
                "\"username\":\"" + escapeJson(botName) + "\"," +
                "\"avatar_url\":\"" + escapeJson(avatarUrl) + "\"," +
                "\"embeds\":[{" +
                "\"title\":\"🛡️ HBanSystem Güvenlik Tespiti\"," +
                "\"color\":10038562," +
                "\"fields\":[" +
                "{\"name\":\"Oyuncu\",\"value\":\"" + escapeJson(evidence.playerName()) + "\",\"inline\":true}," +
                "{\"name\":\"Tespit\",\"value\":\"" + escapeJson(evidence.detectionType()) + "\",\"inline\":true}," +
                "{\"name\":\"Seviye\",\"value\":\"" + escapeJson(evidence.level().getDisplayName()) + "\",\"inline\":true}," +
                "{\"name\":\"Güven Oranı\",\"value\":\"%" + evidence.confidenceScore() + "\",\"inline\":true}," +
                "{\"name\":\"Uygulanan Eylem\",\"value\":\"" + escapeJson(evidence.actionTaken().getDisplayName()) + "\",\"inline\":true}," +
                "{\"name\":\"Özet Kanıt\",\"value\":\"" + escapeJson(evidence.explanation()) + "\",\"inline\":false}" +
                "]," +
                "\"footer\":{\"text\":\"HBanSystem Security Shield • Ardelys\"}," +
                "\"timestamp\":\"" + Instant.ofEpochMilli(evidence.timestamp()).toString() + "\"" +
                "}]" +
                "}";

        enqueue(payload);
    }

    private boolean isEventEnabled(@NotNull PunishmentType type) {
        return switch (type) {
            case BAN, TEMPBAN -> eventBan;
            case IP_BAN, TEMP_IP_BAN -> eventBan;
            case MUTE, TEMP_MUTE -> eventMute;
            case KICK -> eventKick;
            case WARN -> eventWarn;
        };
    }

    private void enqueue(@NotNull String payload) {
        if (!queue.offer(payload)) {
            logger.warning("[HBanSystem Discord] Webhook kuyruğu dolu, mesaj atlandı.");
        }
    }

    private String escapeJson(@Nullable String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
