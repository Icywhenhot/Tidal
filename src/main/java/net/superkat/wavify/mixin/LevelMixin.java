package net.superkat.wavify.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.superkat.wavify.duck.WavifyWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block-update hook. Targets {@link Level} (not {@link ClientLevel}) because {@code setBlock(...II)Z} lives
 * on {@code Level} - a {@code ClientLevel}-targeted inject would fail to find the method. The instanceof
 * guard keeps this client-only (this mixin is in the client list, but singleplayer also runs a server
 * {@code ServerLevel} through {@code Level#setBlock}).
 */
@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("TAIL"))
    private void wavify$blockUpdateEvent(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (((Object) this) instanceof ClientLevel clientLevel) {
            ((WavifyWorld) clientLevel).wavify$wavifyWaveHandler().waterHandler.onBlockUpdate(pos, state);
        }
    }
}
