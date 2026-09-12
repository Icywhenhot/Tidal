package net.superkat.wavify.sprite;

import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;
import net.superkat.wavify.Wavify;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class WavifySpriteHandler implements SimpleResourceReloadListener<SpriteLoader.StitchResult> {
    public static final String MOD_ID = Wavify.MOD_ID;
    public static final Identifier WAVE_ATLAS_ID = Identifier.of(MOD_ID, "textures/atlas/waves.png");
    private static final Identifier TEXTURE_SOURCE_PATH = Identifier.of(MOD_ID, "wave");

    public static final Set<ResourceMetadataSerializer<?>> METADATA_READERS = Set.of(WaveResourceMetadata.SERIALIZER);

    public SpriteAtlasTexture atlas;

    private final Map<Identifier, WaveSprite> waveSprites = new HashMap<>();

    public WaveSprite getWaveSprite(Identifier id) {
        return this.waveSprites.computeIfAbsent(id, key -> WaveSprite.of(getSprite(key)));
    }

    public Sprite getSprite(Identifier id) {
        return this.atlas.getSprite(id);
    }

    @Override
    public CompletableFuture<SpriteLoader.StitchResult> load(ResourceManager manager, Executor executor) {
        if(this.atlas == null) {
            this.atlas = new SpriteAtlasTexture(WAVE_ATLAS_ID);
            MinecraftClient.getInstance().getTextureManager().registerTexture(this.atlas.getId(), this.atlas);
        }

        return SpriteLoader.fromAtlas(this.atlas)
                .load(manager, TEXTURE_SOURCE_PATH, 0, executor, METADATA_READERS);
    }

    @Override
    public CompletableFuture<Void> apply(SpriteLoader.StitchResult stitchResult, ResourceManager manager, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            this.atlas.create(stitchResult);
            this.waveSprites.clear();
        }, executor);
    }

    public void clearAtlas() {
        this.waveSprites.clear();
        this.atlas.clear();
    }

    @Override
    public Identifier getFabricId() {
        return ResourceReloadListenerKeys.TEXTURES;
    }

}
