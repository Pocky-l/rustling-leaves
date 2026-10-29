package com.pockyl.rustling_leaves.client.render.backend;

import net.minecraft.resources.Identifier;

/**
 * The texture of {@link com.pockyl.rustling_leaves.client.compat.ShaderData}: one row of RGBA32F texels, registered in
 * the texture manager, where Iris looks it up on every bind. Render thread only. STUB of the 1.21.11 port: nothing is
 * created or uploaded yet.
 */
public final class DataTexture {
    private DataTexture() {
    }

    /** Creates a texture of {@code width} texels (all zero) and registers it as {@code id}. */
    public static DataTexture register(Identifier id, int width) {
        return new DataTexture();
    }

    /** Replaces the texels: {@code width * 4} floats, RGBA per texel. */
    public void upload(float[] values) {
    }
}
