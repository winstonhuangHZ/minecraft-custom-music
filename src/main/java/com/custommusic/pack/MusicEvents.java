package com.custommusic.pack;

import com.custommusic.CustomMusicClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 找出游戏里所有的配乐事件。
 *
 * <p>26.2 一共有 32 个 {@code music.*} 事件：除了常见的 game/creative/menu/under_water，
 * 每个生物群系（下界 5 个 + 主世界 20 个）都有自己的配乐事件，还有末地、末影龙、终末之诗。
 * 手写列表必然漏，所以直接扫所有资源包里的 sounds.json，把 key 里 {@code music.} 开头的都收集起来。
 * 命名空间按文件所在目录决定，所以按命名空间分组，之后各自生成一份 sounds.json。
 */
public final class MusicEvents {

    public static final String PREFIX = "music.";

    /** 事件 id -> 中文名。找不到就退回原始 id。 */
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("music.game", "主世界（默认）"),
            Map.entry("music.creative", "创造模式"),
            Map.entry("music.menu", "主菜单"),
            Map.entry("music.under_water", "水下"),
            Map.entry("music.end", "末地"),
            Map.entry("music.dragon", "末影龙"),
            Map.entry("music.credits", "终末之诗"),
            Map.entry("basalt_deltas", "玄武岩三角洲"),
            Map.entry("crimson_forest", "绯红森林"),
            Map.entry("nether_wastes", "下界荒地"),
            Map.entry("soul_sand_valley", "灵魂沙峡谷"),
            Map.entry("warped_forest", "诡异森林"),
            Map.entry("badlands", "恶地"),
            Map.entry("bamboo_jungle", "竹林"),
            Map.entry("cherry_grove", "樱花树林"),
            Map.entry("deep_dark", "深暗之域"),
            Map.entry("desert", "沙漠"),
            Map.entry("dripstone_caves", "溶洞"),
            Map.entry("flower_forest", "繁花森林"),
            Map.entry("forest", "森林"),
            Map.entry("frozen_peaks", "冰封山峰"),
            Map.entry("grove", "雪林"),
            Map.entry("jagged_peaks", "尖峭山峰"),
            Map.entry("jungle", "丛林"),
            Map.entry("lush_caves", "繁茂洞穴"),
            Map.entry("meadow", "草甸"),
            Map.entry("old_growth_taiga", "原始针叶林"),
            Map.entry("snowy_slopes", "积雪山坡"),
            Map.entry("sparse_jungle", "稀疏丛林"),
            Map.entry("stony_peaks", "裸岩山峰"),
            Map.entry("sulfur_caves", "硫磺洞穴"),
            Map.entry("swamp", "沼泽"));

    private MusicEvents() {
    }

    /** 一组情境的预设。id 以 preset: 开头，不会和事件 id（namespace:path）冲突。 */
    public record Preset(String id, String nameKey, java.util.function.Predicate<String> matcher) {
    }

    /** 算作「洞穴」的几个群系（下界那些洞穴不算，它们归到下界组里更好用）。 */
    private static final Set<String> CAVE_PATHS = Set.of(
            "music.overworld.dripstone_caves",
            "music.overworld.lush_caves",
            "music.overworld.deep_dark",
            "music.overworld.sulfur_caves");

    /**
     * 界面上提供的分组，省得一个个点 32 个情境。
     * 顺序就是界面上的显示顺序，「全部」放最前面最顺手。
     */
    public static List<Preset> presets() {
        return List.of(
                new Preset("preset:all", "custommusic.preset.all", path -> true),
                new Preset("preset:caves", "custommusic.preset.caves", CAVE_PATHS::contains),
                new Preset("preset:nether", "custommusic.preset.nether", path -> path.startsWith("music.nether.")),
                new Preset("preset:overworld", "custommusic.preset.overworld",
                        path -> path.startsWith("music.overworld.") && !CAVE_PATHS.contains(path)),
                new Preset("preset:end", "custommusic.preset.end", path -> path.equals("music.end")),
                new Preset("preset:underwater", "custommusic.preset.underwater",
                        path -> path.equals("music.under_water")),
                new Preset("preset:menu", "custommusic.preset.menu", path -> path.equals("music.menu")),
                new Preset("preset:creative", "custommusic.preset.creative",
                        path -> path.equals("music.creative")));
    }

    public static Preset findPreset(String id) {
        for (Preset preset : presets()) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    /** 把一个预设展开成实际的情境 id 列表。 */
    public static List<String> expand(Preset preset, Map<String, SortedSet<String>> discovered) {
        List<String> ids = new java.util.ArrayList<>();
        discovered.forEach((namespace, keys) -> keys.forEach(key -> {
            if (preset.matcher().test(key)) {
                ids.add(namespace + ":" + key);
            }
        }));
        return ids;
    }

    /** 把 "minecraft:music.nether.nether_wastes" 显示成「下界 · 下界荒地」。 */
    public static String displayName(String contextId) {
        String path = contextId;
        int colon = contextId.indexOf(':');
        if (colon >= 0) {
            path = contextId.substring(colon + 1);
        }

        String special = NAMES.get(path);
        if (special != null) {
            return special;
        }

        for (String dimension : List.of("nether", "overworld")) {
            String prefix = "music." + dimension + ".";
            if (path.startsWith(prefix)) {
                String biome = path.substring(prefix.length());
                String dimensionName = dimension.equals("nether") ? "下界" : "主世界";
                return dimensionName + " · " + NAMES.getOrDefault(biome, biome);
            }
        }
        return path;
    }

    /** 命名空间 -> 该命名空间下的 music.* 事件路径。 */
    public static Map<String, SortedSet<String>> discover() {
        Map<String, SortedSet<String>> found = new TreeMap<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return found;
        }

        ResourceManager manager = minecraft.getResourceManager();
        Gson gson = new Gson();
        Map<Identifier, List<Resource>> stacks = manager.listResourceStacks("sounds.json", id -> true);

        for (Map.Entry<Identifier, List<Resource>> entry : stacks.entrySet()) {
            String namespace = entry.getKey().getNamespace();
            for (Resource resource : entry.getValue()) {
                try (BufferedReader reader = resource.openAsReader()) {
                    com.google.gson.JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed == null || !parsed.isJsonObject()) {
                        continue;
                    }
                    JsonObject json = parsed.getAsJsonObject();
                    for (String key : json.keySet()) {
                        if (key.startsWith(PREFIX)) {
                            found.computeIfAbsent(namespace, ns -> new TreeSet<>()).add(key);
                        }
                    }
                } catch (Exception e) {
                    // 单个资源包坏掉不影响其它的
                    CustomMusicClient.LOG.debug("读取 {} 的 sounds.json 失败", namespace, e);
                }
            }
        }
        StringBuilder summary = new StringBuilder();
        int total = 0;
        for (Map.Entry<String, SortedSet<String>> entry : found.entrySet()) {
            total += entry.getValue().size();
            summary.append(summary.length() == 0 ? "" : ", ")
                    .append(entry.getKey()).append('=').append(entry.getValue().size());
        }
        CustomMusicClient.LOG.info("枚举到 {} 个配乐事件（{}）", total, summary);
        return found;
    }

    /**
     * 把配置里写的 "music.game" 或 "某个mod:music.foo" 这样的条目也归到命名空间下，
     * 这样显式列表和自动枚举可以走同一套生成逻辑。
     */
    public static Map<String, SortedSet<String>> fromConfig(List<String> events) {
        Map<String, SortedSet<String>> result = new TreeMap<>();
        for (String raw : events) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String namespace = "minecraft";
            String key = raw.trim();
            int colon = key.indexOf(':');
            if (colon > 0) {
                namespace = key.substring(0, colon);
                key = key.substring(colon + 1);
            }
            result.computeIfAbsent(namespace, ns -> new TreeSet<>()).add(key);
        }
        return result;
    }

    /** 按排除表过滤（"music.dragon" 或 "minecraft:music.dragon" 两种写法都认）。 */
    public static void applyExclusions(Map<String, SortedSet<String>> events, List<String> exclude) {
        for (String raw : exclude) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String value = raw.trim();
            int colon = value.indexOf(':');
            if (colon > 0) {
                String namespace = value.substring(0, colon);
                String key = value.substring(colon + 1);
                SortedSet<String> set = events.get(namespace);
                if (set != null) {
                    set.remove(key);
                }
            } else {
                for (SortedSet<String> set : events.values()) {
                    set.remove(value);
                }
            }
        }
        events.values().removeIf(SortedSet::isEmpty);
    }
}
