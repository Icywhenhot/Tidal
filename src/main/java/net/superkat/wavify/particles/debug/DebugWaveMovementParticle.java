package net.superkat.wavify.particles.debug;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.superkat.wavify.WavifyParticles;
import org.joml.Vector3f;

public class DebugWaveMovementParticle extends DebugAbstractColoredParticle<DebugWaveMovementParticle.DebugWaveMovementParticleEffect> {
    public float yaw = 0;
    public float speed = 0;
    public boolean lifetimeColorMode = false;

    private Vector3f startColor;
    private Vector3f midColor;
    private Vector3f endColor;

    public DebugWaveMovementParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, DebugWaveMovementParticleEffect parameters, SpriteSet spriteProvider) {
        super(level, x, y, z, xd, yd, zd, parameters, spriteProvider);
        this.yaw = parameters.getYaw();
        this.speed = parameters.getSpeed();
        this.lifetime = parameters.getLifetime();

        this.xd = Math.cos(Math.toRadians(yaw)) * speed;
        this.zd = Math.sin(Math.toRadians(yaw)) * speed;

        float red = parameters.color.x();
        float green = parameters.color.y();
        float blue = parameters.color.z();

        if(red == 1 && green == 1 && blue == 1) {
            this.lifetimeColorMode = true;
            this.rCol = red;
            this.gCol = green;
            this.bCol = blue;
        } else {
            this.rCol = this.randomizeColor(parameters.color.x(), 1);
            this.gCol = this.randomizeColor(parameters.color.y(), 1);
            this.bCol = this.randomizeColor(parameters.color.z(), 1);

        }

//        this.hasPhysics = false;
        this.gravity = 0;
        this.yd = -0.01f;

        startColor = new Vector3f(2 / 255f, 246 / 255f, 65 / 255f);
        midColor = new Vector3f(253 / 255f, 179 / 255f, 66 / 255f);
        endColor = new Vector3f(166 / 255f, 17 / 255f, 61 / 255f);
    }

    @Override
    public void tick() {
        super.tick();
        if (lifetimeColorMode && !this.removed) {
            updateColor(0.0f);
        }
    }

    private void updateColor(float tickDelta) {
        float mAge = this.lifetime / 2f;
        float f;
        Vector3f vector3f;
        if(this.age >= mAge) {
            f = ((float)this.age - mAge + tickDelta) / (mAge + 1.0F);
            vector3f = new Vector3f(this.midColor).lerp(this.endColor, f);
        } else {
            f = ((float)this.age + tickDelta) / (mAge + 1.0F);
            vector3f = new Vector3f(this.startColor).lerp(this.midColor, f);
        }

        this.rCol = vector3f.x();
        this.gCol = vector3f.y();
        this.bCol = vector3f.z();

        //i don't think this works but okay
        this.setAlpha(Mth.lerp((float) this.age / this.lifetime, 1f, 0f));
    }

    public static class Factory implements ParticleProvider<DebugWaveMovementParticleEffect> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(DebugWaveMovementParticleEffect dustParticleEffect, ClientLevel clientWorld, double d, double e, double f, double g, double h, double i) {
            return new DebugWaveMovementParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugWaveMovementParticleEffect extends AbstractDebugParticleEffect {
        public static final Codec<DebugWaveMovementParticleEffect> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                                Codec.FLOAT.fieldOf("r").forGetter(effect -> effect.color.x()),
                                Codec.FLOAT.fieldOf("g").forGetter(effect -> effect.color.y()),
                                Codec.FLOAT.fieldOf("b").forGetter(effect -> effect.color.z()),
                                Codec.FLOAT.fieldOf("scale").forGetter(AbstractDebugParticleEffect::getScale),
                                Codec.FLOAT.fieldOf("yaw").forGetter(DebugWaveMovementParticleEffect::getYaw),
                                Codec.FLOAT.fieldOf("speed").forGetter(DebugWaveMovementParticleEffect::getSpeed),
                                Codec.INT.fieldOf("lifetime").forGetter(DebugWaveMovementParticleEffect::getLifetime)
                        )
                        .apply(instance, (r, g, b, scale, yaw, speed, lifetime) ->
                                new DebugWaveMovementParticleEffect(new Vector3f(r, g, b), scale, yaw, speed, lifetime))
        );

        public static final ParticleOptions.Deserializer<DebugWaveMovementParticleEffect> DESERIALIZER = new ParticleOptions.Deserializer<>() {
            @Override
            public DebugWaveMovementParticleEffect fromCommand(ParticleType<DebugWaveMovementParticleEffect> type, StringReader reader) throws CommandSyntaxException {
                Vector3f color = readColor(reader);
                reader.expect(' ');
                float scale = reader.readFloat();
                reader.expect(' ');
                float yaw = reader.readFloat();
                reader.expect(' ');
                float speed = reader.readFloat();
                reader.expect(' ');
                int lifetime = reader.readInt();
                return new DebugWaveMovementParticleEffect(color, scale, yaw, speed, lifetime);
            }

            @Override
            public DebugWaveMovementParticleEffect fromNetwork(ParticleType<DebugWaveMovementParticleEffect> type, FriendlyByteBuf buf) {
                Vector3f color = readColor(buf);
                return new DebugWaveMovementParticleEffect(color, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt());
            }
        };

        private final float yaw;
        private final float speed;
        private final int lifetime;

        public DebugWaveMovementParticleEffect(Vector3f color, float scale, float yaw, float speed, int lifetime) {
            super(color, scale);
            this.yaw = yaw;
            this.speed = speed;
            this.lifetime = lifetime;
        }

        @Override
        public ParticleType<DebugWaveMovementParticleEffect> getType() {
            return WavifyParticles.DEBUG_WAVEMOVEMENT_PARTICLE.get();
        }

        @Override
        public void writeToNetwork(FriendlyByteBuf buf) {
            super.writeToNetwork(buf);
            buf.writeFloat(this.yaw);
            buf.writeFloat(this.speed);
            buf.writeInt(this.lifetime);
        }

        public float getYaw() {
            return yaw;
        }

        public float getSpeed() {
            return speed;
        }

        public int getLifetime() {
            return this.lifetime;
        }
    }
}
