package net.superkat.wavify.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.superkat.wavify.Wavify;

public class WavifySounds {
    public static final SoundEvent OCEAN_WAVE_1 = register("ocean_wave_1");
    public static final SoundEvent OCEAN_WAVE_2 = register("ocean_wave_2");
    public static final SoundEvent RIVER_WAVE = register("river_wave");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(Wavify.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
        // Class-load side-effect: forces the static fields above to register.
    }
}
