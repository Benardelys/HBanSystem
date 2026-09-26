package com.ardelys.hbansystem.web.sync;

import com.ardelys.hbansystem.config.WebConfig;
import com.ardelys.hbansystem.web.security.HmacUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WebSyncService {

    private final Logger logger;
    private final BlockingQueue<WebSyncEvent> eventQueue;
    private final ExecutorService workerExecutor;
    private HttpClient httpClient;
    private volatile boolean running = false;

    private boolean enabled;
    private String endpointUrl;
    private String apiKey;
    private String hmacSecret;
    private boolean batchEnabled;
    private int batchMaxSize;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private int maxRetries;
    private int retryBackoffBaseMs;

    public WebSyncService(@NotNull Logger logger, int queueCapacity) {
        this.logger = logger;
        this.eventQueue = new LinkedBlockingQueue<>(Math.max(100, queueCapacity));
        this.workerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HBanSystem-WebSync-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void initialize(@NotNull WebConfig webConfig) {
        this.enabled = webConfig.syncEnabled();
        this.endpointUrl = webConfig.syncEndpointUrl();
        this.apiKey = HmacUtil.resolveSecret(webConfig.syncApiKey(), "HBANSYSTEM_API_KEY");
        this.hmacSecret = HmacUtil.resolveSecret(webConfig.syncHmacSecret(), "HBANSYSTEM_HMAC_SECRET");
        this.batchEnabled = webConfig.syncBatchEnabled();
        this.batchMaxSize = Math.max(1, Math.min(100, webConfig.syncBatchMaxSize()));
        this.connectTimeoutMs = Math.max(500, webConfig.syncConnectTimeoutMs());
        this.readTimeoutMs = Math.max(1000, webConfig.syncReadTimeoutMs());
        this.maxRetries = Math.max(0, Math.min(5, webConfig.syncMaxRetries()));
        this.retryBackoffBaseMs = Math.max(250, webConfig.syncRetryBackoffBaseMs());

        if (!enabled) {
            stopWorker();
            return;
        }

        if (endpointUrl == null || endpointUrl.isBlank()) {
            logger.warning("[HBanSystem WebSync] Web senkronizasyonu aktif ancak endpoint URL yapılandırılmamış!");
            stopWorker();
            return;
        }

        if (!endpointUrl.toLowerCase().startsWith("https://")) {
            logger.severe("[HBanSystem WebSync] Güvenlik Hatası: Web senkronizasyonu yalnızca HTTPS üzerinden çalışabilir! İptal edildi: " + endpointUrl);
            stopWorker();
            return;
        }

        try {
            SSLContext sslContext = SSLContext.getDefault();
            SSLParameters sslParams = sslContext.getDefaultSSLParameters();
            sslParams.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});

            this.httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .sslContext(sslContext)
                    .sslParameters(sslParams)
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            startWorker();
            logger.info("[HBanSystem WebSync] Güvenli HTTPS Web Senkronizasyonu başlatıldı (" + endpointUrl + ")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[HBanSystem WebSync] Güvenli HTTPS istemcisi oluşturulamadı: " + e.getMessage(), e);
            stopWorker();
        }
    }

    public boolean enqueueEvent(@NotNull WebSyncEvent event) {
        if (!enabled || !running) {
            return false;
        }
        boolean accepted = eventQueue.offer(event);
        if (!accepted) {
            logger.warning("[HBanSystem WebSync] Olay kuyruğu dolu! Eski kayıtlar korunurken yeni web senkronizasyon olayı düşürüldü.");
        }
        return accepted;
    }

    private synchronized void startWorker() {
        if (running) {
            return;
        }
        running = true;
        workerExecutor.submit(this::processQueueLoop);
    }

    private synchronized void stopWorker() {
        running = false;
    }

    private void processQueueLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                WebSyncEvent first = eventQueue.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                List<WebSyncEvent> batch = new ArrayList<>();
                batch.add(first);

                if (batchEnabled && batchMaxSize > 1) {
                    eventQueue.drainTo(batch, batchMaxSize - 1);
                }

                sendBatchWithRetry(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[HBanSystem WebSync] Kuyruk işleme sırasında beklenmeyen hata: " + t.getMessage(), t);
            }
        }
    }

    private void sendBatchWithRetry(@NotNull List<WebSyncEvent> events) {
        String jsonPayload = buildBatchJson(events);
        String bodyHash = HmacUtil.sha256Hex(jsonPayload);

        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (IllegalArgumentException e) {
            logger.warning("[HBanSystem WebSync] Geçersiz senkronizasyon URL'si: " + endpointUrl);
            return;
        }

        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? "/" : uri.getRawPath();

        int attempt = 0;
        boolean delivered = false;

        while (attempt <= maxRetries && !delivered && running) {
            attempt++;
            long timestamp = Instant.now().getEpochSecond();
            String nonce = HmacUtil.generateNonce();
            String requestId = UUID.randomUUID().toString();

            String canonical = HmacUtil.buildCanonicalRequest("POST", path, timestamp, nonce, bodyHash);
            String signature = HmacUtil.computeHmac(hmacSecret, canonical);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .header("User-Agent", "HBanSystem-SecureWebSync/1.0")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-HBan-Timestamp", String.valueOf(timestamp))
                    .header("X-HBan-Nonce", nonce)
                    .header("X-HBan-Request-ID", requestId)
                    .header("X-HBan-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            try {
                HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    delivered = true;
                } else {
                    logger.warning("[HBanSystem WebSync] Sunucu HTTP " + response.statusCode() + " döndürdü (Deneme: " + attempt + "/" + (maxRetries + 1) + ")");
                }
            } catch (Exception e) {
                logger.warning("[HBanSystem WebSync] Ağ isteği başarısız oldu (Deneme: " + attempt + "/" + (maxRetries + 1) + "): " + e.getMessage());
            }

            if (!delivered && attempt <= maxRetries) {
                long backoffMs = (long) retryBackoffBaseMs * (1L << (attempt - 1));
                try {
                    Thread.sleep(Math.min(backoffMs, 10_000L));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!delivered) {
            logger.severe("[HBanSystem WebSync] " + events.size() + " adet ceza olayı web sitesine senkronize edilemedi. Tüm denemeler tükendi.");
        }
    }

    @NotNull
    private String buildBatchJson(@NotNull List<WebSyncEvent> events) {
        StringBuilder sb = new StringBuilder("{\"batch_id\":\"")
                .append(UUID.randomUUID())
                .append("\",\"count\":").append(events.size())
                .append(",\"events\":[");

        for (int i = 0; i < events.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(events.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    public synchronized void shutdown() {
        running = false;
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(1500, TimeUnit.MILLISECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
        }
        eventQueue.clear();
        httpClient = null;
    }

    public boolean isRunning() {
        return running;
    }

    public int getQueueSize() {
        return eventQueue.size();
    }
}
