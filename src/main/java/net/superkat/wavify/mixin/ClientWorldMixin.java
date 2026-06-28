package net.superkat.wavify.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.event.ClientBlockUpdateEvent;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientWorldMixin implements WavifyWorld {
    @Unique
    public WavifyWaveHandler wavifyWaveHandler;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void wavify$createWavifyWaveHandler(ClientPacketListener networkHandler, ClientLevel.ClientLevelData properties, ResourceKey registryRef, Holder dimensionTypeEntry, int loadDistance, int simulationDistance, LevelExtractor levelExtractor, boolean debugWorld, long seed, int seaLevel, CallbackInfo ci) {
        this.wavifyWaveHandler = new WavifyWaveHandler((ClientLevel) (Object) this);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("TAIL"))
    public void wavify$blockUpdateEvent(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        ClientBlockUpdateEvent.BLOCK_UPDATE.invoker().onUpdate(pos, state);
    }

    @Override
    public WavifyWaveHandler wavify$wavifyWaveHandler() {
        return this.wavifyWaveHandler;
    }
}
