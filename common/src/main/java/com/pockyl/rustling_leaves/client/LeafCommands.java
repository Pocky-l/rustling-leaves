package com.pockyl.rustling_leaves.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.pockyl.rustling_leaves.Config;

import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Client commands (work on any server, nothing is sent to it): {@code /rustlingleaves fallrate [value]} shows or sets
 * how many leaves fall from the trees (option {@code leaves.fallRate}, saved to the config file);
 * {@code /rustlingleaves trees} opens the settings of single trees (on the tree looked at), {@code trees reload} reads
 * their file again.
 */
public final class LeafCommands {
    private LeafCommands() {
    }

    /**
     * The command tree for the loader's client command source {@code S}.
     *
     * @param feedback shows a message to the player
     */
    public static <S> LiteralArgumentBuilder<S> command(BiConsumer<S, Component> feedback) {
        return LiteralArgumentBuilder.<S>literal("rustlingleaves")
                .then(LiteralArgumentBuilder.<S>literal("trees")
                        .executes(context -> {
                            Block looked = lookedAtLeaves();
                            LeafManager.openLater(() -> Minecraft.getInstance().setScreen(new TreeEditorScreen(null, looked)));
                            return 1;
                        })
                        .then(LiteralArgumentBuilder.<S>literal("reload")
                                .executes(context -> {
                                    TreeConfig.load();
                                    feedback.accept(context.getSource(), Component.translatable("commands.rustling_leaves.trees.reload"));
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<S>literal("fallrate")
                        .executes(context -> {
                            feedback.accept(context.getSource(), Component.translatable("commands.rustling_leaves.fallrate.get",
                                    format(Config.fallRate())));
                            return 1;
                        })
                        .then(RequiredArgumentBuilder.<S, Double>argument("value",
                                        DoubleArgumentType.doubleArg(Config.MIN_FALL_RATE, Config.MAX_FALL_RATE))
                                .executes(context -> {
                                    double value = DoubleArgumentType.getDouble(context, "value");
                                    Config.setFallRate(value);
                                    LeafManager.onConfigChanged();
                                    feedback.accept(context.getSource(), Component.translatable(
                                            "commands.rustling_leaves.fallrate.set", format(value)));
                                    return 1;
                                })));
    }

    /** The leaves block the player looks at, or null. */
    private static Block lookedAtLeaves() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && minecraft.level != null) {
            BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
            if (state.is(BlockTags.LEAVES)) {
                return state.getBlock();
            }
        }
        return null;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}
