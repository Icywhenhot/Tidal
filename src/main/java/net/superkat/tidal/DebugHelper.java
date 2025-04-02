package net.superkat.tidal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.wave.TidalWaveHandler;
import org.joml.Vector3f;

/**
 * General utils class for helping show data. For example, colors for a list to use with the debug particles.
 * <br><br>
 * Current debug stuff:
 * <ul>
 * <li>Spyglass in hotbar for all scanned water blocks, color coded per closest SitePos</li>
 * <li>Compass to see wave direction of all blocks in your chunk</li>
 * <li>Compass in offhand to see wave direction of all blocks within nearby chunks</li>
 * <li>Spyglass in hotbar to see debug wave particles in your chunk</li>
 * <li>Clock in hotbar to see debug wave particles in nearby chunks</li>
 * <li>Use spyglass in scanned water block to print direction in console</li>
 * </ul>
 */
public class DebugHelper {

    public static boolean debug() {
        return TidalConfig.debug;
    }

    // aha!
    public static boolean usingSpyglass() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if(player.getActiveItem().isOf(Items.SPYGLASS) && player.getItemUseTime() >= 10) {
            if(player.getItemUseTime() == 10) {
                player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            }
            return true;
        }
        return false;
    }

    // yes this is importa-ha-nt
    public static boolean spyglassInHotbar() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        PlayerInventory playerInventory = player.getInventory();
        return PlayerInventory.isValidHotbarIndex(playerInventory.getSlotWithStack(Items.SPYGLASS.getDefaultStack()));
    }

    public static boolean holdingSpyglass() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player.getMainHandStack().isOf(Items.SPYGLASS);
    }

    public static boolean offhandSpyglass() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player.getOffHandStack().isOf(Items.SPYGLASS);
    }

    // stop making fun of my choices of debug items - it's because i watch bdubs
    public static boolean clockInHotbar() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        PlayerInventory playerInventory = player.getInventory();
        return PlayerInventory.isValidHotbarIndex(playerInventory.getSlotWithStack(Items.CLOCK.getDefaultStack()));
    }

    public static boolean offhandClock() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player.getOffHandStack().isOf(Items.CLOCK);
    }

    public static boolean usingShield() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if(player.getActiveItem().isOf(Items.SHIELD) && (player.getItemUseTime() == 1 || player.isSneaking())) {
            player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            return true;
        }
        return false;
    }

    public static boolean holdingCompass() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player.getMainHandStack().isOf(Items.COMPASS);
    }

    public static boolean offhandCompass() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player.getOffHandStack().isOf(Items.COMPASS);
    }

    // sick
    // This method was redone an embarrassing amount of times to get nice looking colors
    public static Vector3f debugColor(int i, int size) {
        if(i == 0) return new Vector3f(1f, 1f, 1f); // white
        if(i == 1) return new Vector3f(1f, 0f, 0f); // red
        if(i == 2) return new Vector3f(0f, 1f, 0f); // green
        if(i == 3) return new Vector3f(0f, 0f, 1f); // blue

        i -= 3; // buy any get first 4 free

        // super ultra cursed debug colors - wait actually I'm a bit of a genuius
        // confusing, mind confusing confused, don't understand no snese uh - confusing
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
        float red = MathHelper.lerp(delta, start.x, end.x);
        float green = MathHelper.lerp(delta, start.y, end.y);
        float blue = MathHelper.lerp(delta, start.z, end.z);
        return new Vector3f(checkColor(red), checkColor(green), checkColor(blue));
    }

    public static Vector3f randomDebugColor() {
        Random random = TidalWaveHandler.getRandom();
        int rgbIncrease = random.nextBetween(1, 3);
        int red = rgbIncrease == 1 ? random.nextBetween(150, 255) : 255;
        int green = rgbIncrease == 2 ? random.nextBetween(150, 255) : 255;
        int blue = rgbIncrease == 3 ? random.nextBetween(150, 255) : 255;
        return new Vector3f(red / 255f, green / 255f, blue / 255f);
    }

    private static float checkColor(float color) {
        // confirm rgb int is within 255 because that debugColor method is pretty cursed
        if(color > 1f) return 1f;
        return Math.max(color, 0f); // wow intellij really smart
    }

}
