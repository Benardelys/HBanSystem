package com.ardelys.hbansystem.manager.security.detector;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

public final class PayloadChannelDetector implements ISecurityDetector {

    private static final Set<String> KNOWN_MALICIOUS_CHANNELS = Set.of(
            "wurst:internal", "wurst:gui", "meteor:channel", "meteor:exploit",
            "liquidbounce:net", "aristois:channel", "baritone:channel",
            "bleach:hack", "vape:injector", "zeroday:payload", "sigma:channel",
            "ares:client", "rusherhack:channel", "future:channel", "injected:client"
    );

    private static final Set<String> LEGITIMATE_PREFIXES = Set.of(
            "minecraft:", "fabric:", "fabric-", "forge:", "fml:", "neoforge:",
            "lunarclient:", "badlion:", "feather:", "essential:", "viaversion:",
            "geyser:", "floodgate:", "labymod:", "bungeecord:", "velocity:"
    );

    @Override
    @NotNull
    public String getName() {
        return "PayloadChannelDetector";
    }

    @Override
    @NotNull
    public String getDescription() {
        return "Tespit edilen şüpheli eklenti mesaj kanallarını ve enjekte edilmiş protokolleri analiz eder.";
    }

    
    public int analyzeChannel(@NotNull String channelName) {
        if (channelName.isBlank()) {
            return 0;
        }

        String lower = channelName.toLowerCase(Locale.ROOT).trim();

        if (KNOWN_MALICIOUS_CHANNELS.contains(lower)) {
            return 98;
        }

        for (String malicious : KNOWN_MALICIOUS_CHANNELS) {
            if (lower.contains(malicious)) {
                return 95;
            }
        }

        if (lower.contains("cheat") || lower.contains("exploit") || lower.contains("hack") || lower.contains("injector")) {
            return 85;
        }

        for (String prefix : LEGITIMATE_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return 0;
            }
        }

        if (channelName.length() > 64 || channelName.contains("\u0000") || channelName.contains("\n")) {
            return 90;
        }

        return 0;
    }
}
