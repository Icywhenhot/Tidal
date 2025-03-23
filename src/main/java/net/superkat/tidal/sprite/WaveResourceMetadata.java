package net.superkat.tidal.sprite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.dynamic.Codecs;

public record WaveResourceMetadata(int frameTime, int frameHeight) {
    public static final String KEY = "wave_animation";

    public static final WaveResourceMetadata DEFAULT = new WaveResourceMetadata(5, 16);
    public static final Codec<WaveResourceMetadata> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codecs.POSITIVE_INT.optionalFieldOf("frametime", 5).forGetter(WaveResourceMetadata::frameTime),
                    Codecs.POSITIVE_INT.optionalFieldOf("frame_height", 16).forGetter(WaveResourceMetadata::frameHeight)
            ).apply(instance, WaveResourceMetadata::new));
    public static final ResourceMetadataSerializer<WaveResourceMetadata> SERIALIZER = ResourceMetadataSerializer.fromCodec(KEY, CODEC);


}
