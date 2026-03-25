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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoundPlayer {

    private static final int MAX_CONCURRENT_SOUNDS = 8;

    private static final ExecutorService SOUND_EXECUTOR = Executors.newFixedThreadPool(
            MAX_CONCURRENT_SOUNDS,
            runnable -> {
                Thread t = new Thread(runnable, "Strange-SoundPlayer");
                t.setDaemon(true);
                return t;
            }
    );

    public static void playSound(String fileName, float volumePercent, boolean async) {
        Runnable task = () -> playResourceSound(fileName, volumePercent);

        if (async) {
            SOUND_EXECUTOR.execute(task);
        } else {
            task.run();
        }
    }

    public static void playCustomSound(String fileName, float volumePercent, boolean async) {
        Runnable task = () -> playExternalSound(fileName, volumePercent);

        if (async) {
            SOUND_EXECUTOR.execute(task);
        } else {
            task.run();
        }
    }

    private static void playResourceSound(String fileName, float volumePercent) {
        String path = "/assets/strange/sounds/wav/" + fileName + ".wav";

        try (InputStream stream = SoundPlayer.class.getResourceAsStream(path)) {
            if (stream == null) {
                return;
            }

            try (BufferedInputStream buffered = new BufferedInputStream(stream);
                 AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(buffered)) {
                playAudioStream(audioInputStream, volumePercent);
            }
        } catch (Exception ignored) {
        }
    }

    private static void playExternalSound(String fileName, float volumePercent) {
        Path resolved = resolveCustomSound(fileName);
        if (resolved == null) {
            return;
        }

        try (InputStream stream = Files.newInputStream(resolved);
             BufferedInputStream buffered = new BufferedInputStream(stream);
             AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(buffered)) {
            playAudioStream(audioInputStream, volumePercent);
        } catch (Exception ignored) {
        }
    }

    private static void playAudioStream(AudioInputStream audioInputStream, float volumePercent) throws Exception {
        Clip clip = AudioSystem.getClip();
        boolean started = false;
        try {
            clip.open(audioInputStream);

            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float volume = Math.max(0f, Math.min(100f, volumePercent));
                if (volume == 0f) {
                    gainControl.setValue(gainControl.getMinimum());
                } else {
                    float dB = (float) (20.0 * Math.log10(volume / 100.0));
                    gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                }
            }

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });

            clip.start();
            started = true;
        } finally {
            if (!started) {
                clip.close();
            }
        }
    }

    private static Path resolveCustomSound(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        String normalized = fileName.trim();

        Path direct = Path.of(normalized);
        if (Files.isRegularFile(direct)) {
            return direct;
        }

        Path hitSoundRoot = Strange.root.toPath().resolve("hitsounds");
        Path relative = hitSoundRoot.resolve(normalized);
        if (Files.isRegularFile(relative)) {
            return relative;
        }

        if (!normalized.toLowerCase().endsWith(".wav")) {
            Path withExtension = hitSoundRoot.resolve(normalized + ".wav");
            if (Files.isRegularFile(withExtension)) {
                return withExtension;
            }
        }

        return null;
    }
}
