package com.pockyl.rustling_leaves.client.render.backend;

import net.minecraft.client.Camera;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Where the leaves are drawn into the level: right after the cutout terrain, so they are depth tested against the
 * blocks and drawn before water and other translucent blocks.
 */
@EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class LevelRenderHook {
    private LevelRenderHook() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }
        Camera camera = event.getCamera();
        LeafManager.render(camera.getPosition(), camera.getLookVector(), event.getFrustum(), event.getModelViewMatrix(),
                event.getProjectionMatrix(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
}
