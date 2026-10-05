package com.pockyl.rustling_leaves.sim;

/**
 * Tunables of the simulation as plain fields. The client copies them from the config; game tests use the defaults,
 * so the simulation never touches a config spec that is not loaded on the server.
 */
public final class LeafSettings {
    /** Maximum number of moving (simulated) leaves; leaves lying on the ground are not counted. */
    public int maxLeaves = 8000;
    public float fallRate = 1.0F;
    public int spawnRadius = 32;
    /** Natural leaf carpet thickness in leaves per quarter-block cell; falling leaves stop thickening it beyond this. */
    public int carpetDepth = 6;
    /** Whether new forest ground starts with drifts against walls and trunks and the odd leaf pile. */
    public boolean naturalPiles = true;
    public float leafSize = 1.0F;
    public float autumnColors = 0.35F;
    public float windStrength = 1.0F;
    /** How often squalls and whirlwinds happen (multiplier, 0 disables them). */
    public float windEvents = 1.0F;
    public float entityStrength = 1.0F;
    public float explosionStrength = 1.0F;
    public int leavesPerBreak = 14;
    public float rustleVolume = 0.6F;
    /** Rake leaves into piles with a hoe or shovel. */
    public boolean raking = true;

    /** Moving leaves farther than this (horizontally) from the camera are dropped. */
    public double despawnRadius() {
        return spawnRadius + 24;
    }

    /** Leaf litter is kept in memory (and drawn) within this horizontal distance from the camera. */
    public double litterRadius() {
        return Math.max(48, spawnRadius + 16);
    }
}
