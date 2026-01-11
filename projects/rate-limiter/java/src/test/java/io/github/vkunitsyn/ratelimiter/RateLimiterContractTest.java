package io.github.vkunitsyn.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RateLimiterContractTest {
    static Stream<RateLimiter> limiters() {
        long period = Duration.ofMillis(100).toNanos();
        return Stream.of(
                new TokenBucket(10, 1, period),
                new SpacingLeakyBucket(10, period),
                new FixedWindowCounter(10, period),
                new SlidingWindowLog(10, period));
    }

    @ParameterizedTest
    @MethodSource("limiters")
    void retryAfterIsNeverNegative(RateLimiter limiter) {
        long t0 = 123_456L;

        assertThat(limiter.tryAcquire(t0, 10).isAcquired()).isTrue();

        var r = limiter.tryAcquire(t0, 1);
        assertThat(r.isAcquired()).isFalse();
        assertThat(r.retryAfterNanos()).isGreaterThanOrEqualTo(0);

        long ra = limiter.retryAfterNanos(t0, 1);
        assertThat(ra).isGreaterThanOrEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("limiters")
    void availableTokensIsNeverNegative(RateLimiter limiter) {
        long t0 = 999L;
        assertThat(limiter.availableTokens(t0)).isGreaterThanOrEqualTo(0);
    }
}
