package com.pockyl.rustling_leaves.sim;

/** Xorshift; much cheaper than a synchronized {@code Random} for tens of thousands of draws per tick. */
final class FastRandom {
    private long seed;

    FastRandom(long seed) {
        this.seed = seed == 0 ? 0x2545F4914F6CDD1DL : seed;
    }

    /** Uniform in [0, 1). */
    float next() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (seed >>> 40) * 0x1.0p-24F;
    }
}
