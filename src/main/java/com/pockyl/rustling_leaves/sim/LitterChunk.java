package com.pockyl.rustling_leaves.sim;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Leaf litter of one chunk column: a 64x64 grid of quarter-block cells, each holding a stack of leaves on one surface.
 * A cell stores how many leaves lie there, the height of the surface under them, the color of the tree they came
 * from and their shape; individual leaves are reconstructed from a hash of the cell and layer, except for the top
 * leaf, which keeps the exact pose of the last leaf that landed there (so a landing leaf never jumps).
 */
public final class LitterChunk {
    public static final int SIZE = 64;
    public static final int AREA = SIZE * SIZE;
    /** Render tiles are 16x16 cells (4x4 blocks). */
    public static final int TILE = 16;
    public static final int TILES = SIZE / TILE;
    private static final int FORMAT = 1;

    public final int x;
    public final int z;
    public final short[] count = new short[AREA];
    public final float[] base = new float[AREA];
    public final int[] color = new int[AREA];
    public final byte[] shape = new byte[AREA];
    /** Exact pose of the top leaf (see {@link LitterField#encodeTop}), 0 if the top leaf uses the hashed pose. */
    public final long[] top = new long[AREA];
    public final int[] topColor = new int[AREA];
    /**
     * Leaves at the bottom of a stack that stand for a leaf pile block of another mod (see {@link PileBlocks}): they
     * stay as long as the block does and are never saved (the block brings them back).
     */
    public final short[] pinned = new short[AREA];
    /**
     * How many leaves the pile block over a cell stands for. A growing pile is below it: leaves that land in the cell
     * are pinned until it is reached, so the pile fills up with leaves that fell there.
     */
    public final short[] pileTarget = new short[AREA];
    /** Incremented whenever a cell of the tile (or a neighbor that affects its look) changes. */
    public final int[] tileRevision = new int[TILES * TILES];

    int total;
    boolean seeded;
    boolean dirty;
    boolean pilesScanned;

    public LitterChunk(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static int index(int localX, int localZ) {
        return localZ << 6 | localX;
    }

    public static int tile(int index) {
        return (index >> 6 >> 4) * TILES + ((index & 63) >> 4);
    }

    /** Changes whenever any cell of the chunk changes (the sum of the tile revisions). */
    public int revision() {
        int sum = 0;
        for (int t = 0; t < tileRevision.length; t++) {
            sum += tileRevision[t];
        }
        return sum;
    }

    /** Leaves of a cell that can be moved (not pinned by a pile block). */
    public int loose(int index) {
        return count[index] - pinned[index];
    }

    /** Whether the pile blocks of this chunk have been turned into pinned litter. */
    public boolean pilesScanned() {
        return pilesScanned;
    }

    public int total() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public boolean seeded() {
        return seeded;
    }

    /** Fills an empty cell when the chunk is first seen (natural carpet, drifts, piles). */
    public void seedCell(int index, int leaves, float surface, int treeColor, int leafShape) {
        if (count[index] > 0 || leaves <= 0) {
            return;
        }
        int n = Math.min(leaves, LitterField.MAX_LAYERS);
        count[index] = (short) n;
        base[index] = surface;
        color[index] = treeColor;
        shape[index] = (byte) leafShape;
        top[index] = 0L;
        total += n;
    }

    public void markSeeded() {
        for (int t = 0; t < tileRevision.length; t++) {
            tileRevision[t]++;
        }
        seeded = true;
        dirty = true;
    }

    /** Whether the chunk changed since it was last saved; clears the flag. */
    public boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(FORMAT);
        out.writeInt(x);
        out.writeInt(z);
        out.writeBoolean(seeded);
        int cells = 0;
        for (int i = 0; i < AREA; i++) {
            if (loose(i) > 0) {
                cells++;
            }
        }
        out.writeShort(cells);
        for (int i = 0; i < AREA; i++) {
            if (loose(i) > 0) {
                out.writeShort(i);
                out.writeShort(loose(i));
                out.writeFloat(base[i]);
                out.writeInt(color[i]);
                out.writeByte(shape[i]);
                out.writeLong(top[i]);
                out.writeInt(topColor[i]);
            }
        }
    }

    public static LitterChunk read(DataInput in) throws IOException {
        int format = in.readByte();
        if (format != FORMAT) {
            throw new IOException("Unknown litter format " + format);
        }
        LitterChunk chunk = new LitterChunk(in.readInt(), in.readInt());
        chunk.seeded = in.readBoolean();
        int cells = in.readUnsignedShort();
        for (int n = 0; n < cells; n++) {
            int i = in.readUnsignedShort() & (AREA - 1);
            chunk.count[i] = in.readShort();
            chunk.base[i] = in.readFloat();
            chunk.color[i] = in.readInt();
            chunk.shape[i] = in.readByte();
            chunk.top[i] = in.readLong();
            chunk.topColor[i] = in.readInt();
            chunk.total += chunk.count[i];
        }
        return chunk;
    }
}
