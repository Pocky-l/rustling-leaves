package com.pockyl.rustling_leaves.client.render.backend;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Where the leaves are drawn into the level: right after the opaque and cutout terrain, so they are depth tested
 * against the blocks and drawn before water and other translucent blocks. Since 1.21.9 the level is first extracted
 * (camera, frustum, partial tick) and then drawn; the drawing stage only hands out the model-view matrix, so the rest
 * is kept from the extraction of the same frame. The loaders call {@link #extract} and {@link #render} at those
 * moments.
 */
public final class LevelRenderHook {
    private static Frustum frustum;
    private static Vec3 camera = Vec3.ZERO;
    private static final Vector3f LOOK = new Vector3f();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    /** The backend binds the level's projection buffer itself; the matrix is not needed on this version. */
    private static final Matrix4f UNUSED_PROJECTION = new Matrix4f();
    private static float partialTick;

    private LevelRenderHook() {
    }

    /** Called by the loaders when the level of a frame has been extracted, before it is drawn. */
    public static void extract(Camera view, Frustum viewFrustum, float framePartialTick) {
        frustum = viewFrustum;
        camera = view.position();
        LOOK.set(view.forwardVector());
        partialTick = framePartialTick;
    }

    /**
     * Called by the loaders right after the opaque and cutout terrain is drawn. {@code modelView} is the model-view
     * matrix the terrain is drawn with (it holds the camera rotation).
     */
    public static void render(Matrix4fc modelView) {
        if (frustum == null) {
            return;
        }
        LeafManager.render(camera, LOOK, frustum, MODEL_VIEW.set(modelView), UNUSED_PROJECTION, partialTick);
    }
}
