package com.pockyl.rustling_leaves.client.compat;

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

    private static ImmersiveWeathering instance;
    /** Whether the pile models are hidden (read by chunk meshing threads). */
    private static volatile boolean hidden;

    private final Map<Block, IntegerProperty> piles = new IdentityHashMap<>();

    private ImmersiveWeathering() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isPile(block.getClass()) && block.getStateDefinition().getProperty("layers") instanceof IntegerProperty layers) {
                piles.put(block, layers);
            }
        }
    }

    /** The leaf piles of the installed mod (registries must be complete). */
    public static synchronized ImmersiveWeathering piles() {
        if (instance == null) {
            instance = new ImmersiveWeathering();
        }
        return instance;
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
        for (Block block : piles.piles.keySet()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (piles.layers(state) > 0) {
                    models.computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new HiddenModel(model));
                }
            }
        }
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
