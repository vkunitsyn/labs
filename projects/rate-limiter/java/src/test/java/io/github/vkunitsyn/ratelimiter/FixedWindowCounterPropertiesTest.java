package io.github.vkunitsyn.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class FixedWindowCounterPropertiesTest {
    private record WindowAndOffset(long windowSizeNanos, long offsetInWindowNanos) {}

    private record RateAndPermits(long rate, long permits) {}

    private record Case(long rate, long windowSizeNanos, long permits, long offsetInWindowNanos, long windowIndex) {}

    @Provide
    Arbitrary<Long> rates() {
        return Arbitraries.longs().between(1L, 200L);
    }

    @Provide
    Arbitrary<Long> windowSizes() {
        return Arbitraries.longs()
                .between(Duration.ofMillis(1).toNanos(), Duration.ofSeconds(5).toNanos());
    }

    @Provide
    Arbitrary<Long> windowIndex() {
        return Arbitraries.longs().between(0L, 1_000L);
    }

    // Generates a window size and an offset strictly inside that same window
    @Provide
    Arbitrary<WindowAndOffset> windowAndOffset() {
        return windowSizes()
                .flatMap(ws -> Arbitraries.longs().between(0L, ws - 1).map(off -> new WindowAndOffset(ws, off)));
    }

    // Generates rate and permits with a hard dependency: permits in [1..rate].
    @Provide
    Arbitrary<RateAndPermits> rateAndPermits() {
        return rates().flatMap(rate ->
                Arbitraries.longs().between(1L, rate).map(permits -> new RateAndPermits(rate, permits)));
    }

    @Provide
    Arbitrary<Case> cases() {
        return Combinators.combine(rateAndPermits(), windowAndOffset(), windowIndex())
                .as((rp, wo, idx) ->
                        new Case(rp.rate(), wo.windowSizeNanos(), rp.permits(), wo.offsetInWindowNanos(), idx));
    }

    @Property(tries = 300)
    void fixedWindowCounter_invariantsHold(@ForAll("cases") Case c) {
        long rate = c.rate();
        long windowSizeNanos = c.windowSizeNanos();
        long permits = c.permits();
        long offset = c.offsetInWindowNanos();
        long windowIndex = c.windowIndex();

        long windowStart = Utils.saturatedMultiply(windowSizeNanos, windowIndex);
        long now = Utils.saturatedAdd(windowStart, offset);
        long windowEnd = Utils.saturatedAdd(windowStart, windowSizeNanos);

        RateLimiter limiter = new FixedWindowCounter(rate, windowSizeNanos);

        // Invariant A: within a single window, total successfully acquired permits cannot exceed rate.
        long acquiredTotal = 0;
        boolean rejected = false;
        long rejectRetryAfter = -1;

        long maxSuccesses = rate / permits;
        long maxAttempts = maxSuccesses + 2; // keep it fast; cannot succeed more than rate/permits times anyway
        for (long i = 0; i < maxAttempts; i++) {
            var result = limiter.tryAcquire(now, permits);
            if (result.isAcquired()) {
                acquiredTotal = Utils.saturatedAdd(acquiredTotal, permits);
                assertThat(acquiredTotal)
                        .as("Total acquired permits within one window must not exceed rate")
                        .isLessThanOrEqualTo(rate);
            } else {
                rejected = true;
                rejectRetryAfter = result.retryAfterNanos();
                break;
            }
        }

        // Invariant B: if rejected inside a window, retryAfter is (0, remainingWindowTime] and matches API.
        if (rejected) {
            long remaining = windowEnd - now; // > 0 by construction (offset <= windowSize-1)

            assertThat(rejectRetryAfter)
                    .as("retryAfter on rejection must be positive")
                    .isGreaterThan(0L);

            assertThat(rejectRetryAfter)
                    .as("retryAfter on rejection must not exceed remaining window time")
                    .isLessThanOrEqualTo(remaining);

            assertThat(limiter.retryAfterNanos(now, permits))
                    .as("retryAfterNanos(now, permits) must match tryAcquire rejection")
                    .isEqualTo(rejectRetryAfter);
        }

        // Invariant C: at the next window boundary, permits are refilled and retryAfter is 0.
        assertThat(limiter.retryAfterNanos(windowEnd, permits))
                .as("At window boundary, retryAfter must be 0")
                .isEqualTo(0L);

        assertThat(limiter.availableTokens(windowEnd))
                .as("At window boundary, availableTokens must reset to rate")
                .isEqualTo(rate);

        assertThat(limiter.tryAcquire(windowEnd, permits).isAcquired())
                .as("At window boundary, first acquire must succeed")
                .isTrue();
    }
}
