package com.pockyl.rustling_leaves.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collision boxes of block states as flat arrays {@code [minX, minY, minZ, maxX, maxY, maxZ, ...]} in block-local
 * coordinates, so a point test against slabs, stairs or fences costs a few comparisons and no allocation.
 */
final class ShapeCache {
    private static final double[] EMPTY = new double[0];

    private final Map<BlockState, double[]> cache = new IdentityHashMap<>();

    double[] boxes(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getBlock().hasDynamicShape() || state.hasOffsetFunction()) {
            return compute(state, level, pos);
        }
        double[] boxes = cache.get(state);
        if (boxes == null) {
            boxes = compute(state, level, pos);
            cache.put(state, boxes);
        }
        return boxes;
    }

    /**
     * Fences, walls and fence gates collide up to 1.5 blocks so nothing jumps over them, but a leaf lies on what is
     * drawn: boxes reaching above the block are cut down to the top of its outline.
     */
    private static double[] compute(BlockState state, BlockGetter level, BlockPos pos) {
        double[] boxes = toArray(state.getCollisionShape(level, pos));
        if (boxes.length == 0) {
            return boxes;
        }
        double highest = 0.0;
        for (int b = 4; b < boxes.length; b += 6) {
            highest = Math.max(highest, boxes[b]);
        }
        if (highest <= 1.0) {
            return boxes;
        }
        VoxelShape outline = state.getShape(level, pos);
        double top = outline.isEmpty() ? 1.0 : Math.min(1.0, outline.max(Direction.Axis.Y));
        for (int b = 4; b < boxes.length; b += 6) {
            boxes[b] = Math.max(boxes[b - 3], Math.min(boxes[b], top));
        }
        return boxes;
    }

    /** Whether the boxes fill the whole block. */
    static boolean full(double[] boxes) {
        return boxes.length == 6 && boxes[0] <= 0.0 && boxes[1] <= 0.0 && boxes[2] <= 0.0
                && boxes[3] >= 1.0 && boxes[4] >= 1.0 && boxes[5] >= 1.0;
    }

    private static double[] toArray(VoxelShape shape) {
        if (shape.isEmpty()) {
            return EMPTY;
        }
        List<AABB> list = shape.toAabbs();
        double[] boxes = new double[list.size() * 6];
        for (int i = 0; i < list.size(); i++) {
            AABB box = list.get(i);
            boxes[i * 6] = box.minX;
            boxes[i * 6 + 1] = box.minY;
            boxes[i * 6 + 2] = box.minZ;
            boxes[i * 6 + 3] = box.maxX;
            boxes[i * 6 + 4] = box.maxY;
            boxes[i * 6 + 5] = box.maxZ;
        }
        return boxes;
    }
}
