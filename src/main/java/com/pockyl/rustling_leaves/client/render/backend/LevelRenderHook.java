package com.pockyl.rustling_leaves.client.render.backend;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Where the leaves are drawn into the level: right after the opaque and cutout terrain, so they are depth tested
 * against the blocks and drawn before water and other translucent blocks. Since 1.21.9 the level is first extracted
 * (camera, frustum, partial tick) and then drawn; the drawing stage only hands out the model-view matrix, so the rest
 * is kept from the extraction of the same frame.
 */
@EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class LevelRenderHook {
    private static Frustum frustum;
    private static Vec3 camera = Vec3.ZERO;
    private static final Vector3f LOOK = new Vector3f();
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
    public static void onRenderLevel(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        if (frustum == null) {
            return;
        }
        // The stub backend does not use the projection; the real one takes it from RenderSystem's projection buffer.
        LeafManager.render(camera, LOOK, frustum, event.getModelViewMatrix(), new Matrix4f(), partialTick);
    }
}
