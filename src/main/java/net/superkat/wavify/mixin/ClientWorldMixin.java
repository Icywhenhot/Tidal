package net.superkat.wavify.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.superkat.wavify.duck.WavifyWorld;
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

    @Unique
    private WavifyWaveHandler wavify$ensureWaveHandler() {
        if (this.wavifyWaveHandler == null) {
            this.wavifyWaveHandler = new WavifyWaveHandler((ClientLevel) (Object) this);
        }
        return this.wavifyWaveHandler;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wavify$createWavifyWaveHandler(CallbackInfo ci) {
        this.wavify$ensureWaveHandler();
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("TAIL"))
    private void wavify$blockUpdateEvent(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        this.wavify$ensureWaveHandler().waterHandler.onBlockUpdate(pos, state);
    }

    @Override
    public WavifyWaveHandler wavify$wavifyWaveHandler() {
        return this.wavify$ensureWaveHandler();
    }
}
