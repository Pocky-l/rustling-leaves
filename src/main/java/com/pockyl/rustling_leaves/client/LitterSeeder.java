package com.pockyl.rustling_leaves.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.pockyl.rustling_leaves.sim.LeafPalette;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LitterChunk;
import com.pockyl.rustling_leaves.sim.LitterField;
import com.pockyl.rustling_leaves.sim.LeafSimulation;

/**
 * Gives ground that is seen for the first time the litter a forest floor would have: a carpet under and around tree
 * crowns (thicker where the canopy is dense, patchy), leaves banked against walls and trunks, and now and then a
 * proper leaf pile. Runs once per chunk; the result is saved with the rest of the litter.
 */
final class LitterSeeder {
    private static final int MARGIN = 2;
    private static final int GRID = 16 + 2 * MARGIN;

    private final LeafColors colors;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockState[] canopy = new BlockState[GRID * GRID];
    private final float[] density = new float[256];
    private final double[] ground = new double[256 * 16];

    LitterSeeder(LeafColors colors) {
        this.colors = colors;
    }

    void seed(Level level, LeafSimulation simulation, LitterChunk chunk) {
        LeafSettings settings = simulation.settings();
        int carpet = settings.carpetDepth;
        if (carpet <= 0 && !settings.naturalPiles) {
            return;
        }
        int originX = chunk.x * 16;
        int originZ = chunk.z * 16;
        // Canopy: the top block of every column (with a margin into neighbor chunks) if it is foliage.
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int x = originX + gx - MARGIN;
                int z = originZ + gz - MARGIN;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                BlockState state = level.getBlockState(cursor.set(x, top, z));
                canopy[gz * GRID + gx] = state.is(BlockTags.LEAVES) ? state : null;
            }
        }
        boolean anyTrees = false;
        for (int bz = 0; bz < 16; bz++) {
            for (int bx = 0; bx < 16; bx++) {
                int around = 0;
                for (int dz = -MARGIN; dz <= MARGIN; dz++) {
                    for (int dx = -MARGIN; dx <= MARGIN; dx++) {
                        if (canopy[(bz + MARGIN + dz) * GRID + bx + MARGIN + dx] != null) {
                            around++;
                        }
                    }
                }
                boolean under = canopy[(bz + MARGIN) * GRID + bx + MARGIN] != null;
                float d = 0.5F * (under ? 1.0F : 0.0F) + 0.5F * around / 25.0F;
                density[bz * 16 + bx] = d;
                anyTrees |= d > 0.05F;
            }
        }
        if (!anyTrees) {
            return;
        }

        // Ground under every cell, and the carpet.
        float densitySum = 0.0F;
        for (int bz = 0; bz < 16; bz++) {
            for (int bx = 0; bx < 16; bx++) {
                float d = density[bz * 16 + bx];
                int x = originX + bx;
                int z = originZ + bz;
                double top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 0.5;
                BlockState treeState = nearestCanopy(bx, bz);
                for (int c = 0; c < 16; c++) {
                    ground[(bz * 16 + bx) * 16 + c] = Double.NaN;
                }
                if (d <= 0.05F || treeState == null) {
                    continue;
                }
                densitySum += d;
                int color = colors.base(treeState, level, cursor.set(x, (int) top, z));
                int shape = LeafShapes.of(treeState.getBlock()).ordinal();
                long blockNoise = LeafPalette.hash(x, z, 0x5EED);
                // One deep probe per block; the cells then only look around that height (slabs, stairs, paths).
                double blockGround = simulation.groundBelow(x + 0.5, top, z + 0.5, 48);
                if (Double.isNaN(blockGround)) {
                    continue;
                }
                int walls = settings.naturalPiles && d >= 0.3F ? walls(simulation, x, z, blockGround + 0.1) : 0;
                for (int c = 0; c < 16; c++) {
                    int cellX = x * 4 + (c & 3);
                    int cellZ = z * 4 + (c >> 2);
                    double cx = (cellX + 0.5) * LitterField.CELL;
                    double cz = (cellZ + 0.5) * LitterField.CELL;
                    double surface = simulation.groundBelow(cx, Math.floor(blockGround) + 1.0, cz, 2);
                    if (Double.isNaN(surface) || !litterGround(level, cx, surface, cz)) {
                        continue;
                    }
                    ground[(bz * 16 + bx) * 16 + c] = surface;
                    float noise = 0.5F * unit(LeafPalette.hash(cellX, cellZ, 0x5EED)) + 0.5F * unit(blockNoise);
                    // Patchy: in the sparser half of the noise the ground stays bare.
                    int leaves = Math.max(0, Math.round(d * carpet * (noise - 0.4F) * 2.4F));
                    leaves += wallDrift(walls, c & 3, c >> 2, d);
                    chunk.seedCell(LitterField.index(cellX, cellZ), leaves, (float) surface, color, shape);
                }
            }
        }
        if (settings.naturalPiles && densitySum / 256.0F > 0.3F) {
            long chunkHash = LeafPalette.hash(chunk.x, chunk.z, 0x9113);
            int piles = unit(chunkHash) < 0.12F ? 1 : 0;
            for (int p = 0; p < piles; p++) {
                addPile(chunk, originX, originZ, LeafPalette.hash(chunk.x, chunk.z, p));
            }
        }
    }

    /** Litter lies on solid natural or built ground, not on top of logs or on snow. */
    private boolean litterGround(Level level, double x, double surface, double z) {
        BlockState below = level.getBlockState(cursor.set(Mth.floor(x), Mth.floor(surface - 0.01), Mth.floor(z)));
        return !below.is(BlockTags.LOGS) && !below.is(Blocks.SNOW) && !below.is(Blocks.SNOW_BLOCK) && !below.is(Blocks.POWDER_SNOW)
                && !below.is(BlockTags.ICE);
    }

    /** Which sides of a block column have a wall or trunk at ground level: bits east, west, south, north. */
    private static int walls(LeafSimulation simulation, int x, int z, double y) {
        int walls = 0;
        walls |= Double.isNaN(simulation.solidTop(x + 1.5, y, z + 0.5)) ? 0 : 1;
        walls |= Double.isNaN(simulation.solidTop(x - 0.5, y, z + 0.5)) ? 0 : 2;
        walls |= Double.isNaN(simulation.solidTop(x + 0.5, y, z + 1.5)) ? 0 : 4;
        walls |= Double.isNaN(simulation.solidTop(x + 0.5, y, z - 0.5)) ? 0 : 8;
        return walls;
    }

    /** Extra leaves blown against a wall or trunk next to the block, fading out over the block's four cells. */
    private static int wallDrift(int walls, int cellX, int cellZ, float d) {
        if (walls == 0) {
            return 0;
        }
        int nearest = 4;
        if ((walls & 1) != 0) {
            nearest = Math.min(nearest, 3 - cellX);
        }
        if ((walls & 2) != 0) {
            nearest = Math.min(nearest, cellX);
        }
        if ((walls & 4) != 0) {
            nearest = Math.min(nearest, 3 - cellZ);
        }
        if ((walls & 8) != 0) {
            nearest = Math.min(nearest, cellZ);
        }
        return nearest >= 4 ? 0 : Math.round((2 + 6 * d) * (1.0F - nearest / 4.0F));
    }

    /** A dome-shaped leaf pile somewhere under the trees of this chunk. */
    private void addPile(LitterChunk chunk, int originX, int originZ, long hash) {
        int column = -1;
        for (int attempt = 0; attempt < 8 && column < 0; attempt++) {
            int candidate = (int) (hash >>> (attempt * 8) & 0xFF);
            if (density[candidate] > 0.2F && !Double.isNaN(ground[candidate * 16 + 5])) {
                column = candidate;
            }
        }
        if (column < 0) {
            return;
        }
        double centerGround = ground[column * 16 + 5];
        int sourceCell = LitterField.index((originX + (column & 15)) * 4 + 1, (originZ + (column >> 4)) * 4 + 1);
        int pileColor = chunk.color[sourceCell];
        int pileShape = chunk.shape[sourceCell];
        float radius = 0.8F + unit(hash >>> 13) * 0.9F;
        float peak = 10.0F + unit(hash >>> 29) * 14.0F;
        double centerX = originX + (column & 15) + 0.5;
        double centerZ = originZ + (column >> 4) + 0.5;
        int reach = Mth.ceil(radius * 4);
        int centerCellX = LitterField.cell(centerX);
        int centerCellZ = LitterField.cell(centerZ);
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                int cellX = centerCellX + dx;
                int cellZ = centerCellZ + dz;
                if (cellX >> 6 != chunk.x || cellZ >> 6 != chunk.z) {
                    continue;
                }
                float distance = Mth.sqrt(dx * dx + dz * dz) * LitterField.CELL / radius;
                if (distance >= 1.0F) {
                    continue;
                }
                int bx = (cellX >> 2) - originX;
                int bz = (cellZ >> 2) - originZ;
                int c = (cellX & 3) + (cellZ & 3) * 4;
                double surface = ground[(bz * 16 + bx) * 16 + c];
                if (Double.isNaN(surface) || Math.abs(surface - centerGround) > 0.6) {
                    continue;
                }
                int index = LitterField.index(cellX, cellZ);
                int extra = Math.round(peak * (1.0F - distance * distance));
                int existing = chunk.count[index];
                if (existing == 0) {
                    chunk.seedCell(index, extra, (float) surface, pileColor, pileShape);
                } else {
                    chunk.count[index] = (short) Math.min(LitterField.MAX_LAYERS, existing + extra);
                }
            }
        }
        recountTotal(chunk);
    }

    private static void recountTotal(LitterChunk chunk) {
        int total = 0;
        for (int i = 0; i < LitterChunk.AREA; i++) {
            total += chunk.count[i];
        }
        chunk.setTotal(total);
    }

    private BlockState nearestCanopy(int bx, int bz) {
        BlockState own = canopy[(bz + MARGIN) * GRID + bx + MARGIN];
        if (own != null) {
            return own;
        }
        for (int r = 1; r <= MARGIN; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    BlockState state = canopy[(bz + MARGIN + dz) * GRID + bx + MARGIN + dx];
                    if (state != null) {
                        return state;
                    }
                }
            }
        }
        return null;
    }

    private static float unit(long hash) {
        return (hash & 0xFFFFF) / (float) 0x100000;
    }
}
