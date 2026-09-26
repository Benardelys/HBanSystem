package com.ardelys.hbansystem.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RateLimiter {

    private final boolean enabled;
    private final int publicMaxRequests;
    private final int authenticatedMaxRequests;
    private final Cache<String, RequestBucket> buckets;

    public RateLimiter(boolean enabled, int publicMaxRequests, int authenticatedMaxRequests) {
        this.enabled = enabled;
        this.publicMaxRequests = Math.max(1, publicMaxRequests);
        this.authenticatedMaxRequests = Math.max(publicMaxRequests, authenticatedMaxRequests);
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    public boolean allowRequest(@NotNull String clientIp, boolean authenticated) {
        if (!enabled) {
            return true;
        }

        int limit = authenticated ? authenticatedMaxRequests : publicMaxRequests;
        long now = System.currentTimeMillis();
        RequestBucket bucket = buckets.get(clientIp, k -> new RequestBucket(now));

        synchronized (bucket) {
            if (now - bucket.windowStart >= 60_000L) {
                bucket.windowStart = now;
                bucket.count.set(1);
                return true;
            }

            return bucket.count.incrementAndGet() <= limit;
        }
    }

    public void clear() {
        buckets.invalidateAll();
    }

    private static class RequestBucket {
        private volatile long windowStart;
        private final AtomicInteger count;

        public RequestBucket(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(0);
        }
    }
}
