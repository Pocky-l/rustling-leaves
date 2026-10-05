package com.pockyl.rustling_leaves.sim;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
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
 * <p>Performance model: a leaf at rest costs a counter increment and an occasional support check, and is drawn from a
 * cached mesh; only leaves in motion get aerodynamics, collisions and per-frame geometry. Everything that pushes leaves
 * (entities, explosions, block changes) finds them through a {@link SpatialGrid} instead of scanning the pool.
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
    private static final int SETTLE_TICKS = 6;
    private static final float WIND_LIFT_THRESHOLD = 0.045F;
    private static final float WIND_LIFT_CHANCE = 0.25F;
    private static final float KICK_THRESHOLD = 0.018F;
    private static final byte FLAG_SETTLING = 4;

    private final LeafSettings settings;
    private final LeafPool pool;
    private final LeafListener listener;
    private final SpatialGrid grid;
    private final ShapeCache shapes = new ShapeCache();
    private final Wind wind = new Wind();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final IntArrayList nearby = new IntArrayList();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos lightCursor = new BlockPos.MutableBlockPos();
    /** Resting leaves in the order they landed, as {@code leaf << 32 | restTick}; stale entries are skipped. */
    private final long[] restQueue;
    private int queueHead;
    private int queueSize;
    private long seed = 0x2545F4914F6CDD1DL;
    private int tick;
    private Level level;

    public LeafSimulation(LeafSettings settings, int capacity, LeafListener listener) {
        this.settings = settings;
        this.listener = listener;
        pool = new LeafPool(capacity);
        grid = new SpatialGrid(capacity);
        restQueue = new long[Math.max(16, capacity * 2)];
    }

    public LeafPool pool() {
        return pool;
    }

    public Wind wind() {
        return wind;
    }

    public LeafSettings settings() {
        return settings;
    }

    /** Runs one full tick: {@link #beginTick} followed by {@link #finishTick}. */
    public void tick(Level level, double cameraX, double cameraZ) {
        beginTick(level);
        finishTick(cameraX, cameraZ);
    }

    /** Updates the wind and indexes the leaves; entity disturbances go between this and {@link #finishTick}. */
    public void beginTick(Level level) {
        this.level = level;
        tick++;
        wind.update(level, tick, settings.windStrength);
        grid.rebuild(pool);
    }

    /** Applies explosions and moves every leaf. */
    public void finishTick(double cameraX, double cameraZ) {
        processShockwaves();
        relieveCapacityPressure();
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
                remove(i);
                continue;
            }
            switch (state) {
                case LeafPool.FALLING -> tickFalling(i);
                case LeafPool.RESTING -> tickResting(i);
                case LeafPool.FLOATING -> tickFloating(i);
                case LeafPool.DYING -> {
                    if (--pool.life[i] <= 0) {
                        remove(i);
                    }
                }
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Spawning and removal
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Adds a falling leaf. When the pool is full the leaf that has been resting the longest makes room.
     *
     * @return the leaf index, or -1 if there was no room
     */
    public int spawn(double x, double y, double z, float vx, float vy, float vz, int color, int sprite, float size) {
        if (pool.count() >= pool.capacity) {
            int oldest = pollOldestResting();
            if (oldest < 0) {
                return -1;
            }
            remove(oldest);
        }
        int i = pool.allocate();
        if (i < 0) {
            return -1;
        }
        pool.x[i] = x;
        pool.y[i] = y;
        pool.z[i] = z;
        pool.vx[i] = vx;
        pool.vy[i] = vy;
        pool.vz[i] = vz;
        pool.yaw[i] = random() * Mth.TWO_PI;
        pool.pitch[i] = (random() - 0.5F) * 1.2F;
        pool.roll[i] = (random() - 0.5F) * 1.2F;
        pool.savePrevious(i);
        pool.spinYaw[i] = (random() - 0.5F) * 0.12F;
        pool.spinPitch[i] = 0.0F;
        pool.spinRoll[i] = 0.0F;
        pool.phase[i] = random() * Mth.TWO_PI;
        pool.freq[i] = 0.1F + random() * 0.12F;
        pool.sway[i] = 0.03F + random() * 0.04F;
        pool.swayDir[i] = random() * Mth.TWO_PI;
        pool.size[i] = size;
        pool.flowX[i] = 0.0F;
        pool.flowZ[i] = 0.0F;
        pool.color[i] = color;
        pool.sprite[i] = (byte) sprite;
        pool.light[i] = level != null ? lightAt(x, y, z) : 0xF000F0;
        pool.age[i] = 0;
        pool.life[i] = 0;
        pool.flags[i] = 0;
        pool.state[i] = LeafPool.FALLING;
        return i;
    }

    /** Removes every leaf, notifying the listener about resting ones. */
    public void clear() {
        for (int i = 0, highWater = pool.highWater(); i < highWater; i++) {
            if (pool.state[i] != LeafPool.FREE) {
                remove(i);
            }
        }
        shockwaves.clear();
        queueSize = 0;
    }

    private void remove(int i) {
        if (pool.state[i] == LeafPool.RESTING) {
            listener.onUnrest(i);
        }
        pool.release(i);
    }

    private void startDying(int i) {
        if (pool.state[i] == LeafPool.RESTING) {
            listener.onUnrest(i);
        }
        pool.state[i] = LeafPool.DYING;
        pool.life[i] = DYING_TICKS;
    }

    /** Keeps some headroom below the cap by letting the oldest resting leaves fade instead of popping out on spawn. */
    private void relieveCapacityPressure() {
        int excess = pool.count() - (int) (pool.capacity * 0.92F);
        int budget = Math.min(excess, Math.max(1, pool.capacity / 400));
        while (budget-- > 0) {
            int oldest = pollOldestResting();
            if (oldest < 0) {
                break;
            }
            startDying(oldest);
        }
    }

    private void pushRest(int i) {
        if (queueSize == restQueue.length) {
            queueHead = (queueHead + 1) % restQueue.length;
            queueSize--;
        }
        restQueue[(queueHead + queueSize) % restQueue.length] = (long) i << 32 | pool.restTick[i] & 0xFFFFFFFFL;
        queueSize++;
    }

    private int pollOldestResting() {
        while (queueSize > 0) {
            long entry = restQueue[queueHead];
            queueHead = (queueHead + 1) % restQueue.length;
            queueSize--;
            int i = (int) (entry >>> 32);
            if (pool.state[i] == LeafPool.RESTING && pool.restTick[i] == (int) entry) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Per-state updates
    // ------------------------------------------------------------------------------------------------------------

    private void tickFalling(int i) {
        if (++pool.age[i] > MAX_AIR_TICKS) {
            remove(i);
            return;
        }
        if ((pool.flags[i] & FLAG_SETTLING) != 0) {
            settle(i);
            return;
        }
        double x = pool.x[i];
        double y = pool.y[i];
        double z = pool.z[i];
        cursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (!level.isLoaded(cursor)) {
            remove(i);
            return;
        }
        BlockState here = level.getBlockState(cursor);
        boolean foliage = here.is(BlockTags.LEAVES);
        if (!foliage && !here.isAir()) {
            FluidState fluid = here.getFluidState();
            if (!fluid.isEmpty()) {
                if (fluid.is(FluidTags.LAVA)) {
                    listener.onBurn(x, y, z);
                    remove(i);
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
            double top = topAt(here, x, y, z);
            if (!Double.isNaN(top)) {
                pool.y[i] = top + restOffset();
                land(i);
                return;
            }
        }

        // Aerodynamics: the leaf is dragged towards the local air velocity (wind plus its own flutter), with drag
        // that depends on whether it currently faces the air broadside or edge-on.
        float windSpeed = wind.speed(x, y, z);
        float phase = pool.phase[i] += pool.freq[i];
        float cos = Mth.cos(phase);
        float sin = Mth.sin(phase);
        float swayDir = pool.swayDir[i] += (random() - 0.5F) * 0.06F;
        float sway = pool.sway[i] * cos;
        float airX = wind.dirX() * windSpeed + Mth.cos(swayDir) * sway;
        float airZ = wind.dirZ() * windSpeed + Mth.sin(swayDir) * sway;
        float relX = airX - pool.vx[i];
        float relY = -pool.vy[i];
        float relZ = airZ - pool.vz[i];
        float rel = Mth.sqrt(relX * relX + relY * relY + relZ * relZ);
        float quadratic = (1.0F + QUADRATIC_DRAG * rel) * (foliage ? FOLIAGE_DRAG : 1.0F);
        float dragH = Math.min(0.6F, DRAG_HORIZONTAL * quadratic);
        float dragV = Math.min(0.6F, DRAG_VERTICAL * (0.55F + 0.9F * sin * sin) * quadratic);
        pool.vx[i] += relX * dragH;
        pool.vz[i] += relZ * dragH;
        pool.vy[i] += relY * dragV - GRAVITY - wind.rain() * RAIN_PUSH;
        clampSpeed(i);

        // Orientation: a springy pull towards the flutter pose (tilted into the swing), weakened while the leaf is
        // tumbling fast after a blast. Both faces of a leaf are equivalent, hence the half-turn wrap.
        float tumble = Math.min(1.0F, rel * 5.0F);
        float spring = 0.07F * (1.0F - 0.9F * tumble);
        float damping = 0.85F + 0.12F * tumble;
        float tilt = 0.65F * sin;
        float relDir = swayDir - pool.yaw[i];
        pool.spinPitch[i] = (pool.spinPitch[i] + wrapHalfTurn(tilt * Mth.cos(relDir) - pool.pitch[i]) * spring) * damping;
        pool.spinRoll[i] = (pool.spinRoll[i] + wrapHalfTurn(tilt * Mth.sin(relDir) - pool.roll[i]) * spring) * damping;
        pool.spinYaw[i] *= 0.985F;
        pool.pitch[i] += pool.spinPitch[i];
        pool.roll[i] += pool.spinRoll[i];
        pool.yaw[i] += pool.spinYaw[i];

        move(i);
        if (pool.state[i] == LeafPool.FALLING && ((i + tick) & 3) == 0) {
            pool.light[i] = lightAt(pool.x[i], pool.y[i], pool.z[i]);
        }
    }

    /** Moves a falling leaf by its velocity in sub-steps short enough not to tunnel through blocks. */
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
            double top = solidTop(x, ny, z);
            if (Double.isNaN(top)) {
                y = ny;
            } else if (sy <= 0.0F) {
                pool.x[i] = x;
                pool.y[i] = top + restOffset();
                pool.z[i] = z;
                land(i);
                return;
            } else {
                pool.vy[i] = 0.0F;
                sy = 0.0F;
            }
            double nx = x + sx;
            if (Double.isNaN(solidTop(nx, y, z))) {
                x = nx;
            } else {
                pool.vx[i] *= -0.3F;
                sx = 0.0F;
            }
            double nz = z + sz;
            if (Double.isNaN(solidTop(x, y, nz))) {
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

    /** Touchdown: the leaf stops and spends a few ticks turning flat before it rests. */
    private void land(int i) {
        pool.vx[i] = 0.0F;
        pool.vy[i] = 0.0F;
        pool.vz[i] = 0.0F;
        pool.flags[i] |= FLAG_SETTLING;
        pool.life[i] = SETTLE_TICKS;
    }

    private void settle(int i) {
        pool.pitch[i] += wrapHalfTurn(-pool.pitch[i]) * 0.4F;
        pool.roll[i] += wrapHalfTurn(-pool.roll[i]) * 0.4F;
        pool.yaw[i] += pool.spinYaw[i];
        pool.spinYaw[i] *= 0.6F;
        if (--pool.life[i] <= 0) {
            rest(i);
        }
    }

    private void rest(int i) {
        pool.flags[i] &= (byte) ~FLAG_SETTLING;
        pool.state[i] = LeafPool.RESTING;
        pool.pitch[i] = wrapHalfTurn(pool.pitch[i]) * 0.3F + (random() - 0.5F) * 0.1F;
        pool.roll[i] = wrapHalfTurn(pool.roll[i]) * 0.3F + (random() - 0.5F) * 0.1F;
        pool.spinYaw[i] = 0.0F;
        pool.spinPitch[i] = 0.0F;
        pool.spinRoll[i] = 0.0F;
        pool.savePrevious(i);
        pool.age[i] = 0;
        pool.life[i] = (int) (settings.groundLifetimeTicks * (0.7F + 0.6F * random()));
        pool.restTick[i] = tick;
        cursor.set(Mth.floor(pool.x[i]), Mth.floor(pool.y[i] + 0.25), Mth.floor(pool.z[i]));
        boolean exposed = level.canSeeSky(cursor);
        pool.flags[i] = exposed ? LeafPool.FLAG_EXPOSED : 0;
        if (exposed && level.isRainingAt(cursor)) {
            pool.flags[i] |= LeafPool.FLAG_WET;
        }
        pool.light[i] = lightAt(pool.x[i], pool.y[i], pool.z[i]);
        pushRest(i);
        listener.onRest(i);
    }

    private void tickResting(int i) {
        if (++pool.age[i] >= pool.life[i]) {
            startDying(i);
            return;
        }
        double x = pool.x[i];
        double y = pool.y[i];
        double z = pool.z[i];
        if (((i + tick) & 31) == 0) {
            if (Double.isNaN(solidTop(x, y - 0.05, z))) {
                impulse(i, 0.0F, 0.0F, 0.0F);
                return;
            }
            cursor.set(Mth.floor(x), Mth.floor(y + 0.25), Mth.floor(z));
            if ((pool.flags[i] & LeafPool.FLAG_EXPOSED) != 0 && level.isRainingAt(cursor)) {
                pool.flags[i] |= LeafPool.FLAG_WET;
            } else {
                pool.flags[i] &= (byte) ~LeafPool.FLAG_WET;
            }
        }
        if ((pool.flags[i] & LeafPool.FLAG_EXPOSED) != 0) {
            float speed = wind.speed(x, y, z);
            float excess = speed - WIND_LIFT_THRESHOLD;
            boolean wet = (pool.flags[i] & LeafPool.FLAG_WET) != 0;
            if (excess > 0.0F && random() < excess * WIND_LIFT_CHANCE * (wet ? 0.15F : 1.0F)) {
                float push = speed * (1.2F + random());
                impulse(i, wind.dirX() * push, 0.02F + random() * 0.05F, wind.dirZ() * push);
                pool.spinYaw[i] = (random() - 0.5F) * 0.3F;
                pool.spinPitch[i] = (random() - 0.5F) * 0.3F;
            }
        }
    }

    private void startFloating(int i, double surface) {
        pool.state[i] = LeafPool.FLOATING;
        pool.y[i] = surface + 0.015;
        pool.vx[i] *= 0.3F;
        pool.vy[i] = 0.0F;
        pool.vz[i] *= 0.3F;
        pool.spinYaw[i] = (random() - 0.5F) * 0.03F;
        pool.flowX[i] = 0.0F;
        pool.flowZ[i] = 0.0F;
        pool.age[i] = 0;
        pool.life[i] = (int) (settings.groundLifetimeTicks * (0.4F + 0.4F * random()));
    }

    private void tickFloating(int i) {
        if (++pool.age[i] >= pool.life[i]) {
            startDying(i);
            return;
        }
        double x = pool.x[i];
        double z = pool.z[i];
        cursor.set(Mth.floor(x), Mth.floor(pool.y[i] - 0.05), Mth.floor(z));
        FluidState fluid = level.getFluidState(cursor);
        if (!fluid.is(FluidTags.WATER)) {
            pool.state[i] = LeafPool.FALLING;
            return;
        }
        double surface = cursor.getY() + fluid.getHeight(level, cursor);
        if (((i + tick) % 10) == 0) {
            Vec3 flow = fluid.getFlow(level, cursor);
            pool.flowX[i] = (float) flow.x * 0.06F;
            pool.flowZ[i] = (float) flow.z * 0.06F;
            pool.light[i] = lightAt(x, surface + 0.1, z);
        }
        float windSpeed = wind.speed(x, surface, z) * 0.25F;
        pool.vx[i] += (pool.flowX[i] + wind.dirX() * windSpeed - pool.vx[i]) * 0.08F;
        pool.vz[i] += (pool.flowZ[i] + wind.dirZ() * windSpeed - pool.vz[i]) * 0.08F;
        double y = surface + 0.015;
        double nx = x + pool.vx[i];
        if (Double.isNaN(solidTop(nx, y, z))) {
            x = nx;
        } else {
            pool.vx[i] *= -0.5F;
        }
        double nz = z + pool.vz[i];
        if (Double.isNaN(solidTop(x, y, nz))) {
            z = nz;
        } else {
            pool.vz[i] *= -0.5F;
        }
        pool.x[i] = x;
        pool.y[i] = y;
        pool.z[i] = z;
        pool.spinYaw[i] = pool.spinYaw[i] * 0.98F + (random() - 0.5F) * 0.004F;
        pool.yaw[i] += pool.spinYaw[i];
        float bob = pool.age[i] * 0.12F + i;
        pool.pitch[i] = 0.06F * Mth.sin(bob);
        pool.roll[i] = 0.06F * Mth.cos(bob * 0.8F);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Disturbances
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Adds velocity to a leaf, waking it up if it was resting or floating. Fading leaves are left alone.
     */
    private void impulse(int i, float vx, float vy, float vz) {
        byte state = pool.state[i];
        if (state == LeafPool.DYING || state == LeafPool.FREE) {
            return;
        }
        if (state != LeafPool.FALLING) {
            if (state == LeafPool.RESTING) {
                listener.onUnrest(i);
            }
            pool.state[i] = LeafPool.FALLING;
            pool.vx[i] = 0.0F;
            pool.vy[i] = 0.0F;
            pool.vz[i] = 0.0F;
        }
        pool.flags[i] &= (byte) ~FLAG_SETTLING;
        pool.age[i] = 0;
        pool.vx[i] += vx;
        pool.vy[i] += vy;
        pool.vz[i] += vz;
        clampSpeed(i);
    }

    /**
     * An entity moved by {@code (moveX, moveY, moveZ)} this tick with its feet at {@code (x, y, z)}. Leaves under its
     * feet are kicked up along the movement and outwards, airborne leaves in its way are dragged along. Sneaking barely
     * disturbs anything; landing from a fall throws leaves up around the feet.
     *
     * @param landing downward speed at touchdown in blocks per tick, 0 if the entity did not just land
     * @return how many resting leaves were kicked up
     */
    public int disturb(double x, double y, double z, double moveX, double moveY, double moveZ, float width, float height,
            boolean sneaking, float landing) {
        float strength = settings.entityStrength;
        if (strength <= 0.0F) {
            return 0;
        }
        double horizontal = Math.sqrt(moveX * moveX + moveZ * moveZ);
        float speed = (float) Math.min(horizontal, 1.5);
        boolean landed = landing > 0.3F;
        if (speed < 0.03F && !landed) {
            return 0;
        }
        float reach = width * 0.5F + 0.3F + speed * 0.6F + (landed ? landing * 1.2F : 0.0F);
        double below = 0.35 + speed * 0.8;
        float dirX = horizontal > 1.0E-4 ? (float) (moveX / horizontal) : 0.0F;
        float dirZ = horizontal > 1.0E-4 ? (float) (moveZ / horizontal) : 0.0F;
        grid.query(x - reach, z - reach, x + reach, z + reach, nearby);
        int kicked = 0;
        boolean wet = false;
        for (int n = 0; n < nearby.size(); n++) {
            int i = nearby.getInt(n);
            byte state = pool.state[i];
            if (state == LeafPool.FREE || state == LeafPool.DYING) {
                continue;
            }
            double dx = pool.x[i] - x;
            double dz = pool.z[i] - z;
            double distSq = dx * dx + dz * dz;
            if (distSq >= reach * reach) {
                continue;
            }
            float dist = (float) Math.sqrt(distSq);
            float falloff = 1.0F - dist / reach;
            float outX;
            float outZ;
            if (dist > 1.0E-3F) {
                outX = (float) dx / dist;
                outZ = (float) dz / dist;
            } else {
                float angle = random() * Mth.TWO_PI;
                outX = Mth.cos(angle);
                outZ = Mth.sin(angle);
            }
            double dy = pool.y[i] - y;
            if (state == LeafPool.FALLING && (pool.flags[i] & FLAG_SETTLING) == 0) {
                if (dy < -0.5 || dy > height + 0.3) {
                    continue;
                }
                float pull = 0.35F * falloff * strength;
                pool.vx[i] += ((float) moveX - pool.vx[i]) * pull + outX * 0.04F * falloff * strength;
                pool.vz[i] += ((float) moveZ - pool.vz[i]) * pull + outZ * 0.04F * falloff * strength;
                pool.vy[i] += (float) moveY * 0.2F * falloff + 0.01F * falloff * strength * random();
                pool.spinPitch[i] += (random() - 0.5F) * speed * falloff;
                pool.spinRoll[i] += (random() - 0.5F) * speed * falloff;
                clampSpeed(i);
                continue;
            }
            if (dy < -below || dy > 0.6) {
                continue;
            }
            boolean leafWet = (pool.flags[i] & LeafPool.FLAG_WET) != 0;
            float kick = speed * falloff * strength * (sneaking ? 0.2F : 1.0F) * (leafWet ? 0.6F : 1.0F);
            float stomp = landed ? landing * 0.4F * falloff * strength : 0.0F;
            if (kick < KICK_THRESHOLD && stomp < KICK_THRESHOLD || random() > Math.min(1.0F, (kick + stomp) * 8.0F)) {
                continue;
            }
            float push = kick * (0.6F + 0.8F * random());
            float up = kick * (0.2F + 0.5F * random()) + stomp * (0.5F + 0.6F * random()) + 0.01F;
            impulse(i, (dirX * 0.75F + outX * 0.6F) * push + outX * stomp, up, (dirZ * 0.75F + outZ * 0.6F) * push + outZ * stomp);
            float spin = push + stomp;
            pool.spinYaw[i] = (random() - 0.5F) * spin * 4.0F;
            pool.spinPitch[i] = (random() - 0.5F) * spin * 6.0F;
            pool.spinRoll[i] = (random() - 0.5F) * spin * 6.0F;
            kicked++;
            wet |= leafWet;
        }
        if (kicked > 0) {
            listener.onRustle(x, y, z, kicked, wet);
        }
        return kicked;
    }

    /**
     * An explosion: a spherical shock front that expands over a few ticks and throws leaves outwards and up. Wind
     * charges are weaker but swirl the leaves; real explosions reach much farther.
     */
    public void explode(double x, double y, double z, float power, boolean windCharge) {
        float strength = settings.explosionStrength;
        if (strength <= 0.0F || power <= 0.0F) {
            return;
        }
        float swirl = random() < 0.5F ? -1.0F : 1.0F;
        if (windCharge) {
            shockwaves.add(new Shockwave(x, y, z, Math.min(8.0F, 2.0F + power * 2.2F), 0.8F * strength, 0.55F, 0.9F, 0.45F * swirl));
        } else {
            shockwaves.add(new Shockwave(x, y, z, Math.min(24.0F, power * 3.0F), Math.min(2.0F, 0.9F + 0.15F * power) * strength,
                    0.45F, 2.5F, 0.08F * swirl));
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
                float dist = (float) Math.sqrt(distSq);
                float falloff = 1.0F - dist / wave.radius;
                float magnitude = wave.strength * falloff * Mth.sqrt(falloff) * (0.7F + 0.6F * random());
                float nx;
                float ny;
                float nz;
                if (dist > 0.05F) {
                    nx = (float) dx / dist;
                    ny = (float) dy / dist;
                    nz = (float) dz / dist;
                } else {
                    float angle = random() * Mth.TWO_PI;
                    nx = Mth.cos(angle) * 0.5F;
                    ny = 0.8F;
                    nz = Mth.sin(angle) * 0.5F;
                }
                float lift = wave.lift * magnitude * (0.6F + 0.8F * random());
                impulse(i, (nx - nz * wave.swirl) * magnitude, ny * magnitude * 0.6F + lift, (nz + nx * wave.swirl) * magnitude);
                pool.spinYaw[i] += (random() - 0.5F) * magnitude * 2.0F;
                pool.spinPitch[i] += (random() - 0.5F) * magnitude * 3.0F;
                pool.spinRoll[i] += (random() - 0.5F) * magnitude * 3.0F;
            }
            wave.front = outer;
            if (outer >= wave.radius) {
                it.remove();
            }
        }
    }

    /** A block changed: leaves lying in or on it wake up, so they fall when it is gone or move on top of it. */
    public void blockChanged(BlockPos pos) {
        double minX = pos.getX() - 0.05;
        double minZ = pos.getZ() - 0.05;
        double maxX = pos.getX() + 1.05;
        double maxZ = pos.getZ() + 1.05;
        grid.query(minX, minZ, maxX, maxZ, nearby);
        for (int n = 0; n < nearby.size(); n++) {
            int i = nearby.getInt(n);
            byte state = pool.state[i];
            boolean settling = state == LeafPool.FALLING && (pool.flags[i] & FLAG_SETTLING) != 0;
            if (state != LeafPool.RESTING && state != LeafPool.FLOATING && !settling) {
                continue;
            }
            double x = pool.x[i];
            double y = pool.y[i];
            double z = pool.z[i];
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= pos.getY() - 0.05 && y <= pos.getY() + 1.1) {
                impulse(i, (random() - 0.5F) * 0.01F, 0.0F, (random() - 0.5F) * 0.01F);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------

    /** Top of the collision box containing the point, or NaN when the point is free. Foliage is never solid. */
    private double solidTop(double x, double y, double z) {
        cursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockState state = level.getBlockState(cursor);
        if (state.isAir() || state.is(BlockTags.LEAVES)) {
            return Double.NaN;
        }
        return topAt(state, x, y, z);
    }

    /** Like {@link #solidTop} for the block at {@link #cursor}, which must contain the point. */
    private double topAt(BlockState state, double x, double y, double z) {
        double[] boxes = shapes.boxes(state, level, cursor);
        double lx = x - cursor.getX();
        double ly = y - cursor.getY();
        double lz = z - cursor.getZ();
        for (int b = 0; b < boxes.length; b += 6) {
            if (lx >= boxes[b] && lx <= boxes[b + 3] && ly >= boxes[b + 1] && ly < boxes[b + 4]
                    && lz >= boxes[b + 2] && lz <= boxes[b + 5]) {
                return cursor.getY() + boxes[b + 4];
            }
        }
        return Double.NaN;
    }

    /** Packed light coordinates (block light in bits 4..7, sky light in bits 20..23) at a position. */
    public int lightAt(double x, double y, double z) {
        lightCursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int sky = level.getBrightness(LightLayer.SKY, lightCursor);
        int block = level.getBrightness(LightLayer.BLOCK, lightCursor);
        return block << 4 | sky << 20;
    }

    /** Small random height above the surface so that overlapping leaves do not z-fight. */
    private float restOffset() {
        return 0.008F + random() * 0.022F;
    }

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

    /** Xorshift; much cheaper than a synchronized {@code Random} for tens of thousands of draws per tick. */
    private float random() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (seed >>> 40) * 0x1.0p-24F;
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
        float front;

        Shockwave(double x, double y, double z, float radius, float strength, float lift, float speed, float swirl) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.strength = strength;
            this.lift = lift;
            this.speed = speed;
            this.swirl = swirl;
        }
    }
}
