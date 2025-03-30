package net.superkat.tidal.sprite;

import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class TidalSprites {

    public static final String MOD_ID = TidalSpriteHandler.MOD_ID;

    public static final Identifier MOVING_TEXTURE_ID = Identifier.of(MOD_ID, "moving");
    public static final Identifier MOVING_WHITE_TEXTURE_ID = Identifier.of(MOD_ID, "moving_white");

    public static final Identifier TOP_WASHING_ID = Identifier.of(MOD_ID, "washing_top_colorable");
    public static final Identifier TOP_WASHING_WHITE_ID = Identifier.of(MOD_ID, "washing_top_white");
    public static final Identifier BOTTOM_WASHING_ID = Identifier.of(MOD_ID, "washing_bottom_colorable");
    public static final Identifier BOTTOM_WASHING_WHITE_ID = Identifier.of(MOD_ID, "washing_bottom_white");

    public static final Identifier WASHING_TEXTURE_ID = Identifier.of(MOD_ID, "washing");
    public static final Identifier WASHING_WHITE_TEXTURE_ID = Identifier.of(MOD_ID, "washing_white");

    public static final Identifier WET_OVERLAY_TEXTURE_ID = Identifier.of(MOD_ID, "wet_overlay");

    public static int getFrameFromAge(Sprite sprite, int age, int maxAge) {
        int totalFrames = getTotalFrames(sprite);
        int frameTime = getMetadata(sprite).frameTime();
        if(frameTime <= 0) {
            return MathHelper.lerp((float) age / maxAge, 0, totalFrames);
//            return (age / maxAge) * totalFrames;
        }
        return (age / frameTime) % totalFrames;
    }

    public static float getMinU(Sprite sprite) {
        return sprite.getMinU();
    }

    public static float getMaxU(Sprite sprite) {
        return sprite.getMaxU();
    }

    public static float getMinV(Sprite sprite, int frame) {
        int totalFrames = getTotalFrames(sprite);
        float vRange = sprite.getMaxV() - sprite.getMinV();
        return sprite.getMinV() + (vRange / totalFrames) * frame;
    }

    public static float getMaxV(Sprite sprite, int frame) {
        int totalFrames = getTotalFrames(sprite);
        float vRange = sprite.getMaxV() - sprite.getMinV();
        return sprite.getMinV() + (vRange / totalFrames) * (frame + 1);
    }

    private static int getTotalFrames(Sprite sprite) {
        return sprite.getContents().getHeight() / getMetadata(sprite).frameHeight();
    }

    private static WaveResourceMetadata getMetadata(Sprite sprite) {
        return sprite.getContents().getMetadata().decode(WaveResourceMetadata.SERIALIZER).orElse(WaveResourceMetadata.DEFAULT);
    }
}
