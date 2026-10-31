package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.compat.IrisBlocks;
import com.pockyl.rustling_leaves.client.render.QuadSink;
import com.pockyl.rustling_leaves.platform.ClientPlatform;

/**
 * The GPU side of the leaf renderer: meshes on the GPU, filling them and the two passes that draw them. The renderer
 * decides what to draw and writes the quads; only this class knows how Minecraft draws them, so it is what a port to
 * another Minecraft version rewrites. Every version's backend has the same public members, doing this:
 * <ul>
 *   <li>{@link #begin} hands out the {@link QuadSink} of a pass for one new mesh; {@link #finish} ends it and puts it
 *   into a {@link Mesh} (the mesh is left as it was when nothing was written), {@link #discard} ends it unused. One
 *   mesh per pass is written at a time, the two passes' meshes may be written side by side.</li>
 *   <li>A {@link Mesh} keeps its data on the GPU until it is filled again or closed. {@link #staticMesh} is for
 *   litter, rebuilt now and then; {@link #streamingMesh} for the moving leaves, refilled every frame.</li>
 *   <li>{@link #beginPass} sets up a pass for the frame (false: it cannot be drawn now, skip it), {@link #draw}
 *   draws a mesh whose coordinates are relative to {@code origin} (world coordinates; a mesh built relative to the
 *   camera is drawn at the camera position), {@link #endPass} restores the state the level renderer expects. All
 *   meshes of a frame are filled before its first pass and stay untouched until both passes are over, so a backend
 *   may also just note the draws and issue them all in {@link #endPass}.</li>
 * </ul>
 * This is the 1.21.1 backend: one {@link VertexBuffer} per mesh, vanilla's terrain shaders through render types and
 * their {@code ChunkOffset} uniform for the origin, like vanilla draws the chunk sections; Iris swaps the shaders for
 * the pack's terrain programs by itself.
 */
public final class RenderBackend implements AutoCloseable {
    /** The two passes, in the order the leaves are drawn. */
    public enum Pass {
        /**
         * Leaves, pile surfaces and the pile's drop shadows: the vanilla cutout terrain shader with the mipped block
         * atlas and the lightmap, depth tested and written, no face culling (leaves are seen from both sides), quads
         * in the block vertex format.
         */
        LEAVES,
        /**
         * Pixel shadows on the ground: translucent black silhouettes with the block atlas without mipmaps and the
         * lightmap, colour written, depth tested but not written, polygon offset like vanilla's decals, no culling.
         */
        SHADOWS
    }

    private static final RenderType LEAVES = LeafRenderType.LEAVES;
    private static final RenderType SHADOWS = LeafRenderType.SHADOWS;

    private final Sink leaves = new Sink(new ByteBufferBuilder(1 << 18));
    private final Sink shadows = new Sink(new ByteBufferBuilder(1 << 16));
    /** The pass being drawn, its shader and the shader's chunk offset (null when the shader has none). */
    private RenderType passType;
    private ShaderInstance shader;
    private Uniform offset;
    private double camX;
    private double camY;
    private double camZ;

    /** A mesh on the GPU, empty until it is first filled. */
    public static final class Mesh implements AutoCloseable {
        private final VertexBuffer.Usage usage;
        private VertexBuffer buffer;

        private Mesh(VertexBuffer.Usage usage) {
            this.usage = usage;
        }

        @Override
        public void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
        }
    }

    /** A mesh that is filled once in a while and drawn many times. */
    public static Mesh staticMesh() {
        return new Mesh(VertexBuffer.Usage.STATIC);
    }

    /** A mesh that is filled anew every frame. */
    public static Mesh streamingMesh() {
        return new Mesh(VertexBuffer.Usage.DYNAMIC);
    }

    /** The atlas the leaves' sprites are stitched into, the one both passes draw with. */
    public TextureAtlas blockAtlas() {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
    }

    /** Starts a new mesh of the pass and returns where to write its quads. */
    public QuadSink begin(Pass pass) {
        return sink(pass).begin();
    }

    /**
     * Ends the mesh of the pass and uploads it into {@code mesh}. Returns false when no quad was written: then
     * {@code mesh} keeps what it held.
     */
    public boolean finish(Pass pass, Mesh mesh) {
        MeshData data = sink(pass).build();
        if (data == null) {
            return false;
        }
        if (mesh.buffer == null) {
            mesh.buffer = new VertexBuffer(mesh.usage);
        }
        mesh.buffer.bind();
        mesh.buffer.upload(data);
        VertexBuffer.unbind();
        return true;
    }

    /** Ends the mesh of the pass without using it. */
    public void discard(Pass pass) {
        sink(pass).discard();
    }

    private Sink sink(Pass pass) {
        return pass == Pass.LEAVES ? leaves : shadows;
    }

    /**
     * Sets up a pass for this frame's level render: the projection, the camera's model-view matrix and the camera
     * position that mesh origins are relative to. Returns false when the pass cannot be drawn (then do not call
     * {@link #draw} or {@link #endPass}).
     */
    public boolean beginPass(Pass pass, Matrix4f modelView, Matrix4f projection, double cameraX, double cameraY, double cameraZ) {
        RenderType type = pass == Pass.LEAVES ? LEAVES : SHADOWS;
        type.setupRenderState();
        ShaderInstance instance = RenderSystem.getShader();
        if (instance == null) {
            type.clearRenderState();
            return false;
        }
        instance.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, Minecraft.getInstance().getWindow());
        instance.apply();
        passType = type;
        shader = instance;
        offset = instance.CHUNK_OFFSET;
        camX = cameraX;
        camY = cameraY;
        camZ = cameraZ;
        return true;
    }

    /** Draws a filled mesh whose coordinates are relative to the world position {@code origin}. */
    public void draw(Mesh mesh, double originX, double originY, double originZ) {
        VertexBuffer buffer = mesh.buffer;
        if (buffer == null) {
            return;
        }
        if (offset != null) {
            offset.set((float) (originX - camX), (float) (originY - camY), (float) (originZ - camZ));
            offset.upload();
        }
        buffer.bind();
        buffer.draw();
    }

    public void endPass() {
        if (offset != null) {
            offset.set(0.0F, 0.0F, 0.0F);
        }
        VertexBuffer.unbind();
        shader.clear();
        passType.clearRenderState();
        passType = null;
        shader = null;
        offset = null;
    }

    @Override
    public void close() {
        leaves.close();
        shadows.close();
    }

    /**
     * Subclass only to reach the render state shards, which are protected in vanilla; {@code RenderType.create} is not
     * public in vanilla either, so the loader makes the types.
     */
    private static final class LeafRenderType extends RenderType {
        static final RenderType LEAVES = ClientPlatform.INSTANCE.createRenderType(RustlingLeaves.MOD_ID + "_leaves", DefaultVertexFormat.BLOCK,
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
        static final RenderType SHADOWS = ClientPlatform.INSTANCE.createRenderType(RustlingLeaves.MOD_ID + "_leaf_shadows",
                DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1 << 16, false, false, RenderType.CompositeState.builder()
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                        .setTextureState(RenderStateShard.BLOCK_SHEET)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                        .createCompositeState(false));

        private LeafRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling,
                boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
        }
    }

    /** Quads of one pass written into a vanilla buffer builder in the block vertex format. */
    private static final class Sink implements QuadSink {
        private final ByteBufferBuilder bytes;
        private BufferBuilder builder;

        Sink(ByteBufferBuilder bytes) {
            this.bytes = bytes;
        }

        Sink begin() {
            // A mesh left open would end up in this one: its bytes stay in the shared byte buffer until built.
            discard();
            builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            return this;
        }

        MeshData build() {
            MeshData data = builder.build();
            builder = null;
            return data;
        }

        void discard() {
            if (builder != null) {
                MeshData unused = build();
                if (unused != null) {
                    unused.close();
                }
            }
        }

        void close() {
            discard();
            bytes.close();
        }

        @Override
        public void vertex(float x, float y, float z, int argb, float u, float v, int light, float nx, float ny, float nz) {
            builder.addVertex(x, y, z, argb, u, v, OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
        }

        @Override
        public void beginBlock(int id, int x, int y, int z) {
            IrisBlocks.begin(builder, id, x, y, z);
        }

        @Override
        public void endBlock() {
            IrisBlocks.end(builder);
        }
    }
}
