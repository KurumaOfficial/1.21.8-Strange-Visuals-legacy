package ru.strange.client.utils.other;

public final class SoundUtil {
    private SoundUtil() {
    }

    public static void playSound_wav(String location, float volume) {
        SoundPlayer.playSound(location, normalizeVolumePercent(volume), true);
    }

    private static float normalizeVolumePercent(float volume) {
        float normalized = volume <= 1.0f ? volume * 100.0f : volume;
        return Math.max(0.0f, Math.min(100.0f, normalized));
    }
}
