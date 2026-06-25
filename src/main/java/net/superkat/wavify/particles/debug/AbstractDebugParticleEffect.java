package net.superkat.wavify.particles.debug;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Shared base for the debug particle options. Replaces 1.20.5+'s {@code ScalableParticleOptionsBase};
 * holds an RGB color and a scale, and provides the {@link ParticleOptions} plumbing the 1.20.1 particle
 * system needs (network/string serialization). The color is encoded as three floats so no Vector3f codec
 * or {@code ByteBufCodecs} (1.20.5+) is required.
 */
public abstract class AbstractDebugParticleEffect implements ParticleOptions {
    public final Vector3f color;
    protected final float scale;

    protected AbstractDebugParticleEffect(Vector3f color, float scale) {
        this.color = color;
        this.scale = scale;
    }

    public Vector3f getColor() {
        return this.color;
    }

    public float getScale() {
        return this.scale;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(this.color.x());
        buf.writeFloat(this.color.y());
        buf.writeFloat(this.color.z());
        buf.writeFloat(this.scale);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f %.2f",
                BuiltInRegistries.PARTICLE_TYPE.getKey(this.getType()), this.color.x(), this.color.y(), this.color.z(), this.scale);
    }

    protected static Vector3f readColor(FriendlyByteBuf buf) {
        return new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    protected static Vector3f readColor(StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        float r = reader.readFloat();
        reader.expect(' ');
        float g = reader.readFloat();
        reader.expect(' ');
        float b = reader.readFloat();
        return new Vector3f(r, g, b);
    }
}
