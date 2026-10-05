package com.pockyl.rustling_leaves.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block queries of the simulation: collision boxes against points and ground probes. Foliage (leaves blocks) is never
 * solid for leaves, they drift through it.
 */
final class Terrain {
    private final ShapeCache shapes = new ShapeCache();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos lightCursor = new BlockPos.MutableBlockPos();
    Level level;

    BlockPos.MutableBlockPos cursor() {
        return cursor;
    }

    /** Top of the collision box containing the point, or NaN when the point is free. */
    double solidTop(double x, double y, double z) {
        cursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockState state = level.getBlockState(cursor);
        if (state.isAir() || state.is(BlockTags.LEAVES)) {
            return Double.NaN;
        }
        return topAt(state, x, y, z);
    }

    /** Like {@link #solidTop} for the block at {@link #cursor}, which must contain the point. */
    double topAt(BlockState state, double x, double y, double z) {
        double[] boxes = shapes.boxes(state, level, cursor);
        double lx = x - cursor.getX();
        double ly = y - cursor.getY();
        double lz = z - cursor.getZ();
        for (int b = 0; b < boxes.length; b += 6) {
            if (lx >= boxes[b] && lx <= boxes[b + 3] && ly >= boxes[b + 1] && ly < boxes[b + 4]
                    && lz >= boxes[b + 2] && lz <= boxes[b + 5]) {
                return cursor.getY() + boxes[b + 4];
            }
        }
        return Double.NaN;
    }

    /**
     * The highest solid surface at or below {@code y} in the column through (x, z), searching {@code depth} blocks
     * down. NaN if there is none, if the column is not loaded, or if water or lava comes first (no litter there).
     */
    double groundBelow(double x, double y, double z, int depth) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = Mth.floor(y);
        cursor.set(bx, top, bz);
        if (!level.isLoaded(cursor)) {
            return Double.NaN;
        }
        double lx = x - bx;
        double lz = z - bz;
        for (int by = top; by >= top - depth; by--) {
            cursor.set(bx, by, bz);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || state.is(BlockTags.LEAVES)) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                return Double.NaN;
            }
            double[] boxes = shapes.boxes(state, level, cursor);
            double best = Double.NEGATIVE_INFINITY;
            double limit = y - by + 1.0E-6;
            for (int b = 0; b < boxes.length; b += 6) {
                if (lx >= boxes[b] && lx <= boxes[b + 3] && lz >= boxes[b + 2] && lz <= boxes[b + 5]
                        && boxes[b + 4] <= limit && boxes[b + 4] > best) {
                    best = boxes[b + 4];
                }
            }
            if (best != Double.NEGATIVE_INFINITY) {
                return by + best;
            }
        }
        return Double.NaN;
    }

    boolean canSeeSky(double x, double y, double z) {
        cursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return level.canSeeSky(cursor);
    }

    /**
     * Packed light coordinates (block light in bits 4..7, sky light in bits 20..23) at a position. Inside a block
     * (a leaf touching a slab, a step or a wall) the stored light is 0 or too low, which would draw the leaf black,
     * so where the position is not in open air the brighter of it and the block above counts.
     */
    int lightAt(double x, double y, double z) {
        lightCursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int sky = level.getBrightness(LightLayer.SKY, lightCursor);
        int block = level.getBrightness(LightLayer.BLOCK, lightCursor);
        BlockState state = level.getBlockState(lightCursor);
        if (!state.isAir() && !state.is(BlockTags.LEAVES)) {
            lightCursor.move(0, 1, 0);
            if (state.isSolidRender(level, lightCursor.below())) {
                sky = level.getBrightness(LightLayer.SKY, lightCursor);
                block = level.getBrightness(LightLayer.BLOCK, lightCursor);
            } else {
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, lightCursor));
                block = Math.max(block, level.getBrightness(LightLayer.BLOCK, lightCursor));
            }
        }
        return block << 4 | sky << 20;
    }

    /**
     * Moves a leaf of half-size {@code half} lying at (x, y, z) out of the blocks around it: away from walls next to
     * it so its edges do not stick into them, and up onto the floor if it sank into it. Returns the corrected position
     * in {@code out[0..2]}.
     */
    void keepClear(double x, double y, double z, float half, double[] out) {
        double reach = half * 0.9;
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        double probeY = y + 0.02;
        double fx = x - bx;
        if (fx < reach && !Double.isNaN(solidTop(bx - 0.01, probeY, z))) {
            x = bx + reach;
        } else if (fx > 1.0 - reach && !Double.isNaN(solidTop(bx + 1.01, probeY, z))) {
            x = bx + 1.0 - reach;
        }
        double fz = z - bz;
        if (fz < reach && !Double.isNaN(solidTop(x, probeY, bz - 0.01))) {
            z = bz + reach;
        } else if (fz > 1.0 - reach && !Double.isNaN(solidTop(x, probeY, bz + 1.01))) {
            z = bz + 1.0 - reach;
        }
        double floor = solidTop(x, y, z);
        if (!Double.isNaN(floor)) {
            y = floor + 0.004;
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }
}
