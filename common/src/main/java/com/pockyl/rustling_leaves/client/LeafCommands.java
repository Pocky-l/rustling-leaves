package com.pockyl.rustling_leaves.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.network.chat.Component;

import com.pockyl.rustling_leaves.Config;

import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Client commands (work on any server, nothing is sent to it): {@code /rustlingleaves fallrate [value]} shows or sets
 * how many leaves fall from the trees (option {@code leaves.fallRate}, saved to the config file).
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

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}
