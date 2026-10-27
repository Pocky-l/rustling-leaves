package com.pockyl.rustling_leaves.client;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.compat.ImmersiveWeathering;
import com.pockyl.rustling_leaves.client.compat.LivingWind;
import com.pockyl.rustling_leaves.client.compat.RealisticWaves;
import com.pockyl.rustling_leaves.client.compat.SereneSeasons;
import com.pockyl.rustling_leaves.network.ModNetwork;
import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.sim.LeafListener;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.LeafSimulation;
import com.pockyl.rustling_leaves.sim.LeafSpawner;
import com.pockyl.rustling_leaves.sim.LitterChunk;
import com.pockyl.rustling_leaves.sim.LitterField;
import com.pockyl.rustling_leaves.sim.PileBlocks;
import com.pockyl.rustling_leaves.sim.SeasonCurve;
import com.pockyl.rustling_leaves.sim.TreeLeaves;
import com.pockyl.rustling_leaves.sim.WaterWaves;
import com.pockyl.rustling_leaves.sim.WindSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the client-side leaf world: creates the simulation for the current level, keeps the litter around the camera
 * loaded, saved and seeded, feeds the simulation entities, explosions, block changes and rake strokes, spawns leaves
 * from trees and renders everything.
 */
@Mod.EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class LeafManager {
    private static final int MAX_BURSTS_PER_TICK = 24;
    private static final int MAX_SOUNDS_PER_TICK = 3;
    private static final int SOUND_COOLDOWN = 4;
    private static final double TELEPORT_DISTANCE = 4.0;
    private static final int SAVE_INTERVAL = 20 * 30;
    private static final float RAKE_RADIUS = 2.0F;
    private static final long SEED_BUDGET_NANOS = 4_000_000L;
    /** Litter chunks per tick whose leaf pile blocks are turned into litter. */
    private static final int PILE_SCANS_PER_TICK = 16;
    /** Seasons change slowly; once a second is plenty (also to follow the biome under the camera). */
    private static final int SEASON_INTERVAL = 20;
    /**
     * Ground that was seeded this many ticks ago gets its natural litter topped up again (one day), unless it lies where
     * trees around the camera keep it covered with falling leaves anyway.
     */
    private static final long REFRESH_TICKS = 24000L;
    private static final boolean SERENE_SEASONS = ModList.get().isLoaded(SereneSeasons.MOD_ID);
    /** Looked up on use: this class loads with the config, before the pile blocks are registered. */
    private static final PileBlocks PILES = ModList.get().isLoaded(ImmersiveWeathering.MOD_ID)
            ? state -> ImmersiveWeathering.piles().layers(state)
            : PileBlocks.NONE;

    private static final LeafSettings SETTINGS = new LeafSettings();
    private static final LeafColors COLORS = new LeafColors();
    private static final Trees TREES = new Trees();
    private static final LeafSpawner SPAWNER = new LeafSpawner(TREES);
    private static final LitterSeeder SEEDER = new LitterSeeder(COLORS);
    /** Vertical movement of each entity in the previous tick, to detect landings. */
    private static final Int2FloatOpenHashMap LAST_FALL = new Int2FloatOpenHashMap();
    private static final Int2IntOpenHashMap SOUND_READY = new Int2IntOpenHashMap();
    /** Swing progress of the other players in the previous tick, to see a new swing start. */
    private static final Int2IntOpenHashMap LAST_SWING = new Int2IntOpenHashMap();

    private static volatile boolean configChanged;
    private static boolean wavesLookedUp;
    private static RealisticWaves realisticWaves;
    private static boolean windLookedUp;
    private static LivingWind livingWind;
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

    /** The running leaf simulation, null while there is no world. */
    public static LeafSimulation simulation() {
        return simulation;
    }

    /** Called from the config event, possibly on another thread; applied on the next client tick. */
    static void onConfigChanged() {
        configChanged = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (configChanged) {
            configChanged = false;
            int capacity = SETTINGS.maxLeaves;
            Config.apply(SETTINGS);
            nextSeasonCheck = ticks;
            if (capacity != SETTINGS.maxLeaves) {
                reset();
            } else if (simulation != null) {
                simulation.setPiles(PILES, TREES, SETTINGS.drawPileBlocks);
                simulation.setWaves(waves());
                simulation.setWindSource(windSource());
            }
            updatePileModels(minecraft);
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
            simulation.setPiles(PILES, TREES, SETTINGS.drawPileBlocks);
            simulation.setWaves(waves());
            simulation.setWindSource(windSource());
            renderer = new LeafRenderer(simulation);
            storage = new LitterStorage(storageDirectory(minecraft, level));
        }
        ticks++;
        burstsThisTick = 0;
        soundsThisTick = 0;
        Vec3 camera = cameraPosition(minecraft);
        // The season goes first: it decides how much litter new ground gets.
        if (ticks >= nextSeasonCheck) {
            nextSeasonCheck = ticks + SEASON_INTERVAL;
            updateSeason(camera);
        }
        updateLitterChunks(camera);
        simulation.beginTick(level, camera.x, camera.y, camera.z);
        disturbByEntities(camera);
        simulation.finishTick();
        rakeByOtherPlayers(minecraft);
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
        // The level pose stack holds the camera rotation: it is the model-view matrix the terrain is drawn with.
        renderer.render(event.getCamera(), event.getFrustum(), event.getPoseStack().last().pose(), event.getProjectionMatrix(),
                event.getPartialTick());
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (simulation == null || !Minecraft.getInstance().options.renderDebug) {
            return;
        }
        LeafPool pool = simulation.pool();
        event.getRight().add("");
        event.getRight().add(String.format("Rustling Leaves: %d/%d moving, %d lying", pool.count(), pool.capacity, simulation.field().total()));
        event.getRight().add(String.format("Litter: %d chunks, %d quads drawn, %d whirlwinds", simulation.field().chunks().size(),
                renderer.tileQuads(), simulation.wind().whirlwinds().size()));
    }

    /**
     * Sneak + right click with a hoe or shovel on leaf litter rakes it together instead of tilling or making a path;
     * without sneaking the tool works as in vanilla.
     */
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
        if (!minecraft.player.isShiftKeyDown() || !stack.is(ItemTags.HOES) && !stack.is(ItemTags.SHOVELS)) {
            return;
        }
        Vec3 at = hit.getLocation();
        if (rakeAt(at)) {
            event.setCanceled(true);
            // The swing reaches the other players through vanilla; clients without the server channel guess the
            // stroke from it (rakeByOtherPlayers), with the channel the exact point follows.
            event.setSwingHand(true);
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null && ModNetwork.present(connection.getConnection())) {
                ModNetwork.sendToServer(new RakePayload(at.x, at.y, at.z));
            }
        }
    }

    /** A player nearby raked at a point (relayed by a server with the mod): the same stroke in our own litter. */
    static void onRemoteRake(RakePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (simulation == null || level == null || minecraft.level != level || !SETTINGS.raking) {
            return;
        }
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        double range = SETTINGS.spawnRadius + 16.0;
        if (at.distanceToSqr(cameraPosition(minecraft)) < range * range) {
            rakeAt(at);
        }
    }

    /** Rakes the litter around a point into a pile with its sound; false when there was nothing to rake. */
    private static boolean rakeAt(Vec3 at) {
        if (simulation.litterAround(at.x, at.y, at.z, RAKE_RADIUS) < 3) {
            return false;
        }
        int moved = simulation.rake(at.x, at.y, at.z, RAKE_RADIUS);
        if (moved <= 0) {
            return false;
        }
        float volume = Math.min(1.0F, 0.4F + moved / 60.0F) * Math.max(0.3F, SETTINGS.rustleVolume);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.AZALEA_LEAVES_STEP, SoundSource.PLAYERS, volume,
                0.8F + level.random.nextFloat() * 0.2F, false);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, volume * 0.5F,
                0.7F + level.random.nextFloat() * 0.2F, false);
        return true;
    }

    /**
     * Without the mod on the server nothing tells us where other players rake, so a stroke is guessed from what every
     * client sees anyway: a sneaking player with a hoe or shovel starts a main hand swing -> rake where they look.
     * Digging while sneaking with a shovel looks the same and rakes too; that is the price of needing no server part.
     */
    private static void rakeByOtherPlayers(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (!SETTINGS.raking || connection == null || ModNetwork.present(connection.getConnection())) {
            LAST_SWING.clear();
            return;
        }
        double range = SETTINGS.spawnRadius + 8.0;
        Vec3 camera = cameraPosition(minecraft);
        for (Player player : level.players()) {
            if (player == minecraft.player || player.isSpectator() || player.distanceToSqr(camera) > range * range) {
                continue;
            }
            int id = player.getId();
            int last = LAST_SWING.getOrDefault(id, Integer.MAX_VALUE);
            int now = player.swinging ? player.swingTime : Integer.MAX_VALUE;
            LAST_SWING.put(id, now);
            // A new swing starts over at -1 / 0; a running one only counts up.
            if (now >= last || player.swingingArm != InteractionHand.MAIN_HAND) {
                continue;
            }
            ItemStack stack = player.getMainHandItem();
            if (!player.isShiftKeyDown() || !stack.is(ItemTags.HOES) && !stack.is(ItemTags.SHOVELS)) {
                continue;
            }
            if (player.pick(player.getBlockReach(), 1.0F, false) instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK) {
                rakeAt(hit.getLocation());
            }
        }
    }

    /** Hooked into block updates of the client level. */
    /**
     * Called instead of a vanilla cherry petal particle under the cherry leaves at {@code pos}: drops a simulated petal
     * there (scaled by the fall rate and the season). Returns false when the simulation is off, so the vanilla particle shows.
     */
    /**
     * Whether the leaf particles of other leaf mods (Falling Leaves, Living Wind) are left out: the simulation drops the
     * leaves itself (unless the player keeps both). Called from their particle spawners.
     */
    public static boolean replacesFallingLeaves() {
        return simulation != null && SETTINGS.fallRate > 0.0F && !SETTINGS.otherModLeaves;
    }

    /** The waves of Realistic Waves for the floating leaves, if installed and wanted. */
    private static WaterWaves waves() {
        if (!SETTINGS.rideWaves || !ModList.get().isLoaded(RealisticWaves.MOD_ID)) {
            return WaterWaves.NONE;
        }
        if (!wavesLookedUp) {
            // Looked up on first use: this class loads with the config, long before the other mod is ready.
            wavesLookedUp = true;
            realisticWaves = RealisticWaves.find();
        }
        return realisticWaves != null ? realisticWaves : WaterWaves.NONE;
    }

    /** The world wind of Living Wind for the leaves, if installed and wanted. */
    private static WindSource windSource() {
        if (!SETTINGS.followWorldWind || !ModList.get().isLoaded(LivingWind.MOD_ID)) {
            return WindSource.NONE;
        }
        if (!windLookedUp) {
            windLookedUp = true;
            livingWind = LivingWind.find();
        }
        return livingWind != null ? livingWind : WindSource.NONE;
    }

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
        if (PILES.layers(oldState) > 0 || PILES.layers(newState) > 0) {
            simulation.pileChanged(pos, PILES.layers(newState) == 0, placedByHand(pos));
        }
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

    /**
     * Whether the local player probably just placed the pile block at {@code pos}: such piles appear at once, piles
     * that grow on their own fill up with falling leaves.
     */
    private static boolean placedByHand(BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(pos.getCenter()) > 8.0 * 8.0) {
            return false;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof BlockItem item && PILES.layers(item.getBlock().defaultBlockState()) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hooked into the explosion packet, which the server sends for TNT, creepers, fireballs and the like. Minecraft
     * 1.20.1 has no wind charges, so every explosion is a real one.
     */
    public static void onExplosion(ClientboundExplodePacket packet) {
        if (simulation == null) {
            return;
        }
        simulation.explode(packet.getX(), packet.getY(), packet.getZ(), packet.getPower(), false);
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
        long now = level.getGameTime();
        boolean refresh = SETTINGS.decayRate() > 0.0F || SETTINGS.seasonLitter != 1.0F;
        if (ticks % 10 == 0) {
            double shedding = SETTINGS.spawnRadius + 16.0;
            List<LitterChunk> far = new ArrayList<>();
            for (LitterChunk chunk : field.chunks()) {
                double distanceSq = chunkDistanceSq(chunk.x, chunk.z, camera);
                if (distanceSq > keepSq) {
                    far.add(chunk);
                } else if (refresh && distanceSq > shedding * shedding && refreshDue(chunk, now)) {
                    chunk.unseed();
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
                    if (chunk == null) {
                        field.put(new LitterChunk(cx, cz));
                        continue;
                    }
                    // The litter rotted while nobody was here, and the trees kept shedding.
                    field.put(chunk);
                    simulation.decay(chunk, now);
                    if (refresh && refreshDue(chunk, now)) {
                        chunk.unseed();
                    }
                }
            }
        }
        // Seed new ground nearest first, within a time budget per tick, once a chunk's neighbors are there for the
        // canopy around it: fast enough to keep ahead of a player flying over a forest. Leaf pile blocks of other
        // mods join the litter once the chunk is seeded.
        List<LitterChunk> unseeded = new ArrayList<>();
        int pileScans = simulation.drawsPiles() ? PILE_SCANS_PER_TICK : 0;
        for (LitterChunk chunk : field.chunks()) {
            if (!chunk.seeded() && neighborsLoaded(chunk.x, chunk.z)) {
                unseeded.add(chunk);
            } else if (chunk.seeded() && !chunk.pilesScanned() && pileScans > 0 && simulation.scanPiles(chunk)) {
                pileScans--;
            }
        }
        if (unseeded.isEmpty()) {
            return;
        }
        unseeded.sort((a, b) -> Double.compare(chunkDistanceSq(a.x, a.z, camera), chunkDistanceSq(b.x, b.z, camera)));
        long deadline = System.nanoTime() + SEED_BUDGET_NANOS;
        for (LitterChunk chunk : unseeded) {
            SEEDER.seed(level, simulation, chunk);
            chunk.markSeeded(now);
            if (System.nanoTime() > deadline) {
                break;
            }
        }
    }

    /**
     * Hides the models of leaf pile blocks while the litter draws them and shows them again when it stops; the chunk
     * meshes are rebuilt when that changes.
     */
    private static void updatePileModels(Minecraft minecraft) {
        boolean hide = PILES != PileBlocks.NONE && SETTINGS.drawPileBlocks && SETTINGS.maxLeaves > 0;
        if (hide != ImmersiveWeathering.hidden() && PILES != PileBlocks.NONE) {
            ImmersiveWeathering.setHidden(hide);
            if (minecraft.level != null) {
                minecraft.levelRenderer.allChanged();
            }
        }
    }

    private static boolean refreshDue(LitterChunk chunk, long now) {
        return chunk.seeded() && chunk.seededAt() != LitterChunk.NEVER && now - chunk.seededAt() >= REFRESH_TICKS;
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
        LAST_SWING.clear();
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
    }
}
