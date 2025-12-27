package io.github.vkunitsyn.ratelimiter;

public class TokenBucket implements RateLimiter {
    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodNanos;
    private long availableTokens;
    private long lastRefillNanos;

    public TokenBucket(long capacity, long refillTokens, long refillPeriodNanos) {
        validateInitialParameters(capacity, refillTokens, refillPeriodNanos);
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriodNanos;
        this.availableTokens = capacity;
    }

    @Override
    public synchronized AcquireResult tryAcquire(long nowNanos, long permits) {
        validatePermits(permits);
        refillTokens(nowNanos);

        if (availableTokens >= permits) {
            availableTokens -= permits;
            return new AcquireResult.Acquired(permits);
        }
        return new AcquireResult.Rejected(retryAfterNanos(nowNanos, permits));
    }

    @Override
    public synchronized long availableTokens(long nowNanos) {
        refillTokens(nowNanos);
        return availableTokens;
    }

    @Override
    public synchronized long retryAfterNanos(long nowNanos, long permits) {
        validatePermits(permits);
        refillTokens(nowNanos);
        return retryAfterNanosInternal(nowNanos, permits);
    }

    private long retryAfterNanosInternal(long nowNanos, long permits) {
        long missingTokens = permits - availableTokens;
        if (missingTokens <= 0) {
            return 0;
        }

        long periodsNeeded = Math.ceilDiv(missingTokens, refillTokens);
        return Utils.saturatedMultiply(periodsNeeded, refillPeriodNanos);
    }

    private void validatePermits(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be positive");
        }
        if (permits > capacity) {
            throw new IllegalArgumentException("Permits exceed bucket capacity");
        }
    }

    private void refillTokens(long nowNanos) {
        if (lastRefillNanos == 0) {
            lastRefillNanos = nowNanos;
            return;
        }
        if (nowNanos <= lastRefillNanos) {
            return;
        }

        long elapsedNanos = nowNanos - lastRefillNanos;
        long periods = elapsedNanos / refillPeriodNanos;
        lastRefillNanos += periods * refillPeriodNanos;

        if (periods > (Long.MAX_VALUE / refillTokens)) {
            availableTokens = capacity;
            return;
        }

        long tokensToAdd = periods * refillTokens;
        long newTokens = availableTokens + tokensToAdd;
        if (newTokens < availableTokens) {
            newTokens = Long.MAX_VALUE;
        }
        availableTokens = Math.min(capacity, newTokens);
    }

    private static void validateInitialParameters(long capacity, long refillTokens, long refillPeriodNanos) {
        if (capacity <= 0 || refillTokens <= 0 || refillPeriodNanos <= 0) {
            throw new IllegalArgumentException("Capacity, refill tokens, and refill period must be positive");
        }
        if (refillTokens > capacity) {
            throw new IllegalArgumentException("Refill tokens cannot exceed capacity");
        }
    }
}
