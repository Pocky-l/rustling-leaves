package com.pockyl.rustling_leaves.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafSimulation;

/**
 * Drops leaves from tree canopies around the camera. Each tick a fixed number of random columns is probed through the
 * heightmap (one lookup each), so the cost does not depend on how many trees there are.
 */
final class LeafSpawner {
    private static final int SAMPLES = 48;
    private static final float CHANCE = 0.04F;
    private static final float BASE_SIZE = 0.1F;
    private static final int MAX_CANOPY_DEPTH = 10;

    private final LeafColors colors = new LeafColors();
    private final RandomSource random = RandomSource.createNewThreadLocalInstance();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    void tick(Level level, LeafSimulation simulation, double cameraX, double cameraZ) {
        LeafSettings settings = simulation.settings();
        if (settings.fallRate <= 0.0F) {
            return;
        }
        int samples = Math.round(SAMPLES * Math.min(1.0F, settings.fallRate));
        // A gust shakes more leaves off the trees.
        float gust = simulation.wind().gust(cameraX, cameraZ);
        float chance = CHANCE * Math.max(1.0F, settings.fallRate) * (0.6F + 0.8F * gust + simulation.wind().rain() * 0.5F);
        float radius = settings.spawnRadius;
        for (int s = 0; s < samples; s++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float distance = radius * Mth.sqrt(random.nextFloat());
            int x = Mth.floor(cameraX + Mth.cos(angle) * distance);
            int z = Mth.floor(cameraZ + Mth.sin(angle) * distance);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (top < level.getMinBuildHeight()) {
                continue;
            }
            BlockState state = level.getBlockState(cursor.set(x, top, z));
            if (!state.is(BlockTags.LEAVES) || random.nextFloat() >= chance) {
                continue;
            }
            int bottom = top;
            while (bottom > top - MAX_CANOPY_DEPTH && level.getBlockState(cursor.set(x, bottom - 1, z)).is(BlockTags.LEAVES)) {
                bottom--;
            }
            // Start anywhere inside the canopy; foliage only slows leaves down, so they drift out of it naturally.
            double y = bottom + random.nextDouble() * (top - bottom + 1);
            spawn(level, simulation, state, cursor.set(x, Mth.floor(y), z), x + random.nextDouble(), y, z + random.nextDouble(), 0.0F);
        }
    }

    /** A leaves block was broken or decayed: a burst of its leaves flies out of it. */
    void burst(Level level, LeafSimulation simulation, BlockPos pos, BlockState state) {
        int count = simulation.settings().leavesPerBreak;
        for (int n = 0; n < count; n++) {
            spawn(level, simulation, state, pos, pos.getX() + 0.1 + random.nextDouble() * 0.8, pos.getY() + 0.1 + random.nextDouble() * 0.8,
                    pos.getZ() + 0.1 + random.nextDouble() * 0.8, 0.12F);
        }
    }

    private void spawn(Level level, LeafSimulation simulation, BlockState state, BlockPos colorPos, double x, double y, double z, float burst) {
        LeafSettings settings = simulation.settings();
        LeafKind kind = LeafKind.of(state.getBlock());
        int color = colors.pick(state, level, colorPos, kind, settings.autumnColors, random);
        int sprite = kind.firstSprite + random.nextInt(kind.variants);
        float size = BASE_SIZE * kind.size * settings.leafSize * (0.8F + random.nextFloat() * 0.45F);
        float vx = (random.nextFloat() - 0.5F) * 2.0F * burst;
        float vy = random.nextFloat() * burst * 0.7F;
        float vz = (random.nextFloat() - 0.5F) * 2.0F * burst;
        simulation.spawn(x, y, z, vx, vy, vz, color, sprite, size);
    }

    void clearCaches() {
        colors.clear();
    }
}
