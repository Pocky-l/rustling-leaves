package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
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
import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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
 * This is the 1.21.11 backend: a GPU buffer per mesh with the shared quad index buffer, the {@link LeafPipelines} and
 * the draws of a pass noted and issued in {@link #endPass}: every draw's transform (model-view and the mesh's offset to
 * the camera) is written first, then one render pass draws them all - a transform cannot be written while a render
 * pass is open.
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

    private final Sink leaves = new Sink(new ByteBufferBuilder(1 << 18));
    private final Sink shadows = new Sink(new ByteBufferBuilder(1 << 16));
    /** The pass being drawn, the camera and the draws noted for it. */
    private Pass pass;
    private final Matrix4f modelView = new Matrix4f();
    private double camX;
    private double camY;
    private double camZ;
    private int draws;
    private GpuBuffer[] drawBuffers = new GpuBuffer[64];
    private int[] drawIndices = new int[64];
    private float[] drawOffsets = new float[64 * 3];

    /** A mesh on the GPU, empty until it is first filled. */
    public static final class Mesh implements AutoCloseable {
        private final boolean streaming;
        private GpuBuffer buffer;
        private int indexCount;

        private Mesh(boolean streaming) {
            this.streaming = streaming;
        }

        /**
         * A static mesh gets a buffer of its own size every time (like the old buffer orphaning); a streaming mesh
         * keeps its buffer and writes into it while the data fits, like vanilla's immediate draws.
         */
        private void upload(ByteBuffer vertices, int indices) {
            GpuDevice device = RenderSystem.getDevice();
            int size = vertices.remaining();
            if (streaming && buffer != null && buffer.size() >= size
                    && !GraphicsWorkarounds.get(device).alwaysCreateFreshImmediateBuffer()) {
                device.createCommandEncoder().writeToBuffer(buffer.slice(0, size), vertices);
            } else {
                close();
                if (streaming) {
                    // Some room to grow, so a frame with a few more leaves does not allocate again.
                    buffer = device.createBuffer(() -> "Rustling Leaves moving leaves", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                            Mth.smallestEncompassingPowerOfTwo(size + size / 2));
                    device.createCommandEncoder().writeToBuffer(buffer.slice(0, size), vertices);
                } else {
                    buffer = device.createBuffer(() -> "Rustling Leaves litter", GpuBuffer.USAGE_VERTEX, vertices);
                }
            }
            indexCount = indices;
        }

        @Override
        public void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
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
        this.pass = pass;
        this.modelView.set(modelView);
        camX = cameraX;
        camY = cameraY;
        camZ = cameraZ;
        draws = 0;
        return true;
    }

    /** Draws a filled mesh whose coordinates are relative to the world position {@code origin}. */
    public void draw(Mesh mesh, double originX, double originY, double originZ) {
        if (mesh.buffer == null || mesh.indexCount == 0) {
            return;
        }
        if (draws == drawBuffers.length) {
            drawBuffers = Arrays.copyOf(drawBuffers, draws * 2);
            drawIndices = Arrays.copyOf(drawIndices, draws * 2);
            drawOffsets = Arrays.copyOf(drawOffsets, draws * 6);
        }
        drawBuffers[draws] = mesh.buffer;
        drawIndices[draws] = mesh.indexCount;
        drawOffsets[draws * 3] = (float) (originX - camX);
        drawOffsets[draws * 3 + 1] = (float) (originY - camY);
        drawOffsets[draws * 3 + 2] = (float) (originZ - camZ);
        draws++;
    }

    public void endPass() {
        if (draws > 0) {
            drawNoted();
        }
        Arrays.fill(drawBuffers, 0, draws, null);
        draws = 0;
        pass = null;
    }

    /**
     * Draws everything noted in the pass: the transforms (model-view and the mesh's offset to the camera) are written
     * first, then the meshes are drawn in one render pass on the main target, with the textures vanilla's terrain uses.
     */
    private void drawNoted() {
        DynamicUniforms.Transform[] transforms = new DynamicUniforms.Transform[draws];
        // A copy of its own: the uniform storage keeps the last transform to compare the next one with.
        Matrix4f view = new Matrix4f(modelView);
        int maxIndices = 0;
        for (int i = 0; i < draws; i++) {
            Vector3f offset = new Vector3f(drawOffsets[i * 3], drawOffsets[i * 3 + 1], drawOffsets[i * 3 + 2]);
            transforms[i] = new DynamicUniforms.Transform(view, WHITE, offset, IDENTITY);
            maxIndices = Math.max(maxIndices, drawIndices[i]);
        }
        GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeTransforms(transforms);
        RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = quads.getBuffer(maxIndices);

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
        boolean leavesPass = pass == Pass.LEAVES;
        // The mipped atlas like vanilla's cutout_mipped leaves; the shadows take only the full-size sprites, so their
        // pixel edges stay sharp in the distance.
        GpuSampler atlasSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, leavesPass);
        GpuTextureView atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> leavesPass ? "Rustling Leaves leaves" : "Rustling Leaves shadows",
                color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(leavesPass ? LeafPipelines.LEAVES : LeafPipelines.SHADOWS);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("Sampler0", atlas, atlasSampler);
            renderPass.bindTexture("Sampler2", minecraft.gameRenderer.lightTexture().getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.setIndexBuffer(indices, quads.type());
            for (int i = 0; i < draws; i++) {
                renderPass.setUniform("DynamicTransforms", slices[i]);
                renderPass.setVertexBuffer(0, drawBuffers[i]);
                renderPass.drawIndexed(0, 0, drawIndices[i], 1);
            }
        }
    }

    @Override
    public void close() {
        leaves.close();
        shadows.close();
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
            // level render), and the pipelines read that layout too.
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
