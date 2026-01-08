package io.github.vkunitsyn.ratelimiter;

public class FixedWindowCounter implements RateLimiter {
    private final long rate;
    private final long windowSizeNanos;
    private long lastWindowTimeNanos;
    private long remainingPermitsInWindow;

    public FixedWindowCounter(long rate, long windowSizeNanos) {
        validateInitialParameters(rate, windowSizeNanos);
        this.rate = rate;
        this.windowSizeNanos = windowSizeNanos;
        this.lastWindowTimeNanos = Long.MIN_VALUE;
    }

    @Override
    public synchronized AcquireResult tryAcquire(long nowNanos, long permits) {
        validatePermits(permits);
        recalculateWindowAndPermits(nowNanos);
        if (remainingPermitsInWindow >= permits) {
            remainingPermitsInWindow -= permits;
            return new AcquireResult.Acquired(permits);
        }
        return new AcquireResult.Rejected(retryAfterNanosInternal(nowNanos, permits));
    }

    @Override
    public synchronized long availableTokens(long nowNanos) {
        recalculateWindowAndPermits(nowNanos);
        return remainingPermitsInWindow;
    }

    @Override
    public synchronized long retryAfterNanos(long nowNanos, long permits) {
        validatePermits(permits);
        recalculateWindowAndPermits(nowNanos);
        return retryAfterNanosInternal(nowNanos, permits);
    }

    private long retryAfterNanosInternal(long nowNanos, long permits) {
        if (remainingPermitsInWindow >= permits) {
            return 0;
        }

        long nextWindowTimeNanos = Utils.saturatedAdd(lastWindowTimeNanos, windowSizeNanos);
        if (nextWindowTimeNanos <= nowNanos) {
            return 0;
        }

        long delayNanos = nextWindowTimeNanos - nowNanos;
        return Math.max(0, delayNanos);
    }

    private void validatePermits(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be positive");
        }
        if (permits > rate) {
            throw new IllegalArgumentException("Permits exceed allowed rate");
        }
    }

    private void recalculateWindowAndPermits(long nowNanos) {
        if (lastWindowTimeNanos == Long.MIN_VALUE) {
            remainingPermitsInWindow = rate;
            lastWindowTimeNanos = nowNanos - Math.floorMod(nowNanos, windowSizeNanos);
            return;
        }

        if (nowNanos <= lastWindowTimeNanos) {
            return;
        }

        long currentWindowStartNanos = nowNanos - Math.floorMod(nowNanos, windowSizeNanos);
        if (currentWindowStartNanos > lastWindowTimeNanos) {
            remainingPermitsInWindow = rate;
            lastWindowTimeNanos = currentWindowStartNanos;
        }
    }

    private static void validateInitialParameters(long rate, long windowSizeNanos) {
        if (rate <= 0 || windowSizeNanos <= 0) {
            throw new IllegalArgumentException("Rate, windows size must be positive");
        }
    }
}
