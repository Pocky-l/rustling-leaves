package com.pockyl.rustling_leaves.client.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.sim.WindSource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The course of the wind of <a href="https://www.curseforge.com/minecraft/mc-mods/petrichor">Petrichor</a> (rain) and
 * Hoarfrost (snow). Both steer their wind along the same slow course, a pure function of the game time
 * ({@code RainSchedule.windAngle} / {@code Wind.course}, radians, blowing towards (cos, sin)), so every player sees rain
 * and snow slant the same way. With either installed the leaves blow along that course too, instead of a heading of
 * their own that could cross the rain; their speed, gusts, squalls and whirlwinds stay this mod's. Called by reflection,
 * so neither mod is needed to build or run.
 */
public final class WeatherWind implements WindSource {
    private static final String[][] COURSES = {
            {"petrichor", "com.pockyl.petrichor.weather.RainSchedule", "windAngle"},
            {"hoarfrost", "com.pockyl.hoarfrost.weather.Wind", "course"},
    };

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MethodHandle course;
    private boolean broken;

    private WeatherWind(MethodHandle course) {
        this.course = course;
    }

    /** The wind course of Petrichor or Hoarfrost, whichever is installed, or null with neither. */
    @Nullable
    public static WeatherWind find() {
        for (String[] candidate : COURSES) {
            if (!ModList.get().isLoaded(candidate[0])) {
                continue;
            }
            try {
                MethodHandle course = MethodHandles.publicLookup().findStatic(Class.forName(candidate[1]), candidate[2],
                        MethodType.methodType(float.class, double.class));
                LOGGER.info("Leaves blow along the wind of {}", candidate[0]);
                return new WeatherWind(course);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                LOGGER.warn("{} is installed, but its wind could not be found", candidate[0], e);
            }
        }
        return null;
    }

    @Override
    public boolean read(float[] out) {
        var level = Minecraft.getInstance().level;
        if (broken || level == null) {
            return false;
        }
        try {
            float angle = (float) course.invokeExact((double) level.getGameTime());
            if (!Float.isFinite(angle)) {
                return false;
            }
            out[0] = Mth.cos(angle);
            out[1] = Mth.sin(angle);
            out[2] = Float.NaN;
            return true;
        } catch (Throwable e) {
            broken = true;
            LOGGER.warn("Reading the wind course failed; leaves blow with their own wind from now on", e);
            return false;
        }
    }
}
