package com.pockyl.rustling_leaves.sim;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The wind field that leaves feel. Three layers, all cheap to sample:
 * <ul>
 *   <li>ambient wind: a slowly wandering direction and a speed that rises with rain and thunder, modulated by gust
 *   waves that travel along the wind, so gusts visibly sweep across a forest;</li>
 *   <li>squalls: rare strong gust fronts that cross the area, shake many leaves off the trees and drive the litter
 *   along the ground;</li>
 *   <li>whirlwinds: wandering vortices (a Rankine vortex with inflow at the bottom, an updraft in the core and outflow
 *   at the top) that suck leaves off the ground and spin them up in a column.</li>
 * </ul>
 * All speeds are in blocks per tick.
 */
public final class Wind {
    private static final float CALM = 0.028F;
    private static final float RAIN = 0.035F;
    private static final float THUNDER = 0.06F;
    private static final int MAX_WHIRLWINDS = 2;
    private static final int MAX_SQUALLS = 2;
    private static final int FADE_TICKS = 40;

    private final List<Whirlwind> whirlwinds = new ArrayList<>();
    private final List<Squall> squalls = new ArrayList<>();
    private float dirX = 1.0F;
    private float dirZ;
    private float base;
    private float time;
    private float rain;
    private float thunder;
    private boolean open;

    /** A wandering vortex. */
    public static final class Whirlwind {
        public double x;
        public double y;
        public double z;
        public final float radius;
        public final float height;
        public final float spin;
        final float strength;
        final int life;
        int age;
        float driftX;
        float driftZ;

        Whirlwind(double x, double y, double z, float radius, float height, float strength, float spin, int life) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.height = height;
            this.strength = strength;
            this.spin = spin;
            this.life = life;
        }

        /** 0..1, fading in and out. */
        public float intensity() {
            return Math.min(1.0F, Math.min(age, life - age) / (float) FADE_TICKS);
        }
    }

    /** A gust front: a band across the wind direction moving downwind. */
    static final class Squall {
        /** Position of the front along the wind direction (dot product with the direction). */
        double front;
        final float speed;
        final float width;
        final float boost;
        final double end;

        Squall(double front, float speed, float width, float boost, double end) {
            this.front = front;
            this.speed = speed;
            this.width = width;
            this.boost = boost;
            this.end = end;
        }
    }

    void update(Level level, int tick, LeafSettings settings, Terrain terrain, FastRandom random, double cameraX, double cameraY,
            double cameraZ) {
        time = tick;
        float angle = 0.9F * Mth.sin(tick * 0.00071F) + 0.5F * Mth.sin(tick * 0.0023F + 1.3F) + 0.7F;
        dirX = Mth.cos(angle);
        dirZ = Mth.sin(angle);
        open = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
        rain = open ? level.getRainLevel(1.0F) : 0.0F;
        thunder = open ? level.getThunderLevel(1.0F) : 0.0F;
        base = open ? (CALM + RAIN * rain + THUNDER * thunder) * settings.windStrength : 0.0F;

        for (Iterator<Squall> it = squalls.iterator(); it.hasNext(); ) {
            Squall squall = it.next();
            squall.front += squall.speed;
            if (squall.front > squall.end) {
                it.remove();
            }
        }
        for (Iterator<Whirlwind> it = whirlwinds.iterator(); it.hasNext(); ) {
            Whirlwind whirlwind = it.next();
            if (++whirlwind.age >= whirlwind.life) {
                it.remove();
                continue;
            }
            // Carried by the wind, wandering a little, hugging the ground.
            whirlwind.driftX = whirlwind.driftX * 0.98F + (random.next() - 0.5F) * 0.004F;
            whirlwind.driftZ = whirlwind.driftZ * 0.98F + (random.next() - 0.5F) * 0.004F;
            float carry = Math.min(0.06F, base * 0.8F + 0.01F);
            whirlwind.x += dirX * carry + whirlwind.driftX;
            whirlwind.z += dirZ * carry + whirlwind.driftZ;
            if ((whirlwind.age & 7) == 0) {
                // Follow the terrain locally (steps up and down); a cliff, water or a wall ends it.
                double ground = terrain.groundBelow(whirlwind.x, whirlwind.y + 2.0, whirlwind.z, 6);
                if (Double.isNaN(ground)) {
                    it.remove();
                } else {
                    whirlwind.y = ground;
                }
            }
        }

        float events = settings.windEvents * (open ? 1.0F : 0.0F);
        if (events <= 0.0F) {
            return;
        }
        float storm = 1.0F + 3.0F * rain + 4.0F * thunder;
        if (squalls.size() < MAX_SQUALLS && random.next() < events * storm / (20.0F * 90.0F)) {
            spawnSquall(random, cameraX, cameraZ);
        }
        // Whirlwinds like dry, gusty weather and do not survive heavy rain.
        float whirlChance = events * (1.0F - 0.7F * rain) / (20.0F * 150.0F);
        if (whirlwinds.size() < MAX_WHIRLWINDS && random.next() < whirlChance) {
            spawnWhirlwind(level, terrain, random, cameraX, cameraZ);
        }
    }

    void spawnSquall(FastRandom random, double cameraX, double cameraZ) {
        double along = cameraX * dirX + cameraZ * dirZ;
        float strength = 0.12F + random.next() * 0.13F + 0.1F * thunder;
        squalls.add(new Squall(along - 56.0, 0.5F + random.next() * 0.35F, 6.0F + random.next() * 8.0F, strength, along + 64.0));
    }

    /** Starts a whirlwind near the camera on open ground; returns it, or null if no spot was found. */
    Whirlwind spawnWhirlwind(Level level, Terrain terrain, FastRandom random, double cameraX, double cameraZ) {
        for (int attempt = 0; attempt < 6; attempt++) {
            float angle = random.next() * Mth.TWO_PI;
            float distance = 8.0F + random.next() * 18.0F;
            double x = cameraX + Mth.cos(angle) * distance;
            double z = cameraZ + Mth.sin(angle) * distance;
            double ground = groundUnder(level, terrain, x, z);
            if (Double.isNaN(ground) || !terrain.canSeeSky(x, ground + 0.5, z)) {
                continue;
            }
            Whirlwind whirlwind = new Whirlwind(x, ground, z, 1.2F + random.next() * 1.2F, 5.0F + random.next() * 4.0F,
                    0.22F + random.next() * 0.16F, random.next() < 0.5F ? -1.0F : 1.0F, 200 + (int) (random.next() * 360));
            whirlwinds.add(whirlwind);
            return whirlwind;
        }
        return null;
    }

    /** Adds a whirlwind at a given spot (tests, commands). */
    public Whirlwind addWhirlwind(double x, double y, double z, float radius, float height, float strength, int life) {
        Whirlwind whirlwind = new Whirlwind(x, y, z, radius, height, strength, 1.0F, life);
        whirlwind.age = FADE_TICKS;
        whirlwinds.add(whirlwind);
        return whirlwind;
    }

    private static double groundUnder(Level level, Terrain terrain, double x, double z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        return terrain.groundBelow(x, top + 0.5, z, 32);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sampling
    // ------------------------------------------------------------------------------------------------------------

    /** Gust factor in [0, 1] of the ambient wind at a horizontal position. */
    public float gust(double x, double z) {
        float s = time * 0.025F - (float) ((x * dirX + z * dirZ) * 0.05);
        float g = 0.5F + 0.32F * Mth.sin(s) + 0.18F * Mth.sin(s * 2.7F + 1.1F);
        return g * g;
    }

    /** How strongly a squall front is passing over a position, in [0, 1]. */
    public float squall(double x, double z) {
        if (squalls.isEmpty()) {
            return 0.0F;
        }
        double along = x * dirX + z * dirZ;
        float strongest = 0.0F;
        for (Squall squall : squalls) {
            float d = (float) (along - squall.front) / squall.width;
            strongest = Math.max(strongest, (float) Math.exp(-d * d));
        }
        return strongest;
    }

    /** Horizontal wind speed (without whirlwinds) at a position: ambient gusts, altitude and squalls. */
    public float speed(double x, double y, double z) {
        float speed = 0.0F;
        if (base > 0.0F) {
            float altitude = 1.0F + Mth.clamp((float) (y - 62.0) / 80.0F, 0.0F, 0.8F);
            speed = base * (0.4F + 1.6F * gust(x, z)) * altitude;
        }
        if (!squalls.isEmpty()) {
            double along = x * dirX + z * dirZ;
            for (Squall squall : squalls) {
                float d = (float) (along - squall.front) / squall.width;
                speed += squall.boost * (float) Math.exp(-d * d);
            }
        }
        return speed;
    }

    /** Air velocity at a position into {@code out[0..2]}, including whirlwinds. */
    public void sample(double x, double y, double z, float[] out) {
        float speed = speed(x, y, z);
        out[0] = dirX * speed;
        out[1] = 0.0F;
        out[2] = dirZ * speed;
        for (int w = 0; w < whirlwinds.size(); w++) {
            addWhirlwind(whirlwinds.get(w), x, y, z, out);
        }
    }

    private static void addWhirlwind(Whirlwind whirlwind, double x, double y, double z, float[] out) {
        float dx = (float) (x - whirlwind.x);
        float dz = (float) (z - whirlwind.z);
        float r = Mth.sqrt(dx * dx + dz * dz);
        float reach = whirlwind.radius * 2.5F;
        float h = (float) (y - whirlwind.y) / whirlwind.height;
        if (r >= reach || h < -0.2F || h > 1.15F || r < 1.0E-3F) {
            return;
        }
        float intensity = whirlwind.intensity();
        float radius = whirlwind.radius * (1.0F + 0.5F * Math.max(0.0F, h));
        // Rankine vortex: solid-body rotation inside the core, decaying outside, faded out at the reach.
        float tangential = r < radius ? r / radius : radius / r * (1.0F - (r - radius) / (reach - radius));
        tangential *= whirlwind.strength * intensity;
        float nx = dx / r;
        float nz = dz / r;
        float height = Mth.clamp(h, 0.0F, 1.0F);
        // Inflow near the ground, outflow near the top.
        float radial = tangential * (0.6F * height - 0.35F);
        float updraft = r < radius * 1.3F ? 0.14F * intensity * (1.0F - r / (radius * 1.3F)) * (1.0F - height * height) : 0.0F;
        out[0] += -nz * tangential * whirlwind.spin + nx * radial;
        out[1] += updraft;
        out[2] += nx * tangential * whirlwind.spin + nz * radial;
    }

    public List<Whirlwind> whirlwinds() {
        return whirlwinds;
    }

    public float dirX() {
        return dirX;
    }

    public float dirZ() {
        return dirZ;
    }

    /** Current rain level in [0, 1] where the sky is open. */
    public float rain() {
        return rain;
    }

    /** Clears whirlwinds and squalls. */
    void reset() {
        whirlwinds.clear();
        squalls.clear();
    }
}
