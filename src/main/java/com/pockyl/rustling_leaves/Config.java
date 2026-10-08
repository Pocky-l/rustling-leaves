package com.pockyl.rustling_leaves;

import net.minecraftforge.common.ForgeConfigSpec;

import com.pockyl.rustling_leaves.sim.LeafSettings;

/** Client config: everything in this mod is visual. */
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.translation("rustling_leaves.configuration.leaves").push("leaves");
    }

    private static final ForgeConfigSpec.IntValue MAX_LEAVES = BUILDER
            .comment("Maximum number of moving leaves simulated at once (0 disables the mod). Leaves lying on the ground",
                    "are not counted: they are stored as leaf litter and cost almost nothing.")
            .translation("rustling_leaves.configuration.maxLeaves")
            .defineInRange("maxLeaves", 8000, 0, 40000);
    private static final ForgeConfigSpec.DoubleValue FALL_RATE = BUILDER
            .comment("How often leaves fall from trees (multiplier, 0 disables falling leaves).")
            .translation("rustling_leaves.configuration.fallRate")
            .defineInRange("fallRate", 1.0, 0.0, 10.0);
    private static final ForgeConfigSpec.BooleanValue TREE_FALL_RATES = BUILDER
            .comment("Whether the tree matters: birches shed more leaves, spruces and other conifers hardly any.")
            .translation("rustling_leaves.configuration.treeFallRates")
            .define("treeFallRates", true);
    private static final ForgeConfigSpec.IntValue SPAWN_RADIUS = BUILDER
            .comment("Radius around the camera, in blocks, in which trees drop leaves.")
            .translation("rustling_leaves.configuration.spawnRadius")
            .defineInRange("spawnRadius", 32, 8, 96);
    private static final ForgeConfigSpec.IntValue CARPET_DEPTH = BUILDER
            .comment("Thickness of the natural leaf carpet under trees, in leaves per quarter block (0 = no carpet).",
                    "Falling leaves stop thickening it beyond this; piles you make are not limited.")
            .translation("rustling_leaves.configuration.carpetDepth")
            .defineInRange("carpetDepth", 4, 0, 40);
    private static final ForgeConfigSpec.BooleanValue NATURAL_PILES = BUILDER
            .comment("Whether forests start with leaves banked against walls and trunks and the odd leaf pile.")
            .translation("rustling_leaves.configuration.naturalPiles")
            .define("naturalPiles", true);
    private static final ForgeConfigSpec.DoubleValue LEAF_SIZE = BUILDER
            .comment("Size of the leaves (multiplier).")
            .translation("rustling_leaves.configuration.leafSize")
            .defineInRange("leafSize", 1.0, 0.3, 3.0);
    private static final ForgeConfigSpec.DoubleValue AUTUMN_COLORS = BUILDER
            .comment("Share of leaves that fall in autumn colors (yellow, orange, red, brown) instead of the tree's color.")
            .translation("rustling_leaves.configuration.autumnColors")
            .defineInRange("autumnColors", 0.35, 0.0, 1.0);
    private static final ForgeConfigSpec.IntValue LITTER_DISTANCE = BUILDER
            .comment("How far away lying leaves are shown, in blocks (also limited by the render distance).",
                    "Beyond about 70 blocks they are drawn as a cheap simplified layer.")
            .translation("rustling_leaves.configuration.litterDistance")
            .defineInRange("litterDistance", 128, 48, 256);
    private static final ForgeConfigSpec.IntValue LEAVES_PER_BREAK = BUILDER
            .comment("Leaves released when a leaves block is broken or decays.")
            .translation("rustling_leaves.configuration.leavesPerBreak")
            .defineInRange("leavesPerBreak", 14, 0, 64);

    static {
        BUILDER.pop().translation("rustling_leaves.configuration.physics").push("physics");
    }

    private static final ForgeConfigSpec.DoubleValue WIND_STRENGTH = BUILDER
            .comment("Strength of the wind (multiplier, 0 for still air). Wind is stronger in rain and thunderstorms.")
            .translation("rustling_leaves.configuration.windStrength")
            .defineInRange("windStrength", 1.0, 0.0, 4.0);
    private static final ForgeConfigSpec.DoubleValue WIND_EVENTS = BUILDER
            .comment("How often squalls (gust fronts that strip the trees) and leaf whirlwinds happen (multiplier, 0 disables).")
            .translation("rustling_leaves.configuration.windEvents")
            .defineInRange("windEvents", 1.0, 0.0, 10.0);
    private static final ForgeConfigSpec.DoubleValue ENTITY_STRENGTH = BUILDER
            .comment("How strongly players, mobs and projectiles stir up leaves (multiplier, 0 disables).")
            .translation("rustling_leaves.configuration.entityStrength")
            .defineInRange("entityStrength", 1.0, 0.0, 3.0);
    private static final ForgeConfigSpec.DoubleValue EXPLOSION_STRENGTH = BUILDER
            .comment("How strongly explosions blow leaves away (multiplier, 0 disables).")
            .translation("rustling_leaves.configuration.explosionStrength")
            .defineInRange("explosionStrength", 1.0, 0.0, 3.0);

    private static final ForgeConfigSpec.BooleanValue RAKING = BUILDER
            .comment("Right click leaf litter with a hoe or shovel to rake it into a pile (instead of tilling or making a path).")
            .translation("rustling_leaves.configuration.raking")
            .define("raking", true);

    static {
        BUILDER.pop().translation("rustling_leaves.configuration.sound").push("sound");
    }

    private static final ForgeConfigSpec.DoubleValue RUSTLE_VOLUME = BUILDER
            .comment("Volume of the rustle when you walk through leaves (0 mutes it).")
            .translation("rustling_leaves.configuration.rustleVolume")
            .defineInRange("rustleVolume", 0.6, 0.0, 1.0);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    /** Copies the loaded values into the simulation settings. */
    public static void apply(LeafSettings settings) {
        settings.maxLeaves = MAX_LEAVES.get();
        settings.fallRate = FALL_RATE.get().floatValue();
        settings.treeFallRates = TREE_FALL_RATES.get();
        settings.spawnRadius = SPAWN_RADIUS.get();
        settings.carpetDepth = CARPET_DEPTH.get();
        settings.naturalPiles = NATURAL_PILES.get();
        settings.windEvents = WIND_EVENTS.get().floatValue();
        settings.raking = RAKING.get();
        settings.leafSize = LEAF_SIZE.get().floatValue();
        settings.autumnColors = AUTUMN_COLORS.get().floatValue();
        settings.leavesPerBreak = LEAVES_PER_BREAK.get();
        settings.litterDistance = LITTER_DISTANCE.get();
        settings.windStrength = WIND_STRENGTH.get().floatValue();
        settings.entityStrength = ENTITY_STRENGTH.get().floatValue();
        settings.explosionStrength = EXPLOSION_STRENGTH.get().floatValue();
        settings.rustleVolume = RUSTLE_VOLUME.get().floatValue();
    }
}
