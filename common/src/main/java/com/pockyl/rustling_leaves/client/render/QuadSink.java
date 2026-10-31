package com.pockyl.rustling_leaves.client.render;

/**
 * Where the leaf renderer writes its quads: four vertices per quad, in the mesh's own coordinates. The render backend
 * turns them into whatever vertex layout the GPU side of the Minecraft version wants, so the code that builds the
 * leaves, piles and shadows is the same on every version.
 */
public interface QuadSink {
    /**
     * One corner of a quad. A quad's corners go counter-clockwise seen from its front: shader packs recompute the
     * normal from the winding. {@code light} is packed block/sky light as in vanilla.
     */
    void vertex(float x, float y, float z, int argb, float u, float v, int light, float nx, float ny, float nz);

    /**
     * The quads written next belong to a block with the shader pack's id {@code id} at block position (x, y, z) in the
     * mesh's coordinates (see {@link com.pockyl.rustling_leaves.client.compat.IrisBlocks}); ignored where that is not
     * possible.
     */
    void beginBlock(int id, int x, int y, int z);

    /** Back to "no block" for whatever is written next. */
    void endBlock();
}
