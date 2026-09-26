package com.ardelys.hbansystem.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public final class WebCache {

    private final boolean enabled;
    private final Cache<String, String> cache;

    public WebCache(boolean enabled, int durationSeconds) {
        this.enabled = enabled;
        long ttl = Math.max(1, durationSeconds);
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
    }

    @Nullable
    public String get(@NotNull String key) {
        if (!enabled) {
            return null;
        }
        return cache.getIfPresent(key);
    }

    public void put(@NotNull String key, @NotNull String content) {
        if (!enabled) {
            return;
        }
        cache.put(key, content);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
