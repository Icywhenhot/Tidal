package net.superkat.tidal.particles.debug;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.particles.ScalableParticleOptionsBase;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.superkat.tidal.TidalParticles;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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
//        startColor = Vec3.unpackRgb(new Color(2, 246, 65).getRGB()).toVector3f();
//        midColor = Vec3.unpackRgb(new Color(253, 179, 66).getRGB()).toVector3f();
//        endColor = Vec3.unpackRgb(new Color(166, 17, 61).getRGB()).toVector3f();
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float tickDelta) {
        if(lifetimeColorMode) updateColor(tickDelta);
        super.extract(state, camera, tickDelta);
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

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleProvider<DebugWaveMovementParticleEffect> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(DebugWaveMovementParticleEffect dustParticleEffect, ClientLevel clientWorld, double d, double e, double f, double g, double h, double i, RandomSource random) {
            return new DebugWaveMovementParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugWaveMovementParticleEffect extends ScalableParticleOptionsBase {
        public static final MapCodec<DebugWaveMovementParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        ExtraCodecs.VECTOR3F.fieldOf("color").forGetter(effect -> effect.color),
                                SCALE.fieldOf("scale").forGetter(ScalableParticleOptionsBase::getScale),
                                Codec.FLOAT.fieldOf("yaw").forGetter(DebugWaveMovementParticleEffect::getYaw),
                                Codec.FLOAT.fieldOf("speed").forGetter(DebugWaveMovementParticleEffect::getSpeed),
                                Codec.INT.fieldOf("lifetime").forGetter(DebugWaveMovementParticleEffect::getLifetime)
                        )
                        .apply(instance, DebugWaveMovementParticleEffect::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, DebugWaveMovementParticleEffect> PACKET_CODEC = StreamCodec.composite(
                ByteBufCodecs.VECTOR3F, effect -> effect.color,
                ByteBufCodecs.FLOAT, ScalableParticleOptionsBase::getScale,
                ByteBufCodecs.FLOAT, DebugWaveMovementParticleEffect::getYaw,
                ByteBufCodecs.FLOAT, DebugWaveMovementParticleEffect::getSpeed,
                ByteBufCodecs.INT, DebugWaveMovementParticleEffect::getLifetime,
                DebugWaveMovementParticleEffect::new
        );
        private final Vector3fc color;
        private final float yaw;
        private final float speed;
        private final int lifetime;

        public DebugWaveMovementParticleEffect(Vector3fc color, float scale, float yaw, float speed, int lifetime) {
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
