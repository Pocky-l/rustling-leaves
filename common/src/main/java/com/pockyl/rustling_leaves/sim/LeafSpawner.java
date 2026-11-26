package com.pockyl.rustling_leaves.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Drops leaves from tree canopies around the camera. Each tick a fixed number of random columns is probed through the
 * heightmap (one lookup each), so the cost does not depend on how many trees there are. Gusts shake more leaves off;
 * a passing squall strips trees in a band that sweeps across the forest. The season scales how many leaves fall.
 */
public final class LeafSpawner {
    private static final int SAMPLES = 48;
    private static final float CHANCE = 0.04F;
    private static final int MAX_CANOPY_DEPTH = 10;

    private final TreeLeaves trees;
    private final RandomSource random = RandomSource.createThreadLocalInstance();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LeafSpawner(TreeLeaves trees) {
        this.trees = trees;
    }

    public void tick(Level level, LeafSimulation simulation, double cameraX, double cameraZ) {
        LeafSettings settings = simulation.settings();
        float season = Math.max(settings.seasonFallRate, settings.seasonPetalRate);
        float rate = settings.fallRate * season;
        if (rate <= 0.0F || simulation.pool().free() < 32) {
            return;
        }
        Wind wind = simulation.wind();
        boolean squall = wind.squall(cameraX, cameraZ) > 0.0F || wind.speed(cameraX, 64, cameraZ) > 0.15F;
        // Low rates probe fewer columns (rounded at random, so winter's trickle does not round down to nothing).
        float budget = SAMPLES * Math.min(1.0F, rate);
        int samples = (Mth.floor(budget) + (random.nextFloat() < Mth.frac(budget) ? 1 : 0)) * (squall ? 3 : 1);
        float chance = CHANCE * Math.max(1.0F, rate) * (0.6F + wind.rain() * 0.5F);
        float radius = settings.spawnRadius;
        for (int s = 0; s < samples; s++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float distance = radius * Mth.sqrt(random.nextFloat());
            int x = Mth.floor(cameraX + Mth.cos(angle) * distance);
            int z = Mth.floor(cameraZ + Mth.sin(angle) * distance);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (top < level.getMinY()) {
                continue;
            }
            BlockState state = level.getBlockState(cursor.set(x, top, z));
            if (!sheds(state, settings)) {
                continue;
            }
            TreeProfile profile = TreeKinds.profile(trees.kind(state));
            // Stronger wind at this spot shakes more leaves off: gust waves, squall fronts, storms. Vanilla cherry trees
            // already shed at their own pace through dropBelow, so only the wind adds petals here.
            float windHere = wind.speed(x, top, z);
            float calm = trees.dropsVanillaLeaves(state) ? 0.0F : 1.0F;
            float local = chance * (calm + windHere * 12.0F + wind.squall(x, z) * 10.0F);
            // The sampling budget is set by the busiest season multiplier (leaves or petals); each tree gets its own share.
            if (random.nextFloat() >= local * profile.shedRate(settings) * settings.seasonRate(profile.shape) / season) {
                continue;
            }
            int bottom = top;
            while (bottom > top - MAX_CANOPY_DEPTH && level.getBlockState(cursor.set(x, bottom - 1, z)).is(BlockTags.LEAVES)) {
                bottom--;
            }
            detach(level, simulation, state, x, z, bottom, top, windHere * 0.8F, wind);
        }
    }

    /**
     * Whether a block is the crown of a tree that drops leaves: any leaves block, but leaves placed by players
     * (persistent) only with {@link LeafSettings#placedLeaves}.
     */
    public static boolean sheds(BlockState state, LeafSettings settings) {
        return state.is(BlockTags.LEAVES) && (settings.placedLeaves || !state.hasProperty(LeavesBlock.PERSISTENT)
                || !state.getValue(LeavesBlock.PERSISTENT));
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
     * A vanilla falling leaf particle is about to spawn under the leaves block at {@code pos} (every leaves block drops
     * them since 1.21.5). Cherry petals become simulated petals one by one ({@link #dropBelow}); the falling leaves of
     * every other tree are the canopy spawner's work, as they were before vanilla had them, so their particle is only
     * left out. Returns whether the vanilla particle is replaced: false while falling leaves are switched off.
     */
    public boolean replaceVanillaLeaf(Level level, LeafSimulation simulation, BlockState state, BlockPos pos) {
        if (!TreeKinds.profile(trees.kind(state)).enabled) {
            // The player switched this tree off: its vanilla particles fall instead.
            return false;
        }
        if (trees.dropsVanillaLeaves(state)) {
            dropBelow(level, simulation, state, pos);
            return true;
        }
        return simulation.settings().fallRate > 0.0F;
    }

    /**
     * One leaf from under the leaves block at {@code pos}, where vanilla spawns its cherry petal particle; the fall
     * rate and the season scale how many come (rate 2 = two leaves, 0.5 = every other one).
     */
    public void dropBelow(Level level, LeafSimulation simulation, BlockState state, BlockPos pos) {
        LeafSettings settings = simulation.settings();
        TreeProfile profile = TreeKinds.profile(trees.kind(state));
        float rate = profile.enabled ? settings.fallRate * settings.seasonRate(profile.shape) : 0.0F;
        int count = Mth.floor(rate) + (random.nextFloat() < Mth.frac(rate) ? 1 : 0);
        Wind wind = simulation.wind();
        float push = wind.speed(pos.getX(), pos.getY(), pos.getZ()) * 0.8F;
        for (int n = 0; n < count && simulation.pool().free() > 0; n++) {
            spawn(level, simulation, state, pos, pos.getX() + random.nextDouble(), pos.getY() - 0.05, pos.getZ() + random.nextDouble(),
                    wind.dirX() * push, -0.01F, wind.dirZ() * push, true);
        }
    }

    /**
     * A player hit a leaves block (a punch, or every swing while mining it): a few leaves shake loose from the face that
     * was hit, pushed the way the blow went, and from the underside when nothing is below.
     *
     * @param face  the face that was hit
     * @param pushX horizontal direction of the blow (unit or zero)
     * @param pushZ horizontal direction of the blow (unit or zero)
     */
    public void shake(Level level, LeafSimulation simulation, BlockPos pos, BlockState state, Direction face, float pushX, float pushZ,
            int count) {
        if (count <= 0 || !TreeKinds.profile(trees.kind(state)).enabled) {
            return;
        }
        boolean openBelow = level.getBlockState(cursor.set(pos.getX(), pos.getY() - 1, pos.getZ())).getCollisionShape(level, cursor).isEmpty();
        for (int n = 0; n < count && simulation.pool().free() > 0; n++) {
            Direction from = openBelow && random.nextFloat() < 0.4F ? Direction.DOWN : face;
            double x = pos.getX() + 0.1 + random.nextDouble() * 0.8;
            double y = pos.getY() + 0.1 + random.nextDouble() * 0.8;
            double z = pos.getZ() + 0.1 + random.nextDouble() * 0.8;
            // Just outside the face, so the leaf is seen from the moment it comes off.
            x = from.getStepX() == 0 ? x : pos.getX() + 0.5 + from.getStepX() * 0.55;
            y = from.getStepY() == 0 ? y : pos.getY() + 0.5 + from.getStepY() * 0.55;
            z = from.getStepZ() == 0 ? z : pos.getZ() + 0.5 + from.getStepZ() * 0.55;
            float out = 0.02F + random.nextFloat() * 0.03F;
            float vx = from.getStepX() * out + pushX * 0.04F + (random.nextFloat() - 0.5F) * 0.04F;
            float vy = from.getStepY() * out + random.nextFloat() * 0.02F;
            float vz = from.getStepZ() * out + pushZ * 0.04F + (random.nextFloat() - 0.5F) * 0.04F;
            int i = spawn(level, simulation, state, pos, x, y, z, vx, vy, vz, false);
            if (i >= 0) {
                simulation.pool().spinPitch[i] += (random.nextFloat() - 0.5F) * 0.4F;
                simulation.pool().spinRoll[i] += (random.nextFloat() - 0.5F) * 0.4F;
            }
        }
    }

    /** A leaves block was broken or decayed: a burst of its leaves flies out of it. */
    public void burst(Level level, LeafSimulation simulation, BlockPos pos, BlockState state) {
        int count = TreeKinds.profile(trees.kind(state)).enabled ? simulation.settings().leavesPerBreak : 0;
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
        int kind = trees.kind(state);
        TreeProfile profile = TreeKinds.profile(kind);
        int base = trees.color(state, level, colorPos);
        int color = profile.vary(base, random.nextLong(), profile.autumnShare(settings));
        int sprite = profile.sprite(random.nextFloat());
        float size = profile.leafSize(settings, random.nextFloat());
        return simulation.spawn(x, y, z, vx, vy, vz, color, base, kind, sprite, size, natural);
    }
}
