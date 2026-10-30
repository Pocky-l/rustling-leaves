package com.pockyl.rustling_leaves.sim;

/**
 * Leaves the player carries in their arms: scooped up from the litter, poured out somewhere else. Only the count and a
 * sample of tree colors and shapes are kept, enough to pour back leaves that look like the ones picked up.
 */
public final class Armful {
    public static final int CAPACITY = 600;
    private static final int SAMPLES = 64;

    private final int[] colors = new int[SAMPLES];
    private final byte[] shapes = new byte[SAMPLES];
    private int samples;
    private int count;

    public int count() {
        return count;
    }

    public int room() {
        return CAPACITY - count;
    }

    public void clear() {
        count = 0;
        samples = 0;
    }

    /** Adds {@code leaves} leaves of one tree color and shape (a bag pouring them back out). */
    public void fill(int leaves, int baseColor, int shape) {
        for (int n = 0; n < leaves && count < CAPACITY; n++) {
            add(baseColor, shape);
        }
    }

    /** Average tree color of the remembered leaves. */
    public int averageColor() {
        int samplesKept = Math.min(samples, SAMPLES);
        if (samplesKept == 0) {
            return 0x8A6A2E;
        }
        long r = 0;
        long g = 0;
        long b = 0;
        for (int s = 0; s < samplesKept; s++) {
            r += colors[s] >> 16 & 0xFF;
            g += colors[s] >> 8 & 0xFF;
            b += colors[s] & 0xFF;
        }
        return (int) (r / samplesKept) << 16 | (int) (g / samplesKept) << 8 | (int) (b / samplesKept);
    }

    /** Shape of the first remembered leaf. */
    public int mainShape() {
        return samples == 0 ? 0 : shapes[0];
    }

    void add(int baseColor, int shape) {
        colors[samples % SAMPLES] = baseColor;
        shapes[samples % SAMPLES] = (byte) shape;
        samples++;
        count++;
    }

    /** Takes one leaf out; returns the index of a remembered sample describing it. */
    int take(float random) {
        count--;
        return (int) (random * Math.min(samples, SAMPLES));
    }

    int color(int sample) {
        return colors[sample];
    }

    int shape(int sample) {
        return shapes[sample];
    }
}
