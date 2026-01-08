[![Java RateLimiter CI](https://github.com/vkunitsyn/labs/actions/workflows/rate-limiter-java.yml/badge.svg)](https://github.com/vkunitsyn/labs/actions/workflows/rate-limiter-java.yml)
# Rate Limiter (Java)

This project is a small Java exploration of rate limiting techniques,
with an emphasis on correctness, observable behavior, and test design.

It is intended as a learning example, not as a production-ready library.

---

## What is implemented

- Token Bucket
- Spacing (Leaky) Bucket
- Fixed Window Counter

All implementations share a common `RateLimiter` interface
and are exercised through the same set of tests.

---

## Behavioral guarantees

The tests specify the expected behavior. In particular:

- `availableTokens()` is never negative
- `retryAfter()` is never negative
- time behavior is monotonic (based on `System.nanoTime()`)

---

## Concurrency

Implementations are **thread-safe**: all state mutations are guarded by
intrinsic locking (`synchronized`), prioritizing correctness and clarity
over lock-free optimizations.

---

## Build & test

From this directory:

- Run tests: `./gradlew test`
- Run verification (includes formatting checks): `./gradlew check`

---

## Formatting

Java formatting is enforced via Spotless (Java sources only):

- Check: `./gradlew spotlessCheck`
- Apply: `./gradlew spotlessApply`

---

## Demo

`io.github.vkunitsyn.RateLimiterDemo`

Run:

Run:
- `./gradlew run` — client that respects retry-after delays
- `./gradlew run --args="--hammer"` — client that issues requests at a fixed high rate

It demonstrates:
- burst handling
- steady high-QPS traffic
- rejection and retry-after behavior

The demo is timing-dependent; the exact numbers may vary.

---

## Notes

- The code favors clarity over micro-optimizations
- Time measurements rely on `System.nanoTime()`
- External dependencies are kept minimal intentionally
