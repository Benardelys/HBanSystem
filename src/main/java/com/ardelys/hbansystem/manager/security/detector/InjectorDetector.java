package com.ardelys.hbansystem.manager.security.detector;

import com.ardelys.hbansystem.manager.security.DetectionContext;
import com.ardelys.hbansystem.manager.security.DetectionEvidence;
import com.ardelys.hbansystem.manager.security.DetectionResult;
import com.ardelys.hbansystem.model.SecurityAction;
import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class InjectorDetector extends AbstractSecurityDetector {

    private static final Set<String> KNOWN_INJECTOR_CHANNELS = Set.of(
            "wurst:internal", "wurst:gui", "meteor:channel", "meteor:exploit",
            "liquidbounce:net", "aristois:channel", "baritone:channel",
            "bleach:hack", "vape:injector", "zeroday:payload", "sigma:channel",
            "ares:client", "rusherhack:channel", "future:channel", "injected:client"
    );

    public InjectorDetector(@NotNull Logger logger) {
        super("InjectorDetector", "İstemci içi enjeksiyon ve hile protokol yüklerini tespit eder.", logger);
    }

    @NotNull
    public DetectionResult analyzeChannelPayload(@NotNull DetectionContext context, @NotNull String channel) {
        if (!isEnabled()) {
            return DetectionResult.safe();
        }

        long start = System.nanoTime();
        try {
            String lower = channel.toLowerCase(Locale.ROOT).trim();

            for (String malicious : KNOWN_INJECTOR_CHANNELS) {
                if (lower.equals(malicious) || lower.contains(malicious)) {
                    DetectionEvidence evidence = DetectionEvidence.builder()
                            .detector(getName())
                            .category("InjectorPayload")
                            .weight(50) 
                            .severity(SecurityDetectionLevel.CRITICAL)
                            .evidence("Bilinen hile enjeksiyon kanalı kaydedilmeye çalışıldı: " + channel)
                            .reliability(0.98)
                            .recommendedAction(SecurityAction.TEMPBAN)
                            .build();

                    recordExecution(start, true);
                    return DetectionResult.suspicious(evidence);
                }
            }

            if (lower.contains("cheat") || lower.contains("injector") || lower.contains("exploit")) {
                DetectionEvidence evidence = DetectionEvidence.builder()
                        .detector(getName())
                        .category("InjectorKeyword")
                        .weight(30) 
                        .severity(SecurityDetectionLevel.HIGH)
                        .evidence("Şüpheli kanal imzası: " + channel)
                        .reliability(0.85)
                        .recommendedAction(SecurityAction.KICK)
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
