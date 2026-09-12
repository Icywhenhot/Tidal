package net.superkat.wavify.sprite;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.superkat.wavify.Wavify;
import net.superkat.wavify.duck.WavifyWorld;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WavifySpriteHandler extends SimplePreparableReloadListener<WavifySpriteHandler.AtlasPreparations> {
    public static final String MOD_ID = Wavify.MOD_ID;
    public static final ResourceLocation WAVE_ATLAS_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/atlas/waves.png");
    private static final ResourceLocation TEXTURE_SOURCE_PATH = ResourceLocation.fromNamespaceAndPath(MOD_ID, "wave");
    private static final String TEXTURE_FOLDER = "textures/wave";

    public TextureAtlas atlas;
    public Map<ResourceLocation, WaveResourceMetadata> waveMetadata = new HashMap<>();

    private final Map<ResourceLocation, WaveSprite> waveSprites = new HashMap<>();

    public record AtlasPreparations(TextureAtlas atlas, SpriteLoader.Preparations stitchResult, Map<ResourceLocation, WaveResourceMetadata> waveMetadata) {
    }

    public TextureAtlasSprite getSprite(ResourceLocation id) {
        return this.atlas.getSprite(id);
    }

    public WaveSprite getWaveSprite(ResourceLocation id) {
        return this.waveSprites.computeIfAbsent(id, key -> {
            TextureAtlasSprite sprite = getSprite(key);
            return WaveSprite.of(sprite, getMetadata(sprite.contents().name()));
        });
    }

    public WaveResourceMetadata getMetadata(ResourceLocation spriteId) {
        return this.waveMetadata.getOrDefault(spriteId, WaveResourceMetadata.DEFAULT);
    }

    @Override
    protected AtlasPreparations prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, WaveResourceMetadata> meta = loadWaveMetadata(manager);

        TextureAtlas atlas = this.atlas == null ? new TextureAtlas(WAVE_ATLAS_ID) : this.atlas;
        SpriteLoader.Preparations stitchResult = SpriteLoader.create(atlas)
                .loadAndStitch(manager, TEXTURE_SOURCE_PATH, 0, Runnable::run)
                .join();
        return new AtlasPreparations(atlas, stitchResult, meta);
    }

    private Map<ResourceLocation, WaveResourceMetadata> loadWaveMetadata(ResourceManager manager) {
        Map<ResourceLocation, WaveResourceMetadata> map = new HashMap<>();
        Map<ResourceLocation, Resource> mcmetaResources = manager.listResources(TEXTURE_FOLDER,
                id -> id.getNamespace().equals(MOD_ID) && id.getPath().endsWith(".png.mcmeta"));

        for (Map.Entry<ResourceLocation, Resource> entry : mcmetaResources.entrySet()) {
            ResourceLocation mcmetaId = entry.getKey();
            try (InputStream is = entry.getValue().open();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                JsonObject root = GsonHelper.parse(reader);
                if (!root.has("wave_animation")) continue;
                JsonObject section = GsonHelper.getAsJsonObject(root, "wave_animation");
                int frameTime = GsonHelper.getAsInt(section, "frametime", 5);
                int frameHeight = GsonHelper.getAsInt(section, "frame_height", 16);

                String path = mcmetaId.getPath();

                String stripped = path.substring(TEXTURE_FOLDER.length() + 1, path.length() - ".png.mcmeta".length());
                ResourceLocation spriteId = ResourceLocation.fromNamespaceAndPath(MOD_ID, stripped);
                map.put(spriteId, new WaveResourceMetadata(frameTime, frameHeight));
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    @Override
    protected void apply(AtlasPreparations preparations, ResourceManager manager, ProfilerFiller profiler) {
        if (this.atlas != preparations.atlas()) {
            this.atlas = preparations.atlas();
            Minecraft.getInstance().getTextureManager().register(this.atlas.location(), this.atlas);
        }

        this.atlas.upload(preparations.stitchResult());
        this.waveMetadata = preparations.waveMetadata();
        this.waveSprites.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        WavifyWorld wavifyWorld = (WavifyWorld) mc.level;
        wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
        wavifyWorld.wavify$wavifyWaveHandler().waterHandler.rebuild();
    }

    public void clearAtlas() {
        this.waveSprites.clear();
        if (this.atlas != null) {
            this.atlas.clearTextureData();
        }
    }

}
