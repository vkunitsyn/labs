package io.github.vkunitsyn.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

/**
 * Scenario-based property tests for rate limiters.
 *
 * <p>Why this is different from "JUnit with random params": - We generate whole *sequences* of
 * operations (time advances + acquire attempts). - We assert invariants on every step, not just for
 * a single call. - If jqwik finds a counterexample, it will try to shrink the scenario to the
 * smallest failing sequence, which is extremely useful for debugging.
 *
 * <p>These tests are intentionally implementation-agnostic: they validate the public contract
 * (availableTokens/retryAfter/tryAcquire consistency) for both implementations.
 */
class RateLimiterScenarioPropertiesTest {

    /**
     * A single step in a generated scenario. - AdvanceTime: move "now" forward by deltaNanos -
     * Acquire: attempt to acquire N permits at current "now"
     */
    sealed interface Step permits StepAdvanceTime, StepAcquire {}

    record StepAdvanceTime(long deltaNanos) implements Step {}

    record StepAcquire(int permits) implements Step {}

    // Shared test configuration (matches demo defaults)
    private static final long CAPACITY = 10;
    private static final long PERIOD_NANOS = Duration.ofMillis(100).toNanos(); // 10 permits/sec

    /**
     * Provides randomized but realistic scenarios: - Mostly Acquire steps (like load) - Sometimes
     * AdvanceTime steps (time passes between requests)
     *
     * <p>Note: We allow delta=0 intentionally (bursty traffic).
     */
    @Provide
    Arbitrary<List<Step>> scenarios() {
        Arbitrary<Step> acquire =
                Arbitraries.integers().between(1, (int) CAPACITY).map(StepAcquire::new);

        // Up to 5 seconds jumps; includes 0 to simulate bursts / same-timestamp events.
        Arbitrary<Step> advance =
                Arbitraries.longs().between(0L, Duration.ofSeconds(5).toNanos()).map(StepAdvanceTime::new);

        // Weighted mix: 80% acquire, 20% time-advance
        Arbitrary<Step> step = Arbitraries.frequencyOf(Tuple.of(8, acquire), Tuple.of(2, advance));

        // A scenario is a list of steps; 300 steps ~ a short "load session"
        return step.list().ofSize(300);
    }

    @Property(tries = 200)
    void tokenBucket_scenarios_hold_invariants(@ForAll("scenarios") List<Step> steps) {
        RateLimiter limiter = new TokenBucket(CAPACITY, 1, PERIOD_NANOS);
        runScenarioAndAssertInvariants("TokenBucket", limiter, steps, CAPACITY);
    }

    @Property(tries = 200)
    void spacingLeakyBucket_scenarios_hold_invariants(@ForAll("scenarios") List<Step> steps) {
        RateLimiter limiter = new SpacingLeakyBucket(CAPACITY, PERIOD_NANOS);
        runScenarioAndAssertInvariants("SpacingLeakyBucket", limiter, steps, CAPACITY);
    }

    @Property(tries = 200)
    void fixedWindowCounter_scenarios_hold_invariants(@ForAll("scenarios") List<Step> steps) {
        RateLimiter limiter = new FixedWindowCounter(CAPACITY, PERIOD_NANOS);
        runScenarioAndAssertInvariants("FixedWindowCounter", limiter, steps, CAPACITY);
    }

    /**
     * The core scenario runner.
     *
     * <p>We validate contract-level properties that should hold for any sane rate limiter: 1)
     * availableTokens(now) is always within [0..capacity] 2) retryAfterNanos(now, permits) is never
     * negative 3) If retryAfterNanos(now, permits) == 0, then tryAcquire(now, permits) MUST succeed
     * 4) If tryAcquire rejects, it should report a positive retryAfter
     *
     * <p>We also keep a human-readable trace; if something fails, the assertion message includes it.
     */
    private static void runScenarioAndAssertInvariants(
            String name, RateLimiter limiter, List<Step> steps, long capacity) {
        long now = 1_000_000_000L; // arbitrary non-zero start time
        List<String> trace = new ArrayList<>(steps.size());

        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);

            if (step instanceof StepAdvanceTime adv) {
                now = Utils.saturatedAdd(now, adv.deltaNanos());
                trace.add(i + ": advance +" + adv.deltaNanos() + "ns");
                continue;
            }

            StepAcquire acq = (StepAcquire) step;
            int permits = acq.permits();
            trace.add(i + ": acquire " + permits);

            // Invariant (1): availableTokens is bounded
            long availableBefore = limiter.availableTokens(now);
            assertThat(availableBefore)
                    .as("%s: availableTokens out of bounds BEFORE acquire at step %s%ntrace=%s", name, i, trace)
                    .isBetween(0L, capacity);

            // Invariant (2): retryAfter is never negative
            long retryPrediction = limiter.retryAfterNanos(now, permits);
            assertThat(retryPrediction)
                    .as("%s: retryAfterNanos is negative at step %s%ntrace=%s", name, i, trace)
                    .isGreaterThanOrEqualTo(0L);

            // Perform the actual operation
            RateLimiter.AcquireResult result = limiter.tryAcquire(now, permits);

            // Invariant (3): if limiter predicts "can acquire now", it must succeed
            if (retryPrediction == 0L) {
                assertThat(result.isAcquired())
                        .as("%s: retryAfterNanos==0 but tryAcquire rejected at step %s%ntrace=%s", name, i, trace)
                        .isTrue();

                assertThat(result.retryAfterNanos())
                        .as("%s: acquired but result.retryAfterNanos!=0 at step %s%ntrace=%s", name, i, trace)
                        .isEqualTo(0L);
            } else {
                // Invariant (4): on rejection, report a positive retryAfter
                if (!result.isAcquired()) {
                    assertThat(result.retryAfterNanos())
                            .as("%s: rejected but result.retryAfterNanos<=0 at step %s%ntrace=%s", name, i, trace)
                            .isGreaterThan(0L);
                }
            }

            // Invariant (1) again: still bounded after operation
            long availableAfter = limiter.availableTokens(now);
            assertThat(availableAfter)
                    .as("%s: availableTokens out of bounds AFTER acquire at step %s%ntrace=%s", name, i, trace)
                    .isBetween(0L, capacity);
        }
    }
}
