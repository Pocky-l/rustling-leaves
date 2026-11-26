package com.pockyl.rustling_leaves.sim;

import net.minecraft.util.Mth;

/**
 * How the leaves of one kind of tree look and behave: whether the tree drops any, its leaf texture (sprites, shadow and
 * pile textures), its colours, how often it sheds, how heavy its leaves are, how well they glide, their size and how long
 * they lie. Players set these per leaves block (client {@code TreeConfig}); unset values follow the options and the
 * leaf shape. Immutable: a config change builds new profiles.
 */
public final class TreeProfile {
    /** Share of shed needles that are dead (brown) unless a tree says otherwise; the rest fell green. */
    public static final float DEAD_NEEDLES = 0.8F;
    /** Pile texture sets of the built-in shapes: broad leaves and conifer needles. */
    public static final int LAYERS_LEAVES = 0;
    public static final int LAYERS_NEEDLES = 1;
    /** Shadow sprites of the built-in shapes (client {@code LeafShapes.SHADOW_SPRITES}). */
    public static final int SHADOW_LEAF = 0;
    public static final int SHADOW_PETAL = 1;
    public static final int SHADOW_NEEDLE = 3;

    private static final TreeProfile[] DEFAULTS = new TreeProfile[LeafShape.values().length];

    static {
        for (LeafShape shape : LeafShape.values()) {
            DEFAULTS[shape.ordinal()] = new Builder(shape).build();
        }
    }

    public final LeafShape shape;
    /** Off: this mod leaves the tree alone - no falling leaves, no carpet, no burst when it is broken. */
    public final boolean enabled;
    /** Whether new ground under these trees starts with a natural leaf carpet. */
    public final boolean carpet;
    /** Whether the texture is the shape's own (then the option {@code oldLeafTextures} applies to it). */
    public final boolean defaultTexture;
    public final int firstSprite;
    public final int variants;
    public final int shadow;
    public final int layers;
    /** The leaves' own colours (0xRRGGBB, each leaf takes one), or null for the tree colour. */
    public final int[] colors;
    /** The colours leaves turn to (dead needles for conifers), or null for the mod's palette. */
    public final int[] autumnColors;
    /** Share of leaves in autumn colours (dead needles for conifers), NaN for the option. */
    public final float autumn;
    /** How often the tree sheds, relative to the fall rate option; NaN for the shape's rate. */
    public final float fallRate;
    /** Heavier leaves fall faster and follow the wind less. */
    public final float weight;
    /** How much the leaves sway and glide on their way down; 0 drops them straight. */
    public final float glide;
    public final float size;
    /** In-game days the litter of this tree lies before it rots; NaN for the option, 0 forever. */
    public final float lifetime;

    private TreeProfile(Builder b) {
        shape = b.shape;
        enabled = b.enabled;
        carpet = b.carpet;
        defaultTexture = b.defaultTexture;
        firstSprite = b.firstSprite;
        variants = Math.max(1, b.variants);
        shadow = b.shadow;
        layers = b.layers;
        colors = b.colors == null || b.colors.length == 0 ? null : b.colors.clone();
        autumnColors = b.autumnColors == null || b.autumnColors.length == 0 ? null : b.autumnColors.clone();
        autumn = b.autumn;
        fallRate = b.fallRate;
        weight = Mth.clamp(b.weight, 0.2F, 5.0F);
        glide = Mth.clamp(b.glide, 0.0F, 4.0F);
        size = Mth.clamp(b.size, 0.2F, 4.0F);
        lifetime = b.lifetime;
    }

    /** The profile of leaves of a shape when nothing is known about their tree. */
    public static TreeProfile defaults(LeafShape shape) {
        return DEFAULTS[shape.ordinal()];
    }

    /** A random sprite of this tree's texture. */
    public int sprite(float random) {
        return firstSprite + Math.min(variants - 1, (int) (random * variants));
    }

    /** Which of the texture's sprites a sprite index is (stored in the litter). */
    public int variant(int sprite) {
        return Mth.clamp(sprite - firstSprite, 0, 15);
    }

    /** The sprite index of a stored variant; variants of another texture (the config changed) wrap around. */
    public int spriteOf(int variant) {
        return firstSprite + Math.floorMod(variant, variants);
    }

    /** Half-size of a new leaf in blocks. */
    public float leafSize(LeafSettings settings, float random) {
        return LeafShape.BASE_SIZE * shape.size * size * settings.leafSize * (0.8F + random * 0.45F);
    }

    /** How often the tree sheds relative to the fall rate option. */
    public float shedRate(LeafSettings settings) {
        if (!enabled) {
            return 0.0F;
        }
        return Float.isNaN(fallRate) ? settings.shedRate(shape) : fallRate;
    }

    /** Share of newly fallen leaves that are turned (autumn colours, dead needles), with the season's boost. */
    public float autumnShare(LeafSettings settings) {
        if (shape == LeafShape.NEEDLE) {
            return Float.isNaN(autumn) ? DEAD_NEEDLES : autumn;
        }
        if (!turns()) {
            return 0.0F;
        }
        return Math.min(1.0F, (Float.isNaN(autumn) ? settings.autumnColors : autumn) * settings.seasonAutumnColors);
    }

    /** Share of turned leaves in the litter, which has been lying a while: a bit more than among falling leaves. */
    public float agedShare(LeafSettings settings) {
        if (shape == LeafShape.NEEDLE) {
            return Float.isNaN(autumn) ? DEAD_NEEDLES : autumn;
        }
        if (!turns()) {
            return 0.0F;
        }
        float share = Float.isNaN(autumn) ? settings.autumnColors : autumn;
        return share <= 0.0F ? 0.0F : Math.min(1.0F, share + 0.3F);
    }

    /** Whether these leaves change colour at all: broad leaves do, petals only if the player gave them autumn colours. */
    private boolean turns() {
        return shape.autumn || autumnColors != null || !Float.isNaN(autumn);
    }

    /** Rot of this tree's litter per tick in natural carpets: its own pace plus the season's. */
    public float decayRate(LeafSettings settings) {
        float days = Float.isNaN(lifetime) ? settings.litterLifetime : lifetime;
        float own = days > 0.0F ? 1.0F / (days * 24000.0F) : 0.0F;
        return own + settings.seasonDecay;
    }

    /**
     * Gives one leaf its own colour: one of the tree's colours (or the tree colour itself), turned to an autumn colour
     * (a dead needle) for a share of the leaves, then a slightly different brightness. A turned leaf keeps at most a
     * fifth of its green: halfway between green and orange is olive, which looks like dirt. Deterministic in
     * {@code hash}.
     */
    public int vary(int base, long hash, float turnedShare) {
        float pick = (hash & 0xFFFF) / 65536.0F;
        int color = colors != null ? colors[(int) ((hash >>> 52) & 0xFF) % colors.length] : base;
        if (pick < turnedShare) {
            int[] palette = autumnColors != null ? autumnColors
                    : shape == LeafShape.NEEDLE ? LeafPalette.NEEDLE_RUST : LeafPalette.AUTUMN;
            int turned = palette[(int) ((hash >>> 16) & 0xFF) % palette.length];
            float amount = autumnColors != null ? 1.0F
                    : shape == LeafShape.NEEDLE ? 0.85F + ((hash >>> 24) & 0xFF) / 255.0F * 0.15F
                    : 0.8F + ((hash >>> 24) & 0xFF) / 255.0F * 0.2F;
            color = LeafPalette.lerp(color, turned, amount);
        }
        float brightness = 0.93F + ((hash >>> 32) & 0xFF) / 255.0F * 0.17F;
        return LeafPalette.scale(color, brightness);
    }

    /** Builds a profile; every value starts at the shape's default. */
    public static final class Builder {
        private final LeafShape shape;
        private boolean enabled = true;
        private boolean carpet = true;
        private boolean defaultTexture = true;
        private int firstSprite;
        private int variants;
        private int shadow;
        private int layers;
        private int[] colors;
        private int[] autumnColors;
        private float autumn = Float.NaN;
        private float fallRate = Float.NaN;
        private float weight = 1.0F;
        private float glide = 1.0F;
        private float size = 1.0F;
        private float lifetime = Float.NaN;

        public Builder(LeafShape shape) {
            this.shape = shape;
            firstSprite = shape.firstSprite;
            variants = shape.variants;
            shadow = shape == LeafShape.PETAL ? SHADOW_PETAL : shape == LeafShape.NEEDLE ? SHADOW_NEEDLE : SHADOW_LEAF;
            layers = shape == LeafShape.NEEDLE ? LAYERS_NEEDLES : LAYERS_LEAVES;
        }

        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        public Builder carpet(boolean value) {
            carpet = value;
            return this;
        }

        /** Another texture than the shape's own. */
        public Builder texture(int firstSprite, int variants, int shadow, int layers) {
            this.defaultTexture = false;
            this.firstSprite = firstSprite;
            this.variants = variants;
            this.shadow = shadow;
            this.layers = layers;
            return this;
        }

        public Builder colors(int[] value) {
            colors = value;
            return this;
        }

        public Builder autumnColors(int[] value) {
            autumnColors = value;
            return this;
        }

        public Builder autumn(float value) {
            autumn = value;
            return this;
        }

        public Builder fallRate(float value) {
            fallRate = value;
            return this;
        }

        public Builder weight(float value) {
            weight = value;
            return this;
        }

        public Builder glide(float value) {
            glide = value;
            return this;
        }

        public Builder size(float value) {
            size = value;
            return this;
        }

        public Builder lifetime(float value) {
            lifetime = value;
            return this;
        }

        public TreeProfile build() {
            return new TreeProfile(this);
        }
    }
}
