package io.github.vkunitsyn.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    @Test
    void startsFullAndAllowsBurstUpToCapacity() {
        long period = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new TokenBucket(10, 1, period);

        long t0 = 1_000_000_000L;

        for (int i = 0; i < 10; i++) {
            var r = limiter.tryAcquire(t0, 1);
            assertThat(r.isAcquired()).isTrue();
            assertThat(r.retryAfterNanos()).isZero();
        }

        var rejected = limiter.tryAcquire(t0, 1);
        assertThat(rejected.isAcquired()).isFalse();
        assertThat(rejected.retryAfterNanos()).isEqualTo(period);

        assertThat(limiter.availableTokens(t0)).isZero();
    }

    @Test
    void refillsOverTimeAndEventuallyAllowsAcquire() {
        long period = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new TokenBucket(10, 1, period);

        long t0 = 1_000L;

        assertThat(limiter.tryAcquire(t0, 10).isAcquired()).isTrue();
        assertThat(limiter.availableTokens(t0)).isZero();

        long t50 = t0 + period / 2;
        assertThat(limiter.availableTokens(t50)).isZero();
        assertThat(limiter.tryAcquire(t50, 1).isAcquired()).isFalse();

        long t100 = t0 + period;
        assertThat(limiter.availableTokens(t100)).isEqualTo(1);
        assertThat(limiter.tryAcquire(t100, 1).isAcquired()).isTrue();
        assertThat(limiter.availableTokens(t100)).isZero();

        long tMuchLater = t0 + period * 1_000;
        assertThat(limiter.availableTokens(tMuchLater)).isEqualTo(10);
    }

    @Test
    void retryAfterReflectsMissingTokens() {
        long period = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new TokenBucket(10, 1, period);

        long t0 = 10_000L;

        assertThat(limiter.tryAcquire(t0, 10).isAcquired()).isTrue();

        long retry = limiter.retryAfterNanos(t0, 3);
        assertThat(retry).isEqualTo(period * 3);

        long t2 = t0 + period * 2;
        assertThat(limiter.retryAfterNanos(t2, 3)).isEqualTo(period);

        long t3 = t0 + period * 3;
        assertThat(limiter.tryAcquire(t3, 3).isAcquired()).isTrue();
    }

    @Test
    void rejectsInvalidPermits() {
        long period = Duration.ofMillis(100).toNanos();
        RateLimiter limiter = new TokenBucket(10, 1, period);

        assertThatThrownBy(() -> limiter.tryAcquire(1_000, 0)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> limiter.tryAcquire(1_000, -1)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> limiter.tryAcquire(1_000, 11)).isInstanceOf(IllegalArgumentException.class);
    }
}
