package com.pockyl.rustling_leaves.sim;

/**
 * Tunables of the simulation as plain fields. The client copies them from the config; game tests use the defaults,
 * so the simulation never touches a config spec that is not loaded on the server.
 */
public final class LeafSettings {
    /** Maximum number of moving (simulated) leaves; leaves lying on the ground are not counted. */
    public int maxLeaves = 8000;
    public float fallRate = 1.0F;
    /** Whether birches and cherries shed more and conifers less than other trees. */
    public boolean treeFallRates = true;
    public int spawnRadius = 32;
    /** Natural leaf carpet thickness in leaves per quarter-block cell; falling leaves stop thickening it beyond this. */
    public int carpetDepth = 4;
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

    /** Whether leaf fall follows the seasons of a season mod. */
    public boolean seasons = true;
    /** Season options: fall rate in late autumn and in winter, autumn color share in late autumn (multipliers). */
    public float autumnFallRate = 3.0F;
    public float winterFallRate = 0.05F;
    public float autumnColorBoost = 3.0F;

    /**
     * Multipliers of the current season (1 = no season): how often trees drop leaves, how often cherries drop petals
     * and how many leaves fall in autumn colors. The client sets them through {@link SeasonCurve} from the season mod.
     */
    public float seasonFallRate = 1.0F;
    public float seasonPetalRate = 1.0F;
    public float seasonAutumnColors = 1.0F;

    /** How often trees with leaves of this shape drop them, relative to the base fall rate. */
    public float shedRate(LeafShape shape) {
        return treeFallRates ? shape.shedRate : 1.0F;
    }

    /** The season's multiplier of how often trees with leaves of this shape drop them. */
    public float seasonRate(LeafShape shape) {
        return shape == LeafShape.PETAL ? seasonPetalRate : seasonFallRate;
    }

    /** Share of newly fallen leaves in autumn colors: the configured share, raised or lowered by the season. */
    public float autumnShare() {
        return Math.min(1.0F, autumnColors * seasonAutumnColors);
    }

    /** Moving leaves farther than this (horizontally) from the camera are dropped. */
    public double despawnRadius() {
        return spawnRadius + 24;
    }

    /** How far lying leaves are shown (the client caps it at the render distance). */
    public int litterDistance = 128;

    /** Leaf litter is kept in memory (and drawn) within this horizontal distance from the camera. */
    public double litterRadius() {
        return Math.max(Math.max(48, spawnRadius + 16), litterDistance);
    }
}
