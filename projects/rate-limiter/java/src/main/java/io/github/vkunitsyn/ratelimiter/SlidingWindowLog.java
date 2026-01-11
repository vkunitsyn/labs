package io.github.vkunitsyn.ratelimiter;

import java.util.LinkedList;

public class SlidingWindowLog implements RateLimiter {
    private final long rate;
    private final LinkedList<Long> log;
    private final long windowSizeNanos;

    public SlidingWindowLog(long rate, long windowSizeNanos) {
        validateInitialParameters(rate, windowSizeNanos);
        this.rate = rate;
        this.log = new LinkedList<>();
        this.windowSizeNanos = windowSizeNanos;
    }

    @Override
    public synchronized AcquireResult tryAcquire(long nowNanos, long permits) {
        validatePermits(permits);
        actualizeLog(nowNanos);
        for (long i = 0; i < permits; i++) {
            log.addLast(nowNanos); // By design we fill log even with rejected events
        }
        if (log.size() <= rate) {
            return new AcquireResult.Acquired(permits);
        }
        return new AcquireResult.Rejected(retryAfterNanosInternal(nowNanos, permits));
    }

    @Override
    public synchronized long availableTokens(long nowNanos) {
        actualizeLog(nowNanos);
        long availableTokens = rate - log.size();
        return Math.max(0, availableTokens);
    }

    @Override
    public synchronized long retryAfterNanos(long nowNanos, long permits) {
        validatePermits(permits);
        actualizeLog(nowNanos);
        return retryAfterNanosInternal(nowNanos, permits);
    }

    private long retryAfterNanosInternal(long nowNanos, long permits) {
        long available = rate - log.size();
        if (available >= permits) {
            return 0;
        }

        long deficit = permits - available;
        long nextAvailableWindowStartNanos = log.get((int) deficit - 1);
        long nextAvailableWindowEndNanos = Utils.saturatedAdd(nextAvailableWindowStartNanos, windowSizeNanos);
        return Math.max(0, nextAvailableWindowEndNanos - nowNanos);
    }

    private void validatePermits(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be positive");
        }
        if (permits > rate) {
            throw new IllegalArgumentException("Permits exceed allowed rate");
        }
    }

    private void actualizeLog(long nowNanos) {
        if (log.isEmpty() || nowNanos < log.getLast()) {
            return; // just ignore non-monotonic nowNanos
        }
        long currentWindowStartNanos = Utils.saturatedAdd(nowNanos, -windowSizeNanos);
        while (!log.isEmpty() && log.getFirst() < currentWindowStartNanos) {
            log.removeFirst();
        }
    }

    private static void validateInitialParameters(long rate, long windowSizeNanos) {
        if (rate <= 0 || windowSizeNanos <= 0) {
            throw new IllegalArgumentException("Rate, window size must be positive");
        }
    }
}
