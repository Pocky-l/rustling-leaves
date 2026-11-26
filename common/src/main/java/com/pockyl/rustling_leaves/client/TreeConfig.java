package com.pockyl.rustling_leaves.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.TreeKinds;
import com.pockyl.rustling_leaves.sim.TreeProfile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The player's settings of single trees, in {@code config/rustling_leaves-trees.json}: for a leaves block (or all
 * blocks of a tag, all trees of a mod, every tree) whether it drops leaves, its leaf texture, colours, how often it
 * sheds, how heavy its leaves are, how well they glide, their size and how long they lie. Only what the player changed
 * is written; everything else follows the options. The in-game editor ({@link TreeEditorScreen}) writes this file, and
 * modpack makers can edit it by hand; {@code /rustlingleaves trees reload} reads it again.
 * <p>
 * Keys, from weakest to strongest: {@code *} (every tree), {@code #namespace:tag}, {@code namespace:*} and a block id.
 */
public final class TreeConfig {
    /** One entry: every field is optional, null keeps the value of a weaker entry or the default. */
    public static final class Entry {
        public Boolean enabled;
        public Boolean carpet;
        /** A built-in texture name, or block atlas sprite ids. */
        public List<String> texture;
        public int[] colors;
        public int[] autumnColors;
        public Float autumn;
        public Float fallRate;
        public Float weight;
        public Float glide;
        public Float size;
        public Float lifetime;

        boolean isEmpty() {
            return enabled == null && carpet == null && texture == null && colors == null && autumnColors == null && autumn == null
                    && fallRate == null && weight == null && glide == null && size == null && lifetime == null;
        }

        Entry copy() {
            Entry copy = new Entry();
            copy.enabled = enabled;
            copy.carpet = carpet;
            copy.texture = texture == null ? null : new ArrayList<>(texture);
            copy.colors = colors == null ? null : colors.clone();
            copy.autumnColors = autumnColors == null ? null : autumnColors.clone();
            copy.autumn = autumn;
            copy.fallRate = fallRate;
            copy.weight = weight;
            copy.glide = glide;
            copy.size = size;
            copy.lifetime = lifetime;
            return copy;
        }

        private void over(Entry stronger) {
            enabled = stronger.enabled != null ? stronger.enabled : enabled;
            carpet = stronger.carpet != null ? stronger.carpet : carpet;
            texture = stronger.texture != null ? stronger.texture : texture;
            colors = stronger.colors != null ? stronger.colors : colors;
            autumnColors = stronger.autumnColors != null ? stronger.autumnColors : autumnColors;
            autumn = stronger.autumn != null ? stronger.autumn : autumn;
            fallRate = stronger.fallRate != null ? stronger.fallRate : fallRate;
            weight = stronger.weight != null ? stronger.weight : weight;
            glide = stronger.glide != null ? stronger.glide : glide;
            size = stronger.size != null ? stronger.size : size;
            lifetime = stronger.lifetime != null ? stronger.lifetime : lifetime;
        }
    }

    private static final String FILE = RustlingLeaves.MOD_ID + "-trees.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] HELP = {
            "Settings of single trees for Rustling Leaves. The easiest way to change them is the editor in game:",
            "the mod's config button, or the command /rustlingleaves trees (looking at a tree opens that tree).",
            "Keys: a leaves block id (minecraft:oak_leaves), every tree of a mod (biomesoplenty:*), a block tag",
            "(#minecraft:leaves) or every tree (*); a block id beats a mod, a mod beats a tag, a tag beats *.",
            "Every value is optional; what is missing follows the mod's options:",
            "  enabled: false = this mod leaves the tree alone (no falling leaves, no carpet, no burst when broken)",
            "  carpet: false = no natural leaf carpet under the tree (leaves still fall and stay where they land)",
            "  texture: leaf, petal, needle, maple, oak, ginkgo, willow, old_leaf, old_round, old_needle, old_petal,",
            "           or block texture ids of a resource pack, e.g. [\"mypack:block/leaf/a\", \"mypack:block/leaf/b\"]",
            "           (greyscale sprites are tinted with the leaf colours, coloured ones keep their colours under white)",
            "  colors: [\"#E8B64A\", \"#C8552E\"] = each leaf takes one of these instead of the tree colour",
            "  autumn: share of leaves in autumn colours, 0 to 1 (dead needles for conifers)",
            "  autumnColors: the colours those leaves turn to",
            "  fallRate: how often the tree sheds (1 = like an oak, 1.5 birch, 0.15 spruce)",
            "  weight: 0.2 to 5, heavier leaves fall faster and drift less",
            "  glide: 0 to 4, how much leaves sway and glide (0 drops them straight)",
            "  size: 0.2 to 4, leaf size",
            "  lifetime: in-game days the fallen leaves lie before they rot (0 = until something moves them)",
    };

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static boolean ownLifetimes;
    private static volatile int revision;

    private TreeConfig() {
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE);
    }

    /** Reads the file (or keeps the defaults if there is none) and lets every tree pick up its settings again. */
    public static synchronized void load() {
        ENTRIES.clear();
        Path file = file();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject trees = root.has("trees") ? root.getAsJsonObject("trees") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : trees.entrySet()) {
                    if (e.getValue().isJsonObject()) {
                        Entry entry = read(e.getValue().getAsJsonObject());
                        if (!entry.isEmpty()) {
                            ENTRIES.put(e.getKey().trim().toLowerCase(Locale.ROOT), entry);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                RustlingLeaves.LOGGER.warn("Could not read the tree settings {}", file, e);
            }
        }
        changed();
    }

    /** Writes the file. */
    public static synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray help = new JsonArray();
        for (String line : HELP) {
            help.add(line);
        }
        root.add("_help", help);
        JsonObject trees = new JsonObject();
        for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
            trees.add(e.getKey(), write(e.getValue()));
        }
        root.add("trees", trees);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            RustlingLeaves.LOGGER.warn("Could not write the tree settings {}", file, e);
        }
    }

    /** The player's own entry of a key (a copy), or an empty one. */
    public static synchronized Entry entry(String key) {
        Entry entry = ENTRIES.get(key);
        return entry == null ? new Entry() : entry.copy();
    }

    /** Sets (or with an empty entry removes) the entry of a key; trees pick it up at once, {@link #save} writes it. */
    public static synchronized void set(String key, Entry entry) {
        if (entry == null || entry.isEmpty()) {
            ENTRIES.remove(key);
        } else {
            ENTRIES.put(key, entry.copy());
        }
        changed();
    }

    /** Whether the player set anything for this exact key. */
    public static synchronized boolean has(String key) {
        return ENTRIES.containsKey(key);
    }

    /** Whether some tree has a lifetime of its own (its litter may rot while the option keeps leaves forever). */
    public static synchronized boolean ownLifetimes() {
        return ownLifetimes;
    }

    /** Changes with every change of the settings: the renderer rebuilds the litter meshes. */
    public static int revision() {
        return revision;
    }

    private static void changed() {
        revision++;
        ownLifetimes = ENTRIES.values().stream().anyMatch(e -> e.lifetime != null && e.lifetime > 0.0F);
        LeafStyles.clearCustom();
        LeafShapes.clearProfiles();
        TreeKinds.refresh();
    }

    /** The settings of a block: the default of its shape, overridden by *, tags, its mod and its own entry. */
    static synchronized Entry effective(String key, Entry ownOverride) {
        Entry result = new Entry();
        Entry all = ENTRIES.get("*");
        if (all != null) {
            result.over(all);
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        Block block = id != null && BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
        if (block != null) {
            for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
                if (e.getKey().startsWith("#")) {
                    ResourceLocation tag = ResourceLocation.tryParse(e.getKey().substring(1));
                    if (tag != null && block.builtInRegistryHolder().is(TagKey.create(Registries.BLOCK, tag))) {
                        result.over(e.getValue());
                    }
                }
            }
        }
        if (id != null) {
            Entry mod = ENTRIES.get(id.getNamespace() + ":*");
            if (mod != null) {
                result.over(mod);
            }
        }
        Entry own = ownOverride != null ? ownOverride : ENTRIES.get(key);
        if (own != null) {
            result.over(own);
        }
        return result;
    }

    /** Builds the profile of a tree key (block id) from its effective settings. */
    static TreeProfile resolve(String key) {
        return resolve(key, null);
    }

    /** The profile a tree would get with {@code own} as its own entry (the editor's preview), or with its stored one. */
    static TreeProfile resolve(String key, Entry own) {
        Entry entry = effective(key, own);
        LeafShape guessed = LeafShapes.guess(key);
        LeafStyles.Style style = null;
        if (entry.texture != null && !entry.texture.isEmpty()) {
            style = entry.texture.size() == 1 ? LeafStyles.get(entry.texture.get(0)) : null;
            if (style == null && !(entry.texture.size() == 1 && "auto".equals(entry.texture.get(0)))) {
                style = LeafStyles.custom(entry.texture, guessed);
            }
        }
        LeafShape shape = style != null ? style.shape() : guessed;
        TreeProfile.Builder builder = new TreeProfile.Builder(shape);
        if (style != null && !(style.firstSprite() == shape.firstSprite && style.variants() == shape.variants)) {
            builder.texture(style.firstSprite(), style.variants(), style.shadow(), style.layers());
        }
        if (entry.enabled != null) {
            builder.enabled(entry.enabled);
        }
        if (entry.carpet != null) {
            builder.carpet(entry.carpet);
        }
        builder.colors(entry.colors).autumnColors(entry.autumnColors);
        if (entry.autumn != null) {
            builder.autumn(entry.autumn);
        }
        if (entry.fallRate != null) {
            builder.fallRate(entry.fallRate);
        }
        if (entry.weight != null) {
            builder.weight(entry.weight);
        }
        if (entry.glide != null) {
            builder.glide(entry.glide);
        }
        if (entry.size != null) {
            builder.size(entry.size);
        }
        if (entry.lifetime != null) {
            builder.lifetime(entry.lifetime);
        }
        return builder.build();
    }

    /** The name of a tree's texture as the editor shows it: "auto", a built-in name or "custom". */
    static String textureName(Entry entry) {
        if (entry.texture == null || entry.texture.isEmpty()) {
            return "auto";
        }
        if (entry.texture.size() == 1 && (LeafStyles.get(entry.texture.get(0)) != null || "auto".equals(entry.texture.get(0)))) {
            return entry.texture.get(0);
        }
        return "custom";
    }

    // --------------------------------------------------------------------------------------------------------------
    // JSON
    // --------------------------------------------------------------------------------------------------------------

    private static Entry read(JsonObject json) {
        Entry entry = new Entry();
        entry.enabled = bool(json, "enabled");
        entry.carpet = bool(json, "carpet");
        if (json.has("texture")) {
            JsonElement texture = json.get("texture");
            List<String> names = new ArrayList<>();
            if (texture.isJsonArray()) {
                texture.getAsJsonArray().forEach(e -> names.add(e.getAsString().trim()));
            } else {
                names.add(texture.getAsString().trim());
            }
            entry.texture = names.isEmpty() ? null : names;
        }
        entry.colors = colors(json, "colors");
        entry.autumnColors = colors(json, "autumnColors");
        entry.autumn = number(json, "autumn", 0.0F, 1.0F);
        entry.fallRate = number(json, "fallRate", 0.0F, 10.0F);
        entry.weight = number(json, "weight", 0.2F, 5.0F);
        entry.glide = number(json, "glide", 0.0F, 4.0F);
        entry.size = number(json, "size", 0.2F, 4.0F);
        entry.lifetime = number(json, "lifetime", 0.0F, 1000.0F);
        return entry;
    }

    private static JsonObject write(Entry entry) {
        JsonObject json = new JsonObject();
        if (entry.enabled != null) {
            json.addProperty("enabled", entry.enabled);
        }
        if (entry.carpet != null) {
            json.addProperty("carpet", entry.carpet);
        }
        if (entry.texture != null) {
            if (entry.texture.size() == 1) {
                json.addProperty("texture", entry.texture.get(0));
            } else {
                JsonArray array = new JsonArray();
                entry.texture.forEach(array::add);
                json.add("texture", array);
            }
        }
        writeColors(json, "colors", entry.colors);
        writeColors(json, "autumnColors", entry.autumnColors);
        writeNumber(json, "autumn", entry.autumn);
        writeNumber(json, "fallRate", entry.fallRate);
        writeNumber(json, "weight", entry.weight);
        writeNumber(json, "glide", entry.glide);
        writeNumber(json, "size", entry.size);
        writeNumber(json, "lifetime", entry.lifetime);
        return json;
    }

    private static Boolean bool(JsonObject json, String name) {
        return json.has(name) && json.get(name).isJsonPrimitive() ? json.get(name).getAsBoolean() : null;
    }

    private static Float number(JsonObject json, String name, float min, float max) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive() || !json.get(name).getAsJsonPrimitive().isNumber()) {
            return null;
        }
        float value = json.get(name).getAsFloat();
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : null;
    }

    private static int[] colors(JsonObject json, String name) {
        if (!json.has(name)) {
            return null;
        }
        JsonElement element = json.get(name);
        List<String> values = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> values.add(e.getAsString()));
        } else {
            values.add(element.getAsString());
        }
        int[] parsed = parseColors(String.join(",", values));
        return parsed == null || parsed.length == 0 ? null : parsed;
    }

    /** Colours written as {@code #RRGGBB} (or {@code RRGGBB}), separated by commas or spaces; null if one is wrong. */
    static int[] parseColors(String text) {
        List<Integer> colors = new ArrayList<>();
        for (String part : text.split("[,;\\s]+")) {
            String hex = part.trim();
            if (hex.isEmpty()) {
                continue;
            }
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.length() != 6) {
                return null;
            }
            try {
                colors.add(Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
                return null;
            }
            if (colors.size() > 32) {
                break;
            }
        }
        return colors.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Colours as the file and the editor write them. */
    static String formatColors(int[] colors) {
        if (colors == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int color : colors) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF));
        }
        return text.toString();
    }

    private static void writeColors(JsonObject json, String name, int[] colors) {
        if (colors != null) {
            JsonArray array = new JsonArray();
            for (int color : colors) {
                array.add(new JsonPrimitive(String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF)));
            }
            json.add(name, array);
        }
    }

    private static void writeNumber(JsonObject json, String name, Float value) {
        if (value != null) {
            json.addProperty(name, Math.round(value * 1000.0F) / 1000.0F);
        }
    }
}
