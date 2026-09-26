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

public final class PacketAnomalyDetector extends AbstractSecurityDetector {

    private static final int MAX_PAYLOAD_BYTES = 32767;
    private static final int FLOOD_BURST_THRESHOLD = 50;

    private final Map<UUID, Long> lastWindowStart = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> packetCountInWindow = new ConcurrentHashMap<>();

    public PacketAnomalyDetector(@NotNull Logger logger) {
        super("PacketAnomalyDetector", "Aşırı paket frekanslarını ve anormal yük boyutlarını denetler.", logger);
    }

    @NotNull
    public DetectionResult analyzePayloadPacket(@NotNull DetectionContext context, int byteLength) {
        if (!isEnabled()) {
            return DetectionResult.safe();
        }

        long start = System.nanoTime();
        UUID uuid = context.getPlayerUuid();

        try {
            if (byteLength > MAX_PAYLOAD_BYTES) {
                DetectionEvidence evidence = DetectionEvidence.builder()
                        .detector(getName())
                        .category("PayloadOverflow")
                        .weight(35)
                        .severity(SecurityDetectionLevel.HIGH)
                        .evidence("İzin verilen maksimum yük boyutu aşıldı (" + byteLength + " bayt)")
                        .reliability(0.90)
                        .recommendedAction(SecurityAction.KICK)
                        .build();

                recordExecution(start, true);
                return DetectionResult.suspicious(evidence);
            }

            long now = System.currentTimeMillis();
            Long windowStart = lastWindowStart.putIfAbsent(uuid, now);
            if (windowStart == null) {
                windowStart = now;
            }

            if (now - windowStart > 1000L) {
                lastWindowStart.put(uuid, now);
                packetCountInWindow.put(uuid, 1);
            } else {
                int count = packetCountInWindow.merge(uuid, 1, Integer::sum);
                if (count > FLOOD_BURST_THRESHOLD) {
                    DetectionEvidence evidence = DetectionEvidence.builder()
                            .detector(getName())
                            .category("PayloadFlooding")
                            .weight(30)
                            .severity(SecurityDetectionLevel.HIGH)
                            .evidence("Saniyede " + count + " adet özel yük paketi gönderildi (Eşik: " + FLOOD_BURST_THRESHOLD + ")")
                            .reliability(0.85)
                            .recommendedAction(SecurityAction.STAFF_ALERT)
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
        lastWindowStart.remove(uuid);
        packetCountInWindow.remove(uuid);
    }
}
