package io.github.vkunitsyn.ratelimiter;

public interface RateLimiter {
    AcquireResult tryAcquire(long nowNanos, long permits);

    long availableTokens(long nowNanos);

    long retryAfterNanos(long nowNanos, long permits);

    default AcquireResult tryAcquire(long permits) {
        return tryAcquire(System.nanoTime(), permits);
    }

    default long availableTokens() {
        return availableTokens(System.nanoTime());
    }

    default long retryAfterNanos(long permits) {
        return retryAfterNanos(System.nanoTime(), permits);
    }

    sealed interface AcquireResult permits AcquireResult.Acquired, AcquireResult.Rejected {

        public boolean isAcquired();

        /**
         * If not acquired, returns the minimal delay after which a retry MAY succeed
         * (>= 0). If
         * acquired, always returns 0.
         */
        public long retryAfterNanos();

        record Acquired(long permits) implements AcquireResult {
            @Override
            public boolean isAcquired() {
                return true;
            }

            @Override
            public long retryAfterNanos() {
                return 0L;
            }
        }

        record Rejected(long retryAfterNanos) implements AcquireResult {
            public Rejected {
                if (retryAfterNanos < 0) {
                    throw new IllegalArgumentException("retryAfterNanos must be >= 0");
                }
            }

            @Override
            public boolean isAcquired() {
                return false;
            }
        }
    }
}
