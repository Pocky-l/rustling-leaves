package com.pockyl.rustling_leaves.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;

import java.util.Locale;

/**
 * Client commands (work on any server, nothing is sent to it): {@code /rustlingleaves fallrate [value]} shows or sets
 * how many leaves fall from the trees (option {@code leaves.fallRate}, saved to the config file).
 */
@EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
public final class LeafCommands {
    private LeafCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rustlingleaves")
                .then(Commands.literal("fallrate")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable("commands.rustling_leaves.fallrate.get",
                                    format(Config.fallRate())), false);
                            return 1;
                        })
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(Config.MIN_FALL_RATE, Config.MAX_FALL_RATE))
                                .executes(context -> {
                                    double value = DoubleArgumentType.getDouble(context, "value");
                                    Config.setFallRate(value);
                                    LeafManager.onConfigChanged();
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "commands.rustling_leaves.fallrate.set", format(value)), false);
                                    return 1;
                                }))));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}
