package net.superkat.wavify.sprite;

import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.superkat.wavify.Wavify;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * This is an alternative to Minecraft's particle texture system.<br><br>
 * Because SpriteIdentifier's Sprites are always ticking their animation, I can't have different timed animations for different waves.<br><br>
 * Particle's fix for this is splitting each frame into its own texture, and using their own ResourceReloader.<br><br>
 * My fix for this is using my own metadata (given via .mcmeta) which says the frame height/width/time, and using my own resource loader. The normal sprite metadata is ignored completely, disallowing the animation to be setup in my atlas.
 */
public class WavifySpriteHandler implements SimpleResourceReloadListener<SpriteLoader.StitchResult> {
    public static final String MOD_ID = Wavify.MOD_ID;
    public static final Identifier WAVE_ATLAS_ID = Identifier.of(MOD_ID, "textures/atlas/waves.png");
    private static final Identifier TEXTURE_SOURCE_PATH = Identifier.of(MOD_ID, "wave");

    public static final Set<ResourceMetadataReader<?>> METADATA_READERS = Set.of(WaveResourceMetadata.SERIALIZER);

    public SpriteAtlasTexture atlas;

    public Sprite getSprite(Identifier id) {
        return this.atlas.getSprite(id);
    }

    @Override
    public CompletableFuture<SpriteLoader.StitchResult> load(ResourceManager manager, Profiler profiler, Executor executor) {
        if(this.atlas == null) {
            this.atlas = new SpriteAtlasTexture(WAVE_ATLAS_ID);
            MinecraftClient.getInstance().getTextureManager().registerTexture(this.atlas.getId(), this.atlas);
        }

        return SpriteLoader.fromAtlas(this.atlas)
                .load(manager, TEXTURE_SOURCE_PATH, 0, executor, METADATA_READERS);
    }

    @Override
    public CompletableFuture<Void> apply(SpriteLoader.StitchResult stitchResult, ResourceManager manager, Profiler profiler, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            profiler.startTick();
            profiler.push("upload");
            this.atlas.upload(stitchResult);
            profiler.pop();
            profiler.endTick();
        }, executor);
    }

    public void clearAtlas() {
        this.atlas.clear();
    }

    @Override
    public Identifier getFabricId() {
        return ResourceReloadListenerKeys.TEXTURES;
    }

}
