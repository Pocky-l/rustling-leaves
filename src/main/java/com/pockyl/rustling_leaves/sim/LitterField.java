package com.pockyl.rustling_leaves.sim;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;

/**
 * The leaf litter on the ground: every leaf that came to rest lives here as part of a stack in a quarter-block cell
 * (see {@link LitterChunk}). Piles of any height cost a few bytes per cell, and only the leaves that can be seen are
 * ever drawn. Leaves become simulated particles again only when something moves them.
 *
 * <p>Piles obey an angle of repose: a cell may stand at most {@link #REPOSE} above a neighbor, otherwise leaves slide
 * down ({@link #relax}). Leaves never stay on a drop-off edge.
 */
public final class LitterField {
    public static final float CELL = 0.25F;
    /** Height one leaf adds to a stack; leaf piles are fluffy. */
    public static final float LAYER = 0.012F;
    /** Maximum height difference between neighboring cells (about 38 degrees). */
    public static final float REPOSE = 0.2F;
    public static final int MAX_LAYERS = 160;
    private static final int RELAX_BUDGET = 6000;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private final LeafSettings settings;
    private final Terrain terrain;
    private final Long2ObjectOpenHashMap<LitterChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final LongArrayFIFOQueue relaxQueue = new LongArrayFIFOQueue();
    private final int[] direction = new int[1];
    private LitterChunk last;

    LitterField(LeafSettings settings, Terrain terrain) {
        this.settings = settings;
        this.terrain = terrain;
    }

    /** Called when leaves would slide over an edge; returns false if the leaf could not be released. */
    interface Spill {
        boolean spill(int fromX, int fromZ, int toX, int toZ);
    }

    /** One leaf of the litter, as drawn and as released into the air. */
    public static final class Pose {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public float roll;
        public float size;
        public int color;
        public int baseColor;
        public int sprite;
        public int shape;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Chunks
    // ------------------------------------------------------------------------------------------------------------

    public static int cell(double coordinate) {
        return Mth.floor(coordinate * 4.0);
    }

    public static int index(int cellX, int cellZ) {
        return LitterChunk.index(cellX & 63, cellZ & 63);
    }

    public LitterChunk chunk(int chunkX, int chunkZ) {
        LitterChunk cached = last;
        if (cached != null && cached.x == chunkX && cached.z == chunkZ) {
            return cached;
        }
        LitterChunk chunk = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk != null) {
            last = chunk;
        }
        return chunk;
    }

    public LitterChunk chunkAtCell(int cellX, int cellZ) {
        return chunk(cellX >> 6, cellZ >> 6);
    }

    public void put(LitterChunk chunk) {
        chunks.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
    }

    public LitterChunk remove(int chunkX, int chunkZ) {
        last = null;
        return chunks.remove(ChunkPos.asLong(chunkX, chunkZ));
    }

    public Collection<LitterChunk> chunks() {
        return chunks.values();
    }

    public void clear() {
        chunks.clear();
        relaxQueue.clear();
        last = null;
    }

    /** Leaves in a cell, 0 if empty or not loaded. */
    public int count(int cellX, int cellZ) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        return chunk == null ? 0 : chunk.count[index(cellX, cellZ)];
    }

    /** Height of the top of a cell's stack, NaN if the cell is empty or not loaded. */
    public double top(int cellX, int cellZ) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        if (chunk == null) {
            return Double.NaN;
        }
        int i = index(cellX, cellZ);
        return chunk.count[i] == 0 ? Double.NaN : chunk.base[i] + chunk.count[i] * LAYER;
    }

    /** Total number of leaves in loaded chunks. */
    public long total() {
        long sum = 0;
        for (LitterChunk chunk : chunks.values()) {
            sum += chunk.total;
        }
        return sum;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Adding and removing leaves
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Adds a leaf on top of a cell without any slope check. If the cell is empty, {@code base} becomes its surface.
     *
     * @param topPose the exact pose of this leaf ({@link #encodeTop}), or 0 to use a hashed pose
     * @return false if the stack is full
     */
    public boolean add(int cellX, int cellZ, double base, int baseColor, int shape, long topPose, int topColor) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        if (chunk == null) {
            return false;
        }
        int i = index(cellX, cellZ);
        int n = chunk.count[i];
        if (n >= MAX_LAYERS) {
            return false;
        }
        if (n == 0) {
            chunk.base[i] = (float) base;
            chunk.color[i] = baseColor;
            chunk.shape[i] = (byte) shape;
        } else {
            chunk.color[i] = LeafPalette.lerp(chunk.color[i], baseColor, 1.0F / (n + 1));
        }
        chunk.count[i] = (short) (n + 1);
        chunk.top[i] = topPose;
        chunk.topColor[i] = topColor;
        chunk.total++;
        touch(chunk, cellX, cellZ);
        return true;
    }

    /** Takes the top leaf of a cell; fills {@code out} with its pose if given. */
    public boolean take(int cellX, int cellZ, Pose out) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        if (chunk == null) {
            return false;
        }
        int i = index(cellX, cellZ);
        int n = chunk.count[i];
        if (n == 0) {
            return false;
        }
        if (out != null) {
            pose(chunk, cellX, cellZ, n - 1, out);
        }
        chunk.count[i] = (short) (n - 1);
        chunk.top[i] = 0L;
        chunk.total--;
        touch(chunk, cellX, cellZ);
        return true;
    }

    /** Moves the whole stack of a cell to a new surface height (its support moved). */
    public void setBase(int cellX, int cellZ, double base) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        if (chunk != null) {
            chunk.base[index(cellX, cellZ)] = (float) base;
            touch(chunk, cellX, cellZ);
        }
    }

    /**
     * Replaces the top leaf of a full enough cell by a new one: the carpet stays as thick, but the leaf that just landed
     * is the one on top.
     */
    public void replaceTop(int cellX, int cellZ, long topPose, int topColor) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        if (chunk != null) {
            int i = index(cellX, cellZ);
            chunk.top[i] = topPose;
            chunk.topColor[i] = topColor;
            touch(chunk, cellX, cellZ);
        }
    }

    /**
     * Moves one leaf from a cell to another without making it a particle. An empty target cell gets {@code targetBase}
     * as its surface. Returns false (and moves nothing) if not possible.
     */
    public boolean move(int fromX, int fromZ, int toX, int toZ, double targetBase) {
        LitterChunk from = chunkAtCell(fromX, fromZ);
        LitterChunk to = chunkAtCell(toX, toZ);
        if (from == null || to == null) {
            return false;
        }
        int i = index(fromX, fromZ);
        int j = index(toX, toZ);
        if (from.count[i] == 0 || to.count[j] >= MAX_LAYERS || to.count[j] == 0 && Double.isNaN(targetBase)) {
            return false;
        }
        int color = from.color[i];
        int shape = from.shape[i];
        take(fromX, fromZ, null);
        add(toX, toZ, to.count[j] > 0 ? to.base[j] : targetBase, color, shape, 0L, 0);
        queueRelax(toX, toZ);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Slopes
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Surface height a leaf would see in a neighboring cell, starting from height {@code from}: the top of its stack,
     * a block face at or above {@code from} (a wall), the ground below, or negative infinity for a drop-off edge.
     * Unloaded cells count as walls.
     */
    double neighborTop(int cellX, int cellZ, double from) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        if (chunk == null) {
            return Double.POSITIVE_INFINITY;
        }
        int i = index(cellX, cellZ);
        if (chunk.count[i] > 0) {
            return chunk.base[i] + chunk.count[i] * LAYER;
        }
        double x = (cellX + 0.5) * CELL;
        double z = (cellZ + 0.5) * CELL;
        double wall = terrain.solidTop(x, from, z);
        if (!Double.isNaN(wall)) {
            return wall;
        }
        double ground = terrain.groundBelow(x, from, z, 2);
        return Double.isNaN(ground) ? Double.NEGATIVE_INFINITY : ground;
    }

    /** The lowest of the four neighbors as seen from height {@code from}; its direction index goes to {@code dir[0]}. */
    double lowestNeighbor(int cellX, int cellZ, double from, int[] dir) {
        double lowest = Double.POSITIVE_INFINITY;
        dir[0] = 0;
        for (int d = 0; d < 4; d++) {
            double top = neighborTop(cellX + DX[d], cellZ + DZ[d], from);
            if (top < lowest) {
                lowest = top;
                dir[0] = d;
            }
        }
        return lowest;
    }

    static int dirX(int dir) {
        return DX[dir];
    }

    static int dirZ(int dir) {
        return DZ[dir];
    }

    public void queueRelax(int cellX, int cellZ) {
        relaxQueue.enqueue(ChunkPos.asLong(cellX, cellZ));
    }

    /**
     * Lets queued cells slide down until no cell stands more than {@link #REPOSE} above a neighbor: leaves move to the
     * lowest neighbor, over drop-off edges they are handed to {@code spill}.
     */
    void relax(Spill spill) {
        int budget = RELAX_BUDGET;
        while (!relaxQueue.isEmpty() && budget-- > 0) {
            long key = relaxQueue.dequeueLong();
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            LitterChunk chunk = chunkAtCell(cx, cz);
            if (chunk == null) {
                continue;
            }
            int i = index(cx, cz);
            int n = chunk.count[i];
            // Only the pile's own height can make it slide: thin litter rests on steps and block edges.
            if (n * LAYER <= REPOSE + LAYER) {
                continue;
            }
            double top = chunk.base[i] + n * LAYER;
            double lowest = lowestNeighbor(cx, cz, top, direction);
            double drop = Math.min(top - lowest, n * LAYER);
            if (drop <= REPOSE + LAYER) {
                continue;
            }
            int tx = cx + DX[direction[0]];
            int tz = cz + DZ[direction[0]];
            if (lowest == Double.NEGATIVE_INFINITY) {
                int released = 0;
                while (released < 4 && chunk.count[i] > 0 && spill.spill(cx, cz, tx, tz)) {
                    released++;
                }
                if (released > 0) {
                    relaxQueue.enqueue(key);
                }
                continue;
            }
            int moves = Mth.clamp((int) ((drop - REPOSE) / (2 * LAYER)), 1, n);
            for (int m = 0; m < moves; m++) {
                if (!move(cx, cz, tx, tz, lowest)) {
                    break;
                }
            }
            relaxQueue.enqueue(key);
        }
        if (budget <= 0) {
            relaxQueue.clear();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Poses
    // ------------------------------------------------------------------------------------------------------------

    /** Packs the exact pose of a leaf for {@link LitterChunk#top}. Offsets are in cells relative to the cell corner. */
    public static long encodeTop(float offX, float offZ, float yaw, float size, int sprite) {
        long yawQ = quantize(Mth.positiveModulo(yaw, Mth.TWO_PI), 0.0F, Mth.TWO_PI);
        return 1L << 63 | quantize(offX, -0.2F, 1.2F) | quantize(offZ, -0.2F, 1.2F) << 10 | yawQ << 20
                | quantize(size, 0.0F, 0.6F) << 30 | (long) (sprite & 15) << 40;
    }

    private static long quantize(float value, float min, float max) {
        return Mth.clamp(Math.round((value - min) / (max - min) * 1023.0F), 0, 1023);
    }

    private static float dequantize(long bits, float min, float max) {
        return min + (bits & 1023) / 1023.0F * (max - min);
    }

    /** Pose of the leaf at {@code layer} (0 = bottom) of a cell. */
    public void pose(LitterChunk chunk, int cellX, int cellZ, int layer, Pose out) {
        int i = index(cellX, cellZ);
        int n = chunk.count[i];
        long hash = LeafPalette.hash(cellX, cellZ, layer);
        LeafShape shape = LeafShape.byId(chunk.shape[i]);
        float offX;
        float offZ;
        long top = chunk.top[i];
        if (layer == n - 1 && top != 0L) {
            offX = dequantize(top, -0.2F, 1.2F);
            offZ = dequantize(top >>> 10, -0.2F, 1.2F);
            out.yaw = dequantize(top >>> 20, 0.0F, Mth.TWO_PI);
            out.size = dequantize(top >>> 30, 0.0F, 0.6F);
            out.sprite = (int) (top >>> 40 & 15);
            out.color = chunk.topColor[i];
        } else {
            offX = -0.15F + unit(hash, 0) * 1.3F;
            offZ = -0.15F + unit(hash, 10) * 1.3F;
            out.yaw = unit(hash, 20) * Mth.TWO_PI;
            out.size = LeafShape.BASE_SIZE * shape.size * settings.leafSize * (0.8F + unit(hash, 30) * 0.45F);
            out.sprite = shape.firstSprite + Math.min(shape.variants - 1, (int) (unit(hash, 40) * shape.variants));
            out.color = LeafPalette.vary(chunk.color[i], hash, Math.min(1.0F, settings.autumnColors + 0.3F), shape);
        }
        out.x = (cellX + offX) * CELL;
        out.z = (cellZ + offZ) * CELL;
        out.y = chunk.base[i] + (layer + 0.5F + (unit(hash, 50) - 0.5F) * 0.5F) * LAYER + 0.004;
        out.baseColor = chunk.color[i];
        out.shape = shape.ordinal();
        restTilt(chunk, cellX, cellZ, layer, out.yaw, out);
    }

    /** Sets {@code pitch} and {@code roll} of a leaf lying in a cell so it follows the slope of the pile. */
    void restTilt(LitterChunk chunk, int cellX, int cellZ, int layer, float yaw, Pose out) {
        int i = index(cellX, cellZ);
        double self = chunk.count[i] > 0 ? chunk.base[i] + chunk.count[i] * LAYER : chunk.base[i];
        double east = slopeNeighbor(cellX + 1, cellZ, chunk.base[i], self);
        double west = slopeNeighbor(cellX - 1, cellZ, chunk.base[i], self);
        double south = slopeNeighbor(cellX, cellZ + 1, chunk.base[i], self);
        double north = slopeNeighbor(cellX, cellZ - 1, chunk.base[i], self);
        float gradX = (float) Mth.clamp((east - west) / (2 * CELL), -1.2, 1.2);
        float gradZ = (float) Mth.clamp((south - north) / (2 * CELL), -1.2, 1.2);
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        long hash = LeafPalette.hash(cellZ, layer, cellX);
        out.roll = (float) Math.atan(gradX * cos - gradZ * sin) + (unit(hash, 0) - 0.5F) * 0.24F;
        out.pitch = (float) -Math.atan(gradX * sin + gradZ * cos) + (unit(hash, 10) - 0.5F) * 0.24F;
    }

    /** Top of a neighbor for slope purposes: its stack, or the cell's own ground when empty or unknown. */
    private double slopeNeighbor(int cellX, int cellZ, double base, double self) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        if (chunk == null) {
            return self;
        }
        int i = index(cellX, cellZ);
        if (chunk.count[i] == 0 || Math.abs(chunk.base[i] - base) > 0.6) {
            return base;
        }
        return chunk.base[i] + chunk.count[i] * LAYER;
    }

    private static float unit(long hash, int shift) {
        return (hash >>> shift & 1023) / 1024.0F;
    }

    private void touch(LitterChunk chunk, int cellX, int cellZ) {
        chunk.dirty = true;
        chunk.tileRevision[LitterChunk.tile(index(cellX, cellZ))]++;
        // Neighbors across a tile edge show this cell's slope and height too.
        int lx = cellX & 15;
        int lz = cellZ & 15;
        if (lx == 0) {
            touchTile(cellX - 1, cellZ);
        } else if (lx == 15) {
            touchTile(cellX + 1, cellZ);
        }
        if (lz == 0) {
            touchTile(cellX, cellZ - 1);
        } else if (lz == 15) {
            touchTile(cellX, cellZ + 1);
        }
    }

    private void touchTile(int cellX, int cellZ) {
        LitterChunk chunk = chunkAtCell(cellX, cellZ);
        if (chunk != null) {
            chunk.tileRevision[LitterChunk.tile(index(cellX, cellZ))]++;
        }
    }
}
