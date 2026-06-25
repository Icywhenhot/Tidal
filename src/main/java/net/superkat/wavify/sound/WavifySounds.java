package net.superkat.wavify.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.superkat.wavify.Wavify;

public class WavifySounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Wavify.MOD_ID);

    public static final RegistryObject<SoundEvent> OCEAN_WAVE_1 = register("ocean_wave_1");
    public static final RegistryObject<SoundEvent> OCEAN_WAVE_2 = register("ocean_wave_2");
    public static final RegistryObject<SoundEvent> RIVER_WAVE = register("river_wave");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(Wavify.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
