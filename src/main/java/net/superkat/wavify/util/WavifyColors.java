package net.superkat.wavify.util;

import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.superkat.wavify.config.WavifyConfig;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

// one place for wave and splash water color, follows the config
// biome mode uses vanilla's water color, custom mode uses one global hex
// per biome overrides beat both of those whenever they're set
public class WavifyColors {

    private static Map<Identifier, Integer> parsedOverrides = null;
    private static String lastOverrideSource = null;

    public static int getWaterColor(ClientWorld world, BlockPos pos) {
        int override = lookupBiomeOverride(world, pos);
        if (override != Integer.MIN_VALUE) return override;

        if (WavifyConfig.colorSource == WavifyConfig.ColorSource.CUSTOM) {
            return WavifyConfig.customColor & 0xFFFFFF;
        }
        return BiomeColors.getWaterColor(world, pos);
    }

    public static Vector3f getWaterColorVec(ClientWorld world, BlockPos pos) {
        int color = getWaterColor(world, pos);
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        return new Vector3f(r, g, b);
    }

    private static int lookupBiomeOverride(ClientWorld world, BlockPos pos) {
        Map<Identifier, Integer> overrides = getParsedOverrides();
        if (overrides.isEmpty()) return Integer.MIN_VALUE;

        RegistryEntry<Biome> entry = world.getBiome(pos);
        return entry.getKey()
                .map(RegistryKey::getValue)
                .map(id -> overrides.getOrDefault(id, Integer.MIN_VALUE))
                .orElse(Integer.MIN_VALUE);
    }

    // turns the namespace:biome=RRGGBB list from the config into a cached map, redone when the string changes
    private static Map<Identifier, Integer> getParsedOverrides() {
        String raw = WavifyConfig.biomeColorOverrides;
        if (raw == null) raw = "";
        if (parsedOverrides != null && raw.equals(lastOverrideSource)) return parsedOverrides;

        Map<Identifier, Integer> map = new HashMap<>();
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
                Identifier id = Identifier.tryParse(idPart);
                if (id != null) map.put(id, color);
            } catch (NumberFormatException ignored) {
            }
        }

        parsedOverrides = map;
        lastOverrideSource = raw;
        return map;
    }
}
