package net.superkat.tidal.particles.debug;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.AbstractDustParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.MathHelper;
import net.superkat.tidal.TidalParticles;
import org.joml.Vector3f;

public class DebugWaveMovementParticle extends DebugAbstractColoredParticle<DebugWaveMovementParticle.DebugWaveMovementParticleEffect> {
    public float yaw = 0;
    public float speed = 0;
    public boolean lifetimeColorMode = false;

    private Vector3f startColor;
    private Vector3f midColor;
    private Vector3f endColor;

    public DebugWaveMovementParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, DebugWaveMovementParticleEffect parameters, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, parameters, spriteProvider);
        this.yaw = parameters.getYaw();
        this.speed = parameters.getSpeed();
        this.maxAge = parameters.getLifetime();

        this.velocityX = Math.cos(Math.toRadians(yaw)) * speed;
        this.velocityZ = Math.sin(Math.toRadians(yaw)) * speed;

        float red = parameters.color.x();
        float green = parameters.color.y();
        float blue = parameters.color.z();

        if(red == 1 && green == 1 && blue == 1) {
            this.lifetimeColorMode = true;
            this.red = red;
            this.green = green;
            this.blue = blue;
        } else {
            this.red = this.darken(parameters.color.x(), 1);
            this.green = this.darken(parameters.color.y(), 1);
            this.blue = this.darken(parameters.color.z(), 1);

        }

//        this.collidesWithWorld = false;
        this.gravityStrength = 0;
        this.velocityY = -0.01f;

        startColor = new Vector3f(2 / 255f, 246 / 255f, 65 / 255f);
        midColor = new Vector3f(253 / 255f, 179 / 255f, 66 / 255f);
        endColor = new Vector3f(166 / 255f, 17 / 255f, 61 / 255f);
//        startColor = Vec3d.unpackRgb(new Color(2, 246, 65).getRGB()).toVector3f();
//        midColor = Vec3d.unpackRgb(new Color(253, 179, 66).getRGB()).toVector3f();
//        endColor = Vec3d.unpackRgb(new Color(166, 17, 61).getRGB()).toVector3f();
    }

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        if(lifetimeColorMode) updateColor(tickDelta);
        super.buildGeometry(vertexConsumer, camera, tickDelta);
    }

    private void updateColor(float tickDelta) {
        float mAge = this.maxAge / 2f;
        float f;
        Vector3f vector3f;
        if(this.age >= mAge) {
            f = ((float)this.age - mAge + tickDelta) / (mAge + 1.0F);
            vector3f = new Vector3f(this.midColor).lerp(this.endColor, f);
        } else {
            f = ((float)this.age + tickDelta) / (mAge + 1.0F);
            vector3f = new Vector3f(this.startColor).lerp(this.midColor, f);
        }

        this.red = vector3f.x();
        this.green = vector3f.y();
        this.blue = vector3f.z();

        //i don't think this works but okay
        this.setAlpha(MathHelper.lerp((float) this.age / this.maxAge, 1f, 0f));
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DebugWaveMovementParticleEffect> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(DebugWaveMovementParticleEffect dustParticleEffect, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            return new DebugWaveMovementParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugWaveMovementParticleEffect extends AbstractDustParticleEffect {
        public static final MapCodec<DebugWaveMovementParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codecs.VECTOR_3F.fieldOf("color").forGetter(effect -> effect.color),
                                SCALE_CODEC.fieldOf("scale").forGetter(AbstractDustParticleEffect::getScale),
                                Codec.FLOAT.fieldOf("yaw").forGetter(DebugWaveMovementParticleEffect::getYaw),
                                Codec.FLOAT.fieldOf("speed").forGetter(DebugWaveMovementParticleEffect::getSpeed),
                                Codec.INT.fieldOf("lifetime").forGetter(DebugWaveMovementParticleEffect::getLifetime)
                        )
                        .apply(instance, DebugWaveMovementParticleEffect::new)
        );
        public static final PacketCodec<RegistryByteBuf, DebugWaveMovementParticleEffect> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.VECTOR3F, effect -> effect.color,
                PacketCodecs.FLOAT, AbstractDustParticleEffect::getScale,
                PacketCodecs.FLOAT, DebugWaveMovementParticleEffect::getYaw,
                PacketCodecs.FLOAT, DebugWaveMovementParticleEffect::getSpeed,
                PacketCodecs.INTEGER, DebugWaveMovementParticleEffect::getLifetime,
                DebugWaveMovementParticleEffect::new
        );
        private final Vector3f color;
        private final float yaw;
        private final float speed;
        private final int lifetime;

        public DebugWaveMovementParticleEffect(Vector3f color, float scale, float yaw, float speed, int lifetime) {
            super(scale);
            this.color = color;
            this.yaw = yaw;
            this.speed = speed;
            this.lifetime = lifetime;
        }

        @Override
        public ParticleType<DebugWaveMovementParticleEffect> getType() {
            return TidalParticles.DEBUG_WAVEMOVEMENT_PARTICLE;
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
