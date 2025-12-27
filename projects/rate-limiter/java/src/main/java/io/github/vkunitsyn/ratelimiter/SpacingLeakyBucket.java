package io.github.vkunitsyn.ratelimiter;

public class SpacingLeakyBucket implements RateLimiter {
    private final long maxBurst;
    private final long rateIntervalNanos;
    private long nextFreeTimeNanos;

    public SpacingLeakyBucket(long maxBurst, long rateIntervalNanos) {
        validateInitialParameters(maxBurst, rateIntervalNanos);
        this.maxBurst = maxBurst;
        this.rateIntervalNanos = rateIntervalNanos;
    }

    @Override
    public synchronized AcquireResult tryAcquire(long nowNanos, long permits) {
        validatePermits(permits);

        long effectiveFreeTimeNanos = Math.max(nowNanos, nextFreeTimeNanos);
        long permitsWindowNanos = Utils.saturatedMultiply(permits, rateIntervalNanos);
        long nextPossibleFreeTimeNanos = Utils.saturatedAdd(effectiveFreeTimeNanos, permitsWindowNanos);
        long burstWindowNanos = Utils.saturatedMultiply(maxBurst, rateIntervalNanos);
        if (nextPossibleFreeTimeNanos > Utils.saturatedAdd(nowNanos, burstWindowNanos)) {
            return new AcquireResult.Rejected(retryAfterNanosInternal(nowNanos, permits));
        }

        nextFreeTimeNanos = nextPossibleFreeTimeNanos;
        return new AcquireResult.Acquired(permits);
    }

    @Override
    public synchronized long availableTokens(long nowNanos) {
        long burstWindow = Utils.saturatedMultiply(maxBurst, rateIntervalNanos);
        long slack = Utils.saturatedAdd(nowNanos, burstWindow) - nextFreeTimeNanos;

        if (slack <= 0) {
            return 0;
        }

        long tokens = slack / rateIntervalNanos;
        return Math.min(tokens, maxBurst);
    }

    @Override
    public synchronized long retryAfterNanos(long nowNanos, long permits) {
        validatePermits(permits);
        return retryAfterNanosInternal(nowNanos, permits);
    }

    private long retryAfterNanosInternal(long nowNanos, long permits) {
        long burstSlack = Utils.saturatedMultiply(maxBurst - permits, rateIntervalNanos);
        long threshold = nextFreeTimeNanos - burstSlack;
        long wait = threshold - nowNanos;
        return Math.max(0, wait);
    }

    private void validatePermits(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be positive");
        }
        if (permits > maxBurst) {
            throw new IllegalArgumentException("Permits exceed bucket capacity");
        }
    }

    private static void validateInitialParameters(long maxBurst, long rateIntervalNanos) {
        if (maxBurst <= 0 || rateIntervalNanos <= 0) {
            throw new IllegalArgumentException("Max burst, rate interval must be positive");
        }
    }
}
