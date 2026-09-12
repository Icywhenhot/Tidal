package net.superkat.wavify.sprite;

import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.resources.Identifier;
import net.superkat.wavify.Wavify;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class WavifySpriteHandler implements SimpleResourceReloadListener<SpriteLoader.Preparations> {
    public static final String MOD_ID = Wavify.MOD_ID;
    public static final Identifier WAVE_ATLAS_ID = Identifier.fromNamespaceAndPath(MOD_ID, "textures/atlas/waves.png");
    private static final Identifier TEXTURE_SOURCE_PATH = Identifier.fromNamespaceAndPath(MOD_ID, "wave");

    public static final Set<MetadataSectionType<?>> METADATA_READERS = Set.of(WaveResourceMetadata.SERIALIZER);

    public TextureAtlas atlas;

    private final Map<Identifier, WaveSprite> waveSprites = new HashMap<>();

    public TextureAtlasSprite getSprite(Identifier id) {
        return this.atlas.getSprite(id);
    }

    public WaveSprite getWaveSprite(Identifier id) {
        return this.waveSprites.computeIfAbsent(id, key -> WaveSprite.of(getSprite(key)));
    }

    @Override
    public CompletableFuture<SpriteLoader.Preparations> load(ResourceManager manager, Executor executor) {
        if(this.atlas == null) {
            this.atlas = new TextureAtlas(WAVE_ATLAS_ID);
            Minecraft.getInstance().getTextureManager().register(this.atlas.location(), this.atlas);
        }

        return SpriteLoader.create(this.atlas)
                .loadAndStitch(manager, TEXTURE_SOURCE_PATH, 0, executor, METADATA_READERS);
    }

    @Override
    public CompletableFuture<Void> apply(SpriteLoader.Preparations stitchResult, ResourceManager manager, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            this.atlas.upload(stitchResult);
            this.waveSprites.clear();
        }, executor);
    }

    public void clearAtlas() {
        this.waveSprites.clear();
        this.atlas.clearTextureData();
    }

    @Override
    public Identifier getFabricId() {
        return ResourceReloadListenerKeys.TEXTURES;
    }

}
