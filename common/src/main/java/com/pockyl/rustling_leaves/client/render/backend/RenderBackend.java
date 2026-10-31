package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.pockyl.rustling_leaves.client.compat.IrisBlocks;
import com.pockyl.rustling_leaves.client.render.QuadSink;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
 * This is the 26.3 backend: a GPU buffer per mesh with the shared quad index buffer and the {@link LeafPipelines}. The
 * level draws its opaque terrain, entities and the leaves in one render pass, and nothing may be uploaded while a pass
 * is open; so {@link LevelRenderHook} runs the renderer before that pass opens, while the level prepares its buffers:
 * the meshes are filled, the draws of each pass noted and in {@link #endPass} their transforms (model-view and the
 * mesh's offset to the camera) written and the quad indices asked for. {@link #drawPrepared} then draws the noted
 * passes into the level's pass, right after the opaque terrain.
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

    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final GpuBufferSlice[] NO_TRANSFORMS = new GpuBufferSlice[0];
    /** The passes prepared for this frame, in drawing order, waiting for the level's render pass. */
    private static final List<Prepared> READY = new ArrayList<>(2);

    private final Sink leaves = new Sink(new ByteBufferBuilder(1 << 18));
    private final Sink shadows = new Sink(new ByteBufferBuilder(1 << 16));
    private final Prepared[] passes = {new Prepared(Pass.LEAVES), new Prepared(Pass.SHADOWS)};
    /** The pass whose draws are being noted, the camera and its model-view matrix. */
    private Prepared noting;
    private final Matrix4f modelView = new Matrix4f();
    private double camX;
    private double camY;
    private double camZ;

    /** A mesh on the GPU, empty until it is first filled. */
    public static final class Mesh implements AutoCloseable {
        private final boolean streaming;
        private GpuBuffer buffer;
        private GpuBufferSlice vertices;
        private int indexCount;

        private Mesh(boolean streaming) {
            this.streaming = streaming;
        }

        /**
         * A static mesh gets a buffer of its own size every time (like the old buffer orphaning); a streaming mesh
         * keeps its buffer and writes into it while the data fits, like vanilla's immediate draws, unless the device
         * writes into buffers slowly.
         */
        private void upload(ByteBuffer data, int indices) {
            GpuDevice device = RenderSystem.getDevice();
            int size = data.remaining();
            if (streaming && buffer != null && buffer.size() >= size
                    && !device.getDeviceInfo().hintsAndWorkarounds().writeToBufferIsSlow()) {
                device.createCommandEncoder().writeToBuffer(buffer.slice(0, size), data);
            } else {
                close();
                if (streaming) {
                    // Some room to grow, so a frame with a few more leaves does not allocate again.
                    buffer = device.createBuffer(() -> "Rustling Leaves moving leaves", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                            Mth.smallestEncompassingPowerOfTwo(size + size / 2));
                    device.createCommandEncoder().writeToBuffer(buffer.slice(0, size), data);
                } else {
                    buffer = device.createBuffer(() -> "Rustling Leaves litter", GpuBuffer.USAGE_VERTEX, data);
                }
                vertices = buffer.slice();
            }
            indexCount = indices;
        }

        @Override
        public void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
                vertices = null;
            }
            indexCount = 0;
        }
    }

    /** A mesh that is filled once in a while and drawn many times. */
    public static Mesh staticMesh() {
        return new Mesh(false);
    }

    /** A mesh that is filled anew every frame. */
    public static Mesh streamingMesh() {
        return new Mesh(true);
    }

    /** The atlas the leaves' sprites are stitched into, the one both passes draw with. */
    public TextureAtlas blockAtlas() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
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
        try (data) {
            mesh.upload(data.vertexBuffer(), data.drawState().indexCount());
        }
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
     * {@link #draw} or {@link #endPass}). The projection is not used here: the level's projection buffer is bound
     * with the other default uniforms.
     */
    public boolean beginPass(Pass pass, Matrix4f modelView, Matrix4f projection, double cameraX, double cameraY, double cameraZ) {
        noting = passes[pass.ordinal()];
        noting.clear();
        this.modelView.set(modelView);
        camX = cameraX;
        camY = cameraY;
        camZ = cameraZ;
        return true;
    }

    /** Draws a filled mesh whose coordinates are relative to the world position {@code origin}. */
    public void draw(Mesh mesh, double originX, double originY, double originZ) {
        if (mesh.buffer == null || mesh.indexCount == 0) {
            return;
        }
        noting.note(mesh.vertices, mesh.indexCount, (float) (originX - camX), (float) (originY - camY), (float) (originZ - camZ));
    }

    /** Writes the transforms of the noted draws and hands the pass over to {@link #drawPrepared}. */
    public void endPass() {
        if (noting.draws > 0) {
            noting.prepare(modelView);
            READY.add(noting);
        }
        noting = null;
    }

    /** Forgets the passes of the last frame (they were drawn or the frame was not drawn): a new frame is prepared. */
    static void beginFrame() {
        for (Prepared pass : READY) {
            pass.clear();
        }
        READY.clear();
    }

    /** Draws the passes prepared for this frame into the level's open render pass, which stays open. */
    static void drawPrepared(RenderPass renderPass) {
        for (Prepared pass : READY) {
            pass.drawInto(renderPass);
            pass.clear();
        }
        READY.clear();
    }

    @Override
    public void close() {
        // The noted draws hold this renderer's buffers, which are closed with it.
        for (Prepared pass : passes) {
            READY.remove(pass);
            pass.clear();
        }
        leaves.close();
        shadows.close();
    }

    /** The draws of one pass in one frame: vertex buffers, index counts, offsets and, once prepared, transforms. */
    private static final class Prepared {
        private final Pass pass;
        private int draws;
        private int maxIndices;
        private GpuBufferSlice[] buffers = new GpuBufferSlice[64];
        private int[] indices = new int[64];
        private float[] offsets = new float[64 * 3];
        private GpuBufferSlice[] transforms = NO_TRANSFORMS;

        Prepared(Pass pass) {
            this.pass = pass;
        }

        void note(GpuBufferSlice vertices, int indexCount, float offsetX, float offsetY, float offsetZ) {
            if (draws == buffers.length) {
                buffers = Arrays.copyOf(buffers, draws * 2);
                indices = Arrays.copyOf(indices, draws * 2);
                offsets = Arrays.copyOf(offsets, draws * 6);
            }
            buffers[draws] = vertices;
            indices[draws] = indexCount;
            offsets[draws * 3] = offsetX;
            offsets[draws * 3 + 1] = offsetY;
            offsets[draws * 3 + 2] = offsetZ;
            maxIndices = Math.max(maxIndices, indexCount);
            draws++;
        }

        /** Writes the transforms and asks for enough quad indices: neither can be done inside the level's pass. */
        void prepare(Matrix4f modelView) {
            DynamicGpuData.Transform[] written = new DynamicGpuData.Transform[draws];
            // A copy of its own: the uniform storage keeps the last transform to compare the next one with.
            Matrix4f view = new Matrix4f(modelView);
            for (int i = 0; i < draws; i++) {
                Vector3f offset = new Vector3f(offsets[i * 3], offsets[i * 3 + 1], offsets[i * 3 + 2]);
                written[i] = new DynamicGpuData.Transform(view, WHITE, offset, IDENTITY);
            }
            transforms = RenderSystem.getDynamicUniforms().writeTransforms(written);
            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(maxIndices);
        }

        /** Draws into the level's pass with the textures vanilla's terrain uses. */
        void drawInto(RenderPass renderPass) {
            RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            if (!quads.hasStorage(maxIndices)) {
                // Not grown as asked (it cannot grow inside the pass): skip this frame rather than read past it.
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            boolean leavesPass = pass == Pass.LEAVES;
            // The mipped atlas the way vanilla samples it for moving blocks (falling blocks, pistons); the shadows
            // take only the full-size sprites, so their pixel edges stay sharp in the distance.
            GpuSampler atlasSampler = leavesPass
                    ? RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                            FilterMode.LINEAR, FilterMode.NEAREST, true)
                    : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            GpuTextureView atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTextureView();
            renderPass.pushDebugGroup(() -> leavesPass ? "Rustling Leaves leaves" : "Rustling Leaves shadows");
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(leavesPass ? LeafPipelines.LEAVES : LeafPipelines.SHADOWS));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("Sampler0", atlas, atlasSampler);
            renderPass.setUniform("Sampler2", minecraft.gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.setIndexBuffer(quads.getBuffer(), quads.type());
            for (int i = 0; i < draws; i++) {
                renderPass.setUniform("DynamicTransforms", transforms[i]);
                renderPass.setVertexBuffer(0, buffers[i]);
                renderPass.drawIndexed(indices[i], 1, 0, 0, 0);
            }
            renderPass.popDebugGroup();
        }

        void clear() {
            Arrays.fill(buffers, 0, draws, null);
            draws = 0;
            maxIndices = 0;
            transforms = NO_TRANSFORMS;
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
            // While a shader pack is on, Iris turns this into its larger terrain layout (meshes are built during the
            // level render), and the pipelines read that layout too. The block format has no normal since 26.1:
            // Iris works it out from the winding (quads counter-clockwise seen from the front).
            builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);
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
