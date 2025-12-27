package io.github.vkunitsyn.ratelimiter;

public class Utils {
    public static long saturatedAdd(long a, long b) {
        long r = a + b;
        // x ^ r = 1 only if sign bits are different
        // 1 & 1 = 1 < 0 only if sign bits are the same -> overflow
        if (((a ^ r) & (b ^ r)) < 0) {
            return (a < 0) ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return r;
    }

    // only positive numbers for simplicity
    public static long saturatedMultiply(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }
}
