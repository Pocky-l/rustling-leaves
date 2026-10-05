package com.pockyl.rustling_leaves.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.pockyl.rustling_leaves.RustlingLeaves;

@Mod(value = RustlingLeaves.MOD_ID, dist = Dist.CLIENT)
public final class RustlingLeavesClient {
    public RustlingLeavesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
