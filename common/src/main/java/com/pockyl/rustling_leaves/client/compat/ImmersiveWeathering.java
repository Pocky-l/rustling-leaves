package com.pockyl.rustling_leaves.client.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.sim.PileBlocks;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Leaf piles of <a href="https://modrinth.com/mod/immersive-weather-renewed">Immersive Weathering</a>: the litter draws
 * them, so their own block models are hidden while it does. Found by class name, so there is no dependency; piles on
 * water (layer 0) keep their model.
 */
public final class ImmersiveWeathering implements PileBlocks {
    public static final String MOD_ID = "immersive_weathering";
    private static final String PILE_CLASS = "com.ordana.immersive_weathering.blocks.LeafPileBlock";

    private static volatile ImmersiveWeathering instance;
    /** Whether the pile models are hidden (read by chunk meshing threads). */
    private static volatile boolean hidden;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<Block, IntegerProperty> piles = new IdentityHashMap<>();

    private ImmersiveWeathering() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isPile(block.getClass()) && block.getStateDefinition().getProperty("layers") instanceof IntegerProperty layers) {
                piles.put(block, layers);
            }
        }
    }

    /** The leaf piles of the installed mod; only call it once the blocks are registered (it is cached). */
    public static ImmersiveWeathering piles() {
        ImmersiveWeathering piles = instance;
        if (piles == null) {
            synchronized (ImmersiveWeathering.class) {
                if (instance == null) {
                    instance = new ImmersiveWeathering();
                    LOGGER.info("Drawing {} Immersive Weathering leaf pile blocks as leaf litter", instance.piles.size());
                }
                piles = instance;
            }
        }
        return piles;
    }

    @Override
    public int layers(BlockState state) {
        IntegerProperty layers = piles.get(state.getBlock());
        return layers == null ? 0 : state.getValue(layers);
    }

    public static boolean hidden() {
        return hidden;
    }

    /** Shows or hides the pile models; the caller rebuilds the chunk meshes when this changes. */
    public static void setHidden(boolean hide) {
        hidden = hide;
    }

    /**
     * The model locations of every pile state on the ground. The loaders wrap these models into ones that draw nothing
     * while {@link #hidden()}, so the piles can be hidden without reloading resources.
     */
    public static Set<ModelResourceLocation> pileModels() {
        ImmersiveWeathering piles = piles();
        Set<ModelResourceLocation> models = new HashSet<>();
        for (Block block : piles.piles.keySet()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (piles.layers(state) > 0) {
                    models.add(BlockModelShaper.stateToModelLocation(state));
                }
            }
        }
        return models;
    }

    private static boolean isPile(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(PILE_CLASS)) {
                return true;
            }
        }
        return false;
    }
}
