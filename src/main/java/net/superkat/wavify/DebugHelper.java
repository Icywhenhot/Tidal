package net.superkat.wavify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.joml.Vector3f;

public class DebugHelper {

    public static boolean debug() {
        return WavifyConfig.debug;
    }

    public static boolean usingSpyglass() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if(player.getUseItem().is(Items.SPYGLASS) && player.getUseItemRemainingTicks() >= 10) {
            if(player.getUseItemRemainingTicks() == 10) {
                player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1f);
            }
            return true;
        }
        return false;
    }

    public static boolean spyglassInHotbar() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        Inventory playerInventory = player.getInventory();
        return Inventory.isHotbarSlot(playerInventory.findSlotMatchingItem(Items.SPYGLASS.getDefaultInstance()));
    }

    public static boolean holdingSpyglass() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return player.getMainHandItem().is(Items.SPYGLASS);
    }

    public static boolean offhandSpyglass() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return player.getOffhandItem().is(Items.SPYGLASS);
    }

    public static boolean clockInHotbar() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        Inventory playerInventory = player.getInventory();
        return Inventory.isHotbarSlot(playerInventory.findSlotMatchingItem(Items.CLOCK.getDefaultInstance()));
    }

    public static boolean offhandClock() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return player.getOffhandItem().is(Items.CLOCK);
    }

    public static boolean usingShield() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if(player.getUseItem().is(Items.SHIELD) && (player.getUseItemRemainingTicks() == 1 || player.isShiftKeyDown())) {
            player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1f);
            return true;
        }
        return false;
    }

    public static boolean holdingCompass() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return player.getMainHandItem().is(Items.COMPASS);
    }

    public static boolean offhandCompass() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return player.getOffhandItem().is(Items.COMPASS);
    }

    public static Vector3f debugColor(int i, int size) {
        if(i == 0) return new Vector3f(1f, 1f, 1f);
        if(i == 1) return new Vector3f(1f, 0f, 0f);
        if(i == 2) return new Vector3f(0f, 1f, 0f);
        if(i == 3) return new Vector3f(0f, 0f, 1f);

        i -= 3;

        int i1 = 255 -  ((((i / 3) + 1) * 30) % 255);
        int i2 = 255 -  ((((i / 3) + 30) * 30) % 255);
        int i3 = 255 -  ((((i / 3) - 90) * 30) % 255);

        int red = (i % 3 == 0 ? i1 : i % 3 == 1 ? i2 : i3);
        int green = (i % 3 == 1 ? i1 : i % 3 == 2 ? i2 : i3);
        int blue = (i % 3 == 2 ? i1 : i % 3 == 0 ? i2 : i3);
        return new Vector3f(checkColor(red / 255f), checkColor(green / 255f), checkColor(blue / 255f));
    }

    public static Vector3f debugTransitionColor(int i, int size) {
        return debugTransitionColor(i, size, new Vector3f(1f, 1f,1f), new Vector3f(0f, 0f, 0f));
    }

    public static Vector3f debugTransitionColor(int i, int size, Vector3f start, Vector3f end) {
        float delta = ((float) (i) / (size));
        float red = Mth.lerp(delta, start.x, end.x);
        float green = Mth.lerp(delta, start.y, end.y);
        float blue = Mth.lerp(delta, start.z, end.z);
        return new Vector3f(checkColor(red), checkColor(green), checkColor(blue));
    }

    public static Vector3f randomDebugColor() {
        RandomSource random = WavifyWaveHandler.getRandom();
        int rgbIncrease = random.nextIntBetweenInclusive(1, 3);
        int red = rgbIncrease == 1 ? random.nextIntBetweenInclusive(150, 255) : 255;
        int green = rgbIncrease == 2 ? random.nextIntBetweenInclusive(150, 255) : 255;
        int blue = rgbIncrease == 3 ? random.nextIntBetweenInclusive(150, 255) : 255;
        return new Vector3f(red / 255f, green / 255f, blue / 255f);
    }

    private static float checkColor(float color) {

        if(color > 1f) return 1f;
        return Math.max(color, 0f);
    }

}
