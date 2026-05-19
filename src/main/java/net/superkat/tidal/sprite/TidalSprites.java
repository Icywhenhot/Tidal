package net.superkat.tidal.sprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class TidalSprites {

    public static final String MOD_ID = TidalSpriteHandler.MOD_ID;

    public static final Identifier MOVING_TEXTURE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "moving");
    public static final Identifier MOVING_WHITE_TEXTURE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "moving_white");

    public static final Identifier TOP_WASHING_ID = Identifier.fromNamespaceAndPath(MOD_ID, "washing_top_colorable");
    public static final Identifier TOP_WASHING_WHITE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "washing_top_white");
    public static final Identifier BOTTOM_WASHING_ID = Identifier.fromNamespaceAndPath(MOD_ID, "washing_bottom_colorable");
    public static final Identifier BOTTOM_WASHING_WHITE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "washing_bottom_white");

    public static final Identifier WASHING_TEXTURE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "washing");
    public static final Identifier WASHING_WHITE_TEXTURE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "washing_white");

    public static final Identifier WET_OVERLAY_TEXTURE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "wet_overlay");

    public static int getFrameFromAge(TextureAtlasSprite sprite, int age, int maxAge) {
        int totalFrames = getTotalFrames(sprite);
        int frameTime = getMetadata(sprite).frameTime();
        if(frameTime <= 0) {
            return (int) Mth.lerp((float) age / maxAge, 0f, (float) totalFrames);
        }
        return (age / frameTime) % totalFrames;
    }

    public static float getU0(TextureAtlasSprite sprite) {
        return sprite.getU0();
    }

    public static float getU1(TextureAtlasSprite sprite) {
        return sprite.getU1();
    }

    public static float getV0(TextureAtlasSprite sprite, int frame) {
        int totalFrames = getTotalFrames(sprite);
        float vRange = sprite.getV1() - sprite.getV0();
        return sprite.getV0() + (vRange / totalFrames) * frame;
    }

    public static float getV1(TextureAtlasSprite sprite, int frame) {
        int totalFrames = getTotalFrames(sprite);
        float vRange = sprite.getV1() - sprite.getV0();
        return sprite.getV0() + (vRange / totalFrames) * (frame + 1);
    }

    private static int getTotalFrames(TextureAtlasSprite sprite) {
        return sprite.contents().height() / getMetadata(sprite).frameHeight();
    }

    private static WaveResourceMetadata getMetadata(TextureAtlasSprite sprite) {
        return sprite.contents().getAdditionalMetadata(WaveResourceMetadata.SERIALIZER).orElse(WaveResourceMetadata.DEFAULT);
    }
}
