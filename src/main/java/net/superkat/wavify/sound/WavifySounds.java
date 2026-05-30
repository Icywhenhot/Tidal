package net.superkat.wavify.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.superkat.wavify.Wavify;

public class WavifySounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Wavify.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> OCEAN_WAVE_1 = register("ocean_wave_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> OCEAN_WAVE_2 = register("ocean_wave_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIVER_WAVE = register("river_wave");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Wavify.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
