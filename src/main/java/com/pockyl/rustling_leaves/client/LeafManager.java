package com.pockyl.rustling_leaves.client;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.compat.SereneSeasons;
import com.pockyl.rustling_leaves.item.BagContents;
import com.pockyl.rustling_leaves.item.LeafBagItem;
import com.pockyl.rustling_leaves.item.LeafBlowerItem;
import com.pockyl.rustling_leaves.network.BagCollectPayload;
import com.pockyl.rustling_leaves.network.WindSpellPayload;
import com.pockyl.rustling_leaves.sim.Armful;
import com.pockyl.rustling_leaves.sim.LeafListener;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.LeafSimulation;
import com.pockyl.rustling_leaves.sim.LeafSpawner;
import com.pockyl.rustling_leaves.sim.LitterChunk;
import com.pockyl.rustling_leaves.sim.LitterField;
import com.pockyl.rustling_leaves.sim.SeasonCurve;
import com.pockyl.rustling_leaves.sim.TreeLeaves;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the client-side leaf world: creates the simulation for the current level, keeps the litter around the camera
 * loaded, saved and seeded, feeds the simulation entities, explosions, block changes and rake strokes, spawns leaves
 * from trees and renders everything.
 */
@EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class LeafManager {
    private static final int MAX_BURSTS_PER_TICK = 24;
    private static final int MAX_SOUNDS_PER_TICK = 3;
    private static final int SOUND_COOLDOWN = 4;
    private static final double TELEPORT_DISTANCE = 4.0;
    private static final int SAVE_INTERVAL = 20 * 30;
    private static final float RAKE_RADIUS = 2.0F;
    private static final long SEED_BUDGET_NANOS = 4_000_000L;
    /** Seasons change slowly; once a second is plenty (also to follow the biome under the camera). */
    private static final int SEASON_INTERVAL = 20;
    private static final boolean SERENE_SEASONS = ModList.get().isLoaded(SereneSeasons.MOD_ID);

    private static final LeafSettings SETTINGS = new LeafSettings();
    private static final LeafColors COLORS = new LeafColors();
    private static final LeafSpawner SPAWNER = new LeafSpawner(new Trees());
    private static final LitterSeeder SEEDER = new LitterSeeder(COLORS);
    /** Vertical movement of each entity in the previous tick, to detect landings. */
    private static final Int2FloatOpenHashMap LAST_FALL = new Int2FloatOpenHashMap();
    private static final Int2IntOpenHashMap SOUND_READY = new Int2IntOpenHashMap();

    private static volatile boolean configChanged;
    private static LeafSimulation simulation;
    private static LeafRenderer renderer;
    private static LitterStorage storage;
    private static ClientLevel level;
    private static int ticks;
    private static int nextSeasonCheck;
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
            nextSeasonCheck = ticks;
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
            simulation.setLevel(level);
            renderer = new LeafRenderer(simulation);
            storage = new LitterStorage(storageDirectory(minecraft, level));
        }
        ticks++;
        burstsThisTick = 0;
        soundsThisTick = 0;
        Vec3 camera = cameraPosition(minecraft);
        updateLitterChunks(camera);
        simulation.beginTick(level, camera.x, camera.y, camera.z);
        disturbByEntities(camera);
        simulation.finishTick();
        if (ticks >= nextSeasonCheck) {
            nextSeasonCheck = ticks + SEASON_INTERVAL;
            updateSeason(camera);
        }
        SPAWNER.tick(level, simulation, camera.x, camera.z);
        if (ticks % SAVE_INTERVAL == 0) {
            save(camera);
        }
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
        event.getRight().add("");
        event.getRight().add(String.format("Rustling Leaves: %d/%d moving, %d lying", pool.count(), pool.capacity, simulation.field().total()));
        event.getRight().add(String.format("Litter: %d chunks, %d quads drawn, %d whirlwinds", simulation.field().chunks().size(),
                renderer.tileQuads(), simulation.wind().whirlwinds().size()));
    }

    /** Right click with a hoe or shovel on leaf litter rakes it together instead of tilling or making a path. */
    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || simulation == null || minecraft.player == null) {
            return;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!SETTINGS.raking || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (!stack.is(ItemTags.HOES) && !stack.is(ItemTags.SHOVELS)) {
            return;
        }
        Vec3 at = hit.getLocation();
        if (simulation.litterAround(at.x, at.y, at.z, RAKE_RADIUS) < 3) {
            return;
        }
        int moved = simulation.rake(at.x, at.y, at.z, RAKE_RADIUS);
        if (moved > 0) {
            event.setCanceled(true);
            event.setSwingHand(true);
            float volume = Math.min(1.0F, 0.4F + moved / 60.0F) * Math.max(0.3F, SETTINGS.rustleVolume);
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.AZALEA_LEAVES_STEP, SoundSource.PLAYERS, volume,
                    0.8F + level.random.nextFloat() * 0.2F, false);
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, volume * 0.5F,
                    0.7F + level.random.nextFloat() * 0.2F, false);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Leaf tools (their items call in through ClientLeafEffects on every client, for every player using them)
    // ------------------------------------------------------------------------------------------------------------

    static void blow(LivingEntity user, Vec3 nozzle, Vec3 direction) {
        if (simulation != null && user.level() == level) {
            simulation.blow(nozzle.x, nozzle.y, nozzle.z, (float) direction.x, (float) direction.y, (float) direction.z,
                    (float) LeafBlowerItem.RANGE, (float) LeafBlowerItem.SPREAD, 0.55F);
        }
    }

    /** The bag of the local player reports what it collected to the server; other players' bags are only drawn. */
    static void vacuum(LivingEntity user, Vec3 mouth, Vec3 direction) {
        if (simulation == null || user.level() != level) {
            return;
        }
        Armful collected = new Armful();
        boolean mine = user == Minecraft.getInstance().player;
        int taken = simulation.vacuum(mouth.x, mouth.y, mouth.z, (float) direction.x, (float) direction.y, (float) direction.z,
                BagCollectPayload.MAX_PER_TICK, collected);
        if (mine && taken > 0) {
            PacketDistributor.sendToServer(new BagCollectPayload(taken, collected.averageColor(), collected.mainShape()));
        }
    }

    static void pour(LivingEntity user, Vec3 mouth, Vec3 direction, int count, BagContents contents) {
        if (simulation == null || user.level() != level) {
            return;
        }
        Armful leaves = new Armful();
        leaves.fill(count, contents.color(), contents.shape());
        simulation.pour(mouth.x, mouth.y, mouth.z, (float) direction.x, (float) direction.y, (float) direction.z, count, leaves);
    }

    static void burst(double x, double y, double z) {
        if (simulation == null) {
            return;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        int base = COLORS.base(Blocks.OAK_LEAVES.defaultBlockState(), level, pos);
        simulation.burst(x, y, z, 220, base, Math.max(0.75F, SETTINGS.autumnColors));
    }

    public static void onWindSpell(WindSpellPayload payload) {
        if (simulation == null) {
            return;
        }
        if (payload.kind() == WindSpellPayload.SQUALL) {
            simulation.wind().sendSquall(payload.x(), payload.z(), payload.dirX(), payload.dirZ());
        } else {
            double ground = simulation.groundBelow(payload.x(), payload.y() + 1.0, payload.z(), 16);
            simulation.wind().summonWhirlwind(payload.x(), Double.isNaN(ground) ? payload.y() : ground, payload.z());
        }
    }

    /**
     * Using an item slows the player down to a fifth. Blowing leaves or bagging them while walking slowly would be
     * tedious, so the leaf tools cancel that out (this runs just before vanilla applies the slowdown).
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player.isUsingItem() && !player.isPassenger()
                && (player.getUseItem().getItem() instanceof LeafBlowerItem || player.getUseItem().getItem() instanceof LeafBagItem)) {
            event.getInput().leftImpulse *= 5.0F;
            event.getInput().forwardImpulse *= 5.0F;
        }
    }

    /** Hooked into block updates of the client level. */
    /**
     * Called instead of a vanilla cherry petal particle under the cherry leaves at {@code pos}: drops a simulated petal
     * there (scaled by the fall rate and the season). Returns false when the simulation is off, so the vanilla particle shows.
     */
    public static boolean replaceCherryPetal(Level particleLevel, BlockPos pos, BlockState state) {
        if (simulation == null || particleLevel != level) {
            return false;
        }
        SPAWNER.dropBelow(level, simulation, state, pos);
        return true;
    }

    public static void onBlockChanged(ClientLevel changed, BlockPos pos, BlockState oldState, BlockState newState) {
        if (simulation == null || changed != level || oldState == newState) {
            return;
        }
        simulation.blockChanged(pos);
        if (oldState.is(BlockTags.LEAVES) && !newState.is(BlockTags.LEAVES) && burstsThisTick < MAX_BURSTS_PER_TICK) {
            burstsThisTick++;
            SPAWNER.burst(level, simulation, pos.immutable(), oldState);
        }
        // Light changes next to the block: let the litter tiles around it pick up the new light.
        LitterField field = simulation.field();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LitterChunk chunk = field.chunkAtCell((pos.getX() + dx * 4) * 4, (pos.getZ() + dz * 4) * 4);
                if (chunk != null) {
                    chunk.tileRevision[LitterChunk.tile(LitterField.index((pos.getX() + dx * 4) * 4, (pos.getZ() + dz * 4) * 4))]++;
                }
            }
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

    // ------------------------------------------------------------------------------------------------------------
    // Litter chunks: load around the camera, seed new ground, save and unload far away
    // ------------------------------------------------------------------------------------------------------------

    private static void updateLitterChunks(Vec3 camera) {
        LitterField field = simulation.field();
        double renderDistance = Math.max(48.0, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0);
        double radius = Math.min(SETTINGS.litterRadius(), renderDistance);
        int camChunkX = Mth.floor(camera.x) >> 4;
        int camChunkZ = Mth.floor(camera.z) >> 4;
        int reach = Mth.ceil(radius / 16.0) + 1;
        double keepSq = (radius + 24.0) * (radius + 24.0);
        double loadSq = (radius + 8.0) * (radius + 8.0);
        if (ticks % 10 == 0) {
            List<LitterChunk> far = new ArrayList<>();
            for (LitterChunk chunk : field.chunks()) {
                if (chunkDistanceSq(chunk.x, chunk.z, camera) > keepSq) {
                    far.add(chunk);
                }
            }
            for (LitterChunk chunk : far) {
                if (chunk.takeDirty()) {
                    storage.store(chunk);
                }
                field.remove(chunk.x, chunk.z);
            }
            for (int cx = camChunkX - reach; cx <= camChunkX + reach; cx++) {
                for (int cz = camChunkZ - reach; cz <= camChunkZ + reach; cz++) {
                    if (field.chunk(cx, cz) != null || chunkDistanceSq(cx, cz, camera) > loadSq
                            || !level.getChunkSource().hasChunk(cx, cz)) {
                        continue;
                    }
                    LitterChunk chunk = storage.load(cx, cz);
                    field.put(chunk != null ? chunk : new LitterChunk(cx, cz));
                }
            }
        }
        // Seed new ground nearest first, within a time budget per tick, once a chunk's neighbors are there for the
        // canopy around it: fast enough to keep ahead of a player flying over a forest.
        List<LitterChunk> unseeded = new ArrayList<>();
        for (LitterChunk chunk : field.chunks()) {
            if (!chunk.seeded() && neighborsLoaded(chunk.x, chunk.z)) {
                unseeded.add(chunk);
            }
        }
        if (unseeded.isEmpty()) {
            return;
        }
        unseeded.sort((a, b) -> Double.compare(chunkDistanceSq(a.x, a.z, camera), chunkDistanceSq(b.x, b.z, camera)));
        long deadline = System.nanoTime() + SEED_BUDGET_NANOS;
        for (LitterChunk chunk : unseeded) {
            SEEDER.seed(level, simulation, chunk);
            chunk.markSeeded();
            if (System.nanoTime() > deadline) {
                break;
            }
        }
    }

    private static boolean neighborsLoaded(int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getChunkSource().hasChunk(chunkX + dx, chunkZ + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double chunkDistanceSq(int chunkX, int chunkZ, Vec3 camera) {
        double dx = chunkX * 16 + 8 - camera.x;
        double dz = chunkZ * 16 + 8 - camera.z;
        return dx * dx + dz * dz;
    }

    private static void save(Vec3 camera) {
        for (LitterChunk chunk : simulation.field().chunks()) {
            if (chunk.takeDirty()) {
                storage.store(chunk);
            }
        }
        storage.flush(Mth.floor(camera.x) >> 4, Mth.floor(camera.z) >> 4);
    }

    /** Per world (singleplayer: inside the save) or per server address, then per dimension. */
    private static Path storageDirectory(Minecraft minecraft, ClientLevel level) {
        Path root;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            root = server.getWorldPath(LevelResource.ROOT).resolve(RustlingLeaves.MOD_ID);
        } else {
            ServerData data = minecraft.getCurrentServer();
            String name = data != null ? data.ip : "unknown";
            root = minecraft.gameDirectory.toPath().resolve(RustlingLeaves.MOD_ID).resolve("servers")
                    .resolve(name.replaceAll("[^A-Za-z0-9._-]", "_"));
        }
        ResourceLocation dimension = level.dimension().location();
        return root.resolve(dimension.getNamespace()).resolve(dimension.getPath());
    }

    // ------------------------------------------------------------------------------------------------------------

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

    private static void updateSeason(Vec3 camera) {
        if (SERENE_SEASONS && SETTINGS.seasons) {
            SereneSeasons.update(level, BlockPos.containing(camera), SETTINGS);
        } else {
            SeasonCurve.clear(SETTINGS);
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
        if (simulation != null && storage != null) {
            for (LitterChunk chunk : simulation.field().chunks()) {
                if (chunk.takeDirty()) {
                    storage.store(chunk);
                }
            }
            storage.close();
        }
        storage = null;
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        simulation = null;
        nextSeasonCheck = 0;
        LAST_FALL.clear();
        SOUND_READY.clear();
        COLORS.clear();
    }

    /** Trees look like their blocks: colors from the block tint and texture, shapes from the block id. */
    private static final class Trees implements TreeLeaves {
        @Override
        public int color(BlockState state, Level level, BlockPos pos) {
            return COLORS.base(state, level, pos);
        }

        @Override
        public LeafShape shape(BlockState state) {
            return LeafShapes.of(state.getBlock());
        }
    }

    private static final class Listener implements LeafListener {
        @Override
        public void onRustle(double x, double y, double z, int count, boolean wet) {
            float volume = SETTINGS.rustleVolume;
            if (count < 2 || volume <= 0.0F || soundsThisTick >= MAX_SOUNDS_PER_TICK || SOUND_READY.get(disturbingEntity) > ticks) {
                return;
            }
            soundsThisTick++;
            SOUND_READY.put(disturbingEntity, ticks + SOUND_COOLDOWN);
            float loudness = Math.min(1.0F, 0.3F + count / 20.0F) * volume;
            float pitch = 1.0F + level.random.nextFloat() * 0.4F - Math.min(0.3F, count / 200.0F);
            level.playLocalSound(x, y, z, wet ? SoundEvents.WET_GRASS_STEP : SoundEvents.AZALEA_LEAVES_STEP, SoundSource.AMBIENT,
                    loudness, pitch, false);
        }

        @Override
        public void onBurn(double x, double y, double z) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0.0, 0.03, 0.0);
        }

        @Override
        public void onWhirl(double x, double y, double z, float intensity) {
            if (SETTINGS.rustleVolume > 0.0F) {
                level.playLocalSound(x, y + 1.0, z, SoundEvents.BREEZE_IDLE_GROUND, SoundSource.AMBIENT,
                        0.25F * intensity * SETTINGS.rustleVolume, 0.6F + level.random.nextFloat() * 0.2F, false);
            }
        }
    }
}
