package com.ardelys.hbansystem.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record SecurityEvidence(
        long id,
        @NotNull UUID playerUuid,
        @NotNull String playerName,
        @Nullable String ip,
        @NotNull String detectionType,
        @NotNull SecurityDetectionLevel level,
        int confidenceScore,
        long timestamp,
        @NotNull String serverVersion,
        @Nullable String clientBrand,
        @NotNull String protocolDetails,
        @NotNull String explanation,
        @NotNull SecurityAction actionTaken
) {
    public SecurityEvidence {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(playerName, "playerName cannot be null");
        Objects.requireNonNull(detectionType, "detectionType cannot be null");
        Objects.requireNonNull(level, "level cannot be null");
        Objects.requireNonNull(serverVersion, "serverVersion cannot be null");
        Objects.requireNonNull(protocolDetails, "protocolDetails cannot be null");
        Objects.requireNonNull(explanation, "explanation cannot be null");
        Objects.requireNonNull(actionTaken, "actionTaken cannot be null");
    }

    public SecurityEvidence withId(long newId) {
        return new SecurityEvidence(
                newId,
                this.playerUuid,
                this.playerName,
                this.ip,
                this.detectionType,
                this.level,
                this.confidenceScore,
                this.timestamp,
                this.serverVersion,
                this.clientBrand,
                this.protocolDetails,
                this.explanation,
                this.actionTaken
        );
    }
}
