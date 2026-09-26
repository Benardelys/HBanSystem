package com.ardelys.hbansystem.manager.security.detector;

import com.ardelys.hbansystem.config.SecuritySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

public final class BrandIntegrityDetector implements ISecurityDetector {

    private static final Set<String> KNOWN_MALICIOUS_BRANDS = Set.of(
            "wurst", "liquidbounce", "meteor", "aristois", "bleachhack",
            "vape", "zeroday", "sigma", "ares", "rusherhack", "future"
    );

    @Override
    @NotNull
    public String getName() {
        return "BrandIntegrityDetector";
    }

    @Override
    @NotNull
    public String getDescription() {
        return "İstemci marka bilgisindeki protokol anomalilerini ve bilinen hile istemcisi izlerini denetler.";
    }

    public int analyzeBrand(@Nullable String rawBrand, @NotNull SecuritySettings settings) {
        if (rawBrand == null || rawBrand.isBlank()) {
            return 0;
        }

        if (rawBrand.contains("\u0000") || rawBrand.contains("\r") || rawBrand.length() > 100) {
            return 95;
        }

        String lower = rawBrand.toLowerCase(Locale.ROOT).trim();

        for (String malicious : KNOWN_MALICIOUS_BRANDS) {
            if (lower.contains(malicious)) {
                return 98;
            }
        }

        if (settings.isBrandWhitelisted(lower)) {
            return 0;
        }

        return 0;
    }
}
