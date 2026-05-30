package net.superkat.wavify.util;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.superkat.wavify.config.WavifyConfig;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Central source for wave / splash water colour. Honours the config:
 *   - BIOME: vanilla {@link BiomeColors#getWaterColor}
 *   - CUSTOM: a single global hex color
 * Per-biome overrides (when non-empty) always win, regardless of mode.
 */
public class WavifyColors {

    private static Map<Identifier, Integer> parsedOverrides = null;
    private static String lastOverrideSource = null;

    public static int getWaterColor(ClientLevel world, BlockPos pos) {
        int override = lookupBiomeOverride(world, pos);
        if (override != Integer.MIN_VALUE) return override;

        if (WavifyConfig.colorSource == WavifyConfig.ColorSource.CUSTOM) {
            return WavifyConfig.customColor & 0xFFFFFF;
        }
        return BiomeColors.getAverageWaterColor(world, pos);
    }

    public static Vector3f getWaterColorVec(ClientLevel world, BlockPos pos) {
        int color = getWaterColor(world, pos);
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        return new Vector3f(r, g, b);
    }

    private static int lookupBiomeOverride(ClientLevel world, BlockPos pos) {
        Map<Identifier, Integer> overrides = getParsedOverrides();
        if (overrides.isEmpty()) return Integer.MIN_VALUE;

        Holder<Biome> entry = world.getBiome(pos);
        return entry.unwrapKey()
                .map(ResourceKey::identifier)
                .map(id -> overrides.getOrDefault(id, Integer.MIN_VALUE))
                .orElse(Integer.MIN_VALUE);
    }

    /**
     * Parses {@link WavifyConfig#biomeColorOverrides} from a simple
     * "namespace:biome=RRGGBB,..." string into a cached map. Re-parses when the
     * raw string changes.
     */
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
