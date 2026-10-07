package com.pockyl.rustling_leaves.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CherryLeavesBlock;
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
            BlockState state = level.getBlockState(cursor.set(x, top, z));
            if (!state.is(BlockTags.LEAVES)) {
                continue;
            }
            // Stronger wind at this spot shakes more leaves off: gust waves, squall fronts, storms. Vanilla cherry trees
            // already shed at their own pace through dropBelow, so only the wind adds petals here.
            float windHere = wind.speed(x, top, z);
            float calm = state.getBlock() instanceof CherryLeavesBlock ? 0.0F : 1.0F;
            float local = chance * (calm + windHere * 12.0F + wind.squall(x, z) * 10.0F);
            if (random.nextFloat() >= local * settings.shedRate(LeafShapes.of(state.getBlock()))) {
                continue;
            }
            int bottom = top;
            while (bottom > top - MAX_CANOPY_DEPTH && level.getBlockState(cursor.set(x, bottom - 1, z)).is(BlockTags.LEAVES)) {
                bottom--;
            }
            detach(level, simulation, state, x, z, bottom, top, windHere * 0.8F, wind);
        }
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * A leaf comes off the outside of the crown: from the underside of the lowest leaves (most of the time) or from
     * an open side of a leaves block in the column, so it is seen from the moment it lets go.
     */
    private void detach(Level level, LeafSimulation simulation, BlockState state, int x, int z, int bottom, int top, float push,
            Wind wind) {
        double sx = x + random.nextDouble();
        double sz = z + random.nextDouble();
        double sy = bottom - 0.05;
        float ox = 0.0F;
        float oz = 0.0F;
        boolean below = level.getBlockState(cursor.set(x, bottom - 1, z)).getCollisionShape(level, cursor).isEmpty();
        if (!below || random.nextFloat() < 0.4F) {
            int y = bottom + random.nextInt(top - bottom + 1);
            int[] side = SIDES[random.nextInt(SIDES.length)];
            BlockState neighbor = level.getBlockState(cursor.set(x + side[0], y, z + side[1]));
            if (neighbor.isAir()) {
                sx = side[0] == 0 ? sx : x + (side[0] > 0 ? 1.05 : -0.05);
                sz = side[1] == 0 ? sz : z + (side[1] > 0 ? 1.05 : -0.05);
                sy = y + random.nextDouble();
                ox = side[0] * 0.02F;
                oz = side[1] * 0.02F;
            } else if (!below) {
                return;
            }
        }
        int i = spawn(level, simulation, state, cursor.set(x, Mth.floor(sy), z), sx, sy, sz, wind.dirX() * push + ox, -0.01F,
                wind.dirZ() * push + oz, true);
        if (i >= 0) {
            simulation.pool().spinPitch[i] += (random.nextFloat() - 0.5F) * 0.3F;
            simulation.pool().spinRoll[i] += (random.nextFloat() - 0.5F) * 0.3F;
        }
    }

    /**
     * One leaf from under the leaves block at {@code pos}, where vanilla spawns its cherry petal particle; the fall
     * rate scales how many come (rate 2 = two leaves, 0.5 = every other one).
     */
    void dropBelow(Level level, LeafSimulation simulation, BlockState state, BlockPos pos) {
        LeafSettings settings = simulation.settings();
        int count = Mth.floor(settings.fallRate) + (random.nextFloat() < Mth.frac(settings.fallRate) ? 1 : 0);
        Wind wind = simulation.wind();
        float push = wind.speed(pos.getX(), pos.getY(), pos.getZ()) * 0.8F;
        for (int n = 0; n < count && simulation.pool().free() > 0; n++) {
            spawn(level, simulation, state, pos, pos.getX() + random.nextDouble(), pos.getY() - 0.05, pos.getZ() + random.nextDouble(),
                    wind.dirX() * push, -0.01F, wind.dirZ() * push, true);
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

    private int spawn(Level level, LeafSimulation simulation, BlockState state, BlockPos colorPos, double x, double y, double z, float vx,
            float vy, float vz, boolean natural) {
        LeafSettings settings = simulation.settings();
        LeafShape shape = LeafShapes.of(state.getBlock());
        int base = colors.base(state, level, colorPos);
        int color = LeafPalette.vary(base, random.nextLong(), settings.autumnColors, shape);
        int sprite = shape.firstSprite + random.nextInt(shape.variants);
        float size = LeafShape.BASE_SIZE * shape.size * settings.leafSize * (0.8F + random.nextFloat() * 0.45F);
        return simulation.spawn(x, y, z, vx, vy, vz, color, base, shape, sprite, size, natural);
    }
}
