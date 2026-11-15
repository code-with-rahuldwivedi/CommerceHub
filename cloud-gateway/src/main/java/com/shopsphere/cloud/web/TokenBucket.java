package com.shopsphere.cloud.web;

/** Minimal thread-safe token bucket: capacity = per-minute limit, refilled continuously. */
final class TokenBucket {

    private final double capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastNanos;

    TokenBucket(int perMinute, long nowNanos) {
        this.capacity = perMinute;
        this.tokens = perMinute;
        this.refillPerNano = perMinute / 60_000_000_000.0;
        this.lastNanos = nowNanos;
    }

    /** @return 0 if a token was taken, otherwise the number of seconds (>= 1) until one is available. */
    synchronized long tryAcquire(long nowNanos) {
        tokens = Math.min(capacity, tokens + (nowNanos - lastNanos) * refillPerNano);
        lastNanos = nowNanos;
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return 0;
        }
        double nanosNeeded = (1.0 - tokens) / refillPerNano;
        return Math.max(1L, (long) Math.ceil(nanosNeeded / 1_000_000_000.0));
    }
}
