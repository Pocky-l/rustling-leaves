package com.pockyl.rustling_leaves.client;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafListener;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafSimulation;

/**
 * Owns the client-side leaf world: creates the simulation for the current level, feeds it entities, explosions and
 * block changes, spawns leaves from trees and renders them.
 */
@EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class LeafManager {
    private static final int MAX_BURSTS_PER_TICK = 24;
    private static final int MAX_SOUNDS_PER_TICK = 3;
    private static final int SOUND_COOLDOWN = 4;
    private static final double TELEPORT_DISTANCE = 4.0;

    private static final LeafSettings SETTINGS = new LeafSettings();
    private static final LeafSpawner SPAWNER = new LeafSpawner();
    /** Vertical movement of each entity in the previous tick, to detect landings. */
    private static final Int2FloatOpenHashMap LAST_FALL = new Int2FloatOpenHashMap();
    private static final Int2IntOpenHashMap SOUND_READY = new Int2IntOpenHashMap();

    private static volatile boolean configChanged;
    private static LeafSimulation simulation;
    private static LeafRenderer renderer;
    private static ClientLevel level;
    private static int ticks;
    private static int burstsThisTick;
    private static int soundsThisTick;
    private static int disturbingEntity;

    private LeafManager() {
    }

    /** Called from the config event, possibly on another thread; applied on the next client tick. */
    static void onConfigChanged() {
        configChanged = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (configChanged) {
            configChanged = false;
            int capacity = SETTINGS.maxLeaves;
            Config.apply(SETTINGS);
            if (capacity != SETTINGS.maxLeaves) {
                reset();
            }
        }
        if (minecraft.level != level) {
            reset();
            level = minecraft.level;
        }
        if (level == null || minecraft.isPaused() || SETTINGS.maxLeaves <= 0) {
            return;
        }
        if (simulation == null) {
            simulation = new LeafSimulation(SETTINGS, SETTINGS.maxLeaves, new Listener());
            renderer = new LeafRenderer(simulation);
        }
        ticks++;
        burstsThisTick = 0;
        soundsThisTick = 0;
        Vec3 camera = cameraPosition(minecraft);
        simulation.beginTick(level);
        disturbByEntities(camera);
        simulation.finishTick(camera.x, camera.z);
        SPAWNER.tick(level, simulation, camera.x, camera.z);
        renderer.tick();
        if (ticks % 200 == 0) {
            LAST_FALL.clear();
            SOUND_READY.clear();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || renderer == null) {
            return;
        }
        renderer.render(event.getCamera(), event.getFrustum(), event.getModelViewMatrix(), event.getProjectionMatrix(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (simulation == null || !Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            return;
        }
        LeafPool pool = simulation.pool();
        int resting = 0;
        for (int i = 0, highWater = pool.highWater(); i < highWater; i++) {
            if (pool.state[i] == LeafPool.RESTING) {
                resting++;
            }
        }
        event.getRight().add("");
        event.getRight().add(String.format("Rustling Leaves: %d/%d (%d moving, %d meshes)", pool.count(), pool.capacity,
                pool.count() - resting, renderer.sectionCount()));
    }

    /** Hooked into block updates of the client level. */
    public static void onBlockChanged(ClientLevel changed, BlockPos pos, BlockState oldState, BlockState newState) {
        if (simulation == null || changed != level || oldState == newState) {
            return;
        }
        simulation.blockChanged(pos);
        renderer.onBlockChanged(pos);
        if (oldState.is(BlockTags.LEAVES) && !newState.is(BlockTags.LEAVES) && burstsThisTick < MAX_BURSTS_PER_TICK) {
            burstsThisTick++;
            SPAWNER.burst(level, simulation, pos.immutable(), oldState);
        }
    }

    /** Hooked into the explosion packet, which the server sends for TNT, creepers, wind charges and the like. */
    public static void onExplosion(ClientboundExplodePacket packet) {
        if (simulation == null) {
            return;
        }
        boolean windCharge = isGust(packet.getSmallExplosionParticles()) || isGust(packet.getLargeExplosionParticles());
        simulation.explode(packet.getX(), packet.getY(), packet.getZ(), packet.getPower(), windCharge);
    }

    private static boolean isGust(ParticleOptions particle) {
        return particle.getType() == ParticleTypes.GUST_EMITTER_SMALL || particle.getType() == ParticleTypes.GUST_EMITTER_LARGE
                || particle.getType() == ParticleTypes.GUST || particle.getType() == ParticleTypes.SMALL_GUST;
    }

    private static void disturbByEntities(Vec3 camera) {
        double range = SETTINGS.spawnRadius + 8.0;
        double rangeSq = range * range;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isSpectator() || entity.isPassenger() || entity.distanceToSqr(camera) > rangeSq) {
                continue;
            }
            double moveX = entity.getX() - entity.xo;
            double moveY = entity.getY() - entity.yo;
            double moveZ = entity.getZ() - entity.zo;
            if (Math.abs(moveX) > TELEPORT_DISTANCE || Math.abs(moveY) > TELEPORT_DISTANCE || Math.abs(moveZ) > TELEPORT_DISTANCE) {
                continue;
            }
            int id = entity.getId();
            float lastFall = LAST_FALL.get(id);
            LAST_FALL.put(id, (float) moveY);
            float landing = entity.onGround() && lastFall < 0.0F ? -lastFall : 0.0F;
            disturbingEntity = id;
            simulation.disturb(entity.getX(), entity.getY(), entity.getZ(), moveX, moveY, moveZ, entity.getBbWidth(),
                    entity.getBbHeight(), entity.isCrouching(), landing);
        }
    }

    private static Vec3 cameraPosition(Minecraft minecraft) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            return camera.getPosition();
        }
        return minecraft.player != null ? minecraft.player.getEyePosition() : Vec3.ZERO;
    }

    private static void reset() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        simulation = null;
        LAST_FALL.clear();
        SOUND_READY.clear();
        SPAWNER.clearCaches();
    }

    private static final class Listener implements LeafListener {
        @Override
        public void onRest(int leaf) {
            renderer.onRest(leaf);
        }

        @Override
        public void onUnrest(int leaf) {
            renderer.onUnrest(leaf);
        }

        @Override
        public void onRustle(double x, double y, double z, int count, boolean wet) {
            float volume = SETTINGS.rustleVolume;
            if (count < 2 || volume <= 0.0F || soundsThisTick >= MAX_SOUNDS_PER_TICK || SOUND_READY.get(disturbingEntity) > ticks) {
                return;
            }
            soundsThisTick++;
            SOUND_READY.put(disturbingEntity, ticks + SOUND_COOLDOWN);
            float loudness = Math.min(1.0F, 0.3F + count / 12.0F) * volume;
            float pitch = 1.0F + level.random.nextFloat() * 0.4F;
            level.playLocalSound(x, y, z, wet ? SoundEvents.WET_GRASS_STEP : SoundEvents.AZALEA_LEAVES_STEP, SoundSource.AMBIENT,
                    loudness, pitch, false);
        }

        @Override
        public void onBurn(double x, double y, double z) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0.0, 0.03, 0.0);
        }
    }
}
