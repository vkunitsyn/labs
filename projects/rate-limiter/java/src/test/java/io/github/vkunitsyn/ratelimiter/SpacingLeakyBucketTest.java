package io.github.vkunitsyn.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SpacingLeakyBucketTest {

    @Test
    void allowsBurstUpToMaxBurstAndThenRejectsWithCorrectRetryAfter() {
        long interval = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new SpacingLeakyBucket(10, interval);

        long t0 = 5_000L;

        assertThat(limiter.tryAcquire(t0, 10).isAcquired()).isTrue();
        assertThat(limiter.availableTokens(t0)).isZero();

        var r = limiter.tryAcquire(t0, 1);
        assertThat(r.isAcquired()).isFalse();
        assertThat(r.retryAfterNanos()).isEqualTo(interval);

        long t1 = t0 + interval;
        assertThat(limiter.availableTokens(t1)).isEqualTo(1);
        assertThat(limiter.tryAcquire(t1, 1).isAcquired()).isTrue();
    }

    @Test
    void retryAfterDecreasesAsTimePassesWithoutSleeping() {
        long interval = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new SpacingLeakyBucket(10, interval);

        long t0 = 1_000_000L;

        assertThat(limiter.tryAcquire(t0, 10).isAcquired()).isTrue();

        long retry0 = limiter.retryAfterNanos(t0, 2);
        assertThat(retry0).isEqualTo(interval * 2);

        long t1 = t0 + interval;
        long retry1 = limiter.retryAfterNanos(t1, 2);
        assertThat(retry1).isEqualTo(interval);

        long t2 = t0 + interval * 2;
        assertThat(limiter.tryAcquire(t2, 2).isAcquired()).isTrue();
    }

    @Test
    void rejectsInvalidPermits() {
        long interval = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new SpacingLeakyBucket(10, interval);

        assertThatThrownBy(() -> limiter.tryAcquire(1_000, 0)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> limiter.tryAcquire(1_000, -1)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> limiter.tryAcquire(1_000, 11)).isInstanceOf(IllegalArgumentException.class);
    }
}
