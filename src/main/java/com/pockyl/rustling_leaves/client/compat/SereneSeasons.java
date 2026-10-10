package com.pockyl.rustling_leaves.client.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.SeasonCurve;

/**
 * The season of <a href="https://www.curseforge.com/minecraft/mc-mods/serene-seasons">Serene Seasons</a>, which syncs
 * it to clients. Only loaded when the mod is installed; nothing else touches its classes.
 */
public final class SereneSeasons {
    public static final String MOD_ID = "sereneseasons";

    private static boolean failed;

    private SereneSeasons() {
    }

    /** Sets the season multipliers of the settings for the season at {@code pos} (no season where it has none). */
    public static void update(Level level, BlockPos pos, LeafSettings settings) {
        if (failed) {
            SeasonCurve.clear(settings);
            return;
        }
        try {
            ISeasonState state = state(level, pos);
            if (state == null) {
                SeasonCurve.clear(settings);
            } else {
                int duration = state.getSubSeasonDuration();
                float position = (float) Math.floorMod(state.getSeasonCycleTicks(), duration * SeasonCurve.SUB_SEASONS) / duration;
                SeasonCurve.apply(settings, position, duration);
            }
        } catch (RuntimeException | LinkageError e) {
            // A changed Serene Seasons must not take the leaves down with it; summer all year is a fine fallback.
            failed = true;
            SeasonCurve.clear(settings);
            RustlingLeaves.LOGGER.warn("Could not read the season from Serene Seasons, leaf fall ignores seasons", e);
        }
    }

    /** The season state at {@code pos} (its sub-season length in ticks is positive), or null where there are no seasons. */
    private static ISeasonState state(Level level, BlockPos pos) {
        // The whitelist is not part of the API, but outside it the client state reads as early spring instead of none.
        if (ModConfig.seasons == null || !ModConfig.seasons.isDimensionWhitelisted(level.dimension())
                || SeasonHelper.usesTropicalSeasons(level.getBiome(pos))) {
            return null;
        }
        ISeasonState state = SeasonHelper.getSeasonState(level);
        return state == null || state.getSubSeasonDuration() <= 0 ? null : state;
    }
}
