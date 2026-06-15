package net.superkat.wavify.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.event.ClientBlockUpdateEvent;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(ClientWorld.class)
public class ClientWorldMixin implements WavifyWorld {
    @Unique
    public WavifyWaveHandler wavifyWaveHandler;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void wavify$createWavifyWaveHandler(ClientPlayNetworkHandler networkHandler, ClientWorld.Properties properties, RegistryKey registryRef, RegistryEntry dimensionTypeEntry, int loadDistance, int simulationDistance, Supplier profiler, WorldRenderer worldRenderer, boolean debugWorld, long seed, CallbackInfo ci) {
        this.wavifyWaveHandler = new WavifyWaveHandler((ClientWorld) (Object) this);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    public void wavify$blockUpdateEvent(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        ClientBlockUpdateEvent.BLOCK_UPDATE.invoker().onUpdate(pos, state);
    }

    @Override
    public WavifyWaveHandler wavify$wavifyWaveHandler() {
        return this.wavifyWaveHandler;
    }
}
