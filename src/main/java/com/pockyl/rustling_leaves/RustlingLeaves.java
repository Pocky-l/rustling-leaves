package com.pockyl.rustling_leaves;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.network.ModNetwork;
import com.pockyl.rustling_leaves.registry.ModDataComponents;
import com.pockyl.rustling_leaves.registry.ModEntities;
import com.pockyl.rustling_leaves.registry.ModItems;
import com.pockyl.rustling_leaves.registry.PockyModsTab;

/**
 * Rustling Leaves. The leaves themselves are client-side (see {@code client.RustlingLeavesClient}) and also work on
 * servers without the mod; installed on a server, the mod adds the leaf tools.
 */
@Mod(RustlingLeaves.MOD_ID)
public final class RustlingLeaves {
    public static final String MOD_ID = "rustling_leaves";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RustlingLeaves(IEventBus modBus) {
        ModDataComponents.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        modBus.addListener(ModNetwork::register);
        PockyModsTab.register(modBus, () -> new ItemStack(ModItems.LEAF_BLOWER.get()), output -> {
            output.accept(ModItems.LEAF_BLOWER);
            output.accept(ModItems.LEAF_BAG);
            output.accept(ModItems.AUTUMN_BOMB);
            output.accept(ModItems.WIND_STAFF);
        });
        modBus.addListener(RustlingLeaves::addToVanillaTabs);
    }

    private static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.LEAF_BLOWER);
            event.accept(ModItems.LEAF_BAG);
            event.accept(ModItems.WIND_STAFF);
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.AUTUMN_BOMB);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
