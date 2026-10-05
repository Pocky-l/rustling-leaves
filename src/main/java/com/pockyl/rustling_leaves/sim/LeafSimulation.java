package com.pockyl.rustling_leaves.sim;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The leaf simulation. Runs once per game tick against a {@link Level} and never touches client classes, so the same
 * code runs in game tests on the server.
 *
 * <p>Leaves exist in two forms. Lying leaves are part of the {@link LitterField}: stacks in quarter-block cells that
 * form carpets and piles and cost almost nothing. Moving leaves are particles in the {@link LeafPool} with full
 * aerodynamics. A leaf turns into a particle when something moves it (a foot, a blast, the wind, a rake) and goes
 * back into the litter, exactly where and how it settled, when it comes to rest.
 *
 * <p>Units: blocks, ticks, radians.
 */
public final class LeafSimulation {
    public static final int DYING_TICKS = 30;

    private static final float GRAVITY = 0.0045F;
    private static final float DRAG_VERTICAL = 0.085F;
    private static final float DRAG_HORIZONTAL = 0.06F;
    /** Drag grows with relative air speed, so blasted leaves slow down quickly instead of flying like stones. */
    private static final float QUADRATIC_DRAG = 3.5F;
    private static final float FOLIAGE_DRAG = 2.5F;
    private static final float RAIN_PUSH = 0.0015F;
    private static final float MAX_SPEED = 2.0F;
    private static final float MAX_STEP = 0.4F;
    private static final int MAX_AIR_TICKS = 20 * 120;
    private static final int MAX_SLIDE_TICKS = 20 * 30;
    private static final int SETTLE_TICKS = 6;
    private static final float GROUND_FRICTION = 0.86F;
    private static final float LIFT_THRESHOLD = 0.045F;
    private static final float LIFT_SCALE = 8.0F;
    private static final int WIND_SAMPLES = 48;
    private static final int PARTICLES_PER_DISTURB = 24;
    /** Leaves per tick that visibly tumble down slumping piles. */
    private static final int CASCADE_PARTICLES = 12;
    /** Leaves at the bottom of a stack that a foot presses down instead of moving. */
    private static final int TRAMPLED = 2;
    private static final float LAYER = LitterField.LAYER;
    private static final float CELL = LitterField.CELL;

    private final LeafSettings settings;
    private final LeafPool pool;
    private final LeafListener listener;
    private final SpatialGrid grid;
    private final Terrain terrain = new Terrain();
    private final LitterField field;
    private final Wind wind = new Wind();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final IntArrayList nearby = new IntArrayList();
    private final FastRandom random = new FastRandom(System.nanoTime());
    private final LitterField.Pose pose = new LitterField.Pose();
    private final float[] air = new float[3];
    private final int[] direction = new int[1];
    private final LitterField.Spill spill = this::spill;
    private int tick;
    private int cascadeBudget;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private Level level;

    public LeafSimulation(LeafSettings settings, int capacity, LeafListener listener) {
        this.settings = settings;
        this.listener = listener;
        pool = new LeafPool(capacity);
        grid = new SpatialGrid(capacity);
        field = new LitterField(settings, terrain);
    }

    public LeafPool pool() {
        return pool;
    }

    public LitterField field() {
        return field;
    }

    public Wind wind() {
        return wind;
    }

    public LeafSettings settings() {
        return settings;
    }

    /** Runs one full tick: {@link #beginTick} followed by {@link #finishTick}. */
    public void tick(Level level, double cameraX, double cameraY, double cameraZ) {
        beginTick(level, cameraX, cameraY, cameraZ);
        finishTick();
    }

    /** Updates the wind and indexes the leaves; entity disturbances go between this and {@link #finishTick}. */
    public void beginTick(Level level, double cameraX, double cameraY, double cameraZ) {
        this.level = level;
        terrain.level = level;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        tick++;
        wind.update(level, tick, settings, terrain, random, cameraX, cameraY, cameraZ);
        grid.rebuild(pool);
    }

    /** Applies explosions and the wind to the litter, moves every leaf and lets piles settle. */
    public void finishTick() {
        processShockwaves();
        windOnLitter();
        whirlwindsOnLitter();
        double despawn = settings.despawnRadius();
        double despawnSq = despawn * despawn;
        int highWater = pool.highWater();
        for (int i = 0; i < highWater; i++) {
            byte state = pool.state[i];
            if (state == LeafPool.FREE) {
                continue;
            }
            pool.savePrevious(i);
            double dx = pool.x[i] - cameraX;
            double dz = pool.z[i] - cameraZ;
            if (dx * dx + dz * dz > despawnSq) {
                pool.release(i);
                continue;
            }
            switch (state) {
                case LeafPool.FALLING -> tickFalling(i);
                case LeafPool.SLIDING -> tickSliding(i);
                case LeafPool.FLOATING -> tickFloating(i);
                case LeafPool.DYING -> {
                    pool.x[i] += pool.vx[i];
                    pool.y[i] += pool.vy[i];
                    pool.z[i] += pool.vz[i];
                    pool.yaw[i] += pool.spinYaw[i];
                    if (--pool.life[i] <= 0) {
                        pool.release(i);
                    }
                }
                default -> {
                }
            }
        }
        cascadeBudget = CASCADE_PARTICLES;
        field.relax(spill);
    }

    /** The highest solid surface at or below y within {@code depth} blocks; NaN if none or water comes first. */
    public double groundBelow(double x, double y, double z, int depth) {
        return terrain.groundBelow(x, y, z, depth);
    }

    /** Top of the collision box containing the point, or NaN when the point is free. Foliage is never solid. */
    public double solidTop(double x, double y, double z) {
        return terrain.solidTop(x, y, z);
    }

    /** Points the simulation at a level without ticking (for seeding before the first tick). */
    public void setLevel(Level level) {
        this.level = level;
        terrain.level = level;
    }

    /** Packed light coordinates at a position (block light in bits 4..7, sky light in bits 20..23). */
    public int lightAt(double x, double y, double z) {
        return terrain.lightAt(x, y, z);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Adds a falling leaf.
     *
     * @param natural whether it fell off a tree on its own: such leaves only thicken the litter up to the natural
     *                carpet depth, so the forest floor does not grow forever
     * @return the leaf index, or -1 if the pool is full
     */
    public int spawn(double x, double y, double z, float vx, float vy, float vz, int color, int baseColor, LeafShape shape, int sprite,
            float size, boolean natural) {
        int i = pool.allocate();
        if (i < 0) {
            return -1;
        }
        initLeaf(i, x, y, z, color, baseColor, shape.ordinal(), sprite, size);
        pool.yaw[i] = random.next() * Mth.TWO_PI;
        pool.pitch[i] = (random.next() - 0.5F) * 1.2F;
        pool.roll[i] = (random.next() - 0.5F) * 1.2F;
        pool.savePrevious(i);
        pool.spinYaw[i] = (random.next() - 0.5F) * 0.12F;
        pool.vx[i] = vx;
        pool.vy[i] = vy;
        pool.vz[i] = vz;
        pool.flags[i] = natural ? LeafPool.FLAG_NATURAL : 0;
        pool.state[i] = LeafPool.FALLING;
        return i;
    }

    /** Lifts the top leaf of a litter cell into the air (or onto the ground, sliding) exactly where it lay. */
    private int release(int cellX, int cellZ, byte state, float vx, float vy, float vz) {
        if (pool.free() == 0 || !field.take(cellX, cellZ, pose)) {
            return -1;
        }
        int i = pool.allocate();
        initLeaf(i, pose.x, pose.y, pose.z, pose.color, pose.baseColor, pose.shape, pose.sprite, pose.size);
        pool.yaw[i] = pose.yaw;
        pool.pitch[i] = pose.pitch;
        pool.roll[i] = pose.roll;
        pool.savePrevious(i);
        pool.vx[i] = vx;
        pool.vy[i] = vy;
        pool.vz[i] = vz;
        pool.ground[i] = pose.y;
        pool.flags[i] = 0;
        pool.state[i] = state;
        return i;
    }

    private void initLeaf(int i, double x, double y, double z, int color, int baseColor, int shape, int sprite, float size) {
        pool.x[i] = x;
        pool.y[i] = y;
        pool.z[i] = z;
        pool.spinYaw[i] = 0.0F;
        pool.spinPitch[i] = 0.0F;
        pool.spinRoll[i] = 0.0F;
        pool.phase[i] = random.next() * Mth.TWO_PI;
        pool.freq[i] = 0.1F + random.next() * 0.12F;
        pool.sway[i] = 0.03F + random.next() * 0.04F;
        pool.swayDir[i] = random.next() * Mth.TWO_PI;
        pool.size[i] = size;
        pool.auxA[i] = 0.0F;
        pool.auxB[i] = 0.0F;
        pool.ground[i] = y;
        pool.color[i] = color;
        pool.baseColor[i] = baseColor;
        pool.shape[i] = (byte) shape;
        pool.sprite[i] = (byte) sprite;
        pool.light[i] = level != null ? terrain.lightAt(x, y + 0.1, z) : 0xF000F0;
        pool.age[i] = 0;
        pool.life[i] = 0;
    }

    /** Removes every moving leaf and all litter in memory. */
    public void clear() {
        for (int i = 0, highWater = pool.highWater(); i < highWater; i++) {
            if (pool.state[i] != LeafPool.FREE) {
                pool.release(i);
            }
        }
        shockwaves.clear();
        field.clear();
        wind.reset();
    }

    private void startDying(int i) {
        pool.state[i] = LeafPool.DYING;
        pool.life[i] = DYING_TICKS;
        pool.vx[i] = 0.0F;
        pool.vy[i] = 0.0F;
        pool.vz[i] = 0.0F;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Falling
    // ------------------------------------------------------------------------------------------------------------

    private void tickFalling(int i) {
        if (++pool.age[i] > MAX_AIR_TICKS) {
            pool.release(i);
            return;
        }
        if ((pool.flags[i] & LeafPool.FLAG_SETTLING) != 0) {
            settle(i);
            return;
        }
        double x = pool.x[i];
        double y = pool.y[i];
        double z = pool.z[i];
        BlockPos.MutableBlockPos cursor = terrain.cursor();
        cursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (!level.isLoaded(cursor)) {
            pool.release(i);
            return;
        }
        BlockState here = level.getBlockState(cursor);
        boolean foliage = here.is(BlockTags.LEAVES);
        if (!foliage && !here.isAir()) {
            FluidState fluid = here.getFluidState();
            if (!fluid.isEmpty()) {
                if (fluid.is(FluidTags.LAVA)) {
                    listener.onBurn(x, y, z);
                    pool.release(i);
                    return;
                }
                if (fluid.is(FluidTags.WATER)) {
                    double surface = cursor.getY() + fluid.getHeight(level, cursor);
                    if (y <= surface) {
                        startFloating(i, surface);
                        return;
                    }
                }
            }
            // A block appeared around the leaf (placed, or a piston): put the leaf on top of it.
            double top = terrain.topAt(here, x, y, z);
            if (!Double.isNaN(top)) {
                pool.y[i] = top;
                land(i, top);
                return;
            }
        }

        // Aerodynamics: the leaf is dragged towards the local air velocity (wind, whirlwinds and its own flutter), with
        // drag that depends on whether it currently faces the air broadside or edge-on.
        wind.sample(x, y, z, air);
        float phase = pool.phase[i] += pool.freq[i];
        float cos = Mth.cos(phase);
        float sin = Mth.sin(phase);
        float swayDir = pool.swayDir[i] += (random.next() - 0.5F) * 0.06F;
        float sway = pool.sway[i] * cos;
        float relX = air[0] + Mth.cos(swayDir) * sway - pool.vx[i];
        float relY = air[1] - pool.vy[i];
        float relZ = air[2] + Mth.sin(swayDir) * sway - pool.vz[i];
        float rel = Mth.sqrt(relX * relX + relY * relY + relZ * relZ);
        float quadratic = (1.0F + QUADRATIC_DRAG * rel) * (foliage ? FOLIAGE_DRAG : 1.0F);
        float dragH = Math.min(0.6F, DRAG_HORIZONTAL * quadratic);
        float dragV = Math.min(0.6F, DRAG_VERTICAL * (0.55F + 0.9F * sin * sin) * quadratic);
        pool.vx[i] += relX * dragH;
        pool.vz[i] += relZ * dragH;
        pool.vy[i] += relY * dragV - GRAVITY - wind.rain() * RAIN_PUSH;
        clampSpeed(i);

        // Orientation: a springy pull towards the flutter pose (tilted into the swing), weakened while the leaf is
        // tumbling fast. Both faces of a leaf are equivalent, hence the half-turn wrap.
        float tumble = Math.min(1.0F, rel * 5.0F);
        float spring = 0.07F * (1.0F - 0.9F * tumble);
        float damping = 0.85F + 0.12F * tumble;
        float tilt = 0.65F * sin;
        float relDir = swayDir - pool.yaw[i];
        pool.spinPitch[i] = (pool.spinPitch[i] + wrapHalfTurn(tilt * Mth.cos(relDir) - pool.pitch[i]) * spring) * damping;
        pool.spinRoll[i] = (pool.spinRoll[i] + wrapHalfTurn(tilt * Mth.sin(relDir) - pool.roll[i]) * spring) * damping;
        pool.spinYaw[i] = pool.spinYaw[i] * 0.985F + (air[0] * pool.vz[i] - air[2] * pool.vx[i]) * 0.5F;
        pool.pitch[i] += pool.spinPitch[i];
        pool.roll[i] += pool.spinRoll[i];
        pool.yaw[i] += pool.spinYaw[i];

        move(i);
        if (pool.state[i] == LeafPool.FALLING && ((i + tick) & 3) == 0) {
            pool.light[i] = terrain.lightAt(pool.x[i], pool.y[i], pool.z[i]);
        }
    }

    /** Moves a falling leaf by its velocity in sub-steps short enough not to tunnel through blocks or piles. */
    private void move(int i) {
        float vx = pool.vx[i];
        float vy = pool.vy[i];
        float vz = pool.vz[i];
        float speed = Math.max(Math.abs(vx), Math.max(Math.abs(vy), Math.abs(vz)));
        int steps = speed > MAX_STEP ? Math.min(5, Mth.ceil(speed / MAX_STEP)) : 1;
        float sx = vx / steps;
        float sy = vy / steps;
        float sz = vz / steps;
        double x = pool.x[i];
        double y = pool.y[i];
        double z = pool.z[i];
        for (int step = 0; step < steps; step++) {
            double ny = y + sy;
            double top = terrain.solidTop(x, ny, z);
            double pile = sy < 0.0F ? pileCrossing(x, y, ny, z) : Double.NaN;
            if (!Double.isNaN(pile) && (Double.isNaN(top) || pile >= top)) {
                top = pile;
            }
            if (Double.isNaN(top)) {
                y = ny;
            } else if (sy <= 0.0F) {
                pool.x[i] = x;
                pool.y[i] = top;
                pool.z[i] = z;
                land(i, top);
                return;
            } else {
                pool.vy[i] = 0.0F;
                sy = 0.0F;
            }
            double nx = x + sx;
            if (Double.isNaN(terrain.solidTop(nx, y, z))) {
                x = nx;
            } else {
                pool.vx[i] *= -0.3F;
                sx = 0.0F;
            }
            double nz = z + sz;
            if (Double.isNaN(terrain.solidTop(x, y, nz))) {
                z = nz;
            } else {
                pool.vz[i] *= -0.3F;
                sz = 0.0F;
            }
        }
        pool.x[i] = x;
        pool.y[i] = y;
        pool.z[i] = z;
    }

    /** Top of the litter stack under (x, z) if a leaf moving from {@code from} down to {@code to} passes through it. */
    private double pileCrossing(double x, double from, double to, double z) {
        int cx = LitterField.cell(x);
        int cz = LitterField.cell(z);
        LitterChunk chunk = field.chunkAtCell(cx, cz);
        if (chunk == null) {
            return Double.NaN;
        }
        int c = LitterField.index(cx, cz);
        int n = chunk.count[c];
        if (n == 0) {
            return Double.NaN;
        }
        double top = chunk.base[c] + n * LAYER;
        return top <= from + 1.0E-4 && top >= to ? top : Double.NaN;
    }

    /** Touchdown on a surface: the leaf stops and spends a few ticks turning to its resting tilt. */
    private void land(int i, double surface) {
        pool.vx[i] = 0.0F;
        pool.vy[i] = 0.0F;
        pool.vz[i] = 0.0F;
        pool.ground[i] = surface;
        pool.y[i] = surface + 0.006;
        pool.flags[i] |= LeafPool.FLAG_SETTLING;
        pool.life[i] = SETTLE_TICKS;
        pool.auxA[i] = 0.0F;
        pool.auxB[i] = 0.0F;
        int cx = LitterField.cell(pool.x[i]);
        int cz = LitterField.cell(pool.z[i]);
        LitterChunk chunk = field.chunkAtCell(cx, cz);
        if (chunk != null) {
            int c = LitterField.index(cx, cz);
            if (chunk.count[c] == 0) {
                chunk.base[c] = (float) surface;
            }
            field.restTilt(chunk, cx, cz, chunk.count[c], pool.yaw[i], pose);
            pool.auxA[i] = pose.pitch;
            pool.auxB[i] = pose.roll;
        }
    }

    private void settle(int i) {
        pool.pitch[i] += wrapHalfTurn(pool.auxA[i] - pool.pitch[i]) * 0.45F;
        pool.roll[i] += wrapHalfTurn(pool.auxB[i] - pool.roll[i]) * 0.45F;
        pool.yaw[i] += pool.spinYaw[i];
        pool.spinYaw[i] *= 0.5F;
        if (--pool.life[i] <= 0) {
            deposit(i);
        }
    }

    /**
     * The leaf comes to rest and joins the litter with its exact pose. On a pile too steep to hold it, it slides
     * down instead; a natural leaf on a full carpet just takes the place of the top leaf.
     */
    private void deposit(int i) {
        int cx = LitterField.cell(pool.x[i]);
        int cz = LitterField.cell(pool.z[i]);
        LitterChunk chunk = field.chunkAtCell(cx, cz);
        if (chunk == null) {
            startDying(i);
            return;
        }
        int c = LitterField.index(cx, cz);
        int n = chunk.count[c];
        double ground = pool.ground[i];
        if (n > 0 && (ground < chunk.base[c] - 0.1 || ground > chunk.base[c] + n * LAYER + 0.5)) {
            // The cell already holds litter on another level (a roof above the ground, say).
            startDying(i);
            return;
        }
        double base = n > 0 ? chunk.base[c] : ground;
        if ((n + 1) * LAYER > LitterField.REPOSE + LAYER || n + 1 >= LitterField.MAX_LAYERS) {
            double newTop = base + (n + 1) * LAYER;
            double lowest = field.lowestNeighbor(cx, cz, newTop, direction);
            if (Math.min(newTop - lowest, (n + 1) * LAYER) > LitterField.REPOSE + LAYER || n + 1 >= LitterField.MAX_LAYERS) {
                slideTowards(i, LitterField.dirX(direction[0]), LitterField.dirZ(direction[0]), 0.05F);
                return;
            }
        }
        float scale = 1.0F / CELL;
        long top = LitterField.encodeTop((float) (pool.x[i] * scale - cx), (float) (pool.z[i] * scale - cz), pool.yaw[i], pool.size[i],
                pool.sprite[i]);
        if ((pool.flags[i] & LeafPool.FLAG_NATURAL) != 0 && n >= settings.carpetDepth && n > 0) {
            field.replaceTop(cx, cz, top, pool.color[i]);
        } else {
            field.add(cx, cz, base, pool.baseColor[i], pool.shape[i], top, pool.color[i]);
        }
        pool.release(i);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sliding
    // ------------------------------------------------------------------------------------------------------------

    private void slideTowards(int i, float dirX, float dirZ, float speed) {
        pool.flags[i] &= (byte) ~LeafPool.FLAG_SETTLING;
        pool.state[i] = LeafPool.SLIDING;
        pool.age[i] = 0;
        pool.vx[i] = dirX * speed + (random.next() - 0.5F) * 0.02F;
        pool.vy[i] = 0.0F;
        pool.vz[i] = dirZ * speed + (random.next() - 0.5F) * 0.02F;
    }

    /**
     * A leaf skidding over the ground: friction slows it, the wind pushes it (only under the open sky), it tumbles
     * over now and then, stops against walls and piles, drops off edges and is lifted by whirlwind updrafts.
     */
    private void tickSliding(int i) {
        double x = pool.x[i];
        double y = pool.y[i];
        double z = pool.z[i];
        if (++pool.age[i] > MAX_SLIDE_TICKS) {
            land(i, pool.ground[i]);
            pool.state[i] = LeafPool.FALLING;
            return;
        }
        if ((pool.age[i] & 15) == 1) {
            boolean exposed = terrain.canSeeSky(x, y + 0.5, z);
            pool.flags[i] = (byte) (exposed ? pool.flags[i] | LeafPool.FLAG_EXPOSED : pool.flags[i] & ~LeafPool.FLAG_EXPOSED);
        }
        boolean exposed = (pool.flags[i] & LeafPool.FLAG_EXPOSED) != 0;
        wind.sample(x, y + 0.2, z, air);
        if (exposed && air[1] > 0.03F) {
            pool.state[i] = LeafPool.FALLING;
            pool.vy[i] = air[1] * 0.8F;
            return;
        }
        float exposure = exposed ? 1.0F : 0.15F;
        pool.vx[i] += (air[0] * exposure - pool.vx[i]) * 0.05F;
        pool.vz[i] += (air[2] * exposure - pool.vz[i]) * 0.05F;
        pool.vx[i] *= GROUND_FRICTION;
        pool.vz[i] *= GROUND_FRICTION;

        double nx = x + pool.vx[i];
        if (Double.isNaN(terrain.solidTop(nx, y + 0.08, z))) {
            x = nx;
        } else {
            pool.vx[i] *= -0.2F;
        }
        double nz = z + pool.vz[i];
        if (Double.isNaN(terrain.solidTop(x, y + 0.08, nz))) {
            z = nz;
        } else {
            pool.vz[i] *= -0.2F;
        }
        double ground = groundAt(x, y, z);
        if (Double.isNaN(ground) || ground < y - 0.3) {
            pool.x[i] = x;
            pool.z[i] = z;
            pool.state[i] = LeafPool.FALLING;
            pool.vy[i] = 0.0F;
            return;
        }
        if (ground > y + 0.26) {
            // A pile or a step in the way: the leaf stops against it.
            x = pool.x[i];
            z = pool.z[i];
            pool.vx[i] *= -0.2F;
            pool.vz[i] *= -0.2F;
            ground = pool.ground[i];
        }
        pool.x[i] = x;
        pool.y[i] = ground + 0.006;
        pool.z[i] = z;
        pool.ground[i] = ground;

        float speed = Mth.sqrt(pool.vx[i] * pool.vx[i] + pool.vz[i] * pool.vz[i]);
        pool.spinYaw[i] = pool.spinYaw[i] * 0.85F + (random.next() - 0.5F) * speed * 0.8F;
        if (speed > 0.05F && random.next() < speed * 0.6F) {
            pool.spinRoll[i] += (random.next() < 0.5F ? -1.0F : 1.0F) * (0.6F + random.next() * 0.5F);
        }
        pool.spinRoll[i] *= 0.75F;
        pool.yaw[i] += pool.spinYaw[i];
        pool.roll[i] += pool.spinRoll[i] + wrapHalfTurn(-pool.roll[i]) * 0.2F;
        pool.pitch[i] += wrapHalfTurn(-pool.pitch[i]) * 0.2F + (random.next() - 0.5F) * speed * 0.3F;
        if (((i + tick) & 3) == 0) {
            pool.light[i] = terrain.lightAt(x, ground + 0.1, z);
        }
        if (speed < 0.006F && Math.abs(pool.spinRoll[i]) < 0.02F) {
            pool.state[i] = LeafPool.FALLING;
            land(i, ground);
        }
    }

    /** The surface a sliding leaf rests on at (x, z) near height y: a litter stack or the ground. */
    private double groundAt(double x, double y, double z) {
        int cx = LitterField.cell(x);
        int cz = LitterField.cell(z);
        LitterChunk chunk = field.chunkAtCell(cx, cz);
        if (chunk != null) {
            int c = LitterField.index(cx, cz);
            int n = chunk.count[c];
            if (n > 0 && chunk.base[c] >= y - 0.6 && chunk.base[c] <= y + 0.3) {
                return chunk.base[c] + n * LAYER;
            }
        }
        return terrain.groundBelow(x, y + 0.25, z, 1);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Floating
    // ------------------------------------------------------------------------------------------------------------

    private void startFloating(int i, double surface) {
        pool.state[i] = LeafPool.FLOATING;
        pool.y[i] = surface + 0.015;
        pool.vx[i] *= 0.3F;
        pool.vy[i] = 0.0F;
        pool.vz[i] *= 0.3F;
        pool.spinYaw[i] = (random.next() - 0.5F) * 0.03F;
        pool.auxA[i] = 0.0F;
        pool.auxB[i] = 0.0F;
        pool.age[i] = 0;
        pool.life[i] = 20 * 60 * 3 + (int) (random.next() * 20 * 60 * 2);
    }

    private void tickFloating(int i) {
        if (++pool.age[i] >= pool.life[i]) {
            startDying(i);
            return;
        }
        double x = pool.x[i];
        double z = pool.z[i];
        BlockPos.MutableBlockPos cursor = terrain.cursor();
        cursor.set(Mth.floor(x), Mth.floor(pool.y[i] - 0.05), Mth.floor(z));
        FluidState fluid = level.getFluidState(cursor);
        if (!fluid.is(FluidTags.WATER)) {
            pool.state[i] = LeafPool.FALLING;
            return;
        }
        double surface = cursor.getY() + fluid.getHeight(level, cursor);
        if (((i + tick) % 10) == 0) {
            Vec3 flow = fluid.getFlow(level, cursor);
            pool.auxA[i] = (float) flow.x * 0.06F;
            pool.auxB[i] = (float) flow.z * 0.06F;
            pool.light[i] = terrain.lightAt(x, surface + 0.1, z);
        }
        wind.sample(x, surface, z, air);
        pool.vx[i] += (pool.auxA[i] + air[0] * 0.25F - pool.vx[i]) * 0.08F;
        pool.vz[i] += (pool.auxB[i] + air[2] * 0.25F - pool.vz[i]) * 0.08F;
        double y = surface + 0.015;
        double nx = x + pool.vx[i];
        if (Double.isNaN(terrain.solidTop(nx, y, z))) {
            x = nx;
        } else {
            pool.vx[i] *= -0.5F;
        }
        double nz = z + pool.vz[i];
        if (Double.isNaN(terrain.solidTop(x, y, nz))) {
            z = nz;
        } else {
            pool.vz[i] *= -0.5F;
        }
        pool.x[i] = x;
        pool.y[i] = y;
        pool.z[i] = z;
        pool.spinYaw[i] = pool.spinYaw[i] * 0.98F + (random.next() - 0.5F) * 0.004F;
        pool.yaw[i] += pool.spinYaw[i];
        float bob = pool.age[i] * 0.12F + i;
        pool.pitch[i] = 0.06F * Mth.sin(bob);
        pool.roll[i] = 0.06F * Mth.cos(bob * 0.8F);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Wind on the litter
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Gusts and squalls pick single leaves off exposed litter: random cells around the camera are probed, so the cost
     * is fixed and the rate follows how much litter there is and how hard the wind blows there.
     */
    private void windOnLitter() {
        if (pool.free() < 64) {
            return;
        }
        float radius = settings.spawnRadius;
        float wetness = 1.0F - 0.75F * wind.rain();
        for (int s = 0; s < WIND_SAMPLES; s++) {
            float angle = random.next() * Mth.TWO_PI;
            float distance = radius * Mth.sqrt(random.next());
            double x = cameraX + Mth.cos(angle) * distance;
            double z = cameraZ + Mth.sin(angle) * distance;
            int cx = LitterField.cell(x);
            int cz = LitterField.cell(z);
            double top = field.top(cx, cz);
            if (Double.isNaN(top)) {
                continue;
            }
            float speed = wind.speed(x, top, z);
            float excess = speed - LIFT_THRESHOLD;
            if (excess <= 0.0F || random.next() >= excess * LIFT_SCALE * wetness || !terrain.canSeeSky(x, top + 0.3, z)) {
                continue;
            }
            float push = speed * (1.2F + random.next());
            boolean hop = random.next() < 0.35F;
            int i = release(cx, cz, hop ? LeafPool.FALLING : LeafPool.SLIDING, wind.dirX() * push,
                    hop ? 0.03F + random.next() * 0.05F : 0.0F, wind.dirZ() * push);
            if (i >= 0) {
                pool.spinYaw[i] = (random.next() - 0.5F) * 0.4F;
                pool.flags[i] |= LeafPool.FLAG_EXPOSED;
            }
        }
    }

    /** Whirlwinds suck leaves off the ground under them; the wind field then spins them up the column. */
    private void whirlwindsOnLitter() {
        for (Wind.Whirlwind whirlwind : wind.whirlwinds()) {
            float intensity = whirlwind.intensity();
            if (tick % 20 == 0) {
                listener.onWhirl(whirlwind.x, whirlwind.y, whirlwind.z, intensity);
            }
            int lifts = Math.round(4.0F * intensity);
            for (int attempt = 0; attempt < lifts * 3 && lifts > 0; attempt++) {
                float angle = random.next() * Mth.TWO_PI;
                float distance = whirlwind.radius * 1.6F * Mth.sqrt(random.next());
                int cx = LitterField.cell(whirlwind.x + Mth.cos(angle) * distance);
                int cz = LitterField.cell(whirlwind.z + Mth.sin(angle) * distance);
                double top = field.top(cx, cz);
                if (Double.isNaN(top) || Math.abs(top - whirlwind.y) > 1.5) {
                    continue;
                }
                float tangential = whirlwind.strength * 0.4F * whirlwind.spin;
                int i = release(cx, cz, LeafPool.FALLING, -Mth.sin(angle) * tangential, 0.08F + random.next() * 0.08F,
                        Mth.cos(angle) * tangential);
                if (i < 0) {
                    break;
                }
                pool.spinPitch[i] = (random.next() - 0.5F) * 0.6F;
                lifts--;
            }
        }
    }

    /**
     * A leaf sliding down a pile: over a drop-off it always tumbles off as a particle; on a slope some leaves are
     * released too, so a slumping pile is seen trickling down instead of just changing shape.
     */
    private boolean spill(int fromX, int fromZ, int toX, int toZ, boolean edge) {
        if (!edge && (cascadeBudget <= 0 || random.next() > 0.12F)) {
            return false;
        }
        float speed = 0.04F + random.next() * 0.04F;
        int i = release(fromX, fromZ, LeafPool.SLIDING, (toX - fromX) * speed, 0.0F, (toZ - fromZ) * speed);
        if (i < 0) {
            return false;
        }
        cascadeBudget--;
        pool.spinRoll[i] = (random.next() - 0.5F) * 1.2F;
        return true;
    }

    /**
     * Moves the top leaf of a cell to another cell without making it a particle. Fails against walls, over drop-offs
     * and into unloaded chunks.
     */
    private boolean shove(int fromX, int fromZ, int toX, int toZ) {
        if (fromX == toX && fromZ == toZ) {
            return false;
        }
        double fromTop = field.top(fromX, fromZ);
        if (Double.isNaN(fromTop)) {
            return false;
        }
        double target = field.neighborTop(toX, toZ, fromTop + 0.05);
        if (Double.isInfinite(target) || target > fromTop + 0.3 || target < fromTop - 2.0) {
            return false;
        }
        if (field.count(toX, toZ) > 0) {
            target = Double.NaN;
        }
        return field.move(fromX, fromZ, toX, toZ, target);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Disturbances
    // ------------------------------------------------------------------------------------------------------------

    /** Adds velocity to a moving leaf; a sliding leaf with enough lift takes off. */
    private void impulse(int i, float vx, float vy, float vz) {
        byte state = pool.state[i];
        if (state == LeafPool.DYING || state == LeafPool.FREE) {
            return;
        }
        if (state == LeafPool.FLOATING || state == LeafPool.SLIDING && vy > 0.02F) {
            pool.state[i] = LeafPool.FALLING;
            pool.vy[i] = 0.0F;
        }
        pool.flags[i] &= (byte) ~LeafPool.FLAG_SETTLING;
        pool.age[i] = 0;
        pool.vx[i] += vx;
        pool.vy[i] += pool.state[i] == LeafPool.SLIDING ? 0.0F : vy;
        pool.vz[i] += vz;
        clampSpeed(i);
    }

    /**
     * An entity moved by {@code (moveX, moveY, moveZ)} this tick with its feet at {@code (x, y, z)}.
     *
     * <p>Like a body wading through real leaves: everything above the feet inside its outline is pushed away. Most of
     * it is shoved aside and ahead (a trench with banks forms behind it), some leaves skid over the ground, and only
     * a few from the top fly up - more when running. Sneaking over a thin carpet leaves it untouched. Landing from a
     * jump digs a crater and splashes leaves up. Airborne leaves in the way are dragged along.
     *
     * @param landing downward speed at touchdown in blocks per tick, 0 if the entity did not just land
     * @return how many lying leaves were moved
     */
    public int disturb(double x, double y, double z, double moveX, double moveY, double moveZ, float width, float height,
            boolean sneaking, float landing) {
        float strength = settings.entityStrength;
        if (strength <= 0.0F) {
            return 0;
        }
        double horizontal = Math.sqrt(moveX * moveX + moveZ * moveZ);
        float speed = (float) Math.min(horizontal, 1.5) * strength;
        boolean landed = landing > 0.3F;
        if (speed < 0.02F && !landed) {
            return 0;
        }
        float dirX = horizontal > 1.0E-4 ? (float) (moveX / horizontal) : 0.0F;
        float dirZ = horizontal > 1.0E-4 ? (float) (moveZ / horizontal) : 0.0F;
        pushParticles(x, y, z, moveX, moveY, moveZ, dirX, dirZ, speed, width, height, landed ? landing : 0.0F);
        int moved = pushLitter(x, y, z, dirX, dirZ, speed, width, sneaking, landed ? landing * strength : 0.0F);
        if (moved > 0) {
            listener.onRustle(x, y, z, moved, wind.rain() > 0.2F);
        }
        return moved;
    }

    private void pushParticles(double x, double y, double z, double moveX, double moveY, double moveZ, float dirX, float dirZ, float speed,
            float width, float height, float landing) {
        float reach = width * 0.5F + 0.3F + speed * 0.6F + landing * 1.2F;
        grid.query(x - reach, z - reach, x + reach, z + reach, nearby);
        for (int n = 0; n < nearby.size(); n++) {
            int i = nearby.getInt(n);
            byte state = pool.state[i];
            if (state == LeafPool.FREE || state == LeafPool.DYING) {
                continue;
            }
            double dx = pool.x[i] - x;
            double dz = pool.z[i] - z;
            double distSq = dx * dx + dz * dz;
            double dy = pool.y[i] - y;
            if (distSq >= reach * reach || dy < -0.5 || dy > height + 0.3) {
                continue;
            }
            float dist = (float) Math.sqrt(distSq);
            float falloff = 1.0F - dist / reach;
            float outX = dist > 1.0E-3F ? (float) dx / dist : dirX;
            float outZ = dist > 1.0E-3F ? (float) dz / dist : dirZ;
            if (state == LeafPool.FALLING && (pool.flags[i] & LeafPool.FLAG_SETTLING) == 0) {
                float pull = 0.35F * falloff;
                pool.vx[i] += ((float) moveX - pool.vx[i]) * pull + outX * 0.04F * falloff;
                pool.vz[i] += ((float) moveZ - pool.vz[i]) * pull + outZ * 0.04F * falloff;
                pool.vy[i] += (float) moveY * 0.2F * falloff + 0.01F * falloff * random.next() + landing * 0.3F * falloff;
                pool.spinPitch[i] += (random.next() - 0.5F) * speed * falloff;
                pool.spinRoll[i] += (random.next() - 0.5F) * speed * falloff;
                clampSpeed(i);
            } else {
                float push = (speed + landing * 0.4F) * falloff * (0.6F + 0.8F * random.next());
                float lift = random.next() < speed + landing ? push * (0.2F + 0.5F * random.next()) : 0.0F;
                impulse(i, (dirX * 0.7F + outX * 0.6F) * push, lift, (dirZ * 0.7F + outZ * 0.6F) * push);
                pool.spinYaw[i] += (random.next() - 0.5F) * push * 4.0F;
            }
        }
    }

    private int pushLitter(double x, double y, double z, float dirX, float dirZ, float speed, float width, boolean sneaking,
            float landing) {
        boolean landed = landing > 0.0F;
        float body = width * 0.5F + 0.1F;
        float reach = landed ? body + 0.3F + landing * 1.5F : body;
        int budget = Math.min(landed ? PARTICLES_PER_DISTURB * 2 : PARTICLES_PER_DISTURB, pool.free());
        boolean wet = wind.rain() > 0.2F;
        int moved = 0;
        int x0 = LitterField.cell(x - reach);
        int x1 = LitterField.cell(x + reach);
        int z0 = LitterField.cell(z - reach);
        int z1 = LitterField.cell(z + reach);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                LitterChunk chunk = field.chunkAtCell(cx, cz);
                if (chunk == null) {
                    continue;
                }
                int c = LitterField.index(cx, cz);
                int n = chunk.count[c];
                if (n == 0) {
                    continue;
                }
                double base = chunk.base[c];
                if (base > y + 0.5 || base + n * LAYER < y - 0.05) {
                    continue;
                }
                float dx = (float) ((cx + 0.5) * CELL - x);
                float dz = (float) ((cz + 0.5) * CELL - z);
                float dist = Mth.sqrt(dx * dx + dz * dz);
                if (dist > reach) {
                    continue;
                }
                // The bottom leaves are pressed into the ground under a foot and stay; of the rest only a share is
                // moved per tick - more when running or landing hard. Walking through leaves thins them, it does not
                // sweep the ground clean.
                int keep = Math.max(Math.min(n, TRAMPLED), Mth.ceil((y - base) / LAYER));
                int loose = n - keep;
                if (loose <= 0 || !landed && sneaking && n <= 3) {
                    continue;
                }
                float falloff = 1.0F - dist / reach;
                float share = landed ? Math.min(0.75F, landing * 1.2F * (0.4F + falloff))
                        : Math.min(0.7F, 0.12F + speed * 1.6F) * (sneaking ? 0.3F : 1.0F);
                int movable = stochasticRound(loose * share);
                float outX = dist > 1.0E-3F ? dx / dist : dirX;
                float outZ = dist > 1.0E-3F ? dz / dist : dirZ;
                for (int k = 0; k < movable; k++) {
                    float flyChance;
                    if (landed) {
                        flyChance = 0.45F * falloff + 0.1F;
                    } else {
                        flyChance = Math.min(0.6F, 0.06F + speed * 1.4F) * (k < 2 ? 1.0F : 0.25F) * (sneaking ? 0.2F : 1.0F);
                    }
                    if (wet) {
                        flyChance *= 0.5F;
                    }
                    float roll = random.next();
                    if (budget > 0 && roll < flyChance) {
                        float push = landed ? landing * (0.2F + 0.3F * random.next()) : speed * (0.6F + 0.8F * random.next());
                        float up = landed ? landing * (0.35F + 0.55F * random.next()) * (0.5F + falloff)
                                : speed * (0.15F + 0.45F * random.next()) + 0.02F;
                        // Kicked leaves follow the walker; splashed ones go up more than out and land close by.
                        float hx = landed ? outX * push * random.next() : (dirX * 0.9F + outX * 0.3F) * push;
                        float hz = landed ? outZ * push * random.next() : (dirZ * 0.9F + outZ * 0.3F) * push;
                        int i = release(cx, cz, LeafPool.FALLING, hx, up, hz);
                        if (i >= 0) {
                            budget--;
                            spinUp(i, push + up);
                            moved++;
                            continue;
                        }
                    } else if (budget > 0 && roll < flyChance + (k < 3 ? 0.18F : 0.04F)) {
                        float push = (speed + landing * 0.4F) * (0.8F + 0.8F * random.next());
                        int i = release(cx, cz, LeafPool.SLIDING, (dirX * 0.8F + outX * 0.7F) * push, 0.0F,
                                (dirZ * 0.8F + outZ * 0.7F) * push);
                        if (i >= 0) {
                            budget--;
                            pool.spinYaw[i] = (random.next() - 0.5F) * push * 3.0F;
                            moved++;
                            continue;
                        }
                    }
                    // Shoved a short way: mostly outwards and along the motion, often just into the next cell.
                    float distance = 0.2F + random.next() * (landed ? 0.6F : 0.45F);
                    float ahead = landed ? 0.0F : speed * 1.2F * Math.max(0.0F, outX * dirX + outZ * dirZ);
                    float spread = (random.next() - 0.5F) * 0.4F;
                    float px = outX * distance + dirX * ahead - outZ * spread;
                    float pz = outZ * distance + dirZ * ahead + outX * spread;
                    if (shove(cx, cz, LitterField.cell((cx + 0.5) * CELL + px), LitterField.cell((cz + 0.5) * CELL + pz))) {
                        moved++;
                    }
                }
                queueNeighbors(cx, cz);
            }
        }
        return moved;
    }

    /** Rounds up with a probability equal to the fraction, so small shares still move a leaf now and then. */
    private int stochasticRound(float value) {
        int whole = (int) value;
        return whole + (random.next() < value - whole ? 1 : 0);
    }

    private void spinUp(int i, float energy) {
        pool.spinYaw[i] = (random.next() - 0.5F) * energy * 4.0F;
        pool.spinPitch[i] = (random.next() - 0.5F) * energy * 6.0F;
        pool.spinRoll[i] = (random.next() - 0.5F) * energy * 6.0F;
    }

    /**
     * Rakes the litter around a point into a pile at that point: leaves in a ring around it are pulled a bit closer,
     * a few of them skid over the ground visibly. Repeated strokes heap them up until the pile is as steep as leaves
     * allow.
     *
     * @return how many leaves were moved
     */
    public int rake(double x, double y, double z, float radius) {
        int moved = 0;
        int budget = Math.min(10, pool.free());
        int x0 = LitterField.cell(x - radius);
        int x1 = LitterField.cell(x + radius);
        int z0 = LitterField.cell(z - radius);
        int z1 = LitterField.cell(z + radius);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                LitterChunk chunk = field.chunkAtCell(cx, cz);
                if (chunk == null) {
                    continue;
                }
                int c = LitterField.index(cx, cz);
                int n = chunk.count[c];
                if (n == 0 || chunk.base[c] < y - 1.5 || chunk.base[c] > y + 1.0) {
                    continue;
                }
                float dx = (float) ((cx + 0.5) * CELL - x);
                float dz = (float) ((cz + 0.5) * CELL - z);
                float dist = Mth.sqrt(dx * dx + dz * dz);
                if (dist > radius || dist < 0.4F) {
                    continue;
                }
                float inX = -dx / dist;
                float inZ = -dz / dist;
                float step = Math.min(dist - 0.2F, 0.6F + random.next() * 0.4F);
                int take = Math.max(1, Mth.ceil(n * 0.4F));
                for (int k = 0; k < take; k++) {
                    if (budget > 0 && random.next() < 0.15F) {
                        float push = 0.08F + random.next() * 0.08F;
                        int i = release(cx, cz, LeafPool.SLIDING, inX * push, 0.0F, inZ * push);
                        if (i >= 0) {
                            budget--;
                            moved++;
                            continue;
                        }
                    }
                    float spread = (random.next() - 0.5F) * 0.5F;
                    int tx = LitterField.cell((cx + 0.5) * CELL + inX * step - inZ * spread);
                    int tz = LitterField.cell((cz + 0.5) * CELL + inZ * step + inX * spread);
                    if (shove(cx, cz, tx, tz)) {
                        moved++;
                    }
                }
            }
        }
        return moved;
    }

    /**
     * Scoops leaves from the litter around a point into the arms: the top leaves of the nearest stacks, a few of
     * them seen flying to {@code hand}.
     *
     * @return how many leaves were picked up
     */
    public int scoop(double x, double y, double z, float radius, int max, double handX, double handY, double handZ, Armful armful) {
        int taken = 0;
        int flying = Math.min(8, pool.free());
        int centerX = LitterField.cell(x);
        int centerZ = LitterField.cell(z);
        int reach = Mth.ceil(radius / CELL);
        // Rings from the center outwards, so the scoop digs a hollow where you reach in.
        for (int ring = 0; ring <= reach && taken < max && armful.room() > 0; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int cx = centerX + dx;
                    int cz = centerZ + dz;
                    LitterChunk chunk = field.chunkAtCell(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    int c = LitterField.index(cx, cz);
                    if (chunk.count[c] == 0 || chunk.base[c] < y - 1.5 || chunk.base[c] > y + 1.0) {
                        continue;
                    }
                    int grab = Math.min(Math.min(chunk.count[c], Math.max(1, (max - taken) / 4)), armful.room());
                    for (int k = 0; k < grab; k++) {
                        if (flying > 0) {
                            int i = release(cx, cz, LeafPool.DYING, 0.0F, 0.0F, 0.0F);
                            if (i >= 0) {
                                flying--;
                                int ticks = 7;
                                pool.life[i] = ticks;
                                pool.vx[i] = (float) (handX - pool.x[i]) / ticks;
                                pool.vy[i] = (float) (handY - pool.y[i]) / ticks;
                                pool.vz[i] = (float) (handZ - pool.z[i]) / ticks;
                                pool.spinYaw[i] = (random.next() - 0.5F) * 0.8F;
                                armful.add(pool.baseColor[i], pool.shape[i]);
                                taken++;
                                continue;
                            }
                        }
                        field.take(cx, cz, pose);
                        armful.add(pose.baseColor, pose.shape);
                        taken++;
                    }
                    queueNeighbors(cx, cz);
                    if (taken >= max || armful.room() == 0) {
                        return taken;
                    }
                }
            }
        }
        return taken;
    }

    /**
     * Pours leaves from the arms: they leave {@code (x, y, z)} with a velocity along {@code (dirX, dirY, dirZ)} and a
     * little scatter, then fall, flutter, land and pile up like any other leaf.
     *
     * @return how many leaves were poured
     */
    public int pour(double x, double y, double z, float dirX, float dirY, float dirZ, int count, Armful armful) {
        int poured = 0;
        while (poured < count && armful.count() > 0 && pool.free() > 0) {
            int sample = armful.take(random.next());
            LeafShape shape = LeafShape.byId(armful.shape(sample));
            int base = armful.color(sample);
            long hash = (long) (random.next() * Integer.MAX_VALUE) << 20 ^ (long) (random.next() * Integer.MAX_VALUE);
            int color = LeafPalette.vary(base, hash, Math.min(1.0F, settings.autumnColors + 0.3F), shape);
            int sprite = shape.firstSprite + Math.min(shape.variants - 1, (int) (random.next() * shape.variants));
            float size = LeafShape.BASE_SIZE * shape.size * settings.leafSize * (0.8F + random.next() * 0.45F);
            float speed = 0.16F + random.next() * 0.08F;
            float jitter = 0.05F;
            spawn(x + (random.next() - 0.5F) * 0.2, y + (random.next() - 0.5F) * 0.1, z + (random.next() - 0.5F) * 0.2,
                    dirX * speed + (random.next() - 0.5F) * jitter, dirY * speed + (random.next() - 0.5F) * jitter,
                    dirZ * speed + (random.next() - 0.5F) * jitter, color, base, shape, sprite, size, false);
            poured++;
        }
        return poured;
    }

    /** Leaves of the litter in a radius around a point, at about that height. */
    public int litterAround(double x, double y, double z, float radius) {
        int sum = 0;
        for (int cx = LitterField.cell(x - radius); cx <= LitterField.cell(x + radius); cx++) {
            for (int cz = LitterField.cell(z - radius); cz <= LitterField.cell(z + radius); cz++) {
                LitterChunk chunk = field.chunkAtCell(cx, cz);
                if (chunk != null) {
                    int c = LitterField.index(cx, cz);
                    if (chunk.count[c] > 0 && Math.abs(chunk.base[c] - y) < 1.5) {
                        sum += chunk.count[c];
                    }
                }
            }
        }
        return sum;
    }

    /**
     * An explosion: a spherical shock front that expands over a few ticks, throws leaves outwards and up and clears
     * the ground; what does not fly lands farther out. Wind charges are weaker but swirl the leaves.
     */
    public void explode(double x, double y, double z, float power, boolean windCharge) {
        float strength = settings.explosionStrength;
        if (strength <= 0.0F || power <= 0.0F) {
            return;
        }
        float swirl = random.next() < 0.5F ? -1.0F : 1.0F;
        if (windCharge) {
            shockwaves.add(new Shockwave(x, y, z, Math.min(8.0F, 2.0F + power * 2.2F), 0.8F * strength, 0.55F, 0.9F, 0.45F * swirl,
                    Math.round(260 * strength)));
        } else {
            shockwaves.add(new Shockwave(x, y, z, Math.min(24.0F, power * 3.0F), Math.min(2.0F, 0.9F + 0.15F * power) * strength,
                    0.45F, 2.5F, 0.08F * swirl, Math.round(Math.min(1500.0F, 200.0F * power) * strength)));
        }
    }

    private void processShockwaves() {
        for (Iterator<Shockwave> it = shockwaves.iterator(); it.hasNext(); ) {
            Shockwave wave = it.next();
            float inner = wave.front;
            float outer = Math.min(wave.radius, inner + wave.speed);
            grid.query(wave.x - outer, wave.z - outer, wave.x + outer, wave.z + outer, nearby);
            for (int n = 0; n < nearby.size(); n++) {
                int i = nearby.getInt(n);
                double dx = pool.x[i] - wave.x;
                double dy = pool.y[i] - wave.y;
                double dz = pool.z[i] - wave.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq >= outer * outer || inner > 0.0F && distSq < inner * inner) {
                    continue;
                }
                blast(wave, i, dx, dy, dz, (float) Math.sqrt(distSq));
            }
            blastLitter(wave, inner, outer);
            wave.front = outer;
            if (outer >= wave.radius) {
                it.remove();
            }
        }
    }

    private void blast(Shockwave wave, int i, double dx, double dy, double dz, float dist) {
        float falloff = 1.0F - dist / wave.radius;
        float magnitude = wave.strength * falloff * Mth.sqrt(falloff) * (0.7F + 0.6F * random.next());
        float nx;
        float ny;
        float nz;
        if (dist > 0.05F) {
            nx = (float) dx / dist;
            ny = (float) dy / dist;
            nz = (float) dz / dist;
        } else {
            float angle = random.next() * Mth.TWO_PI;
            nx = Mth.cos(angle) * 0.5F;
            ny = 0.8F;
            nz = Mth.sin(angle) * 0.5F;
        }
        float lift = wave.lift * magnitude * (0.6F + 0.8F * random.next());
        float vy = ny * magnitude * 0.6F + lift;
        if (pool.state[i] == LeafPool.SLIDING) {
            pool.state[i] = LeafPool.FALLING;
        }
        impulse(i, (nx - nz * wave.swirl) * magnitude, vy, (nz + nx * wave.swirl) * magnitude);
        pool.spinYaw[i] += (random.next() - 0.5F) * magnitude * 2.0F;
        pool.spinPitch[i] += (random.next() - 0.5F) * magnitude * 3.0F;
        pool.spinRoll[i] += (random.next() - 0.5F) * magnitude * 3.0F;
    }

    /** The shock front clears the litter it passes: leaves fly while the budget lasts, the rest lands farther out. */
    private void blastLitter(Shockwave wave, float inner, float outer) {
        for (int cx = LitterField.cell(wave.x - outer); cx <= LitterField.cell(wave.x + outer); cx++) {
            for (int cz = LitterField.cell(wave.z - outer); cz <= LitterField.cell(wave.z + outer); cz++) {
                LitterChunk chunk = field.chunkAtCell(cx, cz);
                if (chunk == null) {
                    continue;
                }
                int c = LitterField.index(cx, cz);
                int n = chunk.count[c];
                if (n == 0) {
                    continue;
                }
                double dx = (cx + 0.5) * CELL - wave.x;
                double dy = chunk.base[c] + n * LAYER - wave.y;
                double dz = (cz + 0.5) * CELL - wave.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq >= outer * outer || inner > 0.0F && distSq < inner * inner) {
                    continue;
                }
                float dist = (float) Math.sqrt(distSq);
                float falloff = 1.0F - dist / wave.radius;
                int removed = Math.min(n, stochasticRound(n * Math.min(0.85F, Mth.sqrt(falloff) * 0.95F)));
                float flat = (float) Math.sqrt(dx * dx + dz * dz);
                float outX = flat > 1.0E-3F ? (float) dx / flat : 1.0F;
                float outZ = flat > 1.0E-3F ? (float) dz / flat : 0.0F;
                for (int k = 0; k < removed; k++) {
                    if (wave.budget > 0 && pool.free() > 0) {
                        int i = release(cx, cz, LeafPool.FALLING, 0.0F, 0.0F, 0.0F);
                        if (i >= 0) {
                            wave.budget--;
                            blast(wave, i, pool.x[i] - wave.x, pool.y[i] - wave.y, pool.z[i] - wave.z, Math.max(0.06F, dist));
                            // Many leaves only hop: little sideways speed, so they come down near where they lay.
                            float keepOut = random.next() < 0.35F ? 0.15F : 0.4F + 0.6F * random.next();
                            pool.vx[i] *= keepOut;
                            pool.vz[i] *= keepOut;
                            continue;
                        }
                    }
                    float throwDistance = (wave.radius - dist) * (0.1F + 0.7F * random.next()) + 0.25F;
                    float spread = (random.next() - 0.5F) * 0.8F;
                    int tx = LitterField.cell((cx + 0.5) * CELL + outX * throwDistance - outZ * spread);
                    int tz = LitterField.cell((cz + 0.5) * CELL + outZ * throwDistance + outX * spread);
                    if (!shove(cx, cz, tx, tz)) {
                        break;
                    }
                }
                queueNeighbors(cx, cz);
            }
        }
    }

    /** The walls of a dent may now be too steep. */
    private void queueNeighbors(int cx, int cz) {
        field.queueRelax(cx + 1, cz);
        field.queueRelax(cx - 1, cz);
        field.queueRelax(cx, cz + 1);
        field.queueRelax(cx, cz - 1);
    }

    /**
     * A block changed. Moving leaves that were settling on it start over; litter lying on a removed block falls to the
     * ground below (some leaves visibly), litter inside a placed block ends up on top of it.
     */
    public void blockChanged(BlockPos pos) {
        double minX = pos.getX() - 0.05;
        double minZ = pos.getZ() - 0.05;
        double maxX = pos.getX() + 1.05;
        double maxZ = pos.getZ() + 1.05;
        grid.query(minX, minZ, maxX, maxZ, nearby);
        for (int n = 0; n < nearby.size(); n++) {
            int i = nearby.getInt(n);
            byte state = pool.state[i];
            if (state == LeafPool.SLIDING || state == LeafPool.FALLING && (pool.flags[i] & LeafPool.FLAG_SETTLING) != 0) {
                double x = pool.x[i];
                double z = pool.z[i];
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    pool.flags[i] &= (byte) ~LeafPool.FLAG_SETTLING;
                    pool.state[i] = LeafPool.FALLING;
                }
            }
        }
        if (level == null) {
            return;
        }
        int baseX = pos.getX() * 4;
        int baseZ = pos.getZ() * 4;
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                int cx = baseX + a;
                int cz = baseZ + b;
                LitterChunk chunk = field.chunkAtCell(cx, cz);
                if (chunk == null) {
                    continue;
                }
                int c = LitterField.index(cx, cz);
                if (chunk.count[c] == 0) {
                    continue;
                }
                double base = chunk.base[c];
                if (base < pos.getY() - 0.05 || base > pos.getY() + 1.05) {
                    continue;
                }
                resupport(cx, cz, base);
            }
        }
    }

    private void resupport(int cx, int cz, double base) {
        double x = (cx + 0.5) * CELL;
        double z = (cz + 0.5) * CELL;
        double inside = terrain.solidTop(x, base + 0.002, z);
        if (!Double.isNaN(inside)) {
            double raised = inside;
            for (int k = 0; k < 3; k++) {
                double next = terrain.solidTop(x, raised + 0.002, z);
                if (Double.isNaN(next)) {
                    break;
                }
                raised = next;
            }
            field.setBase(cx, cz, raised);
            field.queueRelax(cx, cz);
            return;
        }
        if (!Double.isNaN(terrain.solidTop(x, base - 0.01, z))) {
            return;
        }
        // Support gone: a few leaves tumble down visibly, the stack drops to the ground below.
        for (int k = 0; k < 6 && field.count(cx, cz) > 0; k++) {
            release(cx, cz, LeafPool.FALLING, (random.next() - 0.5F) * 0.03F, 0.0F, (random.next() - 0.5F) * 0.03F);
        }
        double ground = terrain.groundBelow(x, base - 0.01, z, 8);
        if (Double.isNaN(ground)) {
            for (int k = 0; k < 24 && field.count(cx, cz) > 0; k++) {
                if (release(cx, cz, LeafPool.FALLING, (random.next() - 0.5F) * 0.05F, 0.0F, (random.next() - 0.5F) * 0.05F) < 0) {
                    break;
                }
            }
            while (field.count(cx, cz) > 0) {
                field.take(cx, cz, null);
            }
            return;
        }
        if (field.count(cx, cz) > 0) {
            field.setBase(cx, cz, ground);
            field.queueRelax(cx, cz);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------

    private void clampSpeed(int i) {
        float speedSq = pool.vx[i] * pool.vx[i] + pool.vy[i] * pool.vy[i] + pool.vz[i] * pool.vz[i];
        if (speedSq > MAX_SPEED * MAX_SPEED) {
            float scale = MAX_SPEED / Mth.sqrt(speedSq);
            pool.vx[i] *= scale;
            pool.vy[i] *= scale;
            pool.vz[i] *= scale;
        }
    }

    /** Wraps an angle difference into [-pi/2, pi/2): a leaf flipped by half a turn looks the same. */
    private static float wrapHalfTurn(float angle) {
        return angle - Mth.PI * Math.round(angle / Mth.PI);
    }

    private static final class Shockwave {
        final double x;
        final double y;
        final double z;
        final float radius;
        final float strength;
        final float lift;
        final float speed;
        final float swirl;
        int budget;
        float front;

        Shockwave(double x, double y, double z, float radius, float strength, float lift, float speed, float swirl, int budget) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.strength = strength;
            this.lift = lift;
            this.speed = speed;
            this.swirl = swirl;
            this.budget = budget;
        }
    }
}
