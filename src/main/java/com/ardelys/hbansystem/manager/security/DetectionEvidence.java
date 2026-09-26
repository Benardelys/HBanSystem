package com.ardelys.hbansystem.manager.security;

import com.ardelys.hbansystem.model.SecurityAction;
import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record DetectionEvidence(
        @NotNull String detector,
        @NotNull String category,
        int weight,
        @NotNull SecurityDetectionLevel severity,
        @NotNull String evidence,
        long timestamp,
        double reliability,
        @NotNull SecurityAction recommendedAction
) {
    public DetectionEvidence {
        Objects.requireNonNull(detector, "detector cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(evidence, "evidence cannot be null");
        Objects.requireNonNull(recommendedAction, "recommendedAction cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String detector = "Unknown";
        private String category = "General";
        private int weight = 15;
        private SecurityDetectionLevel severity = SecurityDetectionLevel.LOW;
        private String evidence = "";
        private long timestamp = System.currentTimeMillis();
        private double reliability = 0.8;
        private SecurityAction recommendedAction = SecurityAction.LOG;

        public Builder detector(@NotNull String detector) {
            this.detector = detector;
            return this;
        }

        public Builder category(@NotNull String category) {
            this.category = category;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder severity(@NotNull SecurityDetectionLevel severity) {
            this.severity = severity;
            return this;
        }

        public Builder evidence(@NotNull String evidence) {
            this.evidence = evidence;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder reliability(double reliability) {
            this.reliability = reliability;
            return this;
        }

        public Builder recommendedAction(@NotNull SecurityAction recommendedAction) {
            this.recommendedAction = recommendedAction;
            return this;
        }

        public DetectionEvidence build() {
            return new DetectionEvidence(detector, category, weight, severity, evidence, timestamp, reliability, recommendedAction);
        }
    }
}
