package net.superkat.wavify.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.superkat.wavify.Wavify;

public class WavifySounds {
    public static final SoundEvent OCEAN_WAVE_1 = create("ocean_wave_1");
    public static final SoundEvent OCEAN_WAVE_2 = create("ocean_wave_2");
    public static final SoundEvent RIVER_WAVE = create("river_wave");

    private static SoundEvent create(String name) {
        ResourceLocation id = new ResourceLocation(Wavify.MOD_ID, name);
        return SoundEvent.createVariableRangeEvent(id);
    }

    public static void register() {
        register("ocean_wave_1", OCEAN_WAVE_1);
        register("ocean_wave_2", OCEAN_WAVE_2);
        register("river_wave", RIVER_WAVE);
    }

    private static void register(String name, SoundEvent soundEvent) {
        Registry.register(BuiltInRegistries.SOUND_EVENT, new ResourceLocation(Wavify.MOD_ID, name), soundEvent);
    }
}
