package net.superkat.wavify.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class ClientBlockUpdateEvent {

    public static final Event<BlockUpdate> BLOCK_UPDATE = EventFactory.createArrayBacked(BlockUpdate.class,
            (listeners) -> ((pos, state) -> {
                for (BlockUpdate listener : listeners) {
                    listener.onUpdate(pos, state);
                }
            })
    );

    @FunctionalInterface
    public interface BlockUpdate {
        void onUpdate(BlockPos pos, BlockState state);
    }

}
