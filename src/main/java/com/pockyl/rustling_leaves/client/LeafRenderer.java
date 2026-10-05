package com.pockyl.rustling_leaves.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafPalette;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.LeafSimulation;
import com.pockyl.rustling_leaves.sim.LitterChunk;
import com.pockyl.rustling_leaves.sim.LitterField;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the leaves in two parts:
 * <ul>
 *   <li>the litter, in tiles of 4x4 blocks, each one static GPU buffer rebuilt only when its cells change (within a
 *   time budget per frame, nearest tiles first). Of every stack only the leaves that can be seen are drawn: the top
 *   ones, plus the sides of a pile where it stands above its neighbors - a pile of thousands of leaves costs a few
 *   hundred quads;</li>
 *   <li>moving leaves, written into one streaming buffer each frame and interpolated between ticks.</li>
 * </ul>
 * Both use the vanilla cutout terrain shader, so fog, lightmap and day/night match the world.
 */
final class LeafRenderer implements AutoCloseable {
    private static final RenderType LEAVES = RenderType.create(RustlingLeaves.MOD_ID + "_leaves", DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS, 1 << 16, false, false, RenderType.CompositeState.builder()
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setShaderState(RenderStateShard.RENDERTYPE_CUTOUT_MIPPED_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    private static final long REBUILD_BUDGET_NANOS = 3_000_000L;
    private static final double DETAIL_DISTANCE = 40.0;
    private static final int TOP_LAYERS = 2;
    private static final int MAX_SIDE_LAYERS = 48;
    private static final int SURFACE_MIN = LitterField.SURFACE_MIN;
    /** With a pile body, only a few loose leaves are added on the sides of a pile. */
    private static final int SURFACE_SIDE_LAYERS = 2;
    /** Loose leaves drawn on top of a pile body: enough to hide it, the body only shows as shadow between them. */
    private static final int SURFACE_TOP_LAYERS = 3;
    /** The pile body sits below the loose leaves and is darker: it reads as the depth of the pile. */
    private static final float BODY_SINK = 0.02F;
    private static final float BODY_SHADE = 0.6F;
    /** Stacks this deep hide the ground completely: they get the solid body under their leaf layer. */
    private static final int BODY_MIN = 12;
    /** Stacks this deep get the denser leaf layer. */
    private static final int DENSE_MIN = 7;
    /** The leaf layer lies just under the loose top leaves. */
    private static final float LAYER_SINK = 0.006F;
    private static final int TEX_BODY = 0;
    private static final int TEX_SPARSE = 1;
    private static final int TEX_LAYER = 2;
    private static final int RING = LitterChunk.TILE + 2;
    private static final float TILE_BLOCKS = LitterChunk.TILE * LitterField.CELL;

    private final LeafSimulation simulation;
    private final LeafPool pool;
    private final LitterField field;
    private final Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
    private final List<Tile> dirty = new ArrayList<>();
    private final LitterField.Pose pose = new LitterField.Pose();
    private final ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 18);
    private final VertexBuffer moving = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
    private boolean movingEmpty = true;
    private final float[] u0 = new float[LeafShape.SPRITE_COUNT];
    private final float[] v0 = new float[LeafShape.SPRITE_COUNT];
    private final float[] u1 = new float[LeafShape.SPRITE_COUNT];
    private final float[] v1 = new float[LeafShape.SPRITE_COUNT];
    private final int[] ringCount = new int[RING * RING];
    private final double[] ringBase = new double[RING * RING];
    private final double[] ringTop = new double[RING * RING];
    private final int[] ringColor = new int[RING * RING];
    private final float[] layerU0 = new float[3];
    private final float[] layerV0 = new float[3];
    private final float[] layerU1 = new float[3];
    private final float[] layerV1 = new float[3];
    private double currentOriginY;
    private float currentSink;
    private int frame;
    private int lastTileQuads;

    LeafRenderer(LeafSimulation simulation) {
        this.simulation = simulation;
        this.pool = simulation.pool();
        this.field = simulation.field();
    }

    private static final class Tile {
        final LitterChunk chunk;
        final int index;
        final int cellX;
        final int cellZ;
        final double originX;
        final double originZ;
        double originY;
        AABB bounds;
        VertexBuffer buffer;
        boolean empty = true;
        int builtRevision = Integer.MIN_VALUE;
        boolean builtDetailed;
        boolean wantDetailed;
        int seenFrame;
        double distanceSq;
        int quads;

        Tile(LitterChunk chunk, int index) {
            this.chunk = chunk;
            this.index = index;
            cellX = chunk.x * LitterChunk.SIZE + index % LitterChunk.TILES * LitterChunk.TILE;
            cellZ = chunk.z * LitterChunk.SIZE + index / LitterChunk.TILES * LitterChunk.TILE;
            originX = cellX * LitterField.CELL;
            originZ = cellZ * LitterField.CELL;
        }

        void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
        }
    }

    int tileQuads() {
        return lastTileQuads;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------------------------

    void render(Camera camera, Frustum frustum, Matrix4f modelView, Matrix4f projection, float partialTick) {
        updateSprites();
        Vec3 cam = camera.getPosition();
        frame++;
        collectTiles(cam);
        rebuildTiles();
        buildMoving(cam, camera.getLookVector(), partialTick);

        LEAVES.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            LEAVES.clearRenderState();
            return;
        }
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, Minecraft.getInstance().getWindow());
        shader.apply();
        Uniform offset = shader.CHUNK_OFFSET;
        int quads = 0;
        for (Tile tile : tiles.values()) {
            if (tile.buffer == null || tile.empty || tile.bounds == null || !frustum.isVisible(tile.bounds)) {
                continue;
            }
            if (offset != null) {
                offset.set((float) (tile.originX - cam.x), (float) (tile.originY - cam.y), (float) (tile.originZ - cam.z));
                offset.upload();
            }
            tile.buffer.bind();
            tile.buffer.draw();
            quads += tile.quads;
        }
        lastTileQuads = quads;
        if (!movingEmpty) {
            if (offset != null) {
                offset.set(0.0F, 0.0F, 0.0F);
                offset.upload();
            }
            moving.bind();
            moving.draw();
        }
        if (offset != null) {
            offset.set(0.0F, 0.0F, 0.0F);
        }
        VertexBuffer.unbind();
        shader.clear();
        LEAVES.clearRenderState();
    }

    private void updateSprites() {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        for (int s = 0; s < LeafShape.SPRITE_COUNT; s++) {
            TextureAtlasSprite sprite = atlas.getSprite(LeafShapes.SPRITES[s]);
            u0[s] = sprite.getU0();
            v0[s] = sprite.getV0();
            u1[s] = sprite.getU1();
            v1[s] = sprite.getV1();
        }
        ResourceLocation[] layers = {LeafShapes.LITTER, LeafShapes.LITTER_SPARSE, LeafShapes.LITTER_LAYER};
        for (int t = 0; t < layers.length; t++) {
            TextureAtlasSprite sprite = atlas.getSprite(layers[t]);
            layerU0[t] = sprite.getU0();
            layerV0[t] = sprite.getV0();
            layerU1[t] = sprite.getU1();
            layerV1[t] = sprite.getV1();
        }
    }

    /** Finds tiles of loaded litter chunks, drops tiles of unloaded ones and lists those that need a rebuild. */
    private void collectTiles(Vec3 cam) {
        dirty.clear();
        for (LitterChunk chunk : field.chunks()) {
            if (chunk.total() == 0) {
                continue;
            }
            for (int t = 0; t < LitterChunk.TILES * LitterChunk.TILES; t++) {
                long key = tileKey(chunk, t);
                Tile tile = tiles.get(key);
                if (tile == null || tile.chunk != chunk) {
                    if (tile != null) {
                        tile.close();
                    }
                    tile = new Tile(chunk, t);
                    tiles.put(key, tile);
                }
                tile.seenFrame = frame;
                double dx = tile.originX + TILE_BLOCKS * 0.5 - cam.x;
                double dz = tile.originZ + TILE_BLOCKS * 0.5 - cam.z;
                tile.distanceSq = dx * dx + dz * dz;
                tile.wantDetailed = tile.distanceSq < DETAIL_DISTANCE * DETAIL_DISTANCE;
                if (tile.builtRevision != chunk.tileRevision[t] || tile.builtDetailed != tile.wantDetailed) {
                    dirty.add(tile);
                }
            }
        }
        for (Iterator<Long2ObjectMap.Entry<Tile>> it = tiles.long2ObjectEntrySet().iterator(); it.hasNext(); ) {
            Tile tile = it.next().getValue();
            if (tile.seenFrame != frame) {
                tile.close();
                it.remove();
            }
        }
    }

    private static long tileKey(LitterChunk chunk, int tile) {
        return ChunkPos.asLong(chunk.x * LitterChunk.TILES + tile % LitterChunk.TILES, chunk.z * LitterChunk.TILES + tile / LitterChunk.TILES);
    }

    private void rebuildTiles() {
        if (dirty.isEmpty()) {
            return;
        }
        dirty.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));
        long deadline = System.nanoTime() + REBUILD_BUDGET_NANOS;
        for (Tile tile : dirty) {
            buildTile(tile);
            if (System.nanoTime() > deadline) {
                break;
            }
        }
        VertexBuffer.unbind();
    }

    private void buildTile(Tile tile) {
        LitterChunk chunk = tile.chunk;
        tile.builtRevision = chunk.tileRevision[tile.index];
        tile.builtDetailed = tile.wantDetailed;
        int topLayers = tile.wantDetailed ? TOP_LAYERS : 1;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int lz = 0; lz < LitterChunk.TILE; lz++) {
            for (int lx = 0; lx < LitterChunk.TILE; lx++) {
                int i = LitterField.index(tile.cellX + lx, tile.cellZ + lz);
                if (chunk.count[i] > 0) {
                    minY = Math.min(minY, chunk.base[i]);
                    maxY = Math.max(maxY, chunk.base[i] + chunk.count[i] * LitterField.LAYER);
                }
            }
        }
        if (minY == Double.MAX_VALUE) {
            tile.empty = true;
            tile.quads = 0;
            return;
        }
        tile.originY = Math.floor(minY);
        currentOriginY = tile.originY;
        tile.bounds = new AABB(tile.originX - 0.3, minY - 0.2, tile.originZ - 0.3, tile.originX + TILE_BLOCKS + 0.3, maxY + 0.3,
                tile.originZ + TILE_BLOCKS + 0.3);
        loadRing(tile);
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        int quads = 0;
        for (int lz = 0; lz < LitterChunk.TILE; lz++) {
            for (int lx = 0; lx < LitterChunk.TILE; lx++) {
                int cellX = tile.cellX + lx;
                int cellZ = tile.cellZ + lz;
                int i = LitterField.index(cellX, cellZ);
                int n = chunk.count[i];
                if (n == 0) {
                    continue;
                }
                double base = chunk.base[i];
                double top = base + n * LitterField.LAYER;
                double lowest = Math.min(Math.min(neighborTop(cellX + 1, cellZ, base), neighborTop(cellX - 1, cellZ, base)),
                        Math.min(neighborTop(cellX, cellZ + 1, base), neighborTop(cellX, cellZ - 1, base)));
                int side = Mth.clamp(Mth.ceil((top - lowest) / LitterField.LAYER), 0, MAX_SIDE_LAYERS);
                int light = simulation.lightAt((cellX + 0.5) * LitterField.CELL, top + 0.05, (cellZ + 0.5) * LitterField.CELL);
                int layers = topLayers;
                if (n >= SURFACE_MIN) {
                    // Carpet: a leaf layer with gaps over the ground. Pile: a solid shadowed body under that layer.
                    if (n >= BODY_MIN) {
                        emitSurface(builder, tile, lx, lz, base, light, TEX_BODY, BODY_SINK, BODY_SHADE);
                        quads++;
                    }
                    emitSurface(builder, tile, lx, lz, base, light, n >= DENSE_MIN ? TEX_LAYER : TEX_SPARSE, LAYER_SINK, 1.0F);
                    quads++;
                    side = Math.min(side, SURFACE_SIDE_LAYERS);
                    layers = tile.wantDetailed ? SURFACE_TOP_LAYERS : 1;
                }
                int visible = Math.min(n, layers + (tile.wantDetailed ? side : side / 2));
                for (int depth = 0; depth < visible; depth++) {
                    field.pose(chunk, cellX, cellZ, n - 1 - depth, pose);
                    // Leaves deeper in the pile are in its shadow.
                    float shade = Math.max(0.68F, 1.0F - depth * 0.05F);
                    emit(builder, (float) (pose.x - tile.originX), (float) (pose.y - tile.originY), (float) (pose.z - tile.originZ),
                            pose.yaw, pose.pitch, pose.roll, pose.size, shade(pose.color, shade), light, pose.sprite);
                    quads++;
                }
            }
        }
        tile.quads = quads;
        MeshData mesh = builder.build();
        if (mesh == null) {
            tile.empty = true;
            return;
        }
        if (tile.buffer == null) {
            tile.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        tile.buffer.bind();
        tile.buffer.upload(mesh);
        tile.empty = false;
    }

    /** Counts, heights and colors of the tile's cells and the ring of cells around it. */
    private void loadRing(Tile tile) {
        float aged = Math.min(1.0F, simulation.settings().autumnColors + 0.3F);
        for (int rz = 0; rz < RING; rz++) {
            for (int rx = 0; rx < RING; rx++) {
                int cellX = tile.cellX + rx - 1;
                int cellZ = tile.cellZ + rz - 1;
                int r = rz * RING + rx;
                LitterChunk chunk = field.chunkAtCell(cellX, cellZ);
                int i = LitterField.index(cellX, cellZ);
                int n = chunk == null ? 0 : chunk.count[i];
                ringCount[r] = n;
                if (n > 0) {
                    ringBase[r] = chunk.base[i];
                    ringTop[r] = chunk.base[i] + n * LitterField.LAYER;
                    ringColor[r] = LeafPalette.vary(chunk.color[i], LeafPalette.hash(cellX, cellZ, 0x5F), aged,
                            LeafShape.byId(chunk.shape[i]));
                }
            }
        }
    }

    /**
     * One quad per cell of the smooth litter surface: its corners are the average heights of the four cells around
     * them, so carpets and piles are smooth mounds that run down to the ground at their edges. Used for the solid
     * pile body and for the leaf layers with gaps; {@code sink} puts it below the loose top leaves.
     */
    private void emitSurface(BufferBuilder builder, Tile tile, int lx, int lz, double base, int light, int texture, float sink,
            float shadeFactor) {
        currentSink = sink;
        float h00 = corner(lx, lz, base);
        float h10 = corner(lx + 1, lz, base);
        float h11 = corner(lx + 1, lz + 1, base);
        float h01 = corner(lx, lz + 1, base);
        int self = (lz + 1) * RING + lx + 1;
        int c00 = cornerColor(lx, lz, self);
        int c10 = cornerColor(lx + 1, lz, self);
        int c11 = cornerColor(lx + 1, lz + 1, self);
        int c01 = cornerColor(lx, lz + 1, self);
        int cellX = tile.cellX + lx;
        int cellZ = tile.cellZ + lz;
        float du = (layerU1[texture] - layerU0[texture]) * 0.25F;
        float dv = (layerV1[texture] - layerV0[texture]) * 0.25F;
        float u = layerU0[texture] + du * (cellX & 3);
        float v = layerV0[texture] + dv * (cellZ & 3);
        float x0 = lx * LitterField.CELL;
        float z0 = lz * LitterField.CELL;
        float x1 = x0 + LitterField.CELL;
        float z1 = z0 + LitterField.CELL;
        bodyVertex(builder, lx, lz, base, x0, h00, z0, c00, u, v, light, shadeFactor);
        bodyVertex(builder, lx, lz + 1, base, x0, h01, z1, c01, u, v + dv, light, shadeFactor);
        bodyVertex(builder, lx + 1, lz + 1, base, x1, h11, z1, c11, u + du, v + dv, light, shadeFactor);
        bodyVertex(builder, lx + 1, lz, base, x1, h10, z0, c10, u + du, v, light, shadeFactor);
    }

    /** One corner of the pile body, shaded with a normal smoothed over the neighboring corners (no facets). */
    private void bodyVertex(BufferBuilder builder, int cornerX, int cornerZ, double base, float x, float y, float z, int color, float u,
            float v, int light, float shadeFactor) {
        float east = corner(Math.min(LitterChunk.TILE, cornerX + 1), cornerZ, base);
        float west = corner(Math.max(0, cornerX - 1), cornerZ, base);
        float south = corner(cornerX, Math.min(LitterChunk.TILE, cornerZ + 1), base);
        float north = corner(cornerX, Math.max(0, cornerZ - 1), base);
        float spanX = (Math.min(LitterChunk.TILE, cornerX + 1) - Math.max(0, cornerX - 1)) * LitterField.CELL;
        float spanZ = (Math.min(LitterChunk.TILE, cornerZ + 1) - Math.max(0, cornerZ - 1)) * LitterField.CELL;
        float slopeX = (east - west) / spanX;
        float slopeZ = (south - north) / spanZ;
        float length = Mth.sqrt(slopeX * slopeX + 1.0F + slopeZ * slopeZ);
        float nx = -slopeX / length;
        float ny = 1.0F / length;
        float nz = -slopeZ / length;
        float shade = (nx * nx * 0.6F + ny * ny + nz * nz * 0.8F) * shadeFactor;
        builder.addVertex(x, y, z, opaque(color, shade), u, v, OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
    }

    /**
     * Height of a cell corner relative to the tile origin: the average of the four cells around it, where empty cells
     * (or cells on another level) count as the ground. Slightly below the stacks, so the loose top leaves lie on it.
     */
    private float corner(int cornerX, int cornerZ, double base) {
        double sum = 0.0;
        for (int dz = 0; dz < 2; dz++) {
            for (int dx = 0; dx < 2; dx++) {
                int r = (cornerZ + dz) * RING + cornerX + dx;
                sum += ringCount[r] > 0 && Math.abs(ringBase[r] - base) < 0.6 ? ringTop[r] : base;
            }
        }
        return (float) (sum * 0.25 - LitterField.SURFACE_DROP - currentSink - currentOriginY);
    }

    /** Color of a cell corner: the average of the surrounding stacks, so colors blend smoothly across a pile. */
    private int cornerColor(int cornerX, int cornerZ, int self) {
        int r = 0;
        int g = 0;
        int b = 0;
        for (int dz = 0; dz < 2; dz++) {
            for (int dx = 0; dx < 2; dx++) {
                int ring = (cornerZ + dz) * RING + cornerX + dx;
                int color = ringCount[ring] > 0 ? ringColor[ring] : ringColor[self];
                r += color >> 16 & 0xFF;
                g += color >> 8 & 0xFF;
                b += color & 0xFF;
            }
        }
        return (r / 4) << 16 | (g / 4) << 8 | b / 4;
    }

    private static int opaque(int rgb, float factor) {
        return 0xFF000000 | shade(rgb, factor);
    }

    /** Top of a neighboring stack for visibility; empty or unloaded neighbors show the ground at this cell's base. */
    private double neighborTop(int cellX, int cellZ, double base) {
        LitterChunk chunk = field.chunkAtCell(cellX, cellZ);
        if (chunk == null) {
            return base;
        }
        int i = LitterField.index(cellX, cellZ);
        int n = chunk.count[i];
        if (n == 0 || Math.abs(chunk.base[i] - base) > 0.6) {
            return base;
        }
        return chunk.base[i] + n * LitterField.LAYER;
    }

    private void buildMoving(Vec3 cam, Vector3f look, float partialTick) {
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (int i = 0, highWater = pool.highWater(); i < highWater; i++) {
            byte state = pool.state[i];
            if (state == LeafPool.FREE) {
                continue;
            }
            float x = (float) (Mth.lerp(partialTick, pool.prevX[i], pool.x[i]) - cam.x);
            float y = (float) (Mth.lerp(partialTick, pool.prevY[i], pool.y[i]) - cam.y);
            float z = (float) (Mth.lerp(partialTick, pool.prevZ[i], pool.z[i]) - cam.z);
            if (x * look.x() + y * look.y() + z * look.z() < -0.5F) {
                continue;
            }
            float size = pool.size[i];
            if (state == LeafPool.DYING) {
                size *= Math.max(0.0F, (pool.life[i] - partialTick) / LeafSimulation.DYING_TICKS);
            }
            float wet = pool.wetness(i);
            int color = wet > 0.0F ? shade(pool.color[i], 1.0F - (1.0F - LeafSimulation.WET_SHADE) * wet) : pool.color[i];
            emit(builder, x, y, z, Mth.lerp(partialTick, pool.prevYaw[i], pool.yaw[i]),
                    Mth.lerp(partialTick, pool.prevPitch[i], pool.pitch[i]), Mth.lerp(partialTick, pool.prevRoll[i], pool.roll[i]),
                    size, color, pool.light[i], pool.sprite[i]);
        }
        MeshData mesh = builder.build();
        movingEmpty = mesh == null;
        if (mesh != null) {
            moving.bind();
            moving.upload(mesh);
            VertexBuffer.unbind();
        }
    }

    private static int shade(int rgb, float factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8 & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }

    /**
     * Writes one leaf as a quad rotated by yaw (Y), pitch (X) and roll (Z), shaded by its normal like vanilla block
     * faces (both faces of a leaf look the same).
     */
    private void emit(BufferBuilder builder, float x, float y, float z, float yaw, float pitch, float roll, float half, int rgb,
            int light, int sprite) {
        float sa = Mth.sin(yaw);
        float ca = Mth.cos(yaw);
        float sb = Mth.sin(pitch);
        float cb = Mth.cos(pitch);
        float sc = Mth.sin(roll);
        float cc = Mth.cos(roll);
        float ax = (ca * cc + sa * sb * sc) * half;
        float ay = cb * sc * half;
        float az = (-sa * cc + ca * sb * sc) * half;
        float bx = sa * cb * half;
        float by = -sb * half;
        float bz = ca * cb * half;
        float nx = -ca * sc + sa * sb * cc;
        float ny = cb * cc;
        float nz = sa * sc + ca * sb * cc;
        float shade = nx * nx * 0.6F + ny * ny + nz * nz * 0.8F;
        int r = (int) ((rgb >> 16 & 0xFF) * shade);
        int g = (int) ((rgb >> 8 & 0xFF) * shade);
        int b = (int) ((rgb & 0xFF) * shade);
        int color = 0xFF000000 | r << 16 | g << 8 | b;
        int overlay = OverlayTexture.NO_OVERLAY;
        builder.addVertex(x - ax - bx, y - ay - by, z - az - bz, color, u0[sprite], v0[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x + ax - bx, y + ay - by, z + az - bz, color, u1[sprite], v0[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x + ax + bx, y + ay + by, z + az + bz, color, u1[sprite], v1[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x - ax + bx, y - ay + by, z - az + bz, color, u0[sprite], v1[sprite], overlay, light, nx, ny, nz);
    }

    @Override
    public void close() {
        for (Tile tile : tiles.values()) {
            tile.close();
        }
        tiles.clear();
        moving.close();
        bytes.close();
    }
}
