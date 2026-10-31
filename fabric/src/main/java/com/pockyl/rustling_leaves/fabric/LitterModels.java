package com.pockyl.rustling_leaves.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import com.pockyl.rustling_leaves.client.LeafLitterModels;

import java.util.List;
import java.util.function.Predicate;

/** Wraps the models of vanilla leaf litter, so the litter can stand in for them (see {@link LeafLitterModels}). */
final class LitterModels {
    private LitterModels() {
    }

    static void register(ModelLoadingPlugin.Context context) {
        context.modifyBlockModelAfterBake().register((model, modelContext) ->
                LeafLitterModels.wraps(modelContext.state()) && !(model instanceof Hideable) ? new Hideable(model) : model);
    }

    /**
     * A leaf litter model that has no parts while the litter draws the leaf litter, for vanilla's chunk meshes and for
     * renderers of Fabric's renderer API (Sodium) alike.
     */
    private static final class Hideable extends WrapperBlockStateModel {
        Hideable(BlockStateModel wrapped) {
            super(wrapped);
        }

        @Override
        public void collectParts(RandomSource random, List<BlockModelPart> parts) {
            if (!LeafLitterModels.hidden()) {
                super.collectParts(random, parts);
            }
        }

        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                Predicate<@Nullable Direction> cullTest) {
            if (!LeafLitterModels.hidden()) {
                super.emitQuads(emitter, level, pos, state, random, cullTest);
            }
        }

        /** No geometry key: what the model draws changes with {@link LeafLitterModels#hidden}. */
        @Override
        @Nullable
        public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return null;
        }
    }
}
