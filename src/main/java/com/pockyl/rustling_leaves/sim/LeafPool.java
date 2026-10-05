package com.pockyl.rustling_leaves.sim;

/**
 * Structure-of-arrays storage for moving leaves (the ones lying still live in the {@link LitterField}). A leaf is an
 * index into the arrays; indices are stable for the lifetime of a leaf and freed slots are reused.
 */
public final class LeafPool {
    public static final byte FREE = 0;
    /** In the air: full aerodynamics and collisions. */
    public static final byte FALLING = 1;
    /** Skidding and tumbling over the ground or a pile. */
    public static final byte SLIDING = 2;
    /** Drifting on a water surface. */
    public static final byte FLOATING = 3;
    /** Shrinking away; {@link #life} counts the remaining ticks. */
    public static final byte DYING = 4;
    /** Soaked through, slowly sinking and swaying down to the bottom of the water. */
    public static final byte SINKING = 5;

    /** The leaf just fell off a tree; it only thickens the litter up to the natural carpet depth. */
    public static final byte FLAG_NATURAL = 1;
    /** Touched down and turning flat before it joins the litter. */
    public static final byte FLAG_SETTLING = 2;
    /** Under the open sky, so the wind can push it along the ground. */
    public static final byte FLAG_EXPOSED = 4;
    /** Falls tumbling about its long axis (and glides sideways) instead of fluttering like a pendulum. */
    public static final byte FLAG_TUMBLER = 8;

    public final int capacity;

    public final double[] x;
    public final double[] y;
    public final double[] z;
    public final double[] prevX;
    public final double[] prevY;
    public final double[] prevZ;
    public final float[] vx;
    public final float[] vy;
    public final float[] vz;

    /** Orientation as Y-X-Z Euler angles; left unwrapped so that interpolation never jumps. */
    public final float[] yaw;
    public final float[] pitch;
    public final float[] roll;
    public final float[] prevYaw;
    public final float[] prevPitch;
    public final float[] prevRoll;
    public final float[] spinYaw;
    public final float[] spinPitch;
    public final float[] spinRoll;

    /** Flutter oscillator: phase, angular frequency, sway amplitude and the slowly drifting sway heading. */
    public final float[] phase;
    public final float[] freq;
    public final float[] sway;
    public final float[] swayDir;
    public final float[] size;
    /** Water flow for floating leaves; target tilt while settling. */
    public final float[] auxA;
    public final float[] auxB;
    /** Surface the leaf touched down on. */
    public final double[] ground;

    /** Color as drawn, and the color of the tree it came from (what the litter remembers). */
    public final int[] color;
    public final int[] baseColor;
    public final int[] light;
    public final int[] age;
    public final int[] life;
    public final byte[] state;
    public final byte[] sprite;
    public final byte[] shape;
    public final byte[] flags;

    private final int[] free;
    private int freeTop;
    private int highWater;
    private int count;

    public LeafPool(int capacity) {
        this.capacity = capacity;
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        prevX = new double[capacity];
        prevY = new double[capacity];
        prevZ = new double[capacity];
        vx = new float[capacity];
        vy = new float[capacity];
        vz = new float[capacity];
        yaw = new float[capacity];
        pitch = new float[capacity];
        roll = new float[capacity];
        prevYaw = new float[capacity];
        prevPitch = new float[capacity];
        prevRoll = new float[capacity];
        spinYaw = new float[capacity];
        spinPitch = new float[capacity];
        spinRoll = new float[capacity];
        phase = new float[capacity];
        freq = new float[capacity];
        sway = new float[capacity];
        swayDir = new float[capacity];
        size = new float[capacity];
        auxA = new float[capacity];
        auxB = new float[capacity];
        ground = new double[capacity];
        color = new int[capacity];
        baseColor = new int[capacity];
        light = new int[capacity];
        age = new int[capacity];
        life = new int[capacity];
        state = new byte[capacity];
        sprite = new byte[capacity];
        shape = new byte[capacity];
        flags = new byte[capacity];
        free = new int[capacity];
    }

    /** Returns a free slot, or -1 when the pool is full. The caller initializes every field and the state. */
    int allocate() {
        int leaf;
        if (freeTop > 0) {
            leaf = free[--freeTop];
        } else if (highWater < capacity) {
            leaf = highWater++;
        } else {
            return -1;
        }
        count++;
        return leaf;
    }

    void release(int leaf) {
        state[leaf] = FREE;
        free[freeTop++] = leaf;
        count--;
    }

    /** Every live leaf has an index below this value. */
    public int highWater() {
        return highWater;
    }

    public int count() {
        return count;
    }

    public int free() {
        return capacity - count;
    }

    public void savePrevious(int leaf) {
        prevX[leaf] = x[leaf];
        prevY[leaf] = y[leaf];
        prevZ[leaf] = z[leaf];
        prevYaw[leaf] = yaw[leaf];
        prevPitch[leaf] = pitch[leaf];
        prevRoll[leaf] = roll[leaf];
    }
}
