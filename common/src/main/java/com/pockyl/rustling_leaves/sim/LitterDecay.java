package com.pockyl.rustling_leaves.sim;

/**
 * Lying leaves rot away. A cell with {@code n} loose leaves loses them at a rate of {@code (n + FLOOR)} times the
 * current decay rate: piles shrink faster than a thin carpet and flatten out as they go, and the last few leaves of a
 * cell do not linger forever. Leaves go from the top of a stack, one cell at a time, and near the camera some of them
 * visibly crumble away instead of just vanishing. Leaves pinned by a pile block belong to the block and never rot.
 *
 * <p>Every litter chunk remembers the game time up to which its rot was applied, so a chunk that was unloaded or never
 * visited catches up on everything it missed when it comes back. Loaded chunks rot in steps, one chunk per tick (the one
 * that waited longest), so the cost per tick is one pass over 64x64 cells at most.
 *
 * <p>Snow does not cover leaves: litter under a snow layer or on snow rots within about a minute.
 */
final class LitterDecay {
    /** Depth of the natural carpet whose lifetime the decay rate is measured in. */
    static final int CARPET = 4;
    /** Thin litter rots as if it had this many leaves more, so the last leaves of a cell go too. */
    private static final float FLOOR = 2.0F;
    /** With rate {@code k} (carpets per tick) a stack of {@code n} decays as (n + FLOOR) e^(-k LOG_CARPET t) - FLOOR. */
    private static final double LOG_CARPET = Math.log(1.0 + CARPET / FLOOR);
    /** Rate on or under snow, in carpets per tick: a carpet is gone in a minute. */
    private static final float SNOW_RATE = 1.0F / 1200.0F;
    /** A loaded chunk rots in steps at least this many ticks apart. */
    static final int INTERVAL = 200;
    /** Leaves per step that visibly crumble away near the camera; the rest just go. */
    private static final int CRUMBLE_PER_STEP = 24;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    /** Gets a say in every leaf that rots in a loaded chunk near the camera. */
    interface Crumble {
        /** The top leaf of the cell rots; returns true if it was taken as a particle that crumbles away. */
        boolean crumble(int cellX, int cellZ);
    }

    private final LeafSettings settings;
    private final LitterField field;
    private final Terrain terrain;
    private final FastRandom random;
    private final Crumble crumble;
    private int crumbleBudget;

    LitterDecay(LeafSettings settings, LitterField field, Terrain terrain, FastRandom random, Crumble crumble) {
        this.settings = settings;
        this.field = field;
        this.terrain = terrain;
        this.random = random;
        this.crumble = crumble;
    }

    /** Lets the chunk that waited longest rot up to {@code gameTime}; chunks seen for the first time start their clock. */
    void tick(long gameTime) {
        LitterChunk oldest = null;
        for (LitterChunk chunk : field.chunks()) {
            if (chunk.decayedAt == LitterChunk.NEVER || chunk.decayedAt > gameTime) {
                start(chunk, gameTime);
            } else if (oldest == null || chunk.decayedAt < oldest.decayedAt) {
                oldest = chunk;
            }
        }
        if (oldest != null && gameTime - oldest.decayedAt >= INTERVAL) {
            crumbleBudget = CRUMBLE_PER_STEP;
            apply(oldest, gameTime);
            crumbleBudget = 0;
        }
    }

    /**
     * Applies the rot of a chunk from the last time it was applied up to {@code gameTime}, all at once (for a chunk
     * that comes back after a while). Returns how many leaves rotted away.
     */
    int catchUp(LitterChunk chunk, long gameTime) {
        crumbleBudget = 0;
        return apply(chunk, gameTime);
    }

    private int apply(LitterChunk chunk, long gameTime) {
        if (chunk.decayedAt == LitterChunk.NEVER || chunk.decayedAt >= gameTime) {
            start(chunk, gameTime);
            return 0;
        }
        long elapsed = gameTime - chunk.decayedAt;
        chunk.decayedAt = gameTime;
        if (chunk.total == 0) {
            return 0;
        }
        double keep = Math.exp(-settings.decayRate() * LOG_CARPET * elapsed);
        double snowKeep = Math.exp(-Math.max(settings.decayRate(), SNOW_RATE) * LOG_CARPET * elapsed);
        boolean checkSnow = terrain.level != null;
        int originX = chunk.x << 6;
        int originZ = chunk.z << 6;
        int rotted = 0;
        for (int i = 0; i < LitterChunk.AREA; i++) {
            int loose = chunk.loose(i);
            if (loose <= 0) {
                continue;
            }
            int cellX = originX + (i & 63);
            int cellZ = originZ + (i >> 6);
            double cellKeep = checkSnow && terrain.snowy((cellX + 0.5) * LitterField.CELL, chunk.base[i], (cellZ + 0.5) * LitterField.CELL)
                    ? snowKeep : keep;
            if (cellKeep >= 1.0) {
                continue;
            }
            double left = (loose + FLOOR) * cellKeep - FLOOR;
            int remain = left <= 0.0 ? 0 : (int) left + (random.next() < left - Math.floor(left) ? 1 : 0);
            int gone = loose - Math.min(loose, remain);
            if (gone > 0) {
                rot(cellX, cellZ, gone);
                rotted += gone;
            }
        }
        return rotted;
    }

    /** Starts the clock of a chunk that was never decayed (or whose time lies in the future, after a world change). */
    private static void start(LitterChunk chunk, long gameTime) {
        chunk.decayedAt = gameTime;
        if (chunk.seeded && chunk.seededAt == LitterChunk.NEVER) {
            chunk.seededAt = gameTime;
        }
    }

    /** Takes {@code gone} leaves off the top of a cell, the first ones as crumbling particles if that is allowed. */
    private void rot(int cellX, int cellZ, int gone) {
        boolean pile = false;
        for (int d = 0; d < 4; d++) {
            pile |= field.count(cellX + DX[d], cellZ + DZ[d]) * LitterField.LAYER > LitterField.REPOSE;
        }
        for (int n = 0; n < gone; n++) {
            if (crumbleBudget > 0 && crumble.crumble(cellX, cellZ)) {
                crumbleBudget--;
            } else if (!field.take(cellX, cellZ, null)) {
                break;
            }
        }
        if (pile) {
            // The neighbors of a shrinking cell may now stand too steep.
            for (int d = 0; d < 4; d++) {
                field.queueRelax(cellX + DX[d], cellZ + DZ[d]);
            }
        }
    }

    /**
     * Snow covered the block at a cell: the leaves under it are gone at once (they would show through a thin layer and
     * stay buried under a thick one). Returns true if the cell lies under or on snow.
     */
    boolean bury(int cellX, int cellZ, double base) {
        if (!terrain.snowy((cellX + 0.5) * LitterField.CELL, base, (cellZ + 0.5) * LitterField.CELL)) {
            return false;
        }
        while (field.take(cellX, cellZ, null)) {
            // Takes the loose leaves; pinned ones belong to a pile block.
        }
        return true;
    }
}
