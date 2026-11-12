package net.superkat.wavify.util;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
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

    private record TagColor(TagKey<Biome> tag, int color) {
    }

    private static Map<ResourceLocation, Integer> parsedOverrides = null;
    private static List<TagColor> tagOverrides = List.of();
    private static String lastOverrideSource = null;
    private static int lastSource = -1;
    private static int lastCustom = -1;

    public static void forget() {
        cache.clear();
    }

    public static int getWaterColor(ClientLevel world, BlockPos pos) {
        dropStaleCache();

        long key = pos.asLong();
        int hit = cache.get(key);
        if (hit != NO_OVERRIDE) return hit;

        int color = computeWaterColor(world, pos) & 0xFFFFFF;
        if (cache.size() >= CACHE_CAP) cache.clear();
        cache.put(key, color);
        return color;
    }

    private static int computeWaterColor(ClientLevel world, BlockPos pos) {
        int override = lookupBiomeOverride(world, pos);
        if (override != NO_OVERRIDE) return override;

        if (WavifyConfig.colorSource == WavifyConfig.ColorSource.CUSTOM) {
            return WavifyConfig.customColor & 0xFFFFFF;
        }
        return BiomeColors.getAverageWaterColor(world, pos);
    }

    private static void dropStaleCache() {
        int source = WavifyConfig.colorSource.ordinal();
        int custom = WavifyConfig.customColor;
        if (source == lastSource && custom == lastCustom) return;
        lastSource = source;
        lastCustom = custom;
        cache.clear();
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

    private static int lookupBiomeOverride(ClientLevel world, BlockPos pos) {
        Map<ResourceLocation, Integer> overrides = getParsedOverrides();
        if (overrides.isEmpty() && tagOverrides.isEmpty()) return NO_OVERRIDE;

        Holder<Biome> entry = world.getBiome(pos);
        int byId = entry.unwrapKey()
                .map(ResourceKey::location)
                .map(id -> overrides.getOrDefault(id, NO_OVERRIDE))
                .orElse(NO_OVERRIDE);
        if (byId != NO_OVERRIDE) return byId;

        for (TagColor tc : tagOverrides) {
            if (entry.is(tc.tag())) return tc.color();
        }
        return NO_OVERRIDE;
    }

    private static Map<ResourceLocation, Integer> getParsedOverrides() {
        String raw = WavifyConfig.biomeColorOverrides;
        if (raw == null) raw = "";
        if (parsedOverrides != null && raw.equals(lastOverrideSource)) return parsedOverrides;

        Map<ResourceLocation, Integer> map = new HashMap<>();
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
                ResourceLocation id = ResourceLocation.tryParse(tag ? idPart.substring(1) : idPart);
                if (id == null) continue;
                if (tag) tags.add(new TagColor(TagKey.create(Registries.BIOME, id), color));
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
