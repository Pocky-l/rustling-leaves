package com.pockyl.rustling_leaves.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafManager;
import com.pockyl.rustling_leaves.sim.LeafSimulation;
import com.pockyl.rustling_leaves.sim.Wind;

/**
 * Hands the leaves' wind to shader packs once per frame, before the world is drawn ({@code rustling_leaves:shader_data},
 * see {@link ShaderData}), so grass and crowns can sway with the very gust waves that blow the leaves off.
 * <pre>
 * 3  gust wave phase (radians, wraps at 20 pi), -, -, -
 * </pre>
 * The gust at a position is 0.5 + 0.32 sin(s) + 0.18 sin(2.7 s + 1.1) with s = phase - dot(xz, direction) * 0.05, as
 * in {@link Wind#gust}.
 */
@Mod.EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class PackWeather {
    /** sin(s) and sin(2.7 s) both repeat after 20 pi. */
    private static final double GUST_PERIOD = 20.0 * Math.PI;
    private static final ShaderData DATA = new ShaderData(RustlingLeaves.MOD_ID, 4);

    private PackWeather() {
    }

    @SubscribeEvent
    public static void onFrameStart(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !DATA.begin(ShaderPacks.inUse())) {
            return;
        }
        float partialTick = event.renderTickTime;
        LeafSimulation simulation = LeafManager.simulation();
        if (simulation == null) {
            DATA.wind(0.0F, 0.0F, partialTick);
        } else {
            Wind wind = simulation.wind();
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            float speed = wind.base() + 0.18F * wind.squall(camera.x, camera.z);
            DATA.wind(wind.dirX() * speed, wind.dirZ() * speed, partialTick);
            DATA.set(3, (float) (((wind.time() + partialTick) * 0.025) % GUST_PERIOD), 0.0F, 0.0F, 0.0F);
        }
        DATA.upload();
    }
}
