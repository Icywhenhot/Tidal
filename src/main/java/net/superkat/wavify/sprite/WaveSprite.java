package net.superkat.wavify.sprite;

import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.MathHelper;

public record WaveSprite(Sprite sprite, int frameTime, int frames) {

    public record Uv(float u0, float u1, float v0, float v1) {
    }

    public static WaveSprite of(Sprite sprite) {
        WaveResourceMetadata meta = sprite.getContents().getMetadata()
                .decode(WaveResourceMetadata.SERIALIZER).orElse(WaveResourceMetadata.DEFAULT);
        int frames = Math.max(1, sprite.getContents().getHeight() / meta.frameHeight());
        return new WaveSprite(sprite, meta.frameTime(), frames);
    }

    public Uv uvAt(int age, int maxAge) {
        float span = (this.sprite.getMaxV() - this.sprite.getMinV()) / this.frames;
        float top = this.sprite.getMinV() + span * frameAt(age, maxAge);
        return new Uv(this.sprite.getMinU(), this.sprite.getMaxU(), top, top + span);
    }

    private int frameAt(int age, int maxAge) {
        if (this.frameTime > 0) {
            return Math.floorMod(age / this.frameTime, this.frames);
        }
        if (maxAge <= 0) return 0;
        return MathHelper.clamp((int) ((float) age / maxAge * this.frames), 0, this.frames - 1);
    }
}
