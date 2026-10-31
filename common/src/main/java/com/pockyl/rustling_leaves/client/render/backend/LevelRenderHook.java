package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Where the leaves are drawn into the level: right after the opaque and cutout terrain, so they are depth tested
 * against the blocks and drawn before water and other translucent blocks. The level is first extracted (camera,
 * frustum, partial tick), then drawn. On 26.3 the level's main render pass is already open when the leaves are drawn
 * and nothing may be uploaded while it is: the leaves are built, uploaded and their transforms written when the level
 * prepares its buffers, just before that pass opens, and only drawn into it after the opaque terrain. The loaders call
 * {@link #extract}, {@link #prepare} and {@link #draw} at those moments.
 */
public final class LevelRenderHook {
    private static Frustum frustum;
    private static Vec3 camera = Vec3.ZERO;
    private static final Vector3f LOOK = new Vector3f();
    /** The level hands out a read-only matrix; copied here instead of a new matrix every frame. */
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
     * Called by the loaders when the level prepares its buffers, before its main render pass opens. {@code viewRotation}
     * is the model-view matrix the terrain of this frame is drawn with (it holds the camera rotation).
     */
    public static void prepare(Matrix4fc viewRotation) {
        RenderBackend.beginFrame();
        if (frustum == null) {
            return;
        }
        LeafManager.render(camera, LOOK, frustum, MODEL_VIEW.set(viewRotation), UNUSED_PROJECTION, partialTick);
    }

    /** Called by the loaders right after the opaque and cutout terrain is drawn into the level's open render pass. */
    public static void draw(RenderPass pass) {
        RenderBackend.drawPrepared(pass);
    }
}
