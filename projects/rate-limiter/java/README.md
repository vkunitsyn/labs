[![Java RateLimiter CI](https://github.com/vkunitsyn/labs/actions/workflows/rate-limiter-java.yml/badge.svg)](https://github.com/vkunitsyn/labs/actions/workflows/rate-limiter-java.yml)
# Rate Limiter (Java)

This project is a small Java exploration of rate limiting techniques, with an emphasis on **correctness**, **observable behavior**, and **test design**.

It is intended as a learning project, not as a production-ready library.

---

## What is implemented

- Token Bucket
- Spacing (Leaky) Bucket
- Fixed Window Counter
- Sliding Window Log

All implementations share a common `RateLimiter` interface
and are exercised through the same set of tests.

---

## Concurrency

Implementations are **thread-safe**: all state mutations are guarded by
intrinsic locking (`synchronized`), prioritizing correctness and clarity
over lock-free optimizations.

---

## Testing

Tests specify the expected behavior (contract + invariants):

- deterministic JUnit tests for concrete scenarios and boundary conditions
- property-based tests (jqwik) to enforce invariants over many generated inputs

Key invariants:

- `availableTokens()` is never negative
- `retryAfter()` is never negative

Code formatting is enforced via **Spotless** during the build.

---

## Build, test & demo

### Run all tests

```bash
./gradlew test
```

### Run the demo

The demo illustrates burst vs steady-state behavior for different limiter implementations.

```bash
./gradlew run
```

The demo supports command-line flags:

- `--algo=token|spacing|fixed|sliding` selects the algorithm
- `--hammer` (alias: `--ignore-retry-after`) makes the client ignore `retryAfter()` and keep pushing at the target QPS

Examples:

```bash
# default: polite client + token bucket
./gradlew run

# choose algorithm
./gradlew run --args='--algo=spacing'
./gradlew run --args='--algo=fixed'
./gradlew run --args='--algo=sliding'

# hammer mode (ignore retryAfter)
./gradlew run --args='--algo=spacing --hammer'
./gradlew run --args='--algo=token --ignore-retry-after'
```

`--hammer` changes the steady-load part of the demo to behave like a load generator (ignore `retryAfter()`).

Demo source: `src/main/java/.../RateLimiterDemo.java`. Limiter selection and scenarios are defined explicitly in the demo code for clarity.

---

## Notes

- The code favors clarity over micro-optimizations
- Time measurements rely on `System.nanoTime()`
- External dependencies are kept minimal intentionally
