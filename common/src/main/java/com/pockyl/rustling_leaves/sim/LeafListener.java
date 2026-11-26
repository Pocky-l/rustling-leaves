package com.pockyl.rustling_leaves.sim;

/** Receives simulation events that the client turns into sounds and particles. */
public interface LeafListener {
    LeafListener NONE = new LeafListener() {
    };

    /** An entity pushed {@code count} leaves of the litter aside at this spot. */
    default void onRustle(double x, double y, double z, int count, boolean wet) {
    }

    /** A leaf fell into lava. */
    default void onBurn(double x, double y, double z) {
    }

    /** A cell of litter caught fire at this spot (see {@link LeafSimulation#burn}). */
    default void onFire(double x, double y, double z) {
    }
}
