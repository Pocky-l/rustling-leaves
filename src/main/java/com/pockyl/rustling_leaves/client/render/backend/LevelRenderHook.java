package com.pockyl.rustling_leaves.client.render.backend;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.PrepareRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Where the leaves are drawn into the level: right after the opaque and cutout terrain, so they are depth tested
 * against the blocks and drawn before water and other translucent blocks. The level is first extracted (camera,
 * frustum, partial tick), then drawn. On 26.3 the level's main render pass is already open when the leaves are drawn
 * and nothing may be uploaded while it is: the leaves are built, uploaded and their transforms written when the level
 * prepares its buffers, just before that pass opens, and only drawn into it after the opaque terrain.
 */
@EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class LevelRenderHook {
    private static Frustum frustum;
    private static Vec3 camera = Vec3.ZERO;
    private static final Vector3f LOOK = new Vector3f();
    /** The backend binds the level's projection buffer itself; the matrix is not needed on this version. */
    private static final Matrix4f UNUSED_PROJECTION = new Matrix4f();
    private static float partialTick;

    private LevelRenderHook() {
    }

    @SubscribeEvent
    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Camera view = event.getCamera();
        frustum = event.getFrustum();
        camera = view.position();
        LOOK.set(view.forwardVector());
        partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    @SubscribeEvent
    public static void onPrepareBuffers(PrepareRenderBuffersEvent event) {
        RenderBackend.beginFrame();
        if (frustum == null) {
            return;
        }
        // The same model-view matrix the stage events of this frame hand out.
        LeafManager.render(camera, LOOK, frustum, event.getLevelRenderState().cameraRenderState.viewRotationMatrix,
                UNUSED_PROJECTION, partialTick);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        RenderBackend.drawPrepared(event.getRenderPass());
    }
}
