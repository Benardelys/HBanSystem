package com.ardelys.hbansystem.manager.security.detector;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractSecurityDetector implements ISecurityDetector {

    private static final int MAX_ALLOWABLE_ERRORS = 5;

    private final String name;
    private final String description;
    private final Logger logger;

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicInteger totalEvents = new AtomicInteger(0);
    private final AtomicInteger totalDetections = new AtomicInteger(0);
    private final AtomicLong lastDurationNanos = new AtomicLong(0);
    private final AtomicLong lastDetectionTime = new AtomicLong(0);

    public AbstractSecurityDetector(@NotNull String name, @NotNull String description, @NotNull Logger logger) {
        this.name = name;
        this.description = description;
        this.logger = logger;
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isEnabled() {
        return enabled.get() && isHealthy();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    @Override
    public int getErrorCount() {
        return errorCount.get();
    }

    @Override
    public int getTotalEventsProcessed() {
        return totalEvents.get();
    }

    @Override
    public int getTotalDetections() {
        return totalDetections.get();
    }

    @Override
    public long getLastExecutionDurationNanos() {
        return lastDurationNanos.get();
    }

    @Override
    public long getLastDetectionTimestamp() {
        return lastDetectionTime.get();
    }

    @Override
    public boolean isHealthy() {
        return errorCount.get() < MAX_ALLOWABLE_ERRORS;
    }

    @Override
    public void resetHealth() {
        errorCount.set(0);
        enabled.set(true);
    }

    protected void recordExecution(long startNanos, boolean detected) {
        long duration = System.nanoTime() - startNanos;
        lastDurationNanos.set(duration);
        totalEvents.incrementAndGet();
        if (detected) {
            totalDetections.incrementAndGet();
            lastDetectionTime.set(System.currentTimeMillis());
        }
    }

    public void recordError(@NotNull Throwable throwable) {
        int count = errorCount.incrementAndGet();
        logger.log(Level.WARNING, "[HBanSystem Security] " + name + " analiz sırasında hata fırlattı (#" + count + "): " + throwable.getMessage(), throwable);
        if (count >= MAX_ALLOWABLE_ERRORS) {
            enabled.set(false);
            logger.severe("[HBanSystem Security] " + name + " art arda çok fazla hata verdiği için güvenlik amacıyla otomatik olarak devre dışı bırakıldı!");
        }
    }
}
