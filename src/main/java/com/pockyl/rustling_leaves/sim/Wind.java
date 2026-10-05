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

    /**
     * A gust front: a band across its direction moving along it. Natural squalls follow the wind and span the whole
     * area; a squall sent by a staff has its own direction and a limited width around its origin.
     */
    static final class Squall {
        final float dirX;
        final float dirZ;
        final double originX;
        final double originZ;
        /** Position of the front along the direction (dot product with the direction). */
        double front;
        final float speed;
        final float width;
        final float boost;
        final double end;
        /** Half-width across the direction; infinite for natural squalls. */
        final float halfWidth;

        Squall(float dirX, float dirZ, double originX, double originZ, double front, float speed, float width, float boost, double end,
                float halfWidth) {
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.originX = originX;
            this.originZ = originZ;
            this.front = front;
            this.speed = speed;
            this.width = width;
            this.boost = boost;
            this.end = end;
            this.halfWidth = halfWidth;
        }

        /** How strongly the front is passing over a position, in [0, 1]. */
        float band(double x, double z) {
            float d = (float) (x * dirX + z * dirZ - front) / width;
            float band = (float) Math.exp(-d * d);
            if (halfWidth != Float.POSITIVE_INFINITY && band > 0.001F) {
                float across = (float) ((x - originX) * -dirZ + (z - originZ) * dirX) / halfWidth;
                band *= Math.max(0.0F, 1.0F - across * across);
            }
            return band;
        }
    }

    /** A leaf blower's cone of air, renewed every tick while the blower runs. */
    static final class Jet {
        final double x;
        final double y;
        final double z;
        final float dirX;
        final float dirY;
        final float dirZ;
        final float length;
        final float spread;
        final float power;
        int ttl = 2;

        Jet(double x, double y, double z, float dirX, float dirY, float dirZ, float length, float spread, float power) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.length = length;
            this.spread = spread;
            this.power = power;
        }

        /** Air speed of the jet at a point, 0 outside the cone. */
        float strength(double px, double py, double pz) {
            double ox = px - x;
            double oy = py - y;
            double oz = pz - z;
            double along = ox * dirX + oy * dirY + oz * dirZ;
            if (along < 0.0 || along > length) {
                return 0.0F;
            }
            double ax = ox - dirX * along;
            double ay = oy - dirY * along;
            double az = oz - dirZ * along;
            double across = Math.sqrt(ax * ax + ay * ay + az * az);
            double radius = 0.3 + along * spread;
            if (across > radius) {
                return 0.0F;
            }
            return (float) (power * (1.0 - along / length) * (1.0 - across / radius));
        }
    }

    private final List<Jet> jets = new ArrayList<>();
    private final List<Blast> blasts = new ArrayList<>();

    /**
     * The air of an explosion or wind charge: a ring of outflow that expands and dies down within about a second,
     * a rising column in the middle and (for wind charges) a swirl. Leaves are light, so this moving air rather than
     * the first kick decides where they go: they ride out on the ring, get caught in the updraft and flutter down.
     */
    static final class Blast {
        final double x;
        final double y;
        final double z;
        final float radius;
        final float strength;
        final float swirl;
        int age;

        Blast(double x, double y, double z, float radius, float strength, float swirl) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.strength = strength;
            this.swirl = swirl;
        }

        boolean done() {
            return age > 36;
        }

        void add(double px, double py, double pz, float[] out) {
            float dx = (float) (px - x);
            float dy = (float) (py - y);
            float dz = (float) (pz - z);
            float flat = Mth.sqrt(dx * dx + dz * dz);
            if (flat > radius * 1.6F || dy < -2.0F || dy > radius * 1.5F) {
                return;
            }
            float decay = (float) Math.exp(-age / 10.0);
            float front = radius * (1.0F - (float) Math.exp(-age / 6.0)) * 1.3F;
            float width = 0.8F + radius * 0.25F;
            float ring = (flat - front) / width;
            float outflow = strength * decay * (float) Math.exp(-ring * ring);
            float nx = flat > 1.0E-3F ? dx / flat : 0.0F;
            float nz = flat > 1.0E-3F ? dz / flat : 0.0F;
            out[0] += (nx - nz * swirl) * outflow;
            out[2] += (nz + nx * swirl) * outflow;
            out[1] += outflow * 0.35F;
            // The column in the middle draws air in at the bottom and lifts it.
            if (flat < radius * 0.6F) {
                float core = 1.0F - flat / (radius * 0.6F);
                out[1] += strength * 0.55F * core * (float) Math.exp(-age / 14.0);
                out[0] -= nx * strength * 0.15F * core * decay;
                out[2] -= nz * strength * 0.15F * core * decay;
            }
        }
    }

    /** Starts the air pulse of an explosion. */
    public void addBlast(double x, double y, double z, float radius, float strength, float swirl) {
        blasts.add(new Blast(x, y, z, radius, strength, swirl));
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

        jets.removeIf(jet -> --jet.ttl <= 0);
        for (Blast blast : blasts) {
            blast.age++;
        }
        blasts.removeIf(Blast::done);
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
        squalls.add(new Squall(dirX, dirZ, cameraX, cameraZ, along - 56.0, 0.5F + random.next() * 0.35F, 6.0F + random.next() * 8.0F,
                strength, along + 64.0, Float.POSITIVE_INFINITY));
    }

    /** A strong squall rolling from a spot along a direction, 12 blocks wide, for 48 blocks (staff of winds). */
    public void sendSquall(double x, double z, float directionX, float directionZ) {
        float length = Mth.sqrt(directionX * directionX + directionZ * directionZ);
        if (length < 1.0E-4F) {
            return;
        }
        float dx = directionX / length;
        float dz = directionZ / length;
        double along = x * dx + z * dz;
        squalls.add(new Squall(dx, dz, x, z, along - 2.0, 0.8F, 5.0F, 0.32F, along + 48.0, 6.0F));
    }

    /** A leaf blower runs this tick: a cone of air from the nozzle. */
    public void addJet(double x, double y, double z, float dirX, float dirY, float dirZ, float length, float spread, float power) {
        jets.add(new Jet(x, y, z, dirX, dirY, dirZ, length, spread, power));
    }

    /** Air speed of the leaf blowers at a point. */
    public float jetStrength(double x, double y, double z) {
        float strongest = 0.0F;
        for (int j = 0; j < jets.size(); j++) {
            strongest = Math.max(strongest, jets.get(j).strength(x, y, z));
        }
        return strongest;
    }

    /** A whirlwind raised at a spot (staff of winds): strong, fading in, wandering like a natural one. */
    public Whirlwind summonWhirlwind(double x, double y, double z) {
        Whirlwind whirlwind = new Whirlwind(x, y, z, 1.8F, 9.0F, 0.42F, 1.0F, 20 * 22);
        whirlwinds.add(whirlwind);
        return whirlwind;
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
        float strongest = 0.0F;
        for (Squall squall : squalls) {
            strongest = Math.max(strongest, squall.band(x, z));
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
        for (int s = 0; s < squalls.size(); s++) {
            Squall squall = squalls.get(s);
            speed += squall.boost * squall.band(x, z);
        }
        return speed;
    }

    /** Air velocity at a position into {@code out[0..2]}, including squalls, whirlwinds and leaf blowers. */
    public void sample(double x, double y, double z, float[] out) {
        float ambient = 0.0F;
        if (base > 0.0F) {
            float altitude = 1.0F + Mth.clamp((float) (y - 62.0) / 80.0F, 0.0F, 0.8F);
            ambient = base * (0.4F + 1.6F * gust(x, z)) * altitude;
        }
        out[0] = dirX * ambient;
        out[1] = 0.0F;
        out[2] = dirZ * ambient;
        for (int s = 0; s < squalls.size(); s++) {
            Squall squall = squalls.get(s);
            float push = squall.boost * squall.band(x, z);
            out[0] += squall.dirX * push;
            out[2] += squall.dirZ * push;
        }
        for (int w = 0; w < whirlwinds.size(); w++) {
            addWhirlwind(whirlwinds.get(w), x, y, z, out);
        }
        for (int b = 0; b < blasts.size(); b++) {
            blasts.get(b).add(x, y, z, out);
        }
        for (int j = 0; j < jets.size(); j++) {
            Jet jet = jets.get(j);
            float strength = jet.strength(x, y, z);
            if (strength > 0.0F) {
                out[0] += jet.dirX * strength;
                out[1] += jet.dirY * strength + strength * 0.15F;
                out[2] += jet.dirZ * strength;
            }
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
        float updraft = r < radius * 1.3F ? 0.24F * intensity * (1.0F - r / (radius * 1.3F)) * (1.0F - height * height) : 0.0F;
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
        jets.clear();
        blasts.clear();
    }
}
