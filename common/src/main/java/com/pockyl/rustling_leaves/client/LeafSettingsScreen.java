package com.pockyl.rustling_leaves.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * The mod's config button: the options of the whole mod, or the settings of single trees ({@link TreeEditorScreen}).
 */
public final class LeafSettingsScreen extends Screen {
    private final Screen parent;
    private final Function<Screen, Screen> options;

    /** @param options makes the loader's screen of the config options, with this screen as its parent */
    public LeafSettingsScreen(Screen parent, Function<Screen, Screen> options) {
        super(Component.translatable("rustling_leaves.settings.title"));
        this.parent = parent;
        this.options = options;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 34;
        Button optionsButton = Button.builder(Component.translatable("rustling_leaves.settings.options"),
                button -> minecraft.setScreen(options.apply(this))).bounds(x, y, 200, 20).build();
        optionsButton.setTooltip(Tooltip.create(Component.translatable("rustling_leaves.settings.options.tooltip")));
        addRenderableWidget(optionsButton);
        Button treesButton = Button.builder(Component.translatable("rustling_leaves.settings.trees"),
                button -> minecraft.setScreen(new TreeEditorScreen(this, null))).bounds(x, y + 24, 200, 20).build();
        treesButton.setTooltip(Tooltip.create(Component.translatable("rustling_leaves.settings.trees.tooltip")));
        addRenderableWidget(treesButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, y + 56, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 58, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
