package com.ardelys.hbansystem.manager.security;

import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class ConfidenceManager {

    private final long slidingWindowMillis;
    private final long decayHalfLifeMillis;

    public ConfidenceManager(long slidingWindowMillis, long decayHalfLifeMillis) {
        this.slidingWindowMillis = slidingWindowMillis > 0 ? slidingWindowMillis : 60_000L;
        this.decayHalfLifeMillis = decayHalfLifeMillis > 0 ? decayHalfLifeMillis : 30_000L;
    }

    
    public int calculateConfidence(@NotNull DetectionContext context) {
        List<DetectionEvidence> activeEvidences = context.getActiveEvidences(slidingWindowMillis);
        if (activeEvidences.isEmpty()) {
            return 0;
        }

        Map<String, List<DetectionEvidence>> byDetector = new HashMap<>();
        for (DetectionEvidence ev : activeEvidences) {
            byDetector.computeIfAbsent(ev.detector(), k -> new ArrayList<>()).add(ev);
        }

        double rawScore = 0.0;
        boolean hasCriticalVerified = false;

        for (Map.Entry<String, List<DetectionEvidence>> entry : byDetector.entrySet()) {
            List<DetectionEvidence> list = entry.getValue();
            list.sort((a, b) -> Integer.compare(b.weight(), a.weight()));

            double detectorScore = 0.0;
            double factor = 1.0;

            for (DetectionEvidence ev : list) {
                if (ev.weight() >= 50 && ev.reliability() >= 0.95) {
                    hasCriticalVerified = true;
                }
                detectorScore += (ev.weight() * ev.reliability() * factor);
                factor *= 0.4;
            }

            rawScore += detectorScore;
        }

        int uniqueDetectors = byDetector.size();
        if (uniqueDetectors >= 4) {
            rawScore += 35.0;
        } else if (uniqueDetectors == 3) {
            rawScore += 25.0;
        } else if (uniqueDetectors == 2) {
            rawScore += 10.0;
        }

        long timeSinceLast = System.currentTimeMillis() - context.getLastDetectionTimestamp();
        if (timeSinceLast > 10_000L) { 
            double periods = (double) (timeSinceLast - 10_000L) / (double) decayHalfLifeMillis;
            double decayMultiplier = Math.pow(0.5, periods);
            rawScore *= decayMultiplier;
        }

        if (uniqueDetectors == 1 && !hasCriticalVerified) {
            rawScore = Math.min(rawScore, 58.0);
        }

        int finalScore = (int) Math.round(Math.max(0.0, Math.min(100.0, rawScore)));
        context.setCachedConfidence(finalScore);
        return finalScore;
    }

    @NotNull
    public SecurityDetectionLevel getDetectionLevel(int confidence) {
        return SecurityDetectionLevel.fromConfidence(confidence);
    }
}
