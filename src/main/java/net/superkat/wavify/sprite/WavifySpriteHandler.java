package net.superkat.wavify.sprite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.superkat.wavify.Wavify;
import net.superkat.wavify.duck.WavifyWorld;

import java.util.Set;

/**
 * This is an alternative to Minecraft's particle texture system.<br><br>
 * Because SpriteIdentifier's Sprites are always ticking their animation, I can't have different timed animations for different waves.<br><br>
 * Particle's fix for this is splitting each frame into its own texture, and using their own ResourceReloader.<br><br>
 * My fix for this is using my own metadata (given via .mcmeta) which says the frame height/width/time, and using my own resource loader. The normal sprite metadata is ignored completely, disallowing the animation to be setup in my atlas.
 */
public class WavifySpriteHandler extends SimplePreparableReloadListener<WavifySpriteHandler.AtlasPreparations> {
    public static final String MOD_ID = Wavify.MOD_ID;
    public static final Identifier WAVE_ATLAS_ID = Identifier.fromNamespaceAndPath(MOD_ID, "textures/atlas/waves.png");
    private static final Identifier TEXTURE_SOURCE_PATH = Identifier.fromNamespaceAndPath(MOD_ID, "wave");

    public static final Set<MetadataSectionType<?>> METADATA_READERS = Set.of(WaveResourceMetadata.SERIALIZER);

    public TextureAtlas atlas;

    public record AtlasPreparations(TextureAtlas atlas, SpriteLoader.Preparations stitchResult) {
    }

    public TextureAtlasSprite getSprite(Identifier id) {
        return this.atlas.getSprite(id);
    }

    @Override
    protected AtlasPreparations prepare(ResourceManager manager, ProfilerFiller profiler) {
        TextureAtlas atlas = this.atlas == null ? new TextureAtlas(WAVE_ATLAS_ID) : this.atlas;
        SpriteLoader.Preparations stitchResult = SpriteLoader.create(atlas)
                .loadAndStitch(manager, TEXTURE_SOURCE_PATH, 0, Runnable::run, METADATA_READERS)
                .join();
        return new AtlasPreparations(atlas, stitchResult);
    }

    @Override
    protected void apply(AtlasPreparations preparations, ResourceManager manager, ProfilerFiller profiler) {
        if (this.atlas != preparations.atlas()) {
            this.atlas = preparations.atlas();
            Minecraft.getInstance().getTextureManager().register(this.atlas.location(), this.atlas);
        }

        this.atlas.upload(preparations.stitchResult());

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        WavifyWorld wavifyWorld = (WavifyWorld) mc.level;
        wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
        wavifyWorld.wavify$wavifyWaveHandler().waterHandler.rebuild();
    }

    public void clearAtlas() {
        if (this.atlas != null) {
            this.atlas.clearTextureData();
        }
    }

}
