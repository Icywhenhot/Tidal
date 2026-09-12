package net.superkat.wavify.sprite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.ExtraCodecs;

public record WaveResourceMetadata(int frameTime, int frameHeight) {
    public static final String KEY = "wave_animation";

    public static final WaveResourceMetadata DEFAULT = new WaveResourceMetadata(5, 16);
    public static final Codec<WaveResourceMetadata> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.INT.optionalFieldOf("frametime", 5).forGetter(WaveResourceMetadata::frameTime),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("frame_height", 16).forGetter(WaveResourceMetadata::frameHeight)
            ).apply(instance, WaveResourceMetadata::new));
    public static final MetadataSectionType<WaveResourceMetadata> SERIALIZER = new MetadataSectionType<>(KEY, CODEC);

}
