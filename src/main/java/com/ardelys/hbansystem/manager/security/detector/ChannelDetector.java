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

public final class ChannelDetector extends AbstractSecurityDetector {

    private static final int MAX_CHANNEL_LENGTH = 64;

    private static final Set<String> RESERVED_LEGIT_PREFIXES = Set.of(
            "minecraft:", "fabric:", "fabric-", "forge:", "fml:", "neoforge:",
            "lunarclient:", "badlion:", "feather:", "essential:", "viaversion:",
            "geyser:", "floodgate:", "labymod:", "bungeecord:", "velocity:"
    );

    public ChannelDetector(@NotNull Logger logger) {
        super("ChannelDetector", "Eklenti mesaj kanallarının söz dizimi ve protokol bütünlüğünü doğrular.", logger);
    }

    @NotNull
    public DetectionResult validateChannel(@NotNull DetectionContext context, @NotNull String channel) {
        if (!isEnabled()) {
            return DetectionResult.safe();
        }

        long start = System.nanoTime();
        try {
            if (channel.length() > MAX_CHANNEL_LENGTH) {
                DetectionEvidence evidence = DetectionEvidence.builder()
                        .detector(getName())
                        .category("IllegalChannelLength")
                        .weight(25)
                        .severity(SecurityDetectionLevel.MEDIUM)
                        .evidence("İzin verilen kanal adı uzunluğu aşıldı (" + channel.length() + " karakter)")
                        .reliability(0.80)
                        .recommendedAction(SecurityAction.LOG)
                        .build();

                recordExecution(start, true);
                return DetectionResult.suspicious(evidence);
            }

            if (channel.contains("\u0000") || channel.contains("\r") || channel.contains("\n")) {
                DetectionEvidence evidence = DetectionEvidence.builder()
                        .detector(getName())
                        .category("MalformedChannelName")
                        .weight(40)
                        .severity(SecurityDetectionLevel.HIGH)
                        .evidence("Kanal adında geçersiz kontrol karakterleri tespit edildi.")
                        .reliability(0.95)
                        .recommendedAction(SecurityAction.KICK)
                        .build();

                recordExecution(start, true);
                return DetectionResult.suspicious(evidence);
            }

            String lower = channel.toLowerCase(Locale.ROOT).trim();
            for (String prefix : RESERVED_LEGIT_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    recordExecution(start, false);
                    return DetectionResult.safe();
                }
            }

            if (!channel.contains(":")) {
                DetectionEvidence evidence = DetectionEvidence.builder()
                        .detector(getName())
                        .category("IllegalChannelNamespace")
                        .weight(20)
                        .severity(SecurityDetectionLevel.LOW)
                        .evidence("Modern protokolde kanal adında ad alanı (namespace:value) bulunmuyor: " + channel)
                        .reliability(0.75)
                        .recommendedAction(SecurityAction.LOG)
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
