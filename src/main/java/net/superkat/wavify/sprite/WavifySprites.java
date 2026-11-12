package net.superkat.wavify.sprite;

import net.minecraft.resources.ResourceLocation;

public class WavifySprites {

    public static final String MOD_ID = WavifySpriteHandler.MOD_ID;

    public static final ResourceLocation MOVING_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "moving");
    public static final ResourceLocation MOVING_WHITE_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "moving_white");

    public static final ResourceLocation TOP_WASHING_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_top_colorable");
    public static final ResourceLocation TOP_WASHING_WHITE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_top_white");
    public static final ResourceLocation BOTTOM_WASHING_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_bottom_colorable");
    public static final ResourceLocation BOTTOM_WASHING_WHITE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "washing_bottom_white");

    public static final ResourceLocation WET_OVERLAY_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "wet_overlay");
    public static final ResourceLocation RIPPLE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "ripple");
    public static final ResourceLocation WAKE_LINE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "wake_line");
    public static final ResourceLocation FOAM_PUFF_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "foam_puff");
    public static final ResourceLocation[] SLUSH_IDS = {
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "slush_1"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "slush_2"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "slush_3")
    };
}
