package com.pockyl.rustling_leaves.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.pockyl.rustling_leaves.sim.LeafPalette;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.LeafSimulation;
import com.pockyl.rustling_leaves.sim.Wind;

/**
 * Drops leaves from tree canopies around the camera. Each tick a fixed number of random columns is probed through the
 * heightmap (one lookup each), so the cost does not depend on how many trees there are. Gusts shake more leaves off;
 * a passing squall strips trees in a band that sweeps across the forest.
 */
final class LeafSpawner {
    private static final int SAMPLES = 48;
    private static final float CHANCE = 0.04F;
    private static final int MAX_CANOPY_DEPTH = 10;

    private final LeafColors colors;
    private final RandomSource random = RandomSource.createNewThreadLocalInstance();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    LeafSpawner(LeafColors colors) {
        this.colors = colors;
    }

    void tick(Level level, LeafSimulation simulation, double cameraX, double cameraZ) {
        LeafSettings settings = simulation.settings();
        if (settings.fallRate <= 0.0F || simulation.pool().free() < 32) {
            return;
        }
        Wind wind = simulation.wind();
        boolean squall = wind.squall(cameraX, cameraZ) > 0.0F || wind.speed(cameraX, 64, cameraZ) > 0.15F;
        int samples = Math.round(SAMPLES * Math.min(1.0F, settings.fallRate)) * (squall ? 3 : 1);
        float chance = CHANCE * Math.max(1.0F, settings.fallRate) * (0.6F + wind.rain() * 0.5F);
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
            // Stronger wind at this spot shakes more leaves off: gust waves, squall fronts, storms.
            float windHere = wind.speed(x, top, z);
            float local = chance * (1.0F + windHere * 12.0F + wind.squall(x, z) * 10.0F);
            BlockState state = level.getBlockState(cursor.set(x, top, z));
            if (!state.is(BlockTags.LEAVES) || random.nextFloat() >= local) {
                continue;
            }
            int bottom = top;
            while (bottom > top - MAX_CANOPY_DEPTH && level.getBlockState(cursor.set(x, bottom - 1, z)).is(BlockTags.LEAVES)) {
                bottom--;
            }
            // Start anywhere inside the canopy; foliage only slows leaves down, so they drift out of it naturally.
            double y = bottom + random.nextDouble() * (top - bottom + 1);
            float push = windHere * 0.8F;
            spawn(level, simulation, state, cursor.set(x, Mth.floor(y), z), x + random.nextDouble(), y, z + random.nextDouble(),
                    wind.dirX() * push, 0.0F, wind.dirZ() * push, true);
        }
    }

    /** A leaves block was broken or decayed: a burst of its leaves flies out of it. */
    void burst(Level level, LeafSimulation simulation, BlockPos pos, BlockState state) {
        int count = simulation.settings().leavesPerBreak;
        for (int n = 0; n < count; n++) {
            float vx = (random.nextFloat() - 0.5F) * 0.24F;
            float vy = random.nextFloat() * 0.084F;
            float vz = (random.nextFloat() - 0.5F) * 0.24F;
            spawn(level, simulation, state, pos, pos.getX() + 0.1 + random.nextDouble() * 0.8, pos.getY() + 0.1 + random.nextDouble() * 0.8,
                    pos.getZ() + 0.1 + random.nextDouble() * 0.8, vx, vy, vz, false);
        }
    }

    private void spawn(Level level, LeafSimulation simulation, BlockState state, BlockPos colorPos, double x, double y, double z, float vx,
            float vy, float vz, boolean natural) {
        LeafSettings settings = simulation.settings();
        LeafShape shape = LeafShapes.of(state.getBlock());
        int base = colors.base(state, level, colorPos);
        int color = LeafPalette.vary(base, random.nextLong(), settings.autumnColors, shape);
        int sprite = shape.firstSprite + random.nextInt(shape.variants);
        float size = LeafShape.BASE_SIZE * shape.size * settings.leafSize * (0.8F + random.nextFloat() * 0.45F);
        simulation.spawn(x, y, z, vx, vy, vz, color, base, shape, sprite, size, natural);
    }
}
