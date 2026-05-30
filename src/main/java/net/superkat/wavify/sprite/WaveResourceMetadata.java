package net.superkat.wavify.sprite;

public record WaveResourceMetadata(int frameTime, int frameHeight) {
    public static final WaveResourceMetadata DEFAULT = new WaveResourceMetadata(5, 16);
}
