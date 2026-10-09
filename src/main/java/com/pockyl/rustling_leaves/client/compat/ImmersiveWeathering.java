package com.pockyl.rustling_leaves.client.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.sim.PileBlocks;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

    /** Wraps the model of every pile state on the ground, so it can be hidden without reloading resources. */
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        ImmersiveWeathering piles = piles();
        int wrapped = 0;
        for (Block block : piles.piles.keySet()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (piles.layers(state) > 0
                        && models.computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new HiddenModel(model)) != null) {
                    wrapped++;
                }
            }
        }
        LOGGER.info("Wrapped {} Immersive Weathering leaf pile models", wrapped);
    }

    private static boolean isPile(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(PILE_CLASS)) {
                return true;
            }
        }
        return false;
    }

    /** A pile model that draws nothing while the litter stands in for the pile (breaking particles still work). */
    private static final class HiddenModel extends BakedModelWrapper<BakedModel> {
        HiddenModel(BakedModel original) {
            super(original);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return hidden ? List.of() : super.getQuads(state, side, rand);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                @Nullable RenderType renderType) {
            return hidden ? List.of() : super.getQuads(state, side, rand, data, renderType);
        }
    }
}
