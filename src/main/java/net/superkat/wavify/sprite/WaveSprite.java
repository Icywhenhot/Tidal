package net.superkat.wavify.sprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

public record WaveSprite(TextureAtlasSprite sprite, int frameTime, int frames) {

    public record Uv(float u0, float u1, float v0, float v1) {
    }

    public static WaveSprite of(TextureAtlasSprite sprite) {
        WaveResourceMetadata meta = sprite.contents()
                .getAdditionalMetadata(WaveResourceMetadata.SERIALIZER).orElse(WaveResourceMetadata.DEFAULT);
        int frames = Math.max(1, sprite.contents().height() / meta.frameHeight());
        return new WaveSprite(sprite, meta.frameTime(), frames);
    }

    public Uv uvAt(int age, int maxAge) {
        float span = (this.sprite.getV1() - this.sprite.getV0()) / this.frames;
        float top = this.sprite.getV0() + span * frameAt(age, maxAge);
        return new Uv(this.sprite.getU0(), this.sprite.getU1(), top, top + span);
    }

    private int frameAt(int age, int maxAge) {
        if (this.frameTime > 0) {
            return Math.floorMod(age / this.frameTime, this.frames);
        }
        if (maxAge <= 0) return 0;
        return Mth.clamp((int) ((float) age / maxAge * this.frames), 0, this.frames - 1);
    }
}
