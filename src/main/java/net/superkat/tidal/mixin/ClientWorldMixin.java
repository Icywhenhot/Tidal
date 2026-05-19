package net.superkat.tidal.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.superkat.tidal.duck.TidalWorld;
import net.superkat.tidal.event.ClientBlockUpdateEvent;
import net.superkat.tidal.wave.TidalWaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientWorldMixin implements TidalWorld {
    @Unique
    public TidalWaveHandler tidalWaveHandler;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void tidal$createTidalWaveHandler(ClientPacketListener networkHandler, ClientLevel.ClientLevelData properties, ResourceKey registryRef, Holder dimensionTypeEntry, int loadDistance, int simulationDistance, LevelRenderer worldRenderer, boolean debugWorld, long seed, int seaLevel, CallbackInfo ci) {
        this.tidalWaveHandler = new TidalWaveHandler((ClientLevel) (Object) this);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("TAIL"))
    public void tidal$blockUpdateEvent(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        ClientBlockUpdateEvent.BLOCK_UPDATE.invoker().onUpdate(pos, state);
    }

    @Override
    public TidalWaveHandler tidal$tidalWaveHandler() {
        return this.tidalWaveHandler;
    }
}
