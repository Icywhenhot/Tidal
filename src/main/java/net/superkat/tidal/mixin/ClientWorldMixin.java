package net.superkat.tidal.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.superkat.tidal.duck.TidalWorld;
import net.superkat.tidal.event.ClientBlockUpdateEvent;
import net.superkat.tidal.wave.TidalWaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin implements TidalWorld {
    @Unique
    public TidalWaveHandler tidalWaveHandler;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void tidal$createTidalWaveHandler(ClientPlayNetworkHandler networkHandler, ClientWorld.Properties properties, RegistryKey registryRef, RegistryEntry dimensionTypeEntry, int loadDistance, int simulationDistance, WorldRenderer worldRenderer, boolean debugWorld, long seed, int seaLevel, CallbackInfo ci) {
        this.tidalWaveHandler = new TidalWaveHandler((ClientWorld) (Object) this);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    public void tidal$blockUpdateEvent(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        ClientBlockUpdateEvent.BLOCK_UPDATE.invoker().onUpdate(pos, state);
    }

    @Override
    public TidalWaveHandler tidal$tidalWaveHandler() {
        return this.tidalWaveHandler;
    }
}
