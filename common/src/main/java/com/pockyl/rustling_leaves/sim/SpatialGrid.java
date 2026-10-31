package com.pockyl.rustling_leaves.sim;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * Hashed 2D grid over the horizontal plane, rebuilt once per tick with a counting sort (two linear passes, no
 * allocation). Turns "which leaves are near this entity / explosion" from a scan of every leaf into a few buckets.
 */
final class SpatialGrid {
    private static final double CELL = 2.0;
    private static final double INV_CELL = 1.0 / CELL;

    private final int mask;
    private final int[] start;
    private final int[] entries;
    private final int[] bucketOf;
    private final int[] stamp;
    private int stampId;

    SpatialGrid(int capacity) {
        int buckets = Integer.highestOneBit(Math.max(1024, capacity) - 1) << 1;
        mask = buckets - 1;
        start = new int[buckets + 1];
        entries = new int[capacity];
        bucketOf = new int[capacity];
        stamp = new int[capacity];
    }

    private int bucket(int cellX, int cellZ) {
        int hash = cellX * 0x9E3779B1 ^ cellZ * 0x85EBCA6B;
        return (hash ^ hash >>> 15) & mask;
    }

    void rebuild(LeafPool pool) {
        int buckets = mask + 1;
        Arrays.fill(start, 0);
        int highWater = pool.highWater();
        int total = 0;
        for (int i = 0; i < highWater; i++) {
            if (pool.state[i] == LeafPool.FREE) {
                bucketOf[i] = -1;
                continue;
            }
            int bucket = bucket(Mth.floor(pool.x[i] * INV_CELL), Mth.floor(pool.z[i] * INV_CELL));
            bucketOf[i] = bucket;
            start[bucket]++;
            total++;
        }
        // Inclusive prefix sums: start[b] is the end of bucket b; filling backwards moves it to the beginning.
        for (int b = 1; b < buckets; b++) {
            start[b] += start[b - 1];
        }
        start[buckets] = total;
        for (int i = highWater - 1; i >= 0; i--) {
            int bucket = bucketOf[i];
            if (bucket >= 0) {
                entries[--start[bucket]] = i;
            }
        }
    }

    /**
     * Collects the leaves whose cell overlaps the horizontal rectangle. The result is a superset (whole cells, hash
     * collisions, leaves that moved or died since the rebuild); callers check the exact distance and state.
     */
    void query(double minX, double minZ, double maxX, double maxZ, IntArrayList out) {
        out.clear();
        if (++stampId == 0) {
            Arrays.fill(stamp, 0);
            stampId = 1;
        }
        int x0 = Mth.floor(minX * INV_CELL);
        int z0 = Mth.floor(minZ * INV_CELL);
        int x1 = Mth.floor(maxX * INV_CELL);
        int z1 = Mth.floor(maxZ * INV_CELL);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                int bucket = bucket(cx, cz);
                for (int k = start[bucket], end = start[bucket + 1]; k < end; k++) {
                    int leaf = entries[k];
                    if (stamp[leaf] != stampId) {
                        stamp[leaf] = stampId;
                        out.add(leaf);
                    }
                }
            }
        }
    }
}
