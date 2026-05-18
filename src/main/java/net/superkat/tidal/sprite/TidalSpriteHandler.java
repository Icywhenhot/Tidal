package net.superkat.tidal.sprite;

import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.resources.Identifier;
import net.superkat.tidal.Tidal;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * This is an alternative to Minecraft's particle texture system.<br><br>
 * Because SpriteIdentifier's Sprites are always ticking their animation, I can't have different timed animations for different waves.<br><br>
 * Particle's fix for this is splitting each frame into its own texture, and using their own ResourceReloader.<br><br>
 * My fix for this is using my own metadata (given via .mcmeta) which says the frame height/width/time, and using my own resource loader. The normal sprite metadata is ignored completely, disallowing the animation to be setup in my atlas.
 */
public class TidalSpriteHandler implements SimpleResourceReloadListener<SpriteLoader.Preparations> {
    public static final String MOD_ID = Tidal.MOD_ID;
    public static final Identifier WAVE_ATLAS_ID = Identifier.of(MOD_ID, "textures/atlas/waves.png");
    private static final Identifier TEXTURE_SOURCE_PATH = Identifier.of(MOD_ID, "wave");

    public static final Set<MetadataSectionType<?>> METADATA_READERS = Set.of(WaveResourceMetadata.SERIALIZER);

    public TextureAtlas atlas;

    public TextureAtlasSprite getSprite(Identifier id) {
        return this.atlas.getSprite(id);
    }

    @Override
    public CompletableFuture<SpriteLoader.Preparations> load(ResourceManager manager, Executor executor) {
        if(this.atlas == null) {
            this.atlas = new TextureAtlas(WAVE_ATLAS_ID);
            Minecraft.getInstance().getTextureManager().registerTexture(this.atlas.location(), this.atlas);
        }

        return SpriteLoader.fromAtlas(this.atlas)
                .load(manager, TEXTURE_SOURCE_PATH, 0, executor, METADATA_READERS);
    }

    @Override
    public CompletableFuture<Void> apply(SpriteLoader.Preparations stitchResult, ResourceManager manager, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            this.atlas.upload(stitchResult);
        }, executor);
    }

    public void clearAtlas() {
        this.atlas.clearTextureData();
    }

    @Override
    public Identifier getFabricId() {
        return ResourceReloadListenerKeys.TEXTURES;
    }

}
