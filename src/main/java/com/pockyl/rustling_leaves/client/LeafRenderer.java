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
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.compat.ShaderPacks;
import com.pockyl.rustling_leaves.sim.LeafPalette;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.LeafSimulation;
import com.pockyl.rustling_leaves.sim.LitterChunk;
import com.pockyl.rustling_leaves.sim.LitterField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the leaves in two parts:
 * <ul>
 *   <li>the litter, in levels of detail. Near the camera in tiles of 4x4 blocks, each one static GPU buffer rebuilt
 *   only when its cells change (within a time budget per frame, nearest first); of every stack only the leaves that
 *   can be seen are drawn, so a pile of thousands of leaves costs a few hundred quads. Far away a whole chunk is one
 *   buffer of block-sized quads with the leaf layer textures - cheap enough to show the litter out to the render
 *   distance. A chunk switches between the two only once the other mesh is ready, so nothing pops in or out;</li>
 *   <li>moving leaves, written into one streaming buffer each frame and interpolated between ticks.</li>
 * </ul>
 * Both use the vanilla cutout terrain shader, so fog, lightmap and day/night match the world; with a shader pack Iris
 * swaps it for the pack's terrain program, so the leaves get the pack's lighting like the blocks around them.
 */
final class LeafRenderer implements AutoCloseable {
    private static final RenderType LEAVES = RenderType.create(RustlingLeaves.MOD_ID + "_leaves", DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS, 1 << 16, false, false, RenderType.CompositeState.builder()
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setShaderState(RenderStateShard.RENDERTYPE_CUTOUT_MIPPED_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    /**
     * Pixel shadows: translucent black silhouettes with hard pixel edges, laid on the ground like vanilla's decals -
     * the vanilla translucent terrain shader (Iris swaps it for the pack's translucent program), the block atlas
     * WITHOUT mipmaps (the edges stay pixel-sharp at a distance), no depth writes and the decal polygon offset, so
     * they neither hide what is behind them nor flicker against the ground.
     */
    private static final RenderType SHADOWS = RenderType.create(RustlingLeaves.MOD_ID + "_leaf_shadows",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1 << 16, false, false, RenderType.CompositeState.builder()
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));
    /**
     * Opacity of a falling leaf's shadow right at the ground; it fades in steps (pixel-art style, no smooth gradient)
     * until {@link #SHADOW_MAX_HEIGHT}.
     */
    private static final float SHADOW_ALPHA = 0.42F;
    private static final int SHADOW_STEPS = 4;
    private static final float SHADOW_MAX_HEIGHT = 4.0F;
    /** Under a canopy (no direct sun) a falling leaf's shadow keeps this much of its opacity. */
    private static final float SHADE_SHADOW = 0.35F;
    /** Opacity of the contact shadow a leaf lying on the ground casts one leaf pixel aside. */
    private static final float CONTACT_ALPHA = 0.3F;
    /** Opacity of the shadow on the ground next to a pile and one cell further out. */
    private static final float FOOT_ALPHA_NEAR = 0.34F;
    private static final float FOOT_ALPHA_FAR = 0.17F;
    /** Shadows on the ground lie this far above it (plus the polygon offset). */
    private static final float SHADOW_LIFT = 0.003F;
    /**
     * A leaf lying on a pile casts its silhouette, darkened, onto the leaves below: this much darker than the pile's
     * colour, one leaf pixel towards +X/+Z (light from the north-west, like the pixel art of the textures).
     */
    private static final float DROP_SHADE = 0.5F;
    /** Stacks at least this deep shade the ground around their foot. */
    private static final int FOOT_SHADOW_MIN = 6;
    /** How much a cell under taller neighbours is darkened at most (cavities and the foot of a pile slope). */
    private static final float OCCLUSION = 0.32F;
    private static final long REBUILD_BUDGET_NANOS = 3_000_000L;
    private static final double DETAIL_DISTANCE = 40.0;
    /** Beyond this distance (to the chunk center) a chunk is drawn as one coarse mesh; 8 blocks of hysteresis. */
    private static final double FAR_DISTANCE = 72.0;
    private static final double FAR_HYSTERESIS = 8.0;
    /**
     * The far mesh is made of patches of 2x2 cells (half a block). A patch is drawn once it holds this many leaves
     * (about the density at which the near tiles hide most of the ground), as one opaque quad: far away a leaf layer
     * with holes only shimmered.
     */
    private static final int FAR_PATCH_MIN = 16;
    /**
     * Far patches lie at least this much above the ground and have no second layer under them: at that distance the
     * depth buffer cannot tell thinner gaps apart, and the litter flickered against the ground.
     */
    private static final float FAR_LIFT = 0.05F;
    /** Halfway between near and far, the pile body sinks deeper under its leaf layer for the same reason. */
    private static final float MID_BODY_SINK = 0.05F;
    private static final int TOP_LAYERS = 2;
    private static final int MAX_SIDE_LAYERS = 48;
    private static final int SURFACE_MIN = LitterField.SURFACE_MIN;
    /** With a pile body, only a few loose leaves are added on the sides of a pile. */
    private static final int SURFACE_SIDE_LAYERS = 2;
    /** Loose leaves drawn on top of a pile body: enough to hide it, the body only shows as shadow between them. */
    private static final int SURFACE_TOP_LAYERS = 3;
    /** The pile body sits below the loose leaves and is darker: it reads as the depth of the pile. */
    private static final float BODY_SINK = 0.02F;
    private static final float BODY_SHADE = 0.8F;
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
    /** The tile and two cells around it, the reach of the shadow at the foot of a stack. */
    private static final int FOOT = LitterChunk.TILE + 4;
    private static final float TILE_BLOCKS = LitterChunk.TILE * LitterField.CELL;

    private final LeafSimulation simulation;
    private final LeafPool pool;
    private final LitterField field;
    private final Long2ObjectOpenHashMap<ChunkView> views = new Long2ObjectOpenHashMap<>();
    private final List<Mesh> dirty = new ArrayList<>();
    private final LitterField.Pose pose = new LitterField.Pose();
    private final ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 18);
    private final ByteBufferBuilder shadowBytes = new ByteBufferBuilder(1 << 16);
    private final VertexBuffer moving = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
    private final VertexBuffer movingShadows = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
    private boolean movingEmpty = true;
    private boolean movingShadowsEmpty = true;
    private boolean shaderPack;
    /** Whether the meshes were built with shadows (option {@code shadows}). */
    private boolean shadows;
    private final float[] shadowU0 = new float[LeafShapes.SHADOW_SPRITES.length];
    private final float[] shadowV0 = new float[LeafShapes.SHADOW_SPRITES.length];
    private final float[] shadowU1 = new float[LeafShapes.SHADOW_SPRITES.length];
    private final float[] shadowV1 = new float[LeafShapes.SHADOW_SPRITES.length];
    /** Shadow on the ground of the tile's cells and a margin of two cells: 0 none, 1 sparse, 2 dense. */
    private final byte[] footShadow = new byte[FOOT * FOOT];
    private final double[] footGround = new double[FOOT * FOOT];
    /** Whether the meshes were built with the mod's old leaf textures (option {@code oldLeafTextures}). */
    private boolean oldTextures;
    private final float[] u0 = new float[LeafShapes.SPRITES.length];
    private final float[] v0 = new float[LeafShapes.SPRITES.length];
    private final float[] u1 = new float[LeafShapes.SPRITES.length];
    private final float[] v1 = new float[LeafShapes.SPRITES.length];
    private final int[] ringCount = new int[RING * RING];
    private final double[] ringBase = new double[RING * RING];
    private final double[] ringTop = new double[RING * RING];
    /** Ground of empty ring cells, looked up on demand (see {@link #emptyGround}); the height it was looked up near. */
    private final double[] ringGround = new double[RING * RING];
    private final double[] ringGroundNear = new double[RING * RING];
    private int ringCellX;
    private int ringCellZ;
    private final int[] ringColor = new int[RING * RING];
    private final float[] layerU0 = new float[3];
    private final float[] layerV0 = new float[3];
    private final float[] layerU1 = new float[3];
    private final float[] layerV1 = new float[3];
    private double currentOriginY;
    private float currentSink;
    /** Stack top of the cell whose surface is being built (see {@link #corner}). */
    private double currentTop;
    private int frame;
    private int lastTileQuads;
    /** The terrain the level draws this frame, see {@link #drawnChunk}. */
    private int viewChunkX;
    private int viewChunkZ;
    private int viewDistance;
    private double viewCamX;
    private double viewCamZ;

    LeafRenderer(LeafSimulation simulation) {
        this.simulation = simulation;
        this.pool = simulation.pool();
        this.field = simulation.field();
    }

    /** A static GPU mesh of litter with its origin, bounds and the chunk revision it was built from. */
    private abstract static class Mesh {
        final LitterChunk chunk;
        double originX;
        double originY;
        double originZ;
        AABB bounds;
        VertexBuffer buffer;
        boolean empty = true;
        VertexBuffer shadowBuffer;
        boolean shadowEmpty = true;
        int builtRevision = Integer.MIN_VALUE;
        double distanceSq;
        int quads;

        Mesh(LitterChunk chunk) {
            this.chunk = chunk;
        }

        boolean built() {
            return builtRevision != Integer.MIN_VALUE;
        }

        void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
            if (shadowBuffer != null) {
                shadowBuffer.close();
                shadowBuffer = null;
            }
            shadowEmpty = true;
            builtRevision = Integer.MIN_VALUE;
        }
    }

    /** 4x4 blocks of detailed litter. */
    private static final class Tile extends Mesh {
        final int index;
        final int cellX;
        final int cellZ;
        boolean builtDetailed;
        boolean wantDetailed;

        Tile(LitterChunk chunk, int index) {
            super(chunk);
            this.index = index;
            cellX = chunk.x * LitterChunk.SIZE + index % LitterChunk.TILES * LitterChunk.TILE;
            cellZ = chunk.z * LitterChunk.SIZE + index / LitterChunk.TILES * LitterChunk.TILE;
            originX = cellX * LitterField.CELL;
            originZ = cellZ * LitterField.CELL;
        }
    }

    /** A whole chunk of litter as block-sized quads, for the distance. */
    private static final class FarMesh extends Mesh {
        FarMesh(LitterChunk chunk) {
            super(chunk);
            originX = chunk.x * 16;
            originZ = chunk.z * 16;
        }
    }

    /** What is drawn for one litter chunk: its detailed tiles or its far mesh. */
    private static final class ChunkView {
        final LitterChunk chunk;
        final Tile[] tiles = new Tile[LitterChunk.TILES * LitterChunk.TILES];
        FarMesh far;
        boolean farMode;
        boolean tilesReady;
        int seenFrame;

        ChunkView(LitterChunk chunk) {
            this.chunk = chunk;
        }

        void closeTiles() {
            for (int t = 0; t < tiles.length; t++) {
                if (tiles[t] != null) {
                    tiles[t].close();
                    tiles[t] = null;
                }
            }
        }

        void close() {
            closeTiles();
            if (far != null) {
                far.close();
                far = null;
            }
        }

        /** Far mesh while far away (once built), and close up until every tile is built. */
        boolean drawFar() {
            return far != null && far.built() && (farMode || !tilesReady);
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
        // While a shader pack is active, Iris reads BLOCK vertices in its larger terrain layout - also from buffers
        // that were built before: switching a pack on or off rebuilds every mesh, otherwise they come out garbled.
        // Switching the leaf textures rebuilds them too.
        boolean old = simulation.settings().oldLeafTextures;
        boolean shadowsOn = simulation.settings().shadows;
        if (ShaderPacks.inUse() != shaderPack || old != oldTextures || shadowsOn != shadows) {
            shaderPack = ShaderPacks.inUse();
            oldTextures = old;
            shadows = shadowsOn;
            for (ChunkView view : views.values()) {
                view.close();
            }
            views.clear();
        }
        viewDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        viewCamX = cam.x;
        viewCamZ = cam.z;
        viewChunkX = Mth.floor(cam.x) >> 4;
        viewChunkZ = Mth.floor(cam.z) >> 4;
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
        for (ChunkView view : views.values()) {
            if (!drawnChunk(view.chunk.x, view.chunk.z)) {
                continue;
            }
            if (view.drawFar()) {
                quads += draw(view.far, frustum, offset, cam);
            } else {
                for (Tile tile : view.tiles) {
                    if (tile != null) {
                        quads += draw(tile, frustum, offset, cam);
                    }
                }
            }
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
        if (shadows) {
            renderShadows(frustum, modelView, projection, cam);
        }
    }

    /** The shadows of the detailed tiles and of the falling leaves, in their own pass (see {@link #SHADOWS}). */
    private void renderShadows(Frustum frustum, Matrix4f modelView, Matrix4f projection, Vec3 cam) {
        SHADOWS.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            SHADOWS.clearRenderState();
            return;
        }
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, Minecraft.getInstance().getWindow());
        shader.apply();
        Uniform offset = shader.CHUNK_OFFSET;
        for (ChunkView view : views.values()) {
            if (!drawnChunk(view.chunk.x, view.chunk.z) || view.drawFar()) {
                continue;
            }
            for (Tile tile : view.tiles) {
                if (tile == null || tile.shadowBuffer == null || tile.shadowEmpty || tile.bounds == null
                        || !frustum.isVisible(tile.bounds)) {
                    continue;
                }
                if (offset != null) {
                    offset.set((float) (tile.originX - cam.x), (float) (tile.originY - cam.y), (float) (tile.originZ - cam.z));
                    offset.upload();
                }
                tile.shadowBuffer.bind();
                tile.shadowBuffer.draw();
            }
        }
        if (!movingShadowsEmpty) {
            if (offset != null) {
                offset.set(0.0F, 0.0F, 0.0F);
                offset.upload();
            }
            movingShadows.bind();
            movingShadows.draw();
        }
        if (offset != null) {
            offset.set(0.0F, 0.0F, 0.0F);
        }
        VertexBuffer.unbind();
        shader.clear();
        SHADOWS.clearRenderState();
    }

    /**
     * Whether the level draws the terrain of this chunk. The client keeps chunks loaded a few chunks beyond the render
     * distance and the litter reaches at least 48 blocks; leaves there would hang in the fog over missing ground. Both
     * vanilla's circle of chunks and Sodium's (camera to the nearest point of the chunk) count, whichever is smaller.
     */
    private boolean drawnChunk(int chunkX, int chunkZ) {
        if (!ChunkTrackingView.isInViewDistance(viewChunkX, viewChunkZ, viewDistance, chunkX, chunkZ)) {
            return false;
        }
        double minX = chunkX * 16.0;
        double minZ = chunkZ * 16.0;
        double dx = Math.max(0.0, Math.max(minX - viewCamX, viewCamX - (minX + 16.0)));
        double dz = Math.max(0.0, Math.max(minZ - viewCamZ, viewCamZ - (minZ + 16.0)));
        double reach = viewDistance * 16.0;
        return dx * dx + dz * dz < reach * reach;
    }

    private static int draw(Mesh mesh, Frustum frustum, Uniform offset, Vec3 cam) {
        if (mesh.buffer == null || mesh.empty || mesh.bounds == null || !frustum.isVisible(mesh.bounds)) {
            return 0;
        }
        if (offset != null) {
            offset.set((float) (mesh.originX - cam.x), (float) (mesh.originY - cam.y), (float) (mesh.originZ - cam.z));
            offset.upload();
        }
        mesh.buffer.bind();
        mesh.buffer.draw();
        return mesh.quads;
    }

    private void updateSprites() {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        for (int s = 0; s < LeafShapes.SPRITES.length; s++) {
            TextureAtlasSprite sprite = atlas.getSprite(LeafShapes.SPRITES[s]);
            u0[s] = sprite.getU0();
            v0[s] = sprite.getV0();
            u1[s] = sprite.getU1();
            v1[s] = sprite.getV1();
        }
        for (int s = 0; s < LeafShapes.SHADOW_SPRITES.length; s++) {
            TextureAtlasSprite sprite = atlas.getSprite(LeafShapes.SHADOW_SPRITES[s]);
            shadowU0[s] = sprite.getU0();
            shadowV0[s] = sprite.getV0();
            shadowU1[s] = sprite.getU1();
            shadowV1[s] = sprite.getV1();
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

    /**
     * Decides for every litter chunk whether it is drawn near (tiles) or far (one coarse mesh), drops views of chunks
     * that are gone and lists the meshes that need a rebuild.
     */
    private void collectTiles(Vec3 cam) {
        dirty.clear();
        double nearSq = (FAR_DISTANCE - FAR_HYSTERESIS) * (FAR_DISTANCE - FAR_HYSTERESIS);
        double farSq = (FAR_DISTANCE + FAR_HYSTERESIS) * (FAR_DISTANCE + FAR_HYSTERESIS);
        for (LitterChunk chunk : field.chunks()) {
            if (chunk.total() == 0) {
                continue;
            }
            long key = ChunkPos.asLong(chunk.x, chunk.z);
            ChunkView view = views.get(key);
            if (view == null || view.chunk != chunk) {
                if (view != null) {
                    view.close();
                }
                view = new ChunkView(chunk);
                views.put(key, view);
            }
            view.seenFrame = frame;
            double cx = chunk.x * 16 + 8 - cam.x;
            double cz = chunk.z * 16 + 8 - cam.z;
            double distanceSq = cx * cx + cz * cz;
            view.farMode = view.farMode ? distanceSq > nearSq : distanceSq > farSq;
            if (view.farMode) {
                if (view.far == null) {
                    view.far = new FarMesh(chunk);
                }
                view.far.distanceSq = distanceSq;
                if (view.far.builtRevision != chunk.revision()) {
                    dirty.add(view.far);
                }
                if (view.far.built()) {
                    view.closeTiles();
                    view.tilesReady = false;
                }
                continue;
            }
            boolean ready = true;
            for (int t = 0; t < view.tiles.length; t++) {
                Tile tile = view.tiles[t];
                if (tile == null) {
                    tile = new Tile(chunk, t);
                    view.tiles[t] = tile;
                }
                double dx = tile.originX + TILE_BLOCKS * 0.5 - cam.x;
                double dz = tile.originZ + TILE_BLOCKS * 0.5 - cam.z;
                tile.distanceSq = dx * dx + dz * dz;
                tile.wantDetailed = tile.distanceSq < DETAIL_DISTANCE * DETAIL_DISTANCE;
                if (tile.builtRevision != chunk.tileRevision[t] || tile.builtDetailed != tile.wantDetailed) {
                    dirty.add(tile);
                }
                ready &= tile.built();
            }
            view.tilesReady = ready;
            if (ready && view.far != null) {
                view.far.close();
                view.far = null;
            }
        }
        for (Iterator<Long2ObjectMap.Entry<ChunkView>> it = views.long2ObjectEntrySet().iterator(); it.hasNext(); ) {
            ChunkView view = it.next().getValue();
            if (view.seenFrame != frame) {
                view.close();
                it.remove();
            }
        }
    }

    private void rebuildTiles() {
        if (dirty.isEmpty()) {
            return;
        }
        dirty.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));
        long deadline = System.nanoTime() + REBUILD_BUDGET_NANOS;
        for (Mesh mesh : dirty) {
            if (mesh instanceof Tile tile) {
                buildTile(tile);
            } else {
                buildFar((FarMesh) mesh);
            }
            if (System.nanoTime() > deadline) {
                break;
            }
        }
        VertexBuffer.unbind();
    }

    /**
     * The coarse mesh of a far chunk, a simplified form of the litter: per patch of 2x2 cells one flat opaque quad at
     * the average height of its leaves, with the solid leaf texture. Thin litter is left out.
     */
    private void buildFar(FarMesh mesh) {
        LitterChunk chunk = mesh.chunk;
        mesh.builtRevision = chunk.revision();
        float aged = Math.min(1.0F, simulation.settings().autumnColors + 0.3F);
        int patches = LitterChunk.SIZE / 2;
        float[] height = new float[patches * patches];
        int[] color = new int[patches * patches];
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int pz = 0; pz < patches; pz++) {
            for (int px = 0; px < patches; px++) {
                int leaves = 0;
                double topSum = 0.0;
                double base = 0.0;
                int filled = 0;
                int rgb = 0;
                for (int c = 0; c < 4; c++) {
                    int i = LitterField.index(chunk.x * LitterChunk.SIZE + px * 2 + (c & 1), chunk.z * LitterChunk.SIZE + pz * 2 + (c >> 1));
                    int n = chunk.count[i];
                    if (n > 0) {
                        leaves += n;
                        topSum += chunk.base[i] + n * LitterField.LAYER;
                        base = chunk.base[i];
                        rgb = chunk.color[i];
                        filled++;
                    }
                }
                int p = pz * patches + px;
                if (leaves < FAR_PATCH_MIN) {
                    height[p] = Float.NaN;
                    continue;
                }
                double y = Math.max(topSum / filled - LitterField.SURFACE_DROP, base + FAR_LIFT);
                height[p] = (float) y;
                int cellX = chunk.x * LitterChunk.SIZE + px * 2;
                int cellZ = chunk.z * LitterChunk.SIZE + pz * 2;
                color[p] = LeafPalette.vary(rgb, LeafPalette.hash(cellX, cellZ, 0x7A), aged, LeafShape.BROAD);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (minY == Double.MAX_VALUE) {
            mesh.empty = true;
            mesh.quads = 0;
            return;
        }
        mesh.originY = Math.floor(minY);
        mesh.bounds = new AABB(mesh.originX, minY - 0.2, mesh.originZ, mesh.originX + 16, maxY + 0.3, mesh.originZ + 16);
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        int quads = 0;
        float half = 2 * LitterField.CELL;
        for (int pz = 0; pz < patches; pz++) {
            for (int px = 0; px < patches; px++) {
                int p = pz * patches + px;
                if (Float.isNaN(height[p])) {
                    continue;
                }
                float x0 = px * half;
                float z0 = pz * half;
                float y = (float) (height[p] - mesh.originY);
                int light = simulation.lightAt(mesh.originX + x0 + half * 0.5, height[p] + 0.1, mesh.originZ + z0 + half * 0.5);
                int cellX = chunk.x * LitterChunk.SIZE + px * 2;
                int cellZ = chunk.z * LitterChunk.SIZE + pz * 2;
                float[] uv = layerUv(TEX_BODY, cellX, cellZ, 2);
                int argb = 0xFF000000 | color[p];
                int overlay = OverlayTexture.NO_OVERLAY;
                // Counter-clockwise seen from above (shader packs recompute the normal from the winding).
                builder.addVertex(x0, y, z0, argb, uv[0], uv[2], overlay, light, 0.0F, 1.0F, 0.0F);
                builder.addVertex(x0, y, z0 + half, argb, uv[0], uv[3], overlay, light, 0.0F, 1.0F, 0.0F);
                builder.addVertex(x0 + half, y, z0 + half, argb, uv[1], uv[3], overlay, light, 0.0F, 1.0F, 0.0F);
                builder.addVertex(x0 + half, y, z0, argb, uv[1], uv[2], overlay, light, 0.0F, 1.0F, 0.0F);
                quads++;
            }
        }
        mesh.quads = quads;
        MeshData data = builder.build();
        if (data == null) {
            mesh.empty = true;
            return;
        }
        if (mesh.buffer == null) {
            mesh.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        mesh.buffer.bind();
        mesh.buffer.upload(data);
        mesh.empty = false;
    }

    /**
     * Texture coordinates (u at the west and east edge, v at the north and south edge) of a span of {@code cells}
     * cells starting at a cell, for a layer texture that covers one block. Every block mirrors the texture its own
     * way: the seams still match (a mirrored edge meets the same pixels), but the pattern no longer repeats block by
     * block in a visible grid.
     */
    private float[] layerUv(int texture, int cellX, int cellZ, int cells) {
        long hash = LeafPalette.hash(cellX >> 2, cellZ >> 2, 0x3C);
        float du = (layerU1[texture] - layerU0[texture]) * 0.25F;
        float dv = (layerV1[texture] - layerV0[texture]) * 0.25F;
        int cx = cellX & 3;
        int cz = cellZ & 3;
        float west;
        float east;
        float north;
        float south;
        if ((hash & 1) != 0) {
            west = layerU1[texture] - du * cx;
            east = west - du * cells;
        } else {
            west = layerU0[texture] + du * cx;
            east = west + du * cells;
        }
        if ((hash & 2) != 0) {
            north = layerV1[texture] - dv * cz;
            south = north - dv * cells;
        } else {
            north = layerV0[texture] + dv * cz;
            south = north + dv * cells;
        }
        return new float[] {west, east, north, south};
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
        tile.bounds = new AABB(tile.originX - 0.6, minY - 0.3, tile.originZ - 0.6, tile.originX + TILE_BLOCKS + 0.6, maxY + 0.3,
                tile.originZ + TILE_BLOCKS + 0.6);
        loadRing(tile);
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        boolean detailShadows = shadows && tile.wantDetailed;
        BufferBuilder shadowBuilder = new BufferBuilder(shadowBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        if (detailShadows) {
            Arrays.fill(footShadow, (byte) 0);
        }
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
                float occlusion = shadows ? occlusion(lx, lz, base, top) : 1.0F;
                if (detailShadows && n >= FOOT_SHADOW_MIN) {
                    markFootShadow(tile, lx, lz, base, top);
                }
                double lowest = Math.min(Math.min(neighborTop(cellX + 1, cellZ, base), neighborTop(cellX - 1, cellZ, base)),
                        Math.min(neighborTop(cellX, cellZ + 1, base), neighborTop(cellX, cellZ - 1, base)));
                int side = Mth.clamp(Mth.ceil((top - lowest) / LitterField.LAYER), 0, MAX_SIDE_LAYERS);
                int light = simulation.lightAt((cellX + 0.5) * LitterField.CELL, top + 0.05, (cellZ + 0.5) * LitterField.CELL);
                int layers = topLayers;
                if (n >= SURFACE_MIN) {
                    // Carpet: a leaf layer with gaps over the ground. Pile: a solid shadowed body under that layer.
                    if (n >= BODY_MIN) {
                        emitSurface(builder, tile, lx, lz, base, light, TEX_BODY, tile.wantDetailed ? BODY_SINK : MID_BODY_SINK,
                                BODY_SHADE * occlusion);
                        quads++;
                    }
                    emitSurface(builder, tile, lx, lz, base, light, n >= DENSE_MIN ? TEX_LAYER : TEX_SPARSE, LAYER_SINK,
                            occlusion);
                    quads++;
                    // Beyond the detail distance the leaf layer stands for the loose leaves: single leaves there are
                    // smaller than a few pixels and only flicker.
                    side = tile.wantDetailed ? Math.min(side, SURFACE_SIDE_LAYERS) : 0;
                    layers = tile.wantDetailed ? SURFACE_TOP_LAYERS : 0;
                }
                int visible = Math.min(n, layers + (tile.wantDetailed ? side : side / 2));
                int self = (lz + 1) * RING + lx + 1;
                for (int depth = 0; depth < visible; depth++) {
                    field.pose(chunk, cellX, cellZ, n - 1 - depth, pose);
                    // Leaves deeper in the pile are in its shadow.
                    float shade = Math.max(0.84F, 1.0F - depth * 0.04F) * occlusion;
                    int sprite = LeafShapes.slot(pose.shape, pose.sprite, oldTextures);
                    float px = (float) (pose.x - tile.originX);
                    float py = (float) (pose.y - tile.originY);
                    float pz = (float) (pose.z - tile.originZ);
                    float step = pose.size * 0.4F;
                    if (detailShadows && n >= SURFACE_MIN && depth < 2) {
                        // The leaf's silhouette, darker, one leaf pixel aside and just under it: a pixel-art drop shadow.
                        emit(builder, px + step, py - 0.004F, pz + step, pose.yaw, pose.pitch, pose.roll, pose.size,
                                shade(ringColor[self], DROP_SHADE * occlusion), light, sprite);
                        quads++;
                    } else if (detailShadows && n < SURFACE_MIN) {
                        // A leaf lying on the ground: a faint contact shadow one leaf pixel aside, so it does not look
                        // painted onto the grass.
                        int s = pose.shape == LeafShape.PETAL.ordinal() ? LeafShapes.SHADOW_PETAL : LeafShapes.SHADOW_LEAF;
                        emitShadow(shadowBuilder, px + step, py, pz + step, (float) (base - tile.originY) + SHADOW_LIFT, 0.0F,
                                pose.yaw, pose.pitch, pose.roll, pose.size, s, shadowColor(CONTACT_ALPHA * occlusion), light);
                    }
                    emit(builder, px, py, pz, pose.yaw, pose.pitch, pose.roll, pose.size, shade(pose.color, shade), light, sprite);
                    quads++;
                }
            }
        }
        tile.quads = quads;
        MeshData mesh = builder.build();
        if (mesh == null) {
            tile.empty = true;
            // Finish the shadow builder too: its bytes would otherwise end up in the next mesh built from them.
            MeshData unused = shadowBuilder.build();
            if (unused != null) {
                unused.close();
            }
            tile.shadowEmpty = true;
            return;
        }
        if (tile.buffer == null) {
            tile.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        tile.buffer.bind();
        tile.buffer.upload(mesh);
        tile.empty = false;
        buildFootShadows(tile, detailShadows, shadowBuilder);
    }

    /**
     * Light that reaches a cell between taller stacks, from 1 (open) down to {@code 1 - OCCLUSION}: the sides and the
     * foot of a pile and hollows in it are darker than its crest, in steps of 0.04 like a pixel-art shading ramp.
     */
    private float occlusion(int lx, int lz, double base, double top) {
        float sum = 0.0F;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int r = (lz + 1 + dz) * RING + lx + 1 + dx;
                if (ringCount[r] > 0 && Math.abs(ringBase[r] - base) <= LitterField.SAME_LEVEL) {
                    sum += (float) Mth.clamp((ringTop[r] - top) / 0.12, 0.0, 1.0);
                }
            }
        }
        float shade = 1.0F - OCCLUSION * Math.min(1.0F, sum / 4.0F);
        return Math.round(shade * 25.0F) / 25.0F;
    }

    /**
     * Shades the ground around a stack that rises above it: the cells next to it get the dense shadow, those two
     * cells away the sparse one (only where the stack stands well above them). Cells with a leaf surface of their own
     * are left alone, {@link #occlusion} shades those.
     */
    private void markFootShadow(Tile tile, int lx, int lz, double base, double top) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring == 0) {
                    continue;
                }
                int f = (lz + dz + 2) * FOOT + lx + dx + 2;
                int cellX = tile.cellX + lx + dx;
                int cellZ = tile.cellZ + lz + dz;
                LitterChunk chunk = field.chunkAtCell(cellX, cellZ);
                if (chunk == null) {
                    continue;
                }
                int i = LitterField.index(cellX, cellZ);
                int n = chunk.count[i];
                if (n >= SURFACE_MIN) {
                    continue;
                }
                double ground = n > 0 ? chunk.base[i] : field.groundAt(cellX, cellZ, base);
                if (Math.abs(ground - base) > LitterField.SAME_LEVEL) {
                    continue;
                }
                double rise = top - ground;
                byte level = ring == 1 ? (byte) (rise >= 0.15 ? 2 : rise >= 0.06 ? 1 : 0) : (byte) (rise >= 0.2 ? 1 : 0);
                if (level > footShadow[f]) {
                    footShadow[f] = level;
                    footGround[f] = ground;
                }
            }
        }
    }

    private void buildFootShadows(Tile tile, boolean detailShadows, BufferBuilder builder) {
        if (detailShadows) {
            for (int f = 0; f < footShadow.length; f++) {
                byte level = footShadow[f];
                if (level == 0) {
                    continue;
                }
                int lx = f % FOOT - 2;
                int lz = f / FOOT - 2;
                double ground = footGround[f];
                int light = simulation.lightAt(tile.originX + (lx + 0.5) * LitterField.CELL, ground + 0.05,
                        tile.originZ + (lz + 0.5) * LitterField.CELL);
                float x0 = lx * LitterField.CELL;
                float z0 = lz * LitterField.CELL;
                float x1 = x0 + LitterField.CELL;
                float z1 = z0 + LitterField.CELL;
                float y = (float) (ground - tile.originY) + SHADOW_LIFT;
                flatQuad(builder, y, x0, z0, x0, z1, x1, z1, x1, z0, LeafShapes.SHADOW_CELL,
                        shadowColor(level == 2 ? FOOT_ALPHA_NEAR : FOOT_ALPHA_FAR), light);
            }
        }
        MeshData mesh = builder.build();
        tile.shadowEmpty = mesh == null;
        if (mesh == null) {
            return;
        }
        if (tile.shadowBuffer == null) {
            tile.shadowBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        tile.shadowBuffer.bind();
        tile.shadowBuffer.upload(mesh);
    }

    /** Black at {@code alpha} opacity. */
    private static int shadowColor(float alpha) {
        return Math.round(alpha * 255.0F) << 24;
    }

    /**
     * A horizontal shadow quad through four corners (x, z) at height y with the whole shadow sprite {@code s} (its
     * corners in the order u0v0, u0v1, u1v1, u1v0), wound counter-clockwise seen from above: shader packs take the
     * normal from the winding.
     */
    private void flatQuad(BufferBuilder builder, float y, float x0, float z0, float x1, float z1, float x2, float z2, float x3,
            float z3, int s, int color, int light) {
        float su0 = shadowU0[s];
        float sv0 = shadowV0[s];
        float su1 = shadowU1[s];
        float sv1 = shadowV1[s];
        // y of (p2 - p0) x (p3 - p1): positive = counter-clockwise from above, else the corners go the other way round.
        float cross = (z2 - z0) * (x3 - x1) - (x2 - x0) * (z3 - z1);
        int overlay = OverlayTexture.NO_OVERLAY;
        if (cross >= 0.0F) {
            builder.addVertex(x0, y, z0, color, su0, sv0, overlay, light, 0.0F, 1.0F, 0.0F);
            builder.addVertex(x1, y, z1, color, su0, sv1, overlay, light, 0.0F, 1.0F, 0.0F);
            builder.addVertex(x2, y, z2, color, su1, sv1, overlay, light, 0.0F, 1.0F, 0.0F);
            builder.addVertex(x3, y, z3, color, su1, sv0, overlay, light, 0.0F, 1.0F, 0.0F);
        } else {
            builder.addVertex(x0, y, z0, color, su0, sv0, overlay, light, 0.0F, 1.0F, 0.0F);
            builder.addVertex(x3, y, z3, color, su1, sv0, overlay, light, 0.0F, 1.0F, 0.0F);
            builder.addVertex(x2, y, z2, color, su1, sv1, overlay, light, 0.0F, 1.0F, 0.0F);
            builder.addVertex(x1, y, z1, color, su0, sv1, overlay, light, 0.0F, 1.0F, 0.0F);
        }
    }

    /** Ground under an empty ring cell near {@code base}, cached per tile build. */
    private double emptyGround(int r, double base) {
        if (ringGroundNear[r] != base) {
            ringGroundNear[r] = base;
            ringGround[r] = field.groundAt(ringCellX + r % RING - 1, ringCellZ + r / RING - 1, base);
        }
        return ringGround[r];
    }

    /** Counts, heights and colors of the tile's cells and the ring of cells around it. */
    private void loadRing(Tile tile) {
        ringCellX = tile.cellX;
        ringCellZ = tile.cellZ;
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
                ringGroundNear[r] = Double.NaN;
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
        currentTop = ringTop[(lz + 1) * RING + lx + 1];
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
        float[] uv = layerUv(texture, cellX, cellZ, 1);
        float x0 = lx * LitterField.CELL;
        float z0 = lz * LitterField.CELL;
        float x1 = x0 + LitterField.CELL;
        float z1 = z0 + LitterField.CELL;
        bodyVertex(builder, lx, lz, base, x0, h00, z0, c00, uv[0], uv[2], light, shadeFactor);
        bodyVertex(builder, lx, lz + 1, base, x0, h01, z1, c01, uv[0], uv[3], light, shadeFactor);
        bodyVertex(builder, lx + 1, lz + 1, base, x1, h11, z1, c11, uv[1], uv[3], light, shadeFactor);
        bodyVertex(builder, lx + 1, lz, base, x1, h10, z0, c10, uv[1], uv[2], light, shadeFactor);
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
                double ground = ringCount[r] > 0 ? ringBase[r] : emptyGround(r, base);
                if (Math.abs(ground - base) > LitterField.SAME_LEVEL) {
                    // A step to another level (slab, stairs): the surface stays level instead of bending over it.
                    sum += currentTop;
                } else {
                    sum += ringCount[r] > 0 ? ringTop[r] : base;
                }
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
        if (n == 0 || Math.abs(chunk.base[i] - base) > LitterField.SAME_LEVEL) {
            return base;
        }
        return chunk.base[i] + n * LitterField.LAYER;
    }

    private void buildMoving(Vec3 cam, Vector3f look, float partialTick) {
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        // How strong the sun's shadows are: none below SHADOW_MIN_SUN, full a little higher, weak in rain.
        float sunStrength = (float) Mth.clamp((simulation.sunY() - LeafSimulation.SHADOW_MIN_SUN) / 0.15, 0.0, 1.0);
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            sunStrength *= 1.0F - 0.7F * level.getRainLevel(partialTick);
        }
        BufferBuilder shadowBuilder = shadows && sunStrength > 0.0F
                ? new BufferBuilder(shadowBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK) : null;
        float sunSlope = (float) (simulation.sunX() / Math.max(LeafSimulation.SHADOW_MIN_SUN, simulation.sunY()));
        for (int i = 0, highWater = pool.highWater(); i < highWater; i++) {
            byte state = pool.state[i];
            if (state == LeafPool.FREE || !drawnChunk(Mth.floor(pool.x[i]) >> 4, Mth.floor(pool.z[i]) >> 4)) {
                continue;
            }
            float x = (float) (Mth.lerp(partialTick, pool.prevX[i], pool.x[i]) - cam.x);
            float y = (float) (Mth.lerp(partialTick, pool.prevY[i], pool.y[i]) - cam.y)
                    + Mth.lerp(partialTick, pool.prevLift[i], pool.lift[i]);
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
            float yaw = Mth.lerp(partialTick, pool.prevYaw[i], pool.yaw[i]);
            float pitch = Mth.lerp(partialTick, pool.prevPitch[i], pool.pitch[i]);
            float roll = Mth.lerp(partialTick, pool.prevRoll[i], pool.roll[i]);
            emit(builder, x, y, z, yaw, pitch, roll, size, color, pool.light[i], LeafShapes.slot(pool.shape[i], pool.sprite[i], oldTextures));
            float ground = pool.shadow[i];
            if (shadowBuilder != null && state == LeafPool.FALLING && !Float.isNaN(ground)) {
                float height = y - (float) (ground - cam.y);
                if (height > 0.04F && height < SHADOW_MAX_HEIGHT) {
                    int s = pool.shape[i] == LeafShape.PETAL.ordinal() ? LeafShapes.SHADOW_PETAL : LeafShapes.SHADOW_LEAF;
                    // Fainter the higher the leaf, in a few even steps.
                    int step = Math.min(SHADOW_STEPS - 1, (int) (height / SHADOW_MAX_HEIGHT * SHADOW_STEPS));
                    float alpha = SHADOW_ALPHA * (SHADOW_STEPS - step) / SHADOW_STEPS * sunStrength
                            * (pool.shadowSunlit[i] ? 1.0F : SHADE_SHADOW);
                    if (alpha >= 0.03F) {
                        emitShadow(shadowBuilder, x, y, z, (float) (ground - cam.y) + SHADOW_LIFT, sunSlope, yaw, pitch, roll, size, s,
                                shadowColor(alpha), pool.light[i]);
                    }
                }
            }
        }
        MeshData mesh = builder.build();
        movingEmpty = mesh == null;
        if (mesh != null) {
            moving.bind();
            moving.upload(mesh);
            VertexBuffer.unbind();
        }
        MeshData shadowMesh = shadowBuilder == null ? null : shadowBuilder.build();
        movingShadowsEmpty = shadowMesh == null;
        if (shadowMesh != null) {
            movingShadows.bind();
            movingShadows.upload(shadowMesh);
            VertexBuffer.unbind();
        }
    }

    /**
     * The shadow of a leaf: its quad (same basis as {@link #emit}) projected along the sunbeam onto the ground at
     * {@code groundY}. {@code sunSlope} = sun x / sun y: 0 at noon (straight down), long shadows to the side towards
     * sunset and sunrise; a leaf seen edge-on by the sun casts a thin sliver.
     */
    private void emitShadow(BufferBuilder builder, float x, float y, float z, float groundY, float sunSlope, float yaw, float pitch,
            float roll, float half, int s, int color, int light) {
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
        float x0 = x - ax - bx - sunSlope * (y - ay - by - groundY);
        float x1 = x - ax + bx - sunSlope * (y - ay + by - groundY);
        float x2 = x + ax + bx - sunSlope * (y + ay + by - groundY);
        float x3 = x + ax - bx - sunSlope * (y + ay - by - groundY);
        flatQuad(builder, groundY, x0, z - az - bz, x1, z - az + bz, x2, z + az + bz, x3, z + az - bz, s, color, light);
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
        // Counter-clockwise seen from the normal's side: shader packs (Iris) recompute the normal from the winding.
        builder.addVertex(x - ax - bx, y - ay - by, z - az - bz, color, u0[sprite], v0[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x - ax + bx, y - ay + by, z - az + bz, color, u0[sprite], v1[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x + ax + bx, y + ay + by, z + az + bz, color, u1[sprite], v1[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x + ax - bx, y + ay - by, z + az - bz, color, u1[sprite], v0[sprite], overlay, light, nx, ny, nz);
    }

    @Override
    public void close() {
        for (ChunkView view : views.values()) {
            view.close();
        }
        views.clear();
        moving.close();
        movingShadows.close();
        bytes.close();
        shadowBytes.close();
    }
}
