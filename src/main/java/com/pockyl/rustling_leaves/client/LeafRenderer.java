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
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.rustling_leaves.RustlingLeaves;
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
        tile.bounds = new AABB(tile.originX - 0.3, minY - 0.2, tile.originZ - 0.3, tile.originX + TILE_BLOCKS + 0.3, maxY + 0.3,
                tile.originZ + TILE_BLOCKS + 0.3);
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
                int visible = Math.min(n, topLayers + (tile.wantDetailed ? side : side / 2));
                int light = simulation.lightAt((cellX + 0.5) * LitterField.CELL, top + 0.05, (cellZ + 0.5) * LitterField.CELL);
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
            emit(builder, x, y, z, Mth.lerp(partialTick, pool.prevYaw[i], pool.yaw[i]),
                    Mth.lerp(partialTick, pool.prevPitch[i], pool.pitch[i]), Mth.lerp(partialTick, pool.prevRoll[i], pool.roll[i]),
                    size, pool.color[i], pool.light[i], pool.sprite[i]);
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
