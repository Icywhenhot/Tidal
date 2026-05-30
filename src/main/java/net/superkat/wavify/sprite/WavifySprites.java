package net.superkat.wavify.sprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.superkat.wavify.WavifyClient;

public class WavifySprites {

    public static final String MOD_ID = WavifySpriteHandler.MOD_ID;

    public static final ResourceLocation MOVING_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "moving");
    public static final ResourceLocation MOVING_WHITE_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "moving_white");

    public static final ResourceLocation TOP_WASHING_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_top_colorable");
    public static final ResourceLocation TOP_WASHING_WHITE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_top_white");
    public static final ResourceLocation BOTTOM_WASHING_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_bottom_colorable");
    public static final ResourceLocation BOTTOM_WASHING_WHITE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_bottom_white");

    public static final ResourceLocation WASHING_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing");
    public static final ResourceLocation WASHING_WHITE_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_white");

    public static final ResourceLocation WET_OVERLAY_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "wet_overlay");

    public static int getFrameFromAge(TextureAtlasSprite sprite, int age, int maxAge) {
        int totalFrames = getTotalFrames(sprite);
        int frameTime = getMetadata(sprite).frameTime();
        if(frameTime <= 0) {
            return (int) Mth.lerp((float) age / maxAge, 0f, (float) totalFrames);
        }
        return (age / frameTime) % totalFrames;
    }

    public static float getMinU(TextureAtlasSprite sprite) {
        return sprite.getU0();
    }

    public static float getMaxU(TextureAtlasSprite sprite) {
        return sprite.getU1();
    }

    public static float getMinV(TextureAtlasSprite sprite, int frame) {
        int totalFrames = getTotalFrames(sprite);
        float vRange = sprite.getV1() - sprite.getV0();
        return sprite.getV0() + (vRange / totalFrames) * frame;
    }

    public static float getMaxV(TextureAtlasSprite sprite, int frame) {
        int totalFrames = getTotalFrames(sprite);
        float vRange = sprite.getV1() - sprite.getV0();
        return sprite.getV0() + (vRange / totalFrames) * (frame + 1);
    }

    private static int getTotalFrames(TextureAtlasSprite sprite) {
        return Math.max(1, sprite.contents().height() / getMetadata(sprite).frameHeight());
    }

    private static WaveResourceMetadata getMetadata(TextureAtlasSprite sprite) {
        return WavifyClient.WAVIFY_SPRITE_HANDLER.getMetadata(sprite.contents().name());
    }
}
