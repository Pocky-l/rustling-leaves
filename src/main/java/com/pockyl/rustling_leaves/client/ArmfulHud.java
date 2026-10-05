package com.pockyl.rustling_leaves.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;

/** A leaf icon and the number of leaves carried in the arms, next to the crosshair. */
final class ArmfulHud {
    private ArmfulHud() {
    }

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int count = LeafManager.armfulCount();
        Minecraft minecraft = Minecraft.getInstance();
        if (count == 0 || minecraft.options.hideGui) {
            return;
        }
        int x = graphics.guiWidth() / 2 + 10;
        int y = graphics.guiHeight() / 2 + 6;
        TextureAtlasSprite leaf = minecraft.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(LeafShapes.SPRITES[1]);
        graphics.blit(x, y, 0, 12, 12, leaf, 0.86F, 0.55F, 0.22F, 1.0F);
        graphics.drawString(minecraft.font, Component.translatable("rustling_leaves.hud.armful", count), x + 14, y + 2, 0xFFE8C78A, true);
    }
}
