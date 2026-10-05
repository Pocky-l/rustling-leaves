package com.pockyl.rustling_leaves.sim;

/**
 * Tunables of the simulation as plain fields. The client copies them from the config; game tests use the defaults,
 * so the simulation never touches a config spec that is not loaded on the server.
 */
public final class LeafSettings {
    public int maxLeaves = 8000;
    public float fallRate = 1.0F;
    public int spawnRadius = 32;
    public int groundLifetimeTicks = 300 * 20;
    public float leafSize = 1.0F;
    public float autumnColors = 0.35F;
    public float windStrength = 1.0F;
    public float entityStrength = 1.0F;
    public float explosionStrength = 1.0F;
    public int leavesPerBreak = 14;
    public float rustleVolume = 0.6F;

    /** Leaves farther than this (horizontally) from the camera are dropped. */
    public double despawnRadius() {
        return spawnRadius + 24;
    }
}
