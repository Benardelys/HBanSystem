package com.ardelys.hbansystem.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record WebConfig(
        boolean enabled,
        boolean apiEnabled,
        @NotNull String host,
        int port,
        @NotNull String basePath,
        @NotNull String apiKey,
        @NotNull String hmacSecret,
        @NotNull String previousHmacSecret,
        boolean requireHmac,
        boolean rateLimitEnabled,
        int requestsPerMinute,
        int authenticatedRequestsPerMinute,
        int maximumLimit,
        int maxBodyBytes,
        int maxQueryChars,
        boolean corsEnabled,
        @NotNull List<String> allowedOrigins,
        boolean compressionEnabled,
        int compressionMinBytes,
        boolean syncEnabled,
        @NotNull String syncEndpointUrl,
        @NotNull String syncApiKey,
        @NotNull String syncHmacSecret,
        int syncQueueCapacity,
        boolean syncBatchEnabled,
        int syncBatchMaxSize,
        int syncConnectTimeoutMs,
        int syncReadTimeoutMs,
        int syncMaxRetries,
        int syncRetryBackoffBaseMs,
        boolean cacheEnabled,
        int cacheDurationSeconds,
        boolean showReason,
        boolean showDuration,
        boolean showStaff,
        boolean showIp,
        boolean showUuid,
        boolean showPunishmentId,
        boolean showSecurityEvidence
) {
    public static WebConfig fromConfig(@NotNull FileConfiguration config) {
        boolean enabled = config.getBoolean("web.enabled", false);
        boolean apiEnabled = config.getBoolean("web.api.enabled", true);
        String host = config.getString("web.api.host", "127.0.0.1");
        int port = config.getInt("web.api.port", 8080);
        String basePath = config.getString("web.api.base-path", "/api/hbansystem");
        String apiKey = config.getString("web.api.api-key", "CHANGE_ME");
        String hmacSecret = config.getString("web.api.hmac-secret", "CHANGE_ME");
        String previousHmacSecret = config.getString("web.api.previous-hmac-secret", "");
        boolean requireHmac = config.getBoolean("web.api.require-hmac", false);
        boolean rateLimitEnabled = config.getBoolean("web.api.rate-limit.enabled", true);
        int requestsPerMinute = config.getInt("web.api.rate-limit.requests-per-minute", 120);
        int authenticatedRequestsPerMinute = config.getInt("web.api.rate-limit.authenticated-requests-per-minute", 600);
        int maximumLimit = config.getInt("web.api.maximum-limit", 100);
        int maxBodyBytes = config.getInt("web.api.max-body-bytes", 65536);
        int maxQueryChars = config.getInt("web.api.max-query-chars", 2048);
        boolean corsEnabled = config.getBoolean("web.api.cors.enabled", true);
        List<String> allowedOrigins = config.getStringList("web.api.cors.allowed-origins");
        if (allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost", "https://localhost");
        }
        boolean compressionEnabled = config.getBoolean("web.api.compression.enabled", true);
        int compressionMinBytes = config.getInt("web.api.compression.min-bytes", 512);

        boolean syncEnabled = config.getBoolean("web.outbound-sync.enabled", false);
        String syncEndpointUrl = config.getString("web.outbound-sync.endpoint-url", "");
        String syncApiKey = config.getString("web.outbound-sync.api-key", "");
        String syncHmacSecret = config.getString("web.outbound-sync.hmac-secret", "");
        int syncQueueCapacity = config.getInt("web.outbound-sync.queue-capacity", 1000);
        boolean syncBatchEnabled = config.getBoolean("web.outbound-sync.batch-enabled", true);
        int syncBatchMaxSize = config.getInt("web.outbound-sync.batch-max-size", 25);
        int syncConnectTimeoutMs = config.getInt("web.outbound-sync.connect-timeout-ms", 2000);
        int syncReadTimeoutMs = config.getInt("web.outbound-sync.read-timeout-ms", 3000);
        int syncMaxRetries = config.getInt("web.outbound-sync.max-retries", 3);
        int syncRetryBackoffBaseMs = config.getInt("web.outbound-sync.retry-backoff-base-ms", 1000);

        boolean cacheEnabled = config.getBoolean("web.cache.enabled", true);
        int cacheDurationSeconds = config.getInt("web.cache.duration-seconds", 10);

        boolean showReason = config.getBoolean("web.public.show-reason", true);
        boolean showDuration = config.getBoolean("web.public.show-duration", true);
        boolean showStaff = config.getBoolean("web.public.show-staff", true);
        boolean showIp = config.getBoolean("web.public.show-ip", false);
        boolean showUuid = config.getBoolean("web.public.show-uuid", false);
        boolean showPunishmentId = config.getBoolean("web.public.show-punishment-id", false);
        boolean showSecurityEvidence = config.getBoolean("web.public.show-security-evidence", false);

        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (basePath.endsWith("/") && basePath.length() > 1) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }

        return new WebConfig(
                enabled,
                apiEnabled,
                host,
                port,
                basePath,
                apiKey,
                hmacSecret,
                previousHmacSecret,
                requireHmac,
                rateLimitEnabled,
                requestsPerMinute,
                authenticatedRequestsPerMinute,
                maximumLimit,
                maxBodyBytes,
                maxQueryChars,
                corsEnabled,
                allowedOrigins,
                compressionEnabled,
                compressionMinBytes,
                syncEnabled,
                syncEndpointUrl,
                syncApiKey,
                syncHmacSecret,
                syncQueueCapacity,
                syncBatchEnabled,
                syncBatchMaxSize,
                syncConnectTimeoutMs,
                syncReadTimeoutMs,
                syncMaxRetries,
                syncRetryBackoffBaseMs,
                cacheEnabled,
                cacheDurationSeconds,
                showReason,
                showDuration,
                showStaff,
                showIp,
                showUuid,
                showPunishmentId,
                showSecurityEvidence
        );
    }
}
