package com.ardelys.hbansystem.manager.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class DetectionContext {

    private static final int MAX_EVIDENCES = 50;

    private final UUID playerUuid;
    private final String playerName;
    private final Deque<DetectionEvidence> evidences = new ConcurrentLinkedDeque<>();
    private final Set<String> compatibilityFlags = ConcurrentHashMap.newKeySet();

    private volatile String clientBrand = "unknown";
    private volatile long lastActivityTimestamp = System.currentTimeMillis();
    private volatile long lastDetectionTimestamp = 0L;
    private volatile int cachedConfidence = 0;

    public DetectionContext(@NotNull UUID playerUuid, @NotNull String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
    }

    @NotNull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @NotNull
    public String getPlayerName() {
        return playerName;
    }

    @NotNull
    public String getClientBrand() {
        return clientBrand;
    }

    public void setClientBrand(@Nullable String clientBrand) {
        if (clientBrand != null) {
            this.clientBrand = clientBrand;
        }
    }

    public void addCompatibilityFlag(@NotNull String flag) {
        compatibilityFlags.add(flag.toLowerCase(Locale.ROOT));
    }

    public boolean hasCompatibilityFlag(@NotNull String flag) {
        return compatibilityFlags.contains(flag.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Set<String> getCompatibilityFlags() {
        return Collections.unmodifiableSet(compatibilityFlags);
    }

    public void addEvidence(@NotNull DetectionEvidence evidence) {
        lastDetectionTimestamp = evidence.timestamp();
        lastActivityTimestamp = evidence.timestamp();

        evidences.addLast(evidence);
        while (evidences.size() > MAX_EVIDENCES) {
            evidences.pollFirst();
        }
    }

    @NotNull
    public List<DetectionEvidence> getActiveEvidences(long windowMillis) {
        long cutoff = System.currentTimeMillis() - windowMillis;
        List<DetectionEvidence> active = new ArrayList<>();
        for (DetectionEvidence ev : evidences) {
            if (ev.timestamp() >= cutoff) {
                active.add(ev);
            }
        }
        return active;
    }

    @NotNull
    public List<DetectionEvidence> getAllEvidences() {
        return new ArrayList<>(evidences);
    }

    public long getLastDetectionTimestamp() {
        return lastDetectionTimestamp;
    }

    public long getLastActivityTimestamp() {
        return lastActivityTimestamp;
    }

    public void updateActivity() {
        this.lastActivityTimestamp = System.currentTimeMillis();
    }

    public int getCachedConfidence() {
        return cachedConfidence;
    }

    public void setCachedConfidence(int cachedConfidence) {
        this.cachedConfidence = cachedConfidence;
    }

    public void clear() {
        evidences.clear();
        compatibilityFlags.clear();
        cachedConfidence = 0;
    }
}
