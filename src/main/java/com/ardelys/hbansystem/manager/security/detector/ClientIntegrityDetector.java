package com.ardelys.hbansystem.manager.security.detector;

import com.ardelys.hbansystem.config.SecuritySettings;
import com.ardelys.hbansystem.manager.security.DetectionContext;
import com.ardelys.hbansystem.manager.security.DetectionEvidence;
import com.ardelys.hbansystem.manager.security.DetectionResult;
import com.ardelys.hbansystem.model.SecurityAction;
import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class ClientIntegrityDetector extends AbstractSecurityDetector {

    private static final Set<String> KNOWN_EXPLICIT_CHEATS = Set.of(
            "wurst", "liquidbounce", "meteor", "aristois", "bleachhack",
            "vape", "zeroday", "sigma", "ares", "rusherhack", "future"
    );

    private static final Set<String> COMMON_LEGIT_BRANDS = Set.of(
            "vanilla", "fabric", "forge", "neoforge", "optifine", "lunarclient",
            "badlion", "feather", "sodium", "iris", "essential", "geyser",
            "viaversion", "labymod", "quilt", "bungeecord", "velocity"
    );

    public ClientIntegrityDetector(@NotNull Logger logger) {
        super("ClientIntegrityDetector", "İstemci marka bütünlüğünü denetler ve meşru istemcileri korur.", logger);
    }

    @NotNull
    public DetectionResult analyzeBrand(
            @NotNull DetectionContext context,
            @Nullable String rawBrand,
            @NotNull SecuritySettings settings
    ) {
        if (!isEnabled()) {
            return DetectionResult.safe();
        }

        long start = System.nanoTime();
        try {
            if (rawBrand == null || rawBrand.isBlank()) {
                recordExecution(start, false);
                return DetectionResult.safe();
            }

            context.setClientBrand(rawBrand);
            String lower = rawBrand.toLowerCase(Locale.ROOT).trim();

            for (String legit : COMMON_LEGIT_BRANDS) {
                if (lower.contains(legit)) {
                    context.addCompatibilityFlag(legit);
                }
            }

            if (rawBrand.contains("\u0000") || rawBrand.contains("\r") || rawBrand.length() > 100) {
                DetectionEvidence evidence = DetectionEvidence.builder()
                        .detector(getName())
                        .category("BrandExploitPayload")
                        .weight(40)
                        .severity(SecurityDetectionLevel.HIGH)
                        .evidence("İstemci marka dizesinde kontrol karakterleri veya aşırı uzunluk: " + rawBrand.length())
                        .reliability(0.92)
                        .recommendedAction(SecurityAction.KICK)
                        .build();

                recordExecution(start, true);
                return DetectionResult.suspicious(evidence);
            }

            for (String cheat : KNOWN_EXPLICIT_CHEATS) {
                if (lower.contains(cheat)) {
                    DetectionEvidence evidence = DetectionEvidence.builder()
                            .detector(getName())
                            .category("KnownCheatBrand")
                            .weight(50) 
                            .severity(SecurityDetectionLevel.CRITICAL)
                            .evidence("Bilinen hile istemci markası tespit edildi: " + rawBrand)
                            .reliability(0.98)
                            .recommendedAction(SecurityAction.TEMPBAN)
                            .build();

                    recordExecution(start, true);
                    return DetectionResult.suspicious(evidence);
                }
            }

            if (settings.isBrandWhitelisted(lower)) {
                recordExecution(start, false);
                return DetectionResult.safe();
            }

            recordExecution(start, false);
            return DetectionResult.safe();
        } catch (Throwable t) {
            recordError(t);
            return DetectionResult.safe();
        }
    }
}
