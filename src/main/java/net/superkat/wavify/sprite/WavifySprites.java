package net.superkat.wavify.sprite;

import net.minecraft.util.Identifier;

public class WavifySprites {

    public static final String MOD_ID = WavifySpriteHandler.MOD_ID;

    public static final Identifier MOVING_TEXTURE_ID = Identifier.of(MOD_ID, "moving");
    public static final Identifier MOVING_WHITE_TEXTURE_ID = Identifier.of(MOD_ID, "moving_white");

    public static final Identifier TOP_WASHING_ID = Identifier.of(MOD_ID, "washing_top_colorable");
    public static final Identifier TOP_WASHING_WHITE_ID = Identifier.of(MOD_ID, "washing_top_white");
    public static final Identifier BOTTOM_WASHING_ID = Identifier.of(MOD_ID, "washing_bottom_colorable");
    public static final Identifier BOTTOM_WASHING_WHITE_ID = Identifier.of(MOD_ID, "washing_bottom_white");

    public static final Identifier WET_OVERLAY_TEXTURE_ID = Identifier.of(MOD_ID, "wet_overlay");
    public static final Identifier RIPPLE_ID = Identifier.of(MOD_ID, "ripple");
    public static final Identifier WAKE_LINE_ID = Identifier.of(MOD_ID, "wake_line");
    public static final Identifier FOAM_PUFF_ID = Identifier.of(MOD_ID, "foam_puff");
    public static final Identifier[] SLUSH_IDS = {
            Identifier.of(MOD_ID, "slush_1"),
            Identifier.of(MOD_ID, "slush_2"),
            Identifier.of(MOD_ID, "slush_3")
    };
}
