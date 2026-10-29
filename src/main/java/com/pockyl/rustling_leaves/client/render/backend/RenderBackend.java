package com.pockyl.rustling_leaves.client.render.backend;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import org.joml.Matrix4f;

import com.pockyl.rustling_leaves.client.render.QuadSink;

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
 * STUB of the 1.21.11 port: the quads are thrown away and nothing is drawn, so the rest of the mod builds and runs
 * while the real backend (render pipelines and GPU buffers) is written.
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

    private static final QuadSink NOWHERE = new QuadSink() {
        @Override
        public void vertex(float x, float y, float z, int argb, float u, float v, int light, float nx, float ny, float nz) {
        }

        @Override
        public void beginBlock(int id, int x, int y, int z) {
        }

        @Override
        public void endBlock() {
        }
    };

    /** A mesh on the GPU, empty until it is first filled. */
    public static final class Mesh implements AutoCloseable {
        private Mesh() {
        }

        @Override
        public void close() {
        }
    }

    /** A mesh that is filled once in a while and drawn many times. */
    public static Mesh staticMesh() {
        return new Mesh();
    }

    /** A mesh that is filled anew every frame. */
    public static Mesh streamingMesh() {
        return new Mesh();
    }

    /** The atlas the leaves' sprites are stitched into, the one both passes draw with. */
    public TextureAtlas blockAtlas() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
    }

    /** Starts a new mesh of the pass and returns where to write its quads. */
    public QuadSink begin(Pass pass) {
        return NOWHERE;
    }

    /**
     * Ends the mesh of the pass and uploads it into {@code mesh}. Returns false when no quad was written: then
     * {@code mesh} keeps what it held.
     */
    public boolean finish(Pass pass, Mesh mesh) {
        return false;
    }

    /** Ends the mesh of the pass without using it. */
    public void discard(Pass pass) {
    }

    /**
     * Sets up a pass for this frame's level render: the projection, the camera's model-view matrix and the camera
     * position that mesh origins are relative to. Returns false when the pass cannot be drawn (then do not call
     * {@link #draw} or {@link #endPass}).
     */
    public boolean beginPass(Pass pass, Matrix4f modelView, Matrix4f projection, double cameraX, double cameraY, double cameraZ) {
        return false;
    }

    /** Draws a filled mesh whose coordinates are relative to the world position {@code origin}. */
    public void draw(Mesh mesh, double originX, double originY, double originZ) {
    }

    public void endPass() {
    }

    @Override
    public void close() {
    }
}
