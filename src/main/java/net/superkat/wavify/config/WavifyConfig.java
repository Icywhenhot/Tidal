package net.superkat.wavify.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.superkat.wavify.Wavify;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WavifyConfig {

    public enum ColorSource {
        BIOME,
        CUSTOM
    }

    public static boolean enableOceanWaves = true;
    public static int chunkRadius = 5;
    public static int chunkUpdatesRescanAmount = 50;
    public static int spawnDistance = 8;
    public static double waveOpacity = 1.0;
    public static double foamOpacity = 1.0;
    public static boolean fullMoonGlow = false;
    public static ColorSource colorSource = ColorSource.BIOME;
    public static int customColor = 0x3F76E4;
    public static String biomeColorOverrides = "";
    public static boolean enableWetOverlay = true;
    public static boolean enableRiverWaves = true;
    public static int riverWaveSpawnRadius = 56;
    public static double riverWaveDensity = 4.0;
    public static int riverWaveTravelBlocks = 11;
    public static double waveYOffset = 0.0;
    public static double riverWaveYOffset = 0.0;
    public static double shaderWaveYSink = -0.30;
    public static boolean enableWaveSounds = true;
    public static double waveSoundVolume = 1.0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WavifyConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(Wavify.MOD_ID + ".json");
    }

    public static void load() {
        Path path = file();
        if (Files.exists(path)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                for (Field field : entries()) {
                    JsonElement value = json.get(field.getName());
                    if (value != null) field.set(null, GSON.fromJson(value, field.getGenericType()));
                }
            } catch (IOException | IllegalAccessException | RuntimeException e) {
                Wavify.LOGGER.warn("could not read {}, keeping defaults", path, e);
            }
        }
        save();
    }

    public static void save() {
        Path path = file();
        JsonObject json = new JsonObject();
        try {
            for (Field field : entries()) {
                json.add(field.getName(), GSON.toJsonTree(field.get(null), field.getGenericType()));
            }
            Files.writeString(path, GSON.toJson(json));
        } catch (IOException | IllegalAccessException e) {
            Wavify.LOGGER.warn("could not save {}", path, e);
        }
    }

    private static List<Field> entries() {
        List<Field> out = new ArrayList<>();
        for (Field field : WavifyConfig.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isPublic(mods) && Modifier.isStatic(mods) && !Modifier.isFinal(mods)) out.add(field);
        }
        return out;
    }
}
