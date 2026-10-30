package com.pockyl.rustling_leaves.client.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.platform.Platform;
import com.pockyl.rustling_leaves.sim.WindSource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The world wind of <a href="https://www.curseforge.com/minecraft/mc-mods/living-wind">Living Wind</a> (mod id
 * {@code livingwind}). Its server simulates one wind per level and syncs it to the clients; {@code ClientWindApi} gives
 * the synced, smoothed state: {@code hasWindState()} (false until the server sent one, e.g. on a server without the mod),
 * {@code globalStrength()} and {@code directionDegrees()}, which blows towards (-sin, cos) like an entity's yaw. Its
 * strength is unitless: clear weather wanders up to about 0.6, rain and thunder add about 0.3 and 0.6, so a tenth of
 * it matches the speeds of this mod's own wind in blocks per tick. Called by reflection, so the mod is not needed to
 * build or run.
 */
public final class LivingWind implements WindSource {
    public static final String MOD_ID = "livingwind";
    private static final String API_CLASS = "com.macatmovie.livingwind.api.ClientWindApi";
    /** Blocks per tick of this mod's wind per unit of Living Wind's strength. */
    private static final float SPEED_PER_STRENGTH = 0.1F;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MethodHandle hasWindState;
    private final MethodHandle strength;
    private final MethodHandle direction;
    private boolean broken;

    private LivingWind(MethodHandle hasWindState, MethodHandle strength, MethodHandle direction) {
        this.hasWindState = hasWindState;
        this.strength = strength;
        this.direction = direction;
    }

    /** The wind of the installed mod, or null when it is not installed or its code is not what this expects. */
    @Nullable
    public static LivingWind find() {
        if (!Platform.INSTANCE.isModLoaded(MOD_ID)) {
            return null;
        }
        try {
            Class<?> api = Class.forName(API_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle hasWindState = lookup.findStatic(api, "hasWindState", MethodType.methodType(boolean.class));
            MethodHandle strength = lookup.findStatic(api, "globalStrength", MethodType.methodType(float.class));
            MethodHandle direction = lookup.findStatic(api, "directionDegrees", MethodType.methodType(float.class));
            LOGGER.info("Leaves blow with the wind of Living Wind");
            return new LivingWind(hasWindState, strength, direction);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("Living Wind is installed, but its wind could not be found; leaves blow with their own wind", e);
            return null;
        }
    }

    @Override
    public boolean read(float[] out) {
        if (broken) {
            return false;
        }
        try {
            if (!(boolean) hasWindState.invokeExact()) {
                return false;
            }
            float strength = (float) this.strength.invokeExact();
            float degrees = (float) direction.invokeExact();
            if (!Float.isFinite(strength) || !Float.isFinite(degrees)) {
                return false;
            }
            float radians = degrees * Mth.DEG_TO_RAD;
            out[0] = -Mth.sin(radians);
            out[1] = Mth.cos(radians);
            out[2] = strength * SPEED_PER_STRENGTH;
            return true;
        } catch (Throwable e) {
            broken = true;
            LOGGER.warn("Reading the wind of Living Wind failed; leaves blow with their own wind from now on", e);
            return false;
        }
    }
}
