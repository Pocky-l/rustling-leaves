package com.pockyl.rustling_leaves.client.compat;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.sim.WaterWaves;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * The swell and surf of <a href="https://www.curseforge.com/minecraft/mc-mods/realistic-waves">Realistic Waves</a> (mod
 * id {@code whitecap}). It draws its own water surface, lifted on the GPU, but keeps the same height field on the CPU
 * for the boats, items and swimmers it bobs: {@code WaveSurface.surfaceY(level, partialTick, x, z)} is the height of
 * the drawn surface (NaN where it draws nothing, e.g. under a shader pack it cannot move or with the effect off), and
 * the sampler's {@code base()} right after it is the still water level that height was lifted from. Called by
 * reflection, so the mod is not needed to build or run. Main thread only: its sampler is a shared instance.
 */
public final class RealisticWaves implements WaterWaves {
    public static final String MOD_ID = "whitecap";
    private static final String SURFACE_CLASS = "dev.fallingcloud.whitecap.render.WaveSurface";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Marks a corner not asked yet this tick (NaN is a valid answer: no waves there). */
    private static final float MISSING = Float.MAX_VALUE;
    /** How far inside a block its corner is sampled. */
    private static final double INSET = 1.0E-3;

    private final MethodHandle surfaceY;
    private final MethodHandle base;
    private final Long2FloatOpenHashMap corners = new Long2FloatOpenHashMap();
    private Level cachedLevel;
    private long cachedTime;
    private boolean broken;

    private RealisticWaves(MethodHandle surfaceY, MethodHandle base) {
        this.surfaceY = surfaceY;
        this.base = base;
        corners.defaultReturnValue(MISSING);
    }

    /** The waves of the installed mod, or null when it is not installed or its code is not what this expects. */
    @Nullable
    public static RealisticWaves find() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return null;
        }
        try {
            Class<?> surface = Class.forName(SURFACE_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle surfaceY = lookup.findStatic(surface, "surfaceY",
                    MethodType.methodType(float.class, Level.class, float.class, double.class, double.class));
            Method sampler = surface.getMethod("sampler");
            Object instance = sampler.invoke(null);
            MethodHandle base = lookup.findVirtual(sampler.getReturnType(), "base", MethodType.methodType(float.class)).bindTo(instance);
            LOGGER.info("Floating leaves ride the waves of Realistic Waves");
            return new RealisticWaves(surfaceY, base);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("Realistic Waves is installed, but its wave heights could not be found; floating leaves stay on flat water", e);
            return null;
        }
    }

    /**
     * The drawn surface is flat over each half of a block (it lifts the block corners and splits each block along its
     * diagonal), so the corners are asked once per tick and shared by all leaves around them: a raft of leaves costs
     * a handful of samples, and the slope a leaf tilts with is exact.
     */
    @Override
    public float lift(double x, double z) {
        Level level = Minecraft.getInstance().level;
        if (broken || level == null) {
            return Float.NaN;
        }
        if (level != cachedLevel || level.getGameTime() != cachedTime) {
            corners.clear();
            cachedLevel = level;
            cachedTime = level.getGameTime();
        }
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        float c0 = corner(level, blockX, blockZ);
        float c1 = corner(level, blockX, blockZ + 1);
        float c2 = corner(level, blockX + 1, blockZ + 1);
        float c3 = corner(level, blockX + 1, blockZ);
        if (Float.isNaN(c0 + c1 + c2 + c3)) {
            return Float.NaN;
        }
        float fx = (float) (x - blockX);
        float fz = (float) (z - blockZ);
        return fz >= fx ? c0 * (1.0F - fz) + c1 * (fz - fx) + c2 * fx : c0 * (1.0F - fx) + c3 * (fx - fz) + c2 * fz;
    }

    /** The lift at a block corner, taken just inside the first of the four blocks around it that has waves. */
    private float corner(Level level, int cornerX, int cornerZ) {
        long key = ChunkPos.pack(cornerX, cornerZ);
        float cached = corners.get(key);
        if (cached != MISSING) {
            return cached;
        }
        float lift = Float.NaN;
        for (int k = 0; k < 4 && Float.isNaN(lift); k++) {
            lift = sample(level, cornerX + ((k & 1) == 0 ? INSET : -INSET), cornerZ + ((k & 2) == 0 ? INSET : -INSET));
        }
        corners.put(key, lift);
        return lift;
    }

    private float sample(Level level, double x, double z) {
        if (broken) {
            return Float.NaN;
        }
        try {
            // One tick ahead: the simulation ticks at the end of the client tick and the frames after it draw the leaves
            // between the previous and this height, as the mod draws its waves between the game ticks.
            float height = (float) surfaceY.invokeExact(level, 1.0F, x, z);
            return Float.isNaN(height) ? Float.NaN : height - (float) base.invokeExact();
        } catch (Throwable e) {
            broken = true;
            LOGGER.warn("Asking Realistic Waves for its wave heights failed; floating leaves stay on flat water from now on", e);
            return Float.NaN;
        }
    }
}
