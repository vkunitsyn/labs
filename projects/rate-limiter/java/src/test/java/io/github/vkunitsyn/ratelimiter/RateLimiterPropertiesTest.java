package io.github.vkunitsyn.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based tests (jqwik) for basic RateLimiter invariants.
 *
 * <p>These tests are deterministic (no sleeps) because our API allows injecting nowNanos.
 */
class RateLimiterPropertiesTest {
    private static final long PERIOD = Duration.ofMillis(100).toNanos(); // 10 permits/sec

    @Property(tries = 500)
    void tokenBucket_invariantsHold(
            @ForAll @LongRange(min = 0, max = 1_000_000_000_000L) long baseTime,
            @ForAll @IntRange(min = 0, max = 10) int drain,
            @ForAll @IntRange(min = 0, max = 2_000) int periodsLater,
            @ForAll @IntRange(min = 1, max = 10) int permits) {
        RateLimiter limiter = new TokenBucket(10, 1, PERIOD);

        long t0 = baseTime;
        if (drain > 0) {
            limiter.tryAcquire(t0, drain); // may drain partially if drain == 10; for TokenBucket it should succeed.
        }

        long t = t0 + (long) periodsLater * PERIOD;

        long available = limiter.availableTokens(t);
        assertThat(available).isBetween(0L, 10L);

        long ra = limiter.retryAfterNanos(t, permits);
        assertThat(ra).isGreaterThanOrEqualTo(0L);

        if (permits <= available) {
            assertThat(ra).isZero();
            assertThat(limiter.tryAcquire(t, permits).isAcquired()).isTrue();
        }
    }

    @Property(tries = 500)
    void spacingLeakyBucket_invariantsHold(
            @ForAll @LongRange(min = 0, max = 1_000_000_000_000L) long baseTime,
            @ForAll @IntRange(min = 0, max = 10) int drain,
            @ForAll @IntRange(min = 0, max = 2_000) int periodsLater,
            @ForAll @IntRange(min = 1, max = 10) int permits) {
        RateLimiter limiter = new SpacingLeakyBucket(10, PERIOD);

        long t0 = baseTime;
        if (drain > 0) {
            limiter.tryAcquire(t0, drain);
        }

        long t = t0 + (long) periodsLater * PERIOD;

        long available = limiter.availableTokens(t);
        assertThat(available).isBetween(0L, 10L);

        long ra = limiter.retryAfterNanos(t, permits);
        assertThat(ra).isGreaterThanOrEqualTo(0L);

        if (permits <= available) {
            assertThat(ra).isZero();
            assertThat(limiter.tryAcquire(t, permits).isAcquired()).isTrue();
        }
    }
}
