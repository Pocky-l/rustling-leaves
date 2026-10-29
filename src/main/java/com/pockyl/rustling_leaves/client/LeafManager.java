package com.pockyl.rustling_leaves.client;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
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
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.compat.LivingWind;
import com.pockyl.rustling_leaves.client.compat.RealisticWaves;
import com.pockyl.rustling_leaves.client.compat.SereneSeasons;
import com.pockyl.rustling_leaves.client.compat.WeatherWind;
import com.pockyl.rustling_leaves.client.compat.ShaderPacks;
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
@EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class LeafManager {
    private static final int MAX_BURSTS_PER_TICK = 24;
    private static final int MAX_SOUNDS_PER_TICK = 3;
    private static final int SOUND_COOLDOWN = 4;
    private static final double TELEPORT_DISTANCE = 4.0;
    private static final int SAVE_INTERVAL = 20 * 30;
    private static final float RAKE_RADIUS = 2.0F;
    private static final long SEED_BUDGET_NANOS = 3_000_000L;
    /** Time per tick for taking saved litter of chunks coming into range (nearest first). */
    private static final long LOAD_BUDGET_NANOS = 2_000_000L;
    /**
     * Time per tick for packing changed litter for the disk. Packing all of it at once (every {@link #SAVE_INTERVAL})
     * took 50-70 ms - a stutter every half minute - so it goes a few chunks per tick and the save only writes out.
     */
    private static final long STORE_BUDGET_NANOS = 1_000_000L;
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
    private static final Identifier DEBUG_ENTRY = RustlingLeaves.id("leaves");

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
    /** How far above the still water floating leaves are drawn while a shader pack draws the water. */
    private static final float SHADER_WATER_LIFT = 0.04F;
    /** Whether a shader pack is on, checked once per tick (it can be switched in game). */
    private static boolean shaderWater;
    /** Chunks within range whose saved litter is still to be taken in, nearest first. */
    private static final LongArrayList toLoad = new LongArrayList();
    /** Chunks out of range whose changes are still to be packed for the disk. */
    private static final Long2ObjectOpenHashMap<LitterChunk> leaving = new Long2ObjectOpenHashMap<>();
    /** Chunks still to be looked at in the current round of packing changed litter. */
    private static final List<LitterChunk> storeQueue = new ArrayList<>();
    /** A round of packing starts every this many ticks (well within {@link #SAVE_INTERVAL}). */
    private static final int STORE_ROUND_TICKS = 100;
    private static int storeRound;
    private static boolean wavesLookedUp;
    private static RealisticWaves realisticWaves;
    private static boolean windLookedUp;
    private static LivingWind livingWind;
    private static WeatherWind weatherWind;
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
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (configChanged) {
            configChanged = false;
            int capacity = SETTINGS.maxLeaves;
            Config.apply(SETTINGS);
            nextSeasonCheck = ticks;
            if (capacity != SETTINGS.maxLeaves) {
                reset();
            } else if (simulation != null) {
                simulation.setPiles(piles(), TREES, true);
                simulation.setWaves(waves());
                simulation.setWindSource(windSource());
            }
            updateLeafLitterModels(minecraft);
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
            simulation.setPiles(piles(), TREES, true);
            simulation.setWaves(waves());
            simulation.setWindSource(windSource());
            renderer = new LeafRenderer(simulation);
            storage = new LitterStorage(storageDirectory(minecraft, level));
        }
        ticks++;
        shaderWater = ShaderPacks.inUse();
        simulation.setSunPathRotation(ShaderPacks.sunPathRotation());
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
        storeSome();
        if (ticks % SAVE_INTERVAL == 0) {
            storage.flush(Mth.floor(camera.x) >> 4, Mth.floor(camera.z) >> 4);
        }
        if (ticks % 200 == 0) {
            LAST_FALL.clear();
            SOUND_READY.clear();
        }
    }

    /** Draws the leaves into the level; called by the render backend at the right moment of the level render. */
    public static void render(Vec3 camera, Vector3fc look, Frustum frustum, Matrix4f modelView, Matrix4f projection,
            float partialTick) {
        if (renderer != null) {
            renderer.render(camera, look, frustum, modelView, projection, partialTick);
        }
    }

    /** The leaf counts on the debug screen (F3), an entry of its own that is shown with the overlay by default. */
    @SubscribeEvent
    public static void onRegisterDebugEntries(RegisterDebugEntriesEvent event) {
        event.register(DEBUG_ENTRY, (displayer, debugLevel, clientChunk, serverChunk) -> {
            if (simulation == null) {
                return;
            }
            LeafPool pool = simulation.pool();
            displayer.addToGroup(DEBUG_ENTRY, List.of(
                    String.format("Rustling Leaves: %d/%d moving, %d lying", pool.count(), pool.capacity, simulation.field().total()),
                    String.format("Litter: %d chunks, %d quads drawn, %d whirlwinds", simulation.field().chunks().size(),
                            renderer.tileQuads(), simulation.wind().whirlwinds().size())));
        });
        event.includeInProfile(DEBUG_ENTRY, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
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
            if (connection != null && connection.hasChannel(RakePayload.TYPE)) {
                ClientPacketDistributor.sendToServer(new RakePayload(at.x, at.y, at.z));
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
        level.playLocalSound(at.x, at.y, at.z, LeafSounds.RAKE, SoundSource.PLAYERS, volume,
                0.92F + level.random.nextFloat() * 0.16F, false);
        return true;
    }

    /**
     * Without the mod on the server nothing tells us where other players rake, so a stroke is guessed from what every
     * client sees anyway: a sneaking player with a hoe or shovel starts a main hand swing -> rake where they look.
     * Digging while sneaking with a shovel looks the same and rakes too; that is the price of needing no server part.
     */
    private static void rakeByOtherPlayers(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (!SETTINGS.raking || connection == null || connection.hasChannel(RakePayload.TYPE)) {
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
            if (player.pick(player.blockInteractionRange(), 1.0F, false) instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK) {
                rakeAt(hit.getLocation());
            }
        }
    }

    /** Hooked into block updates of the client level. */
    /**
     * Called instead of a vanilla falling leaf particle under the leaves at {@code pos}: drops a simulated leaf there
     * (scaled by the fall rate and the season). Returns false when the simulation is off, so the vanilla particle shows.
     */
    /**
     * Whether the leaf particles of other leaf mods (Falling Leaves, Living Wind) are left out: the simulation drops the
     * leaves itself (unless the player keeps both). Called from their particle spawners.
     */
    public static boolean replacesFallingLeaves() {
        return simulation != null && SETTINGS.fallRate > 0.0F && !SETTINGS.otherModLeaves;
    }

    /**
     * The water surface the floating leaves are drawn on: the waves of Realistic Waves where it draws them, and with a
     * shader pack the pack's water, which sways up to about 0.03 blocks above the vanilla surface and would cover
     * leaves lying on the still water.
     */
    private static WaterWaves waves() {
        WaterWaves modWaves = modWaves();
        return (x, z) -> {
            float lift = modWaves.lift(x, z);
            return Float.isNaN(lift) && shaderWater ? SHADER_WATER_LIFT : lift;
        };
    }

    /** The waves of Realistic Waves, if installed and wanted. */
    private static WaterWaves modWaves() {
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

    /**
     * The wind of another mod for the leaves, if installed and wanted: Living Wind's world wind while it has one, else
     * the course of Petrichor's and Hoarfrost's rain and snow.
     */
    private static WindSource windSource() {
        if (!SETTINGS.followWorldWind) {
            return WindSource.NONE;
        }
        if (!windLookedUp) {
            windLookedUp = true;
            livingWind = ModList.get().isLoaded(LivingWind.MOD_ID) ? LivingWind.find() : null;
            weatherWind = WeatherWind.find();
        }
        WindSource world = livingWind != null ? livingWind : WindSource.NONE;
        WindSource weather = weatherWind != null ? weatherWind : WindSource.NONE;
        return out -> world.read(out) || weather.read(out);
    }

    public static boolean replaceLeafParticle(Level particleLevel, BlockPos pos) {
        if (simulation == null || particleLevel != level) {
            return false;
        }
        SPAWNER.dropBelow(level, simulation, level.getBlockState(pos), pos);
        return true;
    }

    public static void onBlockChanged(ClientLevel changed, BlockPos pos, BlockState oldState, BlockState newState) {
        if (simulation == null || changed != level || oldState == newState) {
            return;
        }
        simulation.blockChanged(pos);
        PileBlocks piles = piles();
        if (piles.layers(oldState) > 0 || piles.layers(newState) > 0) {
            simulation.pileChanged(pos, piles.layers(newState) == 0, placedByHand(pos));
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
            if (player.getItemInHand(hand).getItem() instanceof BlockItem item && piles().layers(item.getBlock().defaultBlockState()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Hooked into the explosion packet, which the server sends for TNT, creepers, wind charges and the like. */
    public static void onExplosion(ClientboundExplodePacket packet) {
        if (simulation == null) {
            return;
        }
        boolean windCharge = isGust(packet.explosionParticle());
        Vec3 center = packet.center();
        simulation.explode(center.x, center.y, center.z, packet.radius(), windCharge);
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
                // Packed for the disk over the next ticks (a teleport moves hundreds of chunks out of range at once).
                if (chunk.isDirty()) {
                    leaving.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
                }
                field.remove(chunk.x, chunk.z);
            }
            toLoad.clear();
            for (int cx = camChunkX - reach; cx <= camChunkX + reach; cx++) {
                for (int cz = camChunkZ - reach; cz <= camChunkZ + reach; cz++) {
                    if (field.chunk(cx, cz) == null && chunkDistanceSq(cx, cz, camera) <= loadSq
                            && level.getChunkSource().hasChunk(cx, cz)) {
                        toLoad.add(ChunkPos.asLong(cx, cz));
                    }
                }
            }
            toLoad.sort((a, b) -> Double.compare(chunkDistanceSq(ChunkPos.getX(a), ChunkPos.getZ(a), camera),
                    chunkDistanceSq(ChunkPos.getX(b), ChunkPos.getZ(b), camera)));
        }
        // Saved litter comes in nearest first within a time budget; regions still being read wait for a later tick.
        long loadDeadline = System.nanoTime() + LOAD_BUDGET_NANOS;
        for (int n = 0; n < toLoad.size() && System.nanoTime() < loadDeadline; n++) {
            long key = toLoad.getLong(n);
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (field.chunk(cx, cz) != null || !level.getChunkSource().hasChunk(cx, cz)) {
                toLoad.removeLong(n--);
                continue;
            }
            LitterChunk back = leaving.remove(key);
            if (back != null) {
                // Back in range before its changes reached the disk: the chunk itself, not the older saved copy.
                toLoad.removeLong(n--);
                field.put(back);
                continue;
            }
            if (!storage.ready(cx, cz)) {
                continue;
            }
            toLoad.removeLong(n--);
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
            // At least one chunk per tick; another only if one more, as long as the last one took, still fits.
            long before = System.nanoTime();
            SEEDER.seed(level, simulation, chunk);
            chunk.markSeeded(now);
            long after = System.nanoTime();
            if (after + (after - before) > deadline) {
                break;
            }
        }
    }

    /**
     * Vanilla leaf litter becomes litter of the simulation (the leaves on it react like any other) while the option is
     * on. Off, it keeps its own flat model and is no pile at all: it is too thin to stop falling leaves, which lie on
     * the ground under it.
     */
    private static PileBlocks piles() {
        return SETTINGS.drawPileBlocks ? PileBlocks.LEAF_LITTER : PileBlocks.NONE;
    }

    /**
     * Hides the model of vanilla leaf litter while the litter draws it and shows it again when it stops; the chunk
     * meshes are rebuilt when that changes.
     */
    private static void updateLeafLitterModels(Minecraft minecraft) {
        boolean hide = SETTINGS.drawPileBlocks && SETTINGS.maxLeaves > 0;
        if (hide != LeafLitterModels.hidden()) {
            LeafLitterModels.setHidden(hide);
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

    /** Packs the changed litter of a few chunks for the disk, going round all chunks within a time budget per tick. */
    private static void storeSome() {
        long deadline = System.nanoTime() + STORE_BUDGET_NANOS;
        // Chunks that went out of range first: they are not in the field any more.
        while (!leaving.isEmpty() && System.nanoTime() < deadline) {
            long key = leaving.keySet().iterator().nextLong();
            LitterChunk chunk = leaving.remove(key);
            if (chunk.takeDirty()) {
                storage.store(chunk);
            }
        }
        while (System.nanoTime() < deadline) {
            if (storeQueue.isEmpty()) {
                if (++storeRound % STORE_ROUND_TICKS != 0) {
                    return;
                }
                storeQueue.addAll(simulation.field().chunks());
                if (storeQueue.isEmpty()) {
                    return;
                }
            }
            LitterChunk chunk = storeQueue.remove(storeQueue.size() - 1);
            // Chunks that left the field are stored from the leaving list.
            if (simulation.field().chunk(chunk.x, chunk.z) == chunk && chunk.takeDirty()) {
                storage.store(chunk);
            }
        }
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
        Identifier dimension = level.dimension().identifier();
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
            return camera.position();
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
            for (LitterChunk chunk : leaving.values()) {
                if (chunk.takeDirty()) {
                    storage.store(chunk);
                }
            }
            storage.close();
        }
        leaving.clear();
        storage = null;
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        simulation = null;
        toLoad.clear();
        storeQueue.clear();
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

        @Override
        public boolean dropsVanillaLeaves(BlockState state) {
            return state.getBlock() instanceof LeavesBlock;
        }
    }

    private static final class Listener implements LeafListener {
        @Override
        public void onRustle(double x, double y, double z, int count, boolean wet) {
            SoundEvent sound = wet ? LeafSounds.WET : LeafSounds.RUSTLE;
            float volume = SETTINGS.rustleVolume;
            if (count < 2 || volume <= 0.0F || soundsThisTick >= MAX_SOUNDS_PER_TICK || SOUND_READY.get(disturbingEntity) > ticks) {
                return;
            }
            soundsThisTick++;
            SOUND_READY.put(disturbingEntity, ticks + SOUND_COOLDOWN);
            float loudness = Math.min(1.0F, 0.3F + count / 20.0F) * volume;
            // Field recordings: only a little pitch play, lower for bigger heaps of leaves.
            float pitch = 0.93F + level.random.nextFloat() * 0.14F - Math.min(0.08F, count / 600.0F);
            level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, loudness, pitch, false);
        }

        @Override
        public void onBurn(double x, double y, double z) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0.0, 0.03, 0.0);
        }
    }
}
