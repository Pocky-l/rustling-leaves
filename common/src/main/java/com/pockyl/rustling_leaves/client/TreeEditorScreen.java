package com.pockyl.rustling_leaves.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.rustling_leaves.sim.LeafPalette;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.TreeProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * The settings of single trees in game: every leaves block on the left (and "every tree" on top), the chosen tree on
 * the right - a little window of its leaves falling and lying as they would, and its settings. Changes reach the
 * trees in the world when a control is let go (the game keeps running behind the screen, without blur); Done writes
 * the file ({@link TreeConfig}).
 */
public final class TreeEditorScreen extends Screen {
    private static final String ALL = "*";
    private static final int LIST_WIDTH = 150;
    private static final int ROW = 22;
    private static final int PREVIEW_HEIGHT = 58;
    private static final int PREVIEW_LEAVES = 16;
    private static final String[] TEXTURES;

    static {
        List<String> names = new ArrayList<>();
        names.add("auto");
        names.addAll(LeafStyles.names());
        TEXTURES = names.toArray(new String[0]);
    }

    private final Screen parent;
    private String key;
    private TreeConfig.Entry entry;
    private TreeProfile profile;
    private TreeList list;
    private EditBox search;
    private final List<String> keys = new ArrayList<>();
    private final Preview preview = new Preview();
    private boolean dirty;
    private String searchText = "";
    private double scroll;

    /** @param select the leaves block to show first, or null */
    public TreeEditorScreen(Screen parent, Block select) {
        super(Component.translatable("rustling_leaves.trees.title"));
        this.parent = parent;
        this.key = select != null ? BuiltInRegistries.BLOCK.getKey(select).toString() : ALL;
        this.entry = TreeConfig.entry(key);
        this.profile = TreeConfig.resolve(key);
        collectKeys();
    }

    /** Every leaves block, then the keys of the file that are not blocks (mods, tags). */
    private void collectKeys() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof LeavesBlock || block.defaultBlockState().is(BlockTags.LEAVES)) {
                blocks.add(block);
            }
        }
        blocks.sort(Comparator.comparing(b -> b.getName().getString().toLowerCase(Locale.ROOT)));
        keys.add(ALL);
        for (Block block : blocks) {
            keys.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
    }

    @Override
    protected void init() {
        search = new EditBox(font, 6, 20, LIST_WIDTH, 16, Component.translatable("rustling_leaves.trees.search"));
        search.setHint(Component.translatable("rustling_leaves.trees.search"));
        search.setValue(searchText);
        search.setResponder(text -> {
            searchText = text;
            list.refill(text);
        });
        addRenderableWidget(search);
        list = new TreeList(minecraft, LIST_WIDTH, height - 40 - 30, 40);
        list.setX(6);
        addRenderableWidget(list);
        list.refill(searchText);
        list.setScrollAmount(scroll);
        buildControls();
        Button reset = Button.builder(Component.translatable("rustling_leaves.trees.reset"), button -> {
            entry = new TreeConfig.Entry();
            apply();
            scroll = list.scrollAmount();
            rebuildWidgets();
        }).bounds(width - 6 - 204, height - 26, 100, 20).build();
        reset.setTooltip(Tooltip.create(Component.translatable("rustling_leaves.trees.reset.tooltip")));
        reset.active = TreeConfig.has(key);
        addRenderableWidget(reset);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 6 - 100, height - 26, 100, 20).build());
    }

    private int panelX() {
        return 6 + LIST_WIDTH + 10;
    }

    private int panelWidth() {
        return width - panelX() - 6;
    }

    private void buildControls() {
        LeafSettings settings = LeafManager.settings();
        int x = panelX();
        int columnWidth = (panelWidth() - 4) / 2;
        int x2 = x + columnWidth + 4;
        int y = 34 + PREVIEW_HEIGHT + 6;

        CycleButton<Boolean> enabled = CycleButton.onOffBuilder(profile.enabled).create(x, y, columnWidth, 20,
                Component.translatable("rustling_leaves.trees.enabled"), (button, value) -> {
                    entry.enabled = value ? null : Boolean.FALSE;
                    apply();
                });
        enabled.setTooltip(Tooltip.create(Component.translatable("rustling_leaves.trees.enabled.tooltip")));
        addRenderableWidget(enabled);
        CycleButton<Boolean> carpet = CycleButton.onOffBuilder(profile.carpet).create(x2, y, columnWidth, 20,
                Component.translatable("rustling_leaves.trees.carpet"), (button, value) -> {
                    entry.carpet = value ? null : Boolean.FALSE;
                    apply();
                });
        carpet.setTooltip(Tooltip.create(Component.translatable("rustling_leaves.trees.carpet.tooltip")));
        addRenderableWidget(carpet);

        y += ROW;
        List<String> textures = new ArrayList<>(List.of(TEXTURES));
        String current = TreeConfig.textureName(entry);
        if (!textures.contains(current)) {
            textures.add(current);
        }
        CycleButton<String> texture = CycleButton.<String>builder(name -> Component.translatable("rustling_leaves.trees.texture." + name),
                        current)
                .withValues(textures)
                .create(x, y, columnWidth, 20, Component.translatable("rustling_leaves.trees.texture"), (button, value) -> {
                    if (!"custom".equals(value)) {
                        entry.texture = "auto".equals(value) ? null : List.of(value);
                        apply();
                    }
                });
        texture.setTooltip(Tooltip.create(Component.translatable("rustling_leaves.trees.texture.tooltip")));
        addRenderableWidget(texture);
        float shedDefault = Float.isNaN(profile.fallRate) ? profile.shedRate(settings) : profile.fallRate;
        addRenderableWidget(new Slider(x2, y, columnWidth, "fall_rate", 0.0, 5.0, shedDefault,
                v -> String.format(Locale.ROOT, "%.2f", v), v -> entry.fallRate = (float) v));

        y += ROW;
        addRenderableWidget(new Slider(x, y, columnWidth, "weight", 0.2, 5.0, profile.weight,
                v -> String.format(Locale.ROOT, "%.2f", v), v -> entry.weight = (float) v));
        addRenderableWidget(new Slider(x2, y, columnWidth, "glide", 0.0, 4.0, profile.glide,
                v -> String.format(Locale.ROOT, "%.2f", v), v -> entry.glide = (float) v));

        y += ROW;
        addRenderableWidget(new Slider(x, y, columnWidth, "size", 0.2, 4.0, profile.size,
                v -> String.format(Locale.ROOT, "%.2f", v), v -> entry.size = (float) v));
        float lifetime = Float.isNaN(profile.lifetime) ? settings.litterLifetime : profile.lifetime;
        addRenderableWidget(new Slider(x2, y, columnWidth, "lifetime", 0.0, 60.0, lifetime,
                v -> v < 0.05 ? Component.translatable("rustling_leaves.trees.lifetime.forever").getString()
                        : String.format(Locale.ROOT, "%.1f", v), v -> entry.lifetime = (float) (v < 0.05 ? 0.0 : v)));

        y += ROW;
        float autumn = !Float.isNaN(profile.autumn) ? profile.autumn
                : profile.shape == LeafShape.NEEDLE ? TreeProfile.DEAD_NEEDLES
                : profile.shape.autumn ? settings.autumnColors : 0.0F;
        addRenderableWidget(new Slider(x, y, columnWidth, "autumn", 0.0, 1.0, autumn,
                v -> Math.round(v * 100.0) + "%", v -> entry.autumn = (float) v));
        EditBox autumnColors = colorBox(x2, y, columnWidth, "autumn_colors", entry.autumnColors, colors -> entry.autumnColors = colors);
        addRenderableWidget(autumnColors);

        y += ROW;
        EditBox colors = colorBox(x, y, panelWidth(), "colors", entry.colors, value -> entry.colors = value);
        addRenderableWidget(colors);
    }

    /** A text field of colours ({@code #RRGGBB, ...}): empty for the default, red while it cannot be read. */
    private EditBox colorBox(int x, int y, int w, String name, int[] value, Consumer<int[]> set) {
        EditBox box = new EditBox(font, x + 1, y + 1, w - 2, 18, Component.translatable("rustling_leaves.trees." + name));
        box.setMaxLength(32 * 9);
        box.setValue(TreeConfig.formatColors(value));
        box.setHint(Component.translatable("rustling_leaves.trees." + name + ".hint"));
        box.setTooltip(Tooltip.create(Component.translatable("rustling_leaves.trees." + name + ".tooltip")));
        box.setResponder(text -> {
            int[] parsed = TreeConfig.parseColors(text);
            box.setTextColor(parsed == null ? 0xFFFF6060 : 0xFFE0E0E0);
            if (parsed != null) {
                set.accept(parsed.length == 0 ? null : parsed);
                apply();
            }
        });
        return box;
    }

    /** Stores the entry of the chosen tree: the preview and the trees in the world follow. */
    private void apply() {
        TreeConfig.set(key, entry);
        profile = TreeConfig.resolve(key);
        preview.reset();
        dirty = true;
    }

    private void select(String newKey) {
        if (newKey.equals(key)) {
            return;
        }
        key = newKey;
        scroll = list.scrollAmount();
        entry = TreeConfig.entry(key);
        profile = TreeConfig.resolve(key);
        preview.reset();
        rebuildWidgets();
    }

    private Block block(String k) {
        Identifier id = Identifier.tryParse(k);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
    }

    private Component name(String k) {
        if (ALL.equals(k)) {
            return Component.translatable("rustling_leaves.trees.all");
        }
        Block block = block(k);
        return block != null ? block.getName() : Component.literal(k);
    }

    @Override
    public void tick() {
        preview.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, 6, 6, 0xFFFFFFFF);
        int x = panelX();
        Block block = block(key);
        ItemStack icon = block != null ? new ItemStack(block.asItem()) : new ItemStack(Items.OAK_SAPLING);
        graphics.renderItem(icon, x, 14);
        graphics.drawString(font, name(key), x + 20, 18, 0xFFFFFFFF);
        preview.render(graphics, x, 34, panelWidth(), PREVIEW_HEIGHT, partialTick, block);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            return;
        }
        // No blur: the trees behind the screen show the changes as they are made.
        graphics.fill(0, 0, 6 + LIST_WIDTH + 4, height, 0xA0000000);
        graphics.fill(panelX() - 4, 0, width, height, 0x90000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (dirty) {
            TreeConfig.save();
        }
        minecraft.setScreen(parent);
    }

    /** A slider of one setting: shows the effective value, sets the entry's value when let go. */
    private final class Slider extends AbstractSliderButton {
        private final String name;
        private final double min;
        private final double max;
        private final DoubleFunction<String> format;
        private final DoubleConsumer set;

        Slider(int x, int y, int w, String name, double min, double max, double value, DoubleFunction<String> format,
                DoubleConsumer set) {
            super(x, y, w, 20, Component.empty(), (Mth.clamp(value, min, max) - min) / (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.format = format;
            this.set = set;
            setTooltip(Tooltip.create(Component.translatable("rustling_leaves.trees." + name + ".tooltip")));
            updateMessage();
        }

        private double current() {
            return min + (max - min) * value;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("rustling_leaves.trees." + name, format.apply(current())));
        }

        @Override
        protected void applyValue() {
            set.accept(current());
            // The preview follows at once; the world when the slider is let go.
            profile = previewProfile();
            preview.reset();
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            apply();
        }
    }

    private TreeProfile previewProfile() {
        return TreeConfig.resolve(key, entry);
    }

    /** The trees: "every tree" first, then every leaves block, filtered by the search. */
    private final class TreeList extends ObjectSelectionList<TreeList.Row> {
        TreeList(Minecraft minecraft, int w, int h, int y) {
            super(minecraft, w, h, y, 20);
        }

        void refill(String filter) {
            clearEntries();
            String needle = filter.trim().toLowerCase(Locale.ROOT);
            for (String k : keys) {
                if (needle.isEmpty() || k.contains(needle) || name(k).getString().toLowerCase(Locale.ROOT).contains(needle)) {
                    Row row = new Row(k);
                    addEntry(row);
                    if (k.equals(key)) {
                        setSelected(row);
                    }
                }
            }
        }

        @Override
        public int getRowWidth() {
            return width - 8;
        }

        @Override
        protected int scrollBarX() {
            return getX() + width - 6;
        }

        final class Row extends ObjectSelectionList.Entry<Row> {
            private final String rowKey;
            private final ItemStack icon;

            Row(String rowKey) {
                this.rowKey = rowKey;
                Block block = block(rowKey);
                this.icon = block != null ? new ItemStack(block.asItem()) : new ItemStack(Items.OAK_SAPLING);
            }

            @Override
            public Component getNarration() {
                return name(rowKey);
            }

            @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
                int left = getContentX();
                int top = getContentY();
                int w = getContentWidth();
                graphics.renderItem(icon, left + 1, top + 1);
                boolean own = TreeConfig.has(rowKey);
                boolean off = !TreeConfig.resolve(rowKey).enabled;
                int color = off ? 0xFF808080 : own ? 0xFFFFE07A : 0xFFFFFFFF;
                String text = font.plainSubstrByWidth(name(rowKey).getString(), w - 24);
                graphics.drawString(font, text, left + 20, top + 5, color);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                TreeList.this.setSelected(this);
                select(rowKey);
                return true;
            }
        }
    }

    /**
     * Leaves of the chosen tree falling in a little window onto a strip of its litter: their textures, colours, size,
     * how fast they fall and how much they sway and glide.
     */
    private final class Preview {
        private final RandomSource random = RandomSource.create();
        private final float[] x = new float[PREVIEW_LEAVES];
        private final float[] y = new float[PREVIEW_LEAVES];
        private final float[] prevX = new float[PREVIEW_LEAVES];
        private final float[] prevY = new float[PREVIEW_LEAVES];
        private final float[] phase = new float[PREVIEW_LEAVES];
        private final float[] angle = new float[PREVIEW_LEAVES];
        private final int[] sprite = new int[PREVIEW_LEAVES];
        private final int[] color = new int[PREVIEW_LEAVES];
        private final float[] size = new float[PREVIEW_LEAVES];
        private boolean started;

        void reset() {
            started = false;
        }

        private void spawn(int i, boolean anywhere) {
            LeafSettings settings = LeafManager.settings();
            x[i] = random.nextFloat();
            y[i] = anywhere ? random.nextFloat() : -0.1F - random.nextFloat() * 0.3F;
            prevX[i] = x[i];
            prevY[i] = y[i];
            phase[i] = random.nextFloat() * Mth.TWO_PI;
            angle[i] = random.nextFloat() * Mth.TWO_PI;
            sprite[i] = profile.sprite(random.nextFloat());
            color[i] = profile.vary(baseColor, random.nextLong(), profile.autumnShare(settings));
            size[i] = profile.leafSize(settings, random.nextFloat()) / 0.1F;
        }

        private int baseColor;

        void tick() {
            if (!started) {
                started = true;
                Block block = block(key);
                BlockState state = block != null ? block.defaultBlockState() : null;
                baseColor = LeafManager.treeColor(state);
                for (int i = 0; i < PREVIEW_LEAVES; i++) {
                    spawn(i, true);
                }
            }
            // Same feel as the simulation: heavier falls faster, gliding falls slower and sways wider.
            float fall = 0.012F * Mth.sqrt(profile.weight) / (0.75F + 0.25F * profile.glide);
            for (int i = 0; i < PREVIEW_LEAVES; i++) {
                prevX[i] = x[i];
                prevY[i] = y[i];
                phase[i] += 0.12F;
                y[i] += fall * (0.8F + 0.4F * Mth.abs(Mth.sin(phase[i])));
                x[i] += Mth.cos(phase[i]) * 0.006F * profile.glide;
                angle[i] += Mth.sin(phase[i]) * 0.05F * (0.3F + profile.glide);
                if (y[i] > 1.05F) {
                    spawn(i, false);
                }
            }
        }

        void render(GuiGraphics graphics, int left, int top, int w, int h, float partialTick, Block block) {
            graphics.fill(left, top, left + w, top + h, 0x60000000);
            if (!profile.enabled) {
                graphics.drawCenteredString(font, Component.translatable("rustling_leaves.trees.off"), left + w / 2, top + h / 2 - 4,
                        0xFFA0A0A0);
                return;
            }
            TextureAtlas atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            Identifier[] sprites = LeafStyles.sprites();
            graphics.enableScissor(left, top, left + w, top + h);
            // The litter: a strip of the tree's pile texture along the bottom.
            Identifier[] layer = LeafStyles.layers()[Math.min(profile.layers, LeafStyles.layers().length - 1)];
            TextureAtlasSprite ground = atlas.getSprite(layer[2]);
            int groundColor = profile.vary(baseColor, LeafPalette.hash(1, 2, 3), profile.agedShare(LeafManager.settings()));
            for (int gx = left; gx < left + w; gx += 16) {
                blit(graphics, gx, top + h - 8, 16, 16, ground, groundColor);
            }
            for (int i = 0; i < PREVIEW_LEAVES; i++) {
                int s = sprite[i] < sprites.length ? sprite[i] : 0;
                TextureAtlasSprite leaf = atlas.getSprite(sprites[s]);
                float px = left + Mth.lerp(partialTick, prevX[i], x[i]) * w;
                float py = top + Mth.lerp(partialTick, prevY[i], y[i]) * (h - 6);
                int half = Math.max(3, Math.round(6 * size[i]));
                graphics.pose().pushMatrix();
                graphics.pose().translate(px, py);
                graphics.pose().rotate(angle[i]);
                blit(graphics, -half, -half, half * 2, half * 2, leaf, color[i]);
                graphics.pose().popMatrix();
            }
            graphics.disableScissor();
        }

        private void blit(GuiGraphics graphics, int bx, int by, int bw, int bh, TextureAtlasSprite sprite, int rgb) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, bx, by, bw, bh, 0xFF000000 | rgb);
        }
    }
}
