package com.pockyl.rustling_leaves.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
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
 * proper leaf pile. The result is saved with the rest of the litter.
 *
 * <p>The litter rots away over time, so ground that has not been seeded for a while is seeded again: cells that have
 * thinned out are topped up to the natural carpet (the trees kept shedding while nobody watched), anything thicker
 * stays as it is. With seasons the natural carpet follows the year: a thin scatter of leaves in spring and summer, a
 * carpet that grows through autumn, nothing in winter.
 */
final class LitterSeeder {
    /** How far (in blocks) leaves land from the crown they fell from. */
    private static final int MARGIN = 7;
    private static final int GRID = 16 + 2 * MARGIN;
    private static final int KERNEL = 2 * MARGIN + 1;
    /** Spread of the fall pattern around a crown block, and how far it is carried downwind. */
    private static final float SPREAD = 2.6F;
    private static final float DOWNWIND = 1.6F;
    /** Direction the wind mostly blows from in this mod's wind field (its mean heading). */
    private static final float PREVAILING_X = Mth.cos(0.7F);
    private static final float PREVAILING_Z = Mth.sin(0.7F);
    /** Period of the large patches of litter, in blocks. */
    private static final float PATCH_SIZE = 6.0F;
    private static final float[] FALL_KERNEL = fallKernel();

    private final LeafColors colors;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockState[] canopy = new BlockState[GRID * GRID];
    private final float[] density = new float[256];
    private final double[] blockGround = new double[256];
    private final double[] ground = new double[256 * 16];

    LitterSeeder(LeafColors colors) {
        this.colors = colors;
    }

    /**
     * Where leaves from one crown block end up: a Gaussian around it, shifted downwind. Normalized so that ground
     * under a closed canopy gets density 1.
     */
    private static float[] fallKernel() {
        float[] kernel = new float[KERNEL * KERNEL];
        float sum = 0.0F;
        for (int dz = -MARGIN; dz <= MARGIN; dz++) {
            for (int dx = -MARGIN; dx <= MARGIN; dx++) {
                // dx/dz go from the crown block to the ground block; the pattern is centered downwind of the crown.
                float ox = dx - PREVAILING_X * DOWNWIND;
                float oz = dz - PREVAILING_Z * DOWNWIND;
                float weight = (float) Math.exp(-(ox * ox + oz * oz) / (2.0F * SPREAD * SPREAD));
                kernel[(dz + MARGIN) * KERNEL + dx + MARGIN] = weight;
                sum += weight;
            }
        }
        for (int k = 0; k < kernel.length; k++) {
            kernel[k] /= sum;
        }
        return kernel;
    }

    void seed(Level level, LeafSimulation simulation, LitterChunk chunk) {
        LeafSettings settings = simulation.settings();
        int carpet = settings.carpetDepth;
        float season = settings.seasonLitter;
        if (carpet <= 0 && !settings.naturalPiles || season <= 0.0F) {
            return;
        }
        boolean first = chunk.seededAt() == LitterChunk.NEVER;
        int originX = chunk.x * 16;
        int originZ = chunk.z * 16;
        // Canopy: the top block of every column (with a margin into neighbor chunks) if it is foliage.
        boolean anyCanopy = false;
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int x = originX + gx - MARGIN;
                int z = originZ + gz - MARGIN;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                BlockState state = level.getBlockState(cursor.set(x, top, z));
                boolean leaves = state.is(BlockTags.LEAVES);
                canopy[gz * GRID + gx] = leaves ? state : null;
                anyCanopy |= leaves;
            }
        }
        if (!anyCanopy) {
            return;
        }
        // Fall density: every crown block spreads its leaves over the ground around it (downwind a bit more).
        boolean anyLitter = false;
        for (int bz = 0; bz < 16; bz++) {
            for (int bx = 0; bx < 16; bx++) {
                float d = 0.0F;
                for (int kz = 0; kz < KERNEL; kz++) {
                    for (int kx = 0; kx < KERNEL; kx++) {
                        // Crown block at (bx - dx, bz - dz) for the kernel offset (dx, dz) = (kx - MARGIN, kz - MARGIN).
                        if (canopy[(bz + 2 * MARGIN - kz) * GRID + bx + 2 * MARGIN - kx] != null) {
                            d += FALL_KERNEL[kz * KERNEL + kx];
                        }
                    }
                }
                density[bz * 16 + bx] = d;
                anyLitter |= d > 0.02F;
            }
        }
        if (!anyLitter) {
            return;
        }
        // The ground of every block that gets leaves (one deep probe per block).
        for (int bz = 0; bz < 16; bz++) {
            for (int bx = 0; bx < 16; bx++) {
                int b = bz * 16 + bx;
                blockGround[b] = Double.NaN;
                if (density[b] > 0.02F) {
                    int x = originX + bx;
                    int z = originZ + bz;
                    double top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 0.5;
                    blockGround[b] = simulation.groundBelow(x + 0.5, top, z + 0.5, 48);
                }
            }
        }

        float densitySum = 0.0F;
        for (int bz = 0; bz < 16; bz++) {
            for (int bx = 0; bx < 16; bx++) {
                int b = bz * 16 + bx;
                for (int c = 0; c < 16; c++) {
                    ground[b * 16 + c] = Double.NaN;
                }
                float d = density[b];
                double here = blockGround[b];
                BlockState treeState = nearestCanopy(bx, bz);
                if (d <= 0.02F || Double.isNaN(here) || treeState == null) {
                    continue;
                }
                densitySum += d;
                int x = originX + bx;
                int z = originZ + bz;
                int color = colors.base(treeState, level, cursor.set(x, Mth.floor(here), z));
                int shape = LeafShapes.of(treeState.getBlock()).ordinal();
                // Hollows collect leaves, humps and ledges shed them.
                float relief = (float) Mth.clamp(neighborGround(bx, bz, here) - here, -1.0, 1.0);
                float terrain = Mth.clamp(1.0F + relief * 1.5F, 0.3F, 2.0F);
                int walls = settings.naturalPiles && d >= 0.3F ? walls(simulation, x, z, here + 0.1) : 0;
                for (int c = 0; c < 16; c++) {
                    int cellX = x * 4 + (c & 3);
                    int cellZ = z * 4 + (c >> 2);
                    double cx = (cellX + 0.5) * LitterField.CELL;
                    double cz = (cellZ + 0.5) * LitterField.CELL;
                    double surface = simulation.groundBelow(cx, Math.floor(here) + 1.0, cz, 2);
                    if (Double.isNaN(surface) || !litterGround(level, cx, surface, cz)) {
                        continue;
                    }
                    ground[b * 16 + c] = surface;
                    // Soft patches of a few blocks, with a little grain inside them.
                    float pattern = 0.7F * patchNoise(cx, cz) + 0.3F * unit(LeafPalette.hash(cellX, cellZ, 0x5EED));
                    int leaves = Math.round(d * terrain * carpet * 2.4F * (pattern - 0.38F) * season);
                    if (leaves <= 0) {
                        // Away from the patches only the odd stray leaf (out of season, nearly all there is).
                        leaves = unit(LeafPalette.hash(cellZ, cellX, 0x57A7)) < d * 0.3F * Math.min(1.0F, season * 3.0F) ? 1 : 0;
                    }
                    leaves += Math.round(wallDrift(walls, c & 3, c >> 2, d) * season);
                    chunk.topUpCell(LitterField.index(cellX, cellZ), leaves, (float) surface, color, shape);
                }
            }
        }
        if (first && settings.naturalPiles && densitySum / 256.0F > 0.3F) {
            long chunkHash = LeafPalette.hash(chunk.x, chunk.z, 0x9113);
            int piles = unit(chunkHash) < 0.12F * season ? 1 : 0;
            for (int p = 0; p < piles; p++) {
                addPile(chunk, originX, originZ, LeafPalette.hash(chunk.x, chunk.z, p));
            }
        }
    }

    /** Average ground height of the neighboring blocks of this chunk (the block's own where unknown). */
    private double neighborGround(int bx, int bz, double here) {
        double sum = 0.0;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            int nx = bx + offset[0];
            int nz = bz + offset[1];
            double value = nx >= 0 && nx < 16 && nz >= 0 && nz < 16 ? blockGround[nz * 16 + nx] : Double.NaN;
            sum += Double.isNaN(value) || Math.abs(value - here) > 2.0 ? here : value;
        }
        return sum / 4.0;
    }

    /** Smooth value noise in [0, 1] with features about {@link #PATCH_SIZE} blocks across, the same in every chunk. */
    private static float patchNoise(double x, double z) {
        double gx = x / PATCH_SIZE;
        double gz = z / PATCH_SIZE;
        int x0 = Mth.floor(gx);
        int z0 = Mth.floor(gz);
        float fx = smooth((float) (gx - x0));
        float fz = smooth((float) (gz - z0));
        float n00 = unit(LeafPalette.hash(x0, z0, 0xFA11));
        float n10 = unit(LeafPalette.hash(x0 + 1, z0, 0xFA11));
        float n01 = unit(LeafPalette.hash(x0, z0 + 1, 0xFA11));
        float n11 = unit(LeafPalette.hash(x0 + 1, z0 + 1, 0xFA11));
        return Mth.lerp(fz, Mth.lerp(fx, n00, n10), Mth.lerp(fx, n01, n11));
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /** Litter lies on solid natural or built ground, not on top of logs, not on snow and not under a thin snow layer. */
    private boolean litterGround(Level level, double x, double surface, double z) {
        BlockState below = level.getBlockState(cursor.set(Mth.floor(x), Mth.floor(surface - 0.01), Mth.floor(z)));
        BlockState at = level.getBlockState(cursor.set(Mth.floor(x), Mth.floor(surface + 0.01), Mth.floor(z)));
        return !below.is(BlockTags.LOGS) && !below.is(BlockTags.SNOW) && !below.is(BlockTags.ICE) && !at.is(BlockTags.SNOW);
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
