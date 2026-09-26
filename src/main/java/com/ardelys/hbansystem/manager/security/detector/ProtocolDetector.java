package com.ardelys.hbansystem.manager.security.detector;

import com.ardelys.hbansystem.manager.security.DetectionContext;
import com.ardelys.hbansystem.manager.security.DetectionEvidence;
import com.ardelys.hbansystem.manager.security.DetectionResult;
import com.ardelys.hbansystem.model.SecurityAction;
import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public final class ProtocolDetector extends AbstractSecurityDetector {

    public ProtocolDetector(@NotNull Logger logger) {
        super("ProtocolDetector", "Protokol tutarlılığını ve bağlantı aşamalarını denetler.", logger);
    }

    @NotNull
    public DetectionResult checkProtocolSequence(@NotNull DetectionContext context, @NotNull String packetType, boolean playerSpawned) {
        if (!isEnabled()) {
            return DetectionResult.safe();
        }

        long start = System.nanoTime();
        try {
            if (!playerSpawned && (packetType.contains("Interact") || packetType.contains("Attack") || packetType.contains("Dig"))) {
                DetectionEvidence evidence = DetectionEvidence.builder()
                        .detector(getName())
                        .category("ProtocolTiming")
                        .weight(30)
                        .severity(SecurityDetectionLevel.HIGH)
                        .evidence("Oyuncu dünyaya doğmadan etkileşim paketi gönderdi: " + packetType)
                        .reliability(0.85)
                        .recommendedAction(SecurityAction.STAFF_ALERT)
                        .build();

                recordExecution(start, true);
                return DetectionResult.suspicious(evidence);
            }

            recordExecution(start, false);
            return DetectionResult.safe();
        } catch (Throwable t) {
            recordError(t);
            return DetectionResult.safe();
        }
    }
}
