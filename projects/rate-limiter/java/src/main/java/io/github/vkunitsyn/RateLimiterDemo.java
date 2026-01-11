package io.github.vkunitsyn;

import io.github.vkunitsyn.ratelimiter.FixedWindowCounter;
import io.github.vkunitsyn.ratelimiter.RateLimiter;
import io.github.vkunitsyn.ratelimiter.SlidingWindowLog;
import io.github.vkunitsyn.ratelimiter.SpacingLeakyBucket;
import io.github.vkunitsyn.ratelimiter.TokenBucket;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class RateLimiterDemo {

    public static void main(String[] args) throws Exception {
        // When true, we behave like a polite client: respect retryAfter() (e.g., HTTP
        // 429 Retry-After).
        // When false, we behave like a load generator: always attempt at targetQps,
        // ignoring retryAfter.
        boolean respectRetryAfter = true;
        for (String arg : args) {
            if ("--hammer".equals(arg) || "--ignore-retry-after".equals(arg)) {
                respectRetryAfter = false;
            }
        }

        Supplier<RateLimiter> factory = selectFactory(args);

        System.out.println();
        burstDemo(factory.get());
        System.out.println();
        steadyDemo(factory.get(), 50, Duration.ofSeconds(5), respectRetryAfter); // 50 qps for 5s
    }

    private static void burstDemo(RateLimiter limiter) {
        long now = System.nanoTime();
        long permits = 1;

        int ok = 0, reject = 0;
        long maxRetry = 0;

        System.out.println("== burstDemo ==");
        for (int i = 1; i <= 50; i++) {
            RateLimiter.AcquireResult r = limiter.tryAcquire(now, permits);
            if (r.isAcquired()) {
                ok++;
            } else {
                reject++;
                maxRetry = Math.max(maxRetry, r.retryAfterNanos());
                if (reject <= 5) {
                    System.out.println("reject #" + reject + " retryAfter=" + r.retryAfterNanos() + "ns");
                }
            }
        }

        System.out.println("ok=" + ok + " rejected=" + reject + " availableTokens=" + limiter.availableTokens(now));
        if (reject > 0) System.out.println("maxRetryAfter=" + maxRetry + "ns");
    }

    private static void steadyDemo(RateLimiter limiter, int qps, Duration duration, boolean respectRetryAfter)
            throws InterruptedException {

        long permits = 1;
        long intervalNanos = 1_000_000_000L / Math.max(1, qps);

        long start = System.nanoTime();
        long deadLine = start + duration.toNanos();

        int ok = 0, reject = 0;
        long totalSleepNanos = 0;
        long sumBackoff = 0;
        long maxBackoff = 0;
        long backoffWins = 0;

        System.out.println("== steadyDemo ==");
        System.out.println("clientMode=" + (respectRetryAfter ? "POLITE" : "HAMMER"));
        System.out.println("targetQps=" + qps + " duration=" + duration);

        long nextAttempt = start;
        while (true) {
            long now = System.nanoTime();
            if (now >= deadLine) break;

            if (now < nextAttempt) {
                // we are ahead of schedule, sleep
                long sleep = nextAttempt - now;
                totalSleepNanos += sleep;
                Thread.sleep(Math.max(0, sleep / 1_000_000), (int) (sleep % 1_000_000));
                continue;
            }

            // avoid drifting by basing next attempt on max of now and scheduled time
            long base = Math.max(now, nextAttempt);

            RateLimiter.AcquireResult r = limiter.tryAcquire(now, permits);
            if (r.isAcquired()) {
                ok++;
                // introduce some jitter for every 32 successful acquires
                long jitter = (ok & 31) == 0 ? ThreadLocalRandom.current().nextInt(50_000) : 0;
                nextAttempt = base + intervalNanos + jitter;
            } else {
                reject++;
                long afterDesired = base + intervalNanos;

                if (respectRetryAfter) {
                    long backoff = Math.max(0L, r.retryAfterNanos());
                    sumBackoff += backoff;
                    maxBackoff = Math.max(maxBackoff, backoff);
                    long afterBackoff = base + backoff;
                    if (afterBackoff > afterDesired) backoffWins++;

                    // respect both rate limiter backoff and desired rate
                    nextAttempt = Math.max(afterDesired, afterBackoff);
                } else {
                    // hammer mode: ignore retryAfter and keep pushing at the desired rate
                    nextAttempt = afterDesired;
                }
            }
        }

        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        double attemptQps = (ok + reject) / Math.max(1e-9, seconds);
        double okQps = ok / Math.max(1e-9, seconds);
        double rejectRate = 100.0 * reject / Math.max(1, (ok + reject));

        System.out.println("total=" + (ok + reject) + " ok=" + ok + " rejected=" + reject);
        System.out.printf("attemptQps=%.1f okQps=%.1f rejectRate=%.1f%%%n", attemptQps, okQps, rejectRate);
        System.out.println("avgPaceSleep(ms)=" + (totalSleepNanos / 1_000_000.0) / Math.max(1, (ok + reject)));
        if (respectRetryAfter && reject > 0) {
            System.out.println("avgBackoff(ms)=" + (sumBackoff / 1_000_000.0) / reject);
            System.out.println("maxBackoff(ms)=" + (maxBackoff / 1_000_000.0));
            System.out.println("backoffWins=" + backoffWins + "/" + reject);
        }
    }

    private static Supplier<RateLimiter> selectFactory(String[] args) {
        String algo = "token"; // default
        for (String arg : args) {
            if (arg.startsWith("--algo=")) {
                algo = arg.substring("--algo=".length());
            }
        }

        return switch (algo) {
            case "fixed" -> fixedWindowCounter();
            case "token" -> tokenBucket();
            case "spacing" -> spacingLeakyBucket();
            case "sliding" -> slidingWindowLog();
            default -> throw new IllegalArgumentException("Unknown --algo=" + algo);
        };
    }

    private static Supplier<RateLimiter> fixedWindowCounter() {
        System.out.println("FixedWindowCounter");
        long rate = 10;
        long window = Duration.ofSeconds(1).toNanos(); // 10 permits/sec
        return () -> new FixedWindowCounter(rate, window);
    }

    private static Supplier<RateLimiter> tokenBucket() {
        System.out.println("TokenBucket");
        return () -> new TokenBucket(
                10, // capacity
                1, // refillTokens
                Duration.ofMillis(100).toNanos() // refillPeriod => 10 tokens/sec
                );
    }

    private static Supplier<RateLimiter> spacingLeakyBucket() {
        System.out.println("SpacingLeakyBucket");
        return () -> new SpacingLeakyBucket(
                10, // maxBurst
                Duration.ofMillis(100).toNanos() // interval => 10 permits/sec
                );
    }

    private static Supplier<RateLimiter> slidingWindowLog() {
        System.out.println("SlidingWindowLog");
        return () -> new SlidingWindowLog(
                10, // rate
                Duration.ofSeconds(1).toNanos() // window => 10 permits/sec
                );
    }
}
