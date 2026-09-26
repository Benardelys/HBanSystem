package com.ardelys.hbansystem.manager.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record DetectionResult(
        boolean suspicious,
        @Nullable DetectionEvidence evidence
) {
    public static final DetectionResult SAFE = new DetectionResult(false, null);

    public static DetectionResult safe() {
        return SAFE;
    }

    public static DetectionResult suspicious(@NotNull DetectionEvidence evidence) {
        return new DetectionResult(true, evidence);
    }
}
