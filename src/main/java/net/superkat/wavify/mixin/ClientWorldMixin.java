package net.superkat.wavify.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Override
    public WavifyWaveHandler wavify$wavifyWaveHandler() {
        return this.wavify$ensureWaveHandler();
    }
}
