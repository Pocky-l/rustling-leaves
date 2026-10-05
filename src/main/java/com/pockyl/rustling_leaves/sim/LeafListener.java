package com.pockyl.rustling_leaves.sim;

/** Receives simulation events that the client turns into meshes, sounds and particles. */
public interface LeafListener {
    LeafListener NONE = new LeafListener() {
    };

    /** The leaf settled and stays still until something disturbs it. */
    default void onRest(int leaf) {
    }

    /** The leaf stops resting: it was disturbed, starts fading or is removed. Called while its position is still valid. */
    default void onUnrest(int leaf) {
    }

    /** An entity kicked up {@code count} resting leaves at this spot. */
    default void onRustle(double x, double y, double z, int count, boolean wet) {
    }

    /** A leaf fell into lava. */
    default void onBurn(double x, double y, double z) {
    }
}
