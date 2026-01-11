package io.github.vkunitsyn.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SlidingWindowLogTest {

    @Test
    void allowsRequestsWithinWindowLimit() {
        RateLimiter limiter = new SlidingWindowLog(3, Duration.ofMillis(100).toNanos());
        long now = 1_000L;

        assertThat(limiter.tryAcquire(now, 1).isAcquired()).isTrue();
        assertThat(limiter.tryAcquire(now, 1).isAcquired()).isTrue();
        assertThat(limiter.tryAcquire(now, 1).isAcquired()).isTrue();

        assertThat(limiter.availableTokens(now)).isZero();
    }

    @Test
    void rejectsWhenWindowLimitIsExceeded() {
        RateLimiter limiter = new SlidingWindowLog(2, Duration.ofMillis(100).toNanos());
        long now = 1_000L;

        assertThat(limiter.tryAcquire(now, 2).isAcquired()).isTrue();

        var rejected = limiter.tryAcquire(now, 1);
        assertThat(rejected.isAcquired()).isFalse();
        assertThat(rejected.retryAfterNanos()).isEqualTo(Duration.ofMillis(100).toNanos());
    }

    @Test
    void allowsRequestsAfterWindowSlides() {
        long window = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new SlidingWindowLog(1, window);

        long t0 = 1_000L;
        assertThat(limiter.tryAcquire(t0, 1).isAcquired()).isTrue();

        long afterWindow = t0 + window + 1;
        assertThat(limiter.tryAcquire(afterWindow, 1).isAcquired()).isTrue();
    }

    @Test
    void requestAtExactWindowBoundaryIsStillRejected() {
        long window = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new SlidingWindowLog(1, window);

        long t0 = 1_000L;
        assertThat(limiter.tryAcquire(t0, 1).isAcquired()).isTrue();

        // exactly at window boundary: still same window
        long boundary = t0 + window;
        assertThat(limiter.tryAcquire(boundary, 1).isAcquired()).isFalse();
    }

    @Test
    void rejectsInvalidPermits() {
        RateLimiter limiter = new SlidingWindowLog(1, Duration.ofMillis(100).toNanos());

        assertThatThrownBy(() -> limiter.tryAcquire(1_000, 0)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> limiter.tryAcquire(1_000, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
