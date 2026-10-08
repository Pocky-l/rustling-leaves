package com.pockyl.rustling_leaves;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.client.RustlingLeavesClient;
import com.pockyl.rustling_leaves.network.ModNetwork;
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

    public RustlingLeaves() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModNetwork.register();
        PockyModsTab.register(modBus, () -> new ItemStack(ModItems.LEAF_BLOWER.get()), output -> {
            output.accept(ModItems.LEAF_BLOWER.get());
            output.accept(ModItems.LEAF_BAG.get());
            output.accept(ModItems.AUTUMN_BOMB.get());
            output.accept(ModItems.WIND_STAFF.get());
        });
        modBus.addListener(RustlingLeaves::addToVanillaTabs);
        // The client class is only loaded on a client; a dedicated server never resolves it.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RustlingLeavesClient.init(modBus));
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
        return new ResourceLocation(MOD_ID, path);
    }
}
