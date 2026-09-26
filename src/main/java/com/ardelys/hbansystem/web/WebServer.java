package com.ardelys.hbansystem.web;

import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.config.WebConfig;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import com.ardelys.hbansystem.web.dto.PunishmentDto;
import com.ardelys.hbansystem.web.security.HmacUtil;
import com.ardelys.hbansystem.web.security.ReplayProtection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public final class WebServer {

    private final ConfigManager configManager;
    private final PunishmentRepository punishmentRepository;
    private final Logger logger;

    private HttpServer server;
    private ExecutorService executor;
    private RateLimiter rateLimiter;
    private WebCache webCache;
    private ReplayProtection replayProtection;
    private boolean running = false;

    public WebServer(
            @NotNull ConfigManager configManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull Logger logger
    ) {
        this.configManager = configManager;
        this.punishmentRepository = punishmentRepository;
        this.logger = logger;
    }

    public synchronized void start() {
        WebConfig config = configManager.getWebConfig();
        if (!config.enabled()) {
            return;
        }

        if (running) {
            stop();
        }

        try {
            this.rateLimiter = new RateLimiter(
                    config.rateLimitEnabled(),
                    config.requestsPerMinute(),
                    config.authenticatedRequestsPerMinute()
            );
            this.webCache = new WebCache(config.cacheEnabled(), config.cacheDurationSeconds());
            this.replayProtection = new ReplayProtection(300, 10_000);

            this.executor = new ThreadPoolExecutor(
                    2,
                    8,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(200),
                    new ThreadFactory() {
                        private int count = 0;
                        @Override
                        public Thread newThread(@NotNull Runnable r) {
                            Thread t = new Thread(r, "HBanSystem-WebWorker-" + (++count));
                            t.setDaemon(true);
                            return t;
                        }
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

            InetSocketAddress address = new InetSocketAddress(config.host(), config.port());
            this.server = HttpServer.create(address, 0);
            this.server.setExecutor(executor);

            this.server.createContext("/bans", new PublicBansHandler());
            this.server.createContext("/", new RootHandler());

            if (config.apiEnabled()) {
                String base = config.basePath();
                this.server.createContext(base + "/bans", new ApiBansHandler());
                this.server.createContext(base + "/mutes", new ApiMutesHandler());
                this.server.createContext(base + "/punishments", new ApiPunishmentsHandler());
                this.server.createContext(base + "/player", new ApiPlayerHandler());
            }

            this.server.start();
            this.running = true;
            logger.info("[HBanSystem] Güvenli Web API http://" + config.host() + ":" + config.port() + " adresinde başlatıldı.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[HBanSystem] Web sunucusu başlatılamadı: " + e.getMessage(), e);
            stop();
        }
    }

    public synchronized void stop() {
        if (!running && server == null) {
            return;
        }

        running = false;
        if (server != null) {
            try {
                server.stop(1);
            } catch (Exception ignored) {}
            server = null;
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            executor = null;
        }

        if (webCache != null) {
            webCache.invalidateAll();
        }
        if (rateLimiter != null) {
            rateLimiter.clear();
        }
        if (replayProtection != null) {
            replayProtection.clear();
        }

        logger.info("[HBanSystem] Web sunucusu durduruldu.");
    }

    public synchronized void reload() {
        WebConfig config = configManager.getWebConfig();
        if (config.enabled()) {
            start();
        } else {
            stop();
        }
    }

    public void invalidateCache() {
        if (webCache != null) {
            webCache.invalidateAll();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private boolean checkPreconditions(HttpExchange exchange, boolean requireApiKey) throws IOException {
        applySecurityHeaders(exchange);
        handleCors(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return false;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"success\":false,\"error\":\"Desteklenmeyen HTTP yöntemi.\"}");
            return false;
        }

        WebConfig config = configManager.getWebConfig();

        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery != null && rawQuery.length() > config.maxQueryChars()) {
            sendJson(exchange, 414, "{\"success\":false,\"error\":\"İstek sorgusu izin verilen sınırı aşıyor.\"}");
            return false;
        }

        String contentLengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLengthHeader != null) {
            try {
                long len = Long.parseLong(contentLengthHeader.trim());
                if (len > config.maxBodyBytes()) {
                    sendJson(exchange, 413, "{\"success\":false,\"error\":\"İstek gövdesi boyutu izin verilen sınırı aşıyor.\"}");
                    return false;
                }
            } catch (NumberFormatException ignored) {}
        }

        String remoteIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        boolean authenticated = false;

        String configuredKey = HmacUtil.resolveSecret(config.apiKey(), "HBANSYSTEM_API_KEY");
        String providedKey = extractApiKey(exchange);

        if (configuredKey != null && !configuredKey.isBlank() && !"CHANGE_ME".equals(configuredKey)) {
            authenticated = configuredKey.equals(providedKey);
        }

        if (requireApiKey && !authenticated) {
            sendJson(exchange, 401, "{\"success\":false,\"error\":\"Yetkisiz erişim: Geçersiz API anahtarı.\"}");
            return false;
        }

        if (rateLimiter != null && !rateLimiter.allowRequest(remoteIp, authenticated)) {
            sendJson(exchange, 429, "{\"success\":false,\"error\":\"İstek limiti aşıldı. Lütfen bekleyin.\"}");
            return false;
        }

        if (requireApiKey && config.requireHmac()) {
            if (!validateInboundHmac(exchange, config)) {
                return false;
            }
        }

        return true;
    }

    private boolean validateInboundHmac(HttpExchange exchange, WebConfig config) throws IOException {
        String timestampStr = exchange.getRequestHeaders().getFirst("X-HBan-Timestamp");
        String nonce = exchange.getRequestHeaders().getFirst("X-HBan-Nonce");
        String signature = exchange.getRequestHeaders().getFirst("X-HBan-Signature");
        String requestId = exchange.getRequestHeaders().getFirst("X-HBan-Request-ID");

        if (timestampStr == null || nonce == null || signature == null) {
            sendJson(exchange, 401, "{\"success\":false,\"error\":\"Eksik HMAC güvenlik başlıkları.\"}");
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr.trim());
        } catch (NumberFormatException e) {
            sendJson(exchange, 401, "{\"success\":false,\"error\":\"Geçersiz zaman damgası.\"}");
            return false;
        }

        if (replayProtection != null && !replayProtection.validate(timestamp, nonce, requestId)) {
            sendJson(exchange, 401, "{\"success\":false,\"error\":\"Güvenlik doğrulaması başarısız (Yeniden oynatma koruması).\"}");
            return false;
        }

        String path = exchange.getRequestURI().getPath();
        String bodyHash = HmacUtil.sha256Hex("");
        String canonical = HmacUtil.buildCanonicalRequest(exchange.getRequestMethod(), path, timestamp, nonce, bodyHash);

        String primarySecret = HmacUtil.resolveSecret(config.hmacSecret(), "HBANSYSTEM_HMAC_SECRET");
        boolean valid = HmacUtil.verifySignature(primarySecret, canonical, signature);

        if (!valid && !config.previousHmacSecret().isBlank()) {
            valid = HmacUtil.verifySignature(config.previousHmacSecret(), canonical, signature);
        }

        if (!valid) {
            sendJson(exchange, 401, "{\"success\":false,\"error\":\"Geçersiz HMAC imzası.\"}");
            return false;
        }

        return true;
    }

    @Nullable
    private String extractApiKey(HttpExchange exchange) {
        String key = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        return query.get("api_key");
    }

    private void applySecurityHeaders(HttpExchange exchange) {
        var h = exchange.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "SAMEORIGIN");
        h.set("Referrer-Policy", "strict-origin-when-cross-origin");

        String forwardedProto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        if ("https".equalsIgnoreCase(forwardedProto)) {
            h.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    private void handleCors(HttpExchange exchange) {
        WebConfig config = configManager.getWebConfig();
        if (!config.corsEnabled()) {
            return;
        }

        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank()) {
            List<String> allowed = config.allowedOrigins();
            if (allowed.contains("*") || allowed.contains(origin.trim())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin.trim());
                exchange.getResponseHeaders().set("Vary", "Origin");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-API-Key, Authorization, X-HBan-Timestamp, X-HBan-Nonce, X-HBan-Signature, X-HBan-Request-ID");
            }
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendResponse(exchange, statusCode, json.getBytes(StandardCharsets.UTF_8), "application/json; charset=UTF-8");
    }

    private void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        sendResponse(exchange, statusCode, html.getBytes(StandardCharsets.UTF_8), "text/html; charset=UTF-8");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);

        WebConfig config = configManager.getWebConfig();
        boolean compress = config.compressionEnabled() && bytes.length >= config.compressionMinBytes();

        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        if (compress && acceptEncoding != null && acceptEncoding.contains("gzip")) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(bytes);
            }
            byte[] compressed = baos.toByteArray();
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(statusCode, compressed.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(compressed);
            }
        } else {
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private Map<String, String> parseQuery(@Nullable String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return map;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    map.put(key, val);
                } else if (!pair.isEmpty()) {
                    map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().set("Location", "/bans");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                sendJson(exchange, 404, "{\"success\":false,\"error\":\"Kaynak bulunamadı.\"}");
            }
        }
    }

    private class PublicBansHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkPreconditions(exchange, false)) {
                return;
            }

            String cacheKey = "html:" + exchange.getRequestURI().toString();
            String cached = webCache != null ? webCache.get(cacheKey) : null;
            if (cached != null) {
                sendHtml(exchange, 200, cached);
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            int banPage = parsePositiveInt(query.get("ban_page"), 1);
            int mutePage = parsePositiveInt(query.get("mute_page"), 1);
            String search = query.get("search");

            WebConfig config = configManager.getWebConfig();
            int limit = 15;

            List<PunishmentType> banTypes = List.of(PunishmentType.BAN, PunishmentType.TEMPBAN, PunishmentType.IP_BAN, PunishmentType.TEMP_IP_BAN);
            List<PunishmentType> muteTypes = List.of(PunishmentType.MUTE, PunishmentType.TEMP_MUTE);

            try {
                CompletableFuture<List<Punishment>> bansFuture = punishmentRepository.getPaged(banTypes, true, search, banPage, limit);
                CompletableFuture<Integer> bansCountFuture = punishmentRepository.count(banTypes, true, search);

                CompletableFuture<List<Punishment>> mutesFuture = punishmentRepository.getPaged(muteTypes, true, search, mutePage, limit);
                CompletableFuture<Integer> mutesCountFuture = punishmentRepository.count(muteTypes, true, search);

                List<PunishmentDto> banDtos = bansFuture.join().stream()
                        .map(p -> PunishmentDto.from(p, config))
                        .toList();
                int totalBans = bansCountFuture.join();
                int totalBanPages = (int) Math.ceil((double) totalBans / limit);

                List<PunishmentDto> muteDtos = mutesFuture.join().stream()
                        .map(p -> PunishmentDto.from(p, config))
                        .toList();
                int totalMutes = mutesCountFuture.join();
                int totalMutePages = (int) Math.ceil((double) totalMutes / limit);

                String html = PublicBansPage.render(
                        banDtos,
                        muteDtos,
                        banPage,
                        totalBanPages,
                        mutePage,
                        totalMutePages,
                        search,
                        configManager.getBrandingName()
                );

                if (webCache != null) {
                    webCache.put(cacheKey, html);
                }

                sendHtml(exchange, 200, html);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[HBanSystem Web] Sayfa yüklenirken hata: " + e.getMessage());
                sendHtml(exchange, 500, "<h1>500 - Sunucu Hatası</h1><p>Ceza kayıtları listelenirken bir hata oluştu.</p>");
            }
        }
    }

    private class ApiBansHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkPreconditions(exchange, true)) return;
            handlePagedPunishments(exchange, List.of(PunishmentType.BAN, PunishmentType.TEMPBAN, PunishmentType.IP_BAN, PunishmentType.TEMP_IP_BAN));
        }
    }

    private class ApiMutesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkPreconditions(exchange, true)) return;
            handlePagedPunishments(exchange, List.of(PunishmentType.MUTE, PunishmentType.TEMP_MUTE));
        }
    }

    private class ApiPunishmentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkPreconditions(exchange, true)) return;
            handlePagedPunishments(exchange, null);
        }
    }

    private void handlePagedPunishments(HttpExchange exchange, @Nullable List<PunishmentType> filterTypes) throws IOException {
        String cacheKey = "api:" + exchange.getRequestURI().toString();
        String cached = webCache != null ? webCache.get(cacheKey) : null;
        if (cached != null) {
            sendJson(exchange, 200, cached);
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        int page = parsePositiveInt(query.get("page"), 1);
        WebConfig config = configManager.getWebConfig();
        int requestedLimit = parsePositiveInt(query.get("limit"), 25);
        int limit = Math.min(requestedLimit, config.maximumLimit());
        String search = query.get("search");
        boolean onlyActive = !"false".equalsIgnoreCase(query.get("active"));

        try {
            CompletableFuture<List<Punishment>> listFuture = punishmentRepository.getPaged(filterTypes, onlyActive, search, page, limit);
            CompletableFuture<Integer> countFuture = punishmentRepository.count(filterTypes, onlyActive, search);

            List<Punishment> punishments = listFuture.join();
            int total = countFuture.join();
            int totalPages = (int) Math.ceil((double) total / limit);

            StringBuilder json = new StringBuilder("{\"success\":true,\"page\":").append(page)
                    .append(",\"limit\":").append(limit)
                    .append(",\"total\":").append(total)
                    .append(",\"total_pages\":").append(totalPages)
                    .append(",\"results\":[");

            for (int i = 0; i < punishments.size(); i++) {
                if (i > 0) json.append(",");
                json.append(PunishmentDto.from(punishments.get(i), config).toJson());
            }
            json.append("]}");

            String result = json.toString();
            if (webCache != null) {
                webCache.put(cacheKey, result);
            }
            sendJson(exchange, 200, result);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[HBanSystem Web] API sorgusu sırasında hata: " + e.getMessage());
            sendJson(exchange, 500, "{\"success\":false,\"error\":\"Veritabanı sorgusu gerçekleştirilemedi.\"}");
        }
    }

    private class ApiPlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkPreconditions(exchange, true)) return;

            String path = exchange.getRequestURI().getPath();
            WebConfig config = configManager.getWebConfig();
            String prefix = config.basePath() + "/player/";

            if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
                sendJson(exchange, 400, "{\"success\":false,\"error\":\"Geçersiz oyuncu kimliği. Kullanım: /api/hbansystem/player/<uuid_veya_isim>\"}");
                return;
            }

            String identifier = path.substring(prefix.length()).trim();
            if (identifier.isEmpty()) {
                sendJson(exchange, 400, "{\"success\":false,\"error\":\"Oyuncu kimliği boş olamaz.\"}");
                return;
            }

            try {
                UUID targetUuid;
                try {
                    targetUuid = UUID.fromString(identifier);
                } catch (IllegalArgumentException e) {
                    targetUuid = null;
                }

                CompletableFuture<List<Punishment>> future;
                if (targetUuid != null) {
                    future = punishmentRepository.findByTargetUuid(targetUuid);
                } else {
                    future = punishmentRepository.getPaged(null, false, identifier, 1, 100);
                }

                List<Punishment> punishments = future.join();
                StringBuilder json = new StringBuilder("{\"success\":true,\"target\":\"")
                        .append(identifier)
                        .append("\",\"count\":").append(punishments.size())
                        .append(",\"punishments\":[");

                for (int i = 0; i < punishments.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(PunishmentDto.from(punishments.get(i), config).toJson());
                }
                json.append("]}");

                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                logger.log(Level.WARNING, "[HBanSystem Web] Oyuncu sorgusu sırasında hata: " + e.getMessage());
                sendJson(exchange, 500, "{\"success\":false,\"error\":\"Oyuncu bilgisi çekilirken hata oluştu.\"}");
            }
        }
    }

    private int parsePositiveInt(@Nullable String val, int def) {
        if (val == null) return def;
        try {
            int parsed = Integer.parseInt(val.trim());
            return parsed > 0 ? parsed : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
