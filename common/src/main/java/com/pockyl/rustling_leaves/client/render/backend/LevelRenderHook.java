package com.pockyl.rustling_leaves.client.render.backend;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Where the leaves are drawn into the level: right after the cutout terrain, so they are depth tested against the
 * blocks and drawn before water and other translucent blocks. The loaders call {@link #render} at that moment.
 */
public final class LevelRenderHook {
    private LevelRenderHook() {
    }

    /**
     * Called by the loaders right after the solid and cutout terrain is drawn. {@code modelView} is the model-view
     * matrix the terrain is drawn with (it holds the camera rotation).
     */
    public static void render(Camera camera, Frustum frustum, Matrix4f modelView, Matrix4f projection, float partialTick) {
        LeafManager.render(camera.getPosition(), camera.getLookVector(), frustum, modelView, projection, partialTick);
    }
}
