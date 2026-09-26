package com.ardelys.hbansystem.manager.security.detector;

import com.ardelys.hbansystem.manager.security.DetectionContext;
import com.ardelys.hbansystem.manager.security.DetectionEvidence;
import com.ardelys.hbansystem.manager.security.DetectionResult;
import com.ardelys.hbansystem.model.SecurityAction;
import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class BehaviorDetector extends AbstractSecurityDetector {

    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    public BehaviorDetector(@NotNull Logger logger) {
        super("BehaviorDetector", "İmkansız durum geçişlerini ve davranış anomalilerini izler.", logger);
    }

    public void onPlayerJoin(@NotNull UUID uuid) {
        joinTimes.put(uuid, System.currentTimeMillis());
    }

    @NotNull
    public DetectionResult checkInstantAction(@NotNull DetectionContext context, @NotNull String actionType) {
        if (!isEnabled()) {
            return DetectionResult.safe();
        }

        long start = System.nanoTime();
        UUID uuid = context.getPlayerUuid();
        try {
            Long join = joinTimes.get(uuid);
            if (join != null) {
                long diff = System.currentTimeMillis() - join;
                if (diff < 50 && (actionType.contains("Block") || actionType.contains("Inventory") || actionType.contains("Attack"))) {
                    DetectionEvidence evidence = DetectionEvidence.builder()
                            .detector(getName())
                            .category("InstantWorldAction")
                            .weight(20)
                            .severity(SecurityDetectionLevel.LOW)
                            .evidence("Giriş anından 50ms sonra anında aksiyon paketi: " + actionType)
                            .reliability(0.70)
                            .recommendedAction(SecurityAction.LOG)
                            .build();

                    recordExecution(start, true);
                    return DetectionResult.suspicious(evidence);
                }
            }

            recordExecution(start, false);
            return DetectionResult.safe();
        } catch (Throwable t) {
            recordError(t);
            return DetectionResult.safe();
        }
    }

    public void cleanup(@NotNull UUID uuid) {
        joinTimes.remove(uuid);
    }
}
