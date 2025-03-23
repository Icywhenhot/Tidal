package net.superkat.tidal.sprite;

import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

public class TidalSprites {
    public static final Identifier MOVING_TEXTURE_ID = Identifier.of(TidalSpriteHandler.MOD_ID, "moving");
    public static final Identifier MOVING_WHITE_TEXTURE_ID = Identifier.of(TidalSpriteHandler.MOD_ID, "moving_white");
    public static final Identifier WASHING_TEXTURE_ID = Identifier.of(TidalSpriteHandler.MOD_ID, "washing");
    public static final Identifier WASHING_WHITE_TEXTURE_ID = Identifier.of(TidalSpriteHandler.MOD_ID, "washing_white");
    public static final Identifier WET_OVERLAY_TEXTURE_ID = Identifier.of(TidalSpriteHandler.MOD_ID, "wet_overlay");

    public static int getFrameFromAge(Sprite sprite, int age) {
        int frameCount = getFrameCount(sprite);
        int frameTime = getMetadata(sprite).frameTime();
        return (age / frameTime) % frameCount;
    }

    public static float getMinU(Sprite sprite) {
        return sprite.getMinU();
    }

    public static float getMaxU(Sprite sprite) {
        return sprite.getMaxU();
    }

    public static float getMinV(Sprite sprite, int frame) {
        int frameCount = getFrameCount(sprite);
        float vRange = sprite.getMaxV() - sprite.getMinV();
        return sprite.getMinV() + (vRange / frameCount) * frame;
    }

    public static float getMaxV(Sprite sprite, int frame) {
        int frameCount = getFrameCount(sprite);
        float vRange = sprite.getMaxV() - sprite.getMinV();
        return sprite.getMinV() + (vRange / frameCount) * (frame + 1);
    }

    private static int getFrameCount(Sprite sprite) {
        return sprite.getContents().getHeight() / getMetadata(sprite).frameHeight();
    }

    private static WaveResourceMetadata getMetadata(Sprite sprite) {
        return sprite.getContents().getMetadata().decode(WaveResourceMetadata.SERIALIZER).orElse(WaveResourceMetadata.DEFAULT);
    }
}
