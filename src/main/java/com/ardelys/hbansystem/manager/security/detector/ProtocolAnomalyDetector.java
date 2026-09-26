package com.ardelys.hbansystem.manager.security.detector;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtocolAnomalyDetector implements ISecurityDetector {

    private final Map<UUID, Long> lastPayloadTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> payloadBurstCount = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public String getName() {
        return "ProtocolAnomalyDetector";
    }

    @Override
    @NotNull
    public String getDescription() {
        return "İmkansız paket frekanslarını ve protokol manipülasyonlarını analiz eder.";
    }

    
    public int analyzePayloadRate(@NotNull UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastPayloadTime.put(uuid, now);

        if (last == null || (now - last) > 1000L) {
            payloadBurstCount.put(uuid, 1);
            return 0;
        }

        int count = payloadBurstCount.merge(uuid, 1, Integer::sum);
        if (count > 50) {
            return 92;
        } else if (count > 25) {
            return 60;
        }

        return 0;
    }

    public void cleanup(@NotNull UUID uuid) {
        lastPayloadTime.remove(uuid);
        payloadBurstCount.remove(uuid);
    }
}
