package com.pockyl.rustling_leaves.client.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.client.render.backend.DataTexture;

import java.util.Arrays;
import java.util.Map;

/**
 * Publishes weather to shader packs: a row of float texels registered as {@code <modid>:shader_data}, which a pack
 * binds with {@code customTexture.<name> = <modid>:shader_data} and reads with {@code texelFetch}. Texel 0 is a marker
 * with the format version, texels 1 and 2 the mod's wind and the air it carried; the mod writes its own values from
 * texel 3 on. Layout and the wind shares: docs/shader-data.md of Pocky Shaders.
 * <p>
 * The same class lives in Petrichor, Hoarfrost and Rustling Leaves (only the package differs): the wind shares must be
 * computed by all of them in exactly the same way. Rustling Leaves keeps the texture's GPU calls in its render backend
 * ({@link DataTexture}, the part of the mod that differs between Minecraft versions) instead of a nested class; the
 * texture and the behaviour of this class are the same as in the other two.
 */
public final class ShaderData {
    public static final int FORMAT = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PETRICHOR = "petrichor";
    private static final String HOARFROST = "hoarfrost";
    private static final String LEAVES = "rustling_leaves";
    /** The first version of each mod that publishes its data. */
    private static final Map<String, String> FIRST_VERSION = Map.of(PETRICHOR, "1.7.0", HOARFROST, "1.4.0", LEAVES, "2.6.0");
    /** Who covers the rain, snow and calm shares of the weather: the first of each list that is installed. */
    private static final String[][] OWNERS = {
            {PETRICHOR, LEAVES, HOARFROST},
            {HOARFROST, PETRICHOR, LEAVES},
            {LEAVES, PETRICHOR, HOARFROST},
    };
    /** The air's drift wraps here, so float texels keep a precision of about 0.01 blocks. */
    private static final double DRIFT_WRAP = 100000.0;

    private final String modId;
    private final Identifier id;
    private final float[] values;
    private final boolean[] ownsShare = new boolean[3];
    private DataTexture texture;
    private boolean broken;
    private long lastFrame;
    private float frameTicks;
    private float dirX = 1.0F;
    private float dirZ = 0.35F;
    private double driftX;
    private double driftZ;

    public ShaderData(String modId, int texels) {
        this.modId = modId;
        this.id = Identifier.fromNamespaceAndPath(modId, "shader_data");
        this.values = new float[texels * 4];
        for (int share = 0; share < OWNERS.length; share++) {
            for (String owner : OWNERS[share]) {
                if (publishes(owner)) {
                    ownsShare[share] = owner.equals(modId);
                    break;
                }
            }
        }
    }

    private static boolean publishes(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().compareTo(new DefaultArtifactVersion(FIRST_VERSION.get(modId))) >= 0)
                .orElse(false);
    }

    /**
     * Starts a frame: call on the render thread before the world is drawn. Returns false when nothing is to be
     * published (no shader pack, no world); otherwise fill the texels and call {@link #upload}.
     */
    public boolean begin(boolean packInUse) {
        Minecraft minecraft = Minecraft.getInstance();
        if (broken || !(ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus"))) {
            return false;
        }
        try {
            if (texture == null) {
                // Registered before the first pack frame, so Iris finds it instead of a missing texture.
                texture = DataTexture.register(id, values.length / 4);
            }
        } catch (Throwable e) {
            stop(e);
            return false;
        }
        long now = System.nanoTime();
        if (!packInUse || minecraft.level == null) {
            lastFrame = 0L;
            return false;
        }
        // Game ticks since the last frame, for the drift; nothing moves while the game is paused.
        frameTicks = lastFrame == 0L || minecraft.isPaused() ? 0.0F : Math.min((now - lastFrame) / 50_000_000.0F, 5.0F);
        lastFrame = now;
        Arrays.fill(values, 0.0F);
        set(0, 0.123F, 0.456F, 0.789F, FORMAT);
        return true;
    }

    /**
     * The mod's wind (blocks per tick): texel 1 gets its direction, speed and the share of the weather this mod covers,
     * texel 2 the drift of that share.
     */
    public void wind(float windX, float windZ, float partialTick) {
        float speed = (float) Math.sqrt(windX * windX + windZ * windZ);
        if (speed > 1.0E-4F) {
            dirX = windX / speed;
            dirZ = windZ / speed;
        }
        float share = coveredShare(partialTick);
        driftX = (driftX + windX * share * frameTicks) % DRIFT_WRAP;
        driftZ = (driftZ + windZ * share * frameTicks) % DRIFT_WRAP;
        set(1, dirX, dirZ, speed, share);
        set(2, (float) driftX, (float) driftZ, 0.0F, 0.0F);
    }

    /** The share of the weather at the camera this mod's wind covers (docs/shader-data.md, "Wind shares"). */
    public float coveredShare(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return 0.0F;
        }
        boolean open = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
        float rain = open ? level.getRainLevel(partialTick) : 0.0F;
        BlockPos pos = minecraft.gameRenderer.mainCamera().blockPosition();
        float snowy = level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.SNOW ? 1.0F : 0.0F;
        float[] shares = {rain * (1.0F - snowy), rain * snowy, 1.0F - rain};
        float covered = 0.0F;
        for (int share = 0; share < shares.length; share++) {
            if (ownsShare[share]) {
                covered += shares[share];
            }
        }
        return covered;
    }

    public void set(int texel, float r, float g, float b, float a) {
        values[texel * 4] = r;
        values[texel * 4 + 1] = g;
        values[texel * 4 + 2] = b;
        values[texel * 4 + 3] = a;
    }

    public void upload() {
        try {
            texture.upload(values);
        } catch (Throwable e) {
            stop(e);
        }
    }

    /** Never take the game down for an extra: packs fall back to vanilla weather. */
    private void stop(Throwable e) {
        broken = true;
        LOGGER.error("Weather data for shader packs ({}) stopped", modId, e);
    }
}
