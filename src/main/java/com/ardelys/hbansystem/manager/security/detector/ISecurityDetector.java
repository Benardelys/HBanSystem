package com.ardelys.hbansystem.manager.security.detector;

import org.jetbrains.annotations.NotNull;

public interface ISecurityDetector {

    @NotNull
    String getName();

    @NotNull
    String getDescription();

    default boolean isEnabled() {
        return true;
    }

    default void setEnabled(boolean enabled) {}

    default int getErrorCount() {
        return 0;
    }

    default int getTotalEventsProcessed() {
        return 0;
    }

    default int getTotalDetections() {
        return 0;
    }

    default long getLastExecutionDurationNanos() {
        return 0L;
    }

    default long getLastDetectionTimestamp() {
        return 0L;
    }

    default boolean isHealthy() {
        return true;
    }

    default void resetHealth() {}
}
