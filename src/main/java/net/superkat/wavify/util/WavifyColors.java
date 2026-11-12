package net.superkat.wavify.util;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.superkat.wavify.config.WavifyConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WavifyColors {

    private static final int NO_OVERRIDE = Integer.MIN_VALUE;
    private static final int CACHE_CAP = 8192;

    private static final Long2IntOpenHashMap cache = new Long2IntOpenHashMap();

    static {
        cache.defaultReturnValue(NO_OVERRIDE);
    }

    private static int lastSource = -1;
    private static int lastCustom = -1;

    public static void forget() {
        cache.clear();
    }

    private record TagColor(TagKey<Biome> tag, int color) {
    }

    private static Map<Identifier, Integer> parsedOverrides = null;
    private static List<TagColor> tagOverrides = List.of();
    private static String lastOverrideSource = null;

    public static int getWaterColor(ClientWorld world, BlockPos pos) {
        dropStaleCache();

        long key = pos.asLong();
        int hit = cache.get(key);
        if (hit != NO_OVERRIDE) return hit;

        int color = computeWaterColor(world, pos) & 0xFFFFFF;
        if (cache.size() >= CACHE_CAP) cache.clear();
        cache.put(key, color);
        return color;
    }

    private static void dropStaleCache() {
        int source = WavifyConfig.colorSource.ordinal();
        int custom = WavifyConfig.customColor;
        if (source == lastSource && custom == lastCustom) return;
        lastSource = source;
        lastCustom = custom;
        cache.clear();
    }

    private static int computeWaterColor(ClientWorld world, BlockPos pos) {
        int override = lookupBiomeOverride(world, pos);
        if (override != NO_OVERRIDE) return override;

        if (WavifyConfig.colorSource == WavifyConfig.ColorSource.CUSTOM) {
            return WavifyConfig.customColor & 0xFFFFFF;
        }
        return BiomeColors.getWaterColor(world, pos);
        }

    public static float red(int color) {
        return (color >> 16 & 0xFF) / 255.0F;
    }

    public static float green(int color) {
        return (color >> 8 & 0xFF) / 255.0F;
    }

    public static float blue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    private static int lookupBiomeOverride(ClientWorld world, BlockPos pos) {
        Map<Identifier, Integer> overrides = getParsedOverrides();
        if (overrides.isEmpty() && tagOverrides.isEmpty()) return NO_OVERRIDE;

        RegistryEntry<Biome> entry = world.getBiome(pos);
        int byId = entry.getKey()
                .map(RegistryKey::getValue)
                .map(id -> overrides.getOrDefault(id, NO_OVERRIDE))
                .orElse(NO_OVERRIDE);
        if (byId != NO_OVERRIDE) return byId;

        for (TagColor tc : tagOverrides) {
            if (entry.isIn(tc.tag())) return tc.color();
        }
        return NO_OVERRIDE;
    }

    private static Map<Identifier, Integer> getParsedOverrides() {
        String raw = WavifyConfig.biomeColorOverrides;
        if (raw == null) raw = "";
        if (parsedOverrides != null && raw.equals(lastOverrideSource)) return parsedOverrides;

        Map<Identifier, Integer> map = new HashMap<>();
        List<TagColor> tags = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0 || eq >= trimmed.length() - 1) continue;
            String idPart = trimmed.substring(0, eq).trim();
            String hexPart = trimmed.substring(eq + 1).trim();
            if (hexPart.startsWith("#")) hexPart = hexPart.substring(1);
            if (hexPart.startsWith("0x") || hexPart.startsWith("0X")) hexPart = hexPart.substring(2);
            try {
                int color = Integer.parseInt(hexPart, 16) & 0xFFFFFF;
                boolean tag = idPart.startsWith("#");
                Identifier id = Identifier.tryParse(tag ? idPart.substring(1) : idPart);
                if (id == null) continue;
                if (tag) tags.add(new TagColor(TagKey.of(RegistryKeys.BIOME, id), color));
                else map.put(id, color);
            } catch (NumberFormatException ignored) {
            }
        }

        parsedOverrides = map;
        tagOverrides = tags;
        lastOverrideSource = raw;
        cache.clear();
        return map;
    }
}
