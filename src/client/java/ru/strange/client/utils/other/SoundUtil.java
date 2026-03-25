package ru.strange.client.utils.other;

import ru.strange.client.Strange;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SoundUtil {
    private static final List<Clip> ACTIVE_CLIPS = new ArrayList<>();

    private SoundUtil() {
    }

    public static synchronized void playSound_wav(String location, float volume) {
        cleanupStoppedClips();

        String resourcePath = "/assets/" + Strange.get.rootRes + "/sounds/wav/" + location + ".wav";
        try (InputStream inputStream = SoundUtil.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                Strange.LOGGER.warn("WAV sound resource not found: {}", resourcePath);
                return;
            }

            try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                 AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedInputStream)) {
                Clip clip = AudioSystem.getClip();
                clip.addLineListener(event -> onClipEvent(clip, event));
                clip.open(audioInputStream);
                applyVolume(clip, volume);
                ACTIVE_CLIPS.add(clip);
                clip.start();
            }
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to play WAV sound {}", location, e);
        }
    }

    private static synchronized void cleanupStoppedClips() {
        Iterator<Clip> iterator = ACTIVE_CLIPS.iterator();
        while (iterator.hasNext()) {
            Clip clip = iterator.next();
            if (clip == null) {
                iterator.remove();
                continue;
            }

            if (!clip.isRunning()) {
                closeClip(clip);
                iterator.remove();
            }
        }
    }

    private static synchronized void onClipEvent(Clip clip, LineEvent event) {
        if (event.getType() != LineEvent.Type.STOP && event.getType() != LineEvent.Type.CLOSE) {
            return;
        }

        ACTIVE_CLIPS.remove(clip);
        closeClip(clip);
    }

    private static void closeClip(Clip clip) {
        if (clip == null) {
            return;
        }

        try {
            clip.stop();
        } catch (Exception ignored) {
        }

        try {
            clip.close();
        } catch (Exception ignored) {
        }
    }

    private static void applyVolume(Clip clip, float volume) {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }

        float normalizedVolume = Math.max(0.0001f, Math.min(1.0f, volume));
        FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float gain = (float) (20.0 * Math.log10(normalizedVolume));
        control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), gain)));
    }
}
