package com.ardelys.hbansystem.web.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public final class ReplayProtection {

    private final long maxSkewSeconds;
    private final Cache<String, Boolean> seenIdentifiers;

    public ReplayProtection(long maxSkewSeconds, long maxCacheEntries) {
        this.maxSkewSeconds = Math.max(10, maxSkewSeconds);
        this.seenIdentifiers = Caffeine.newBuilder()
                .expireAfterWrite(this.maxSkewSeconds * 2, TimeUnit.SECONDS)
                .maximumSize(Math.max(1000, maxCacheEntries))
                .build();
    }

    public boolean validate(long timestampSeconds, @Nullable String nonce, @Nullable String requestId) {
        long current = Instant.now().getEpochSecond();
        if (Math.abs(current - timestampSeconds) > maxSkewSeconds) {
            return false;
        }

        if (nonce != null && !nonce.isBlank()) {
            String nonceKey = "nonce:" + nonce;
            if (seenIdentifiers.asMap().putIfAbsent(nonceKey, Boolean.TRUE) != null) {
                return false;
            }
        }

        if (requestId != null && !requestId.isBlank()) {
            String requestKey = "req:" + requestId;
            if (seenIdentifiers.asMap().putIfAbsent(requestKey, Boolean.TRUE) != null) {
                return false;
            }
        }

        return true;
    }

    public void clear() {
        seenIdentifiers.invalidateAll();
    }
}
