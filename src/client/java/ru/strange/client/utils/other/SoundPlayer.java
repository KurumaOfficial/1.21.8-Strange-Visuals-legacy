package ru.strange.client.utils.other;

import ru.strange.client.Strange;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SoundPlayer {
    private static final String DEFAULT_CUSTOM_SOUND_DIRECTORY = "hitsounds";
    private static final int MAX_CONCURRENT_SOUNDS = 8;
    private static final int MAX_QUEUED_SOUNDS = 24;
    private static final Set<String> LOGGED_CUSTOM_SOUND_LOOKUP_FAILURES = ConcurrentHashMap.newKeySet();

    private static final ThreadPoolExecutor SOUND_EXECUTOR = new ThreadPoolExecutor(
            MAX_CONCURRENT_SOUNDS,
            MAX_CONCURRENT_SOUNDS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_SOUNDS),
            runnable -> {
                Thread t = new Thread(runnable, "Strange-SoundPlayer");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public static void playSound(String fileName, float volumePercent, boolean async) {
        Runnable task = () -> playResourceSound(fileName, volumePercent);

        if (async) {
            submitAsync(task, fileName);
        } else {
            task.run();
        }
    }

    public static void playCustomSound(String fileName, float volumePercent, boolean async) {
        Runnable task = () -> playExternalSound(fileName, volumePercent);

        if (async) {
            submitAsync(task, fileName);
        } else {
            task.run();
        }
    }

    private static void submitAsync(Runnable task, String fileName) {
        try {
            SOUND_EXECUTOR.execute(task);
        } catch (RejectedExecutionException exception) {
            Strange.LOGGER.debug("Dropped queued sound task {}", fileName);
        }
    }

    private static void playResourceSound(String fileName, float volumePercent) {
        String path = "/assets/strange/sounds/wav/" + fileName + ".wav";

        try (InputStream stream = SoundPlayer.class.getResourceAsStream(path)) {
            if (stream == null) {
                Strange.LOGGER.warn("Sound resource not found: {}", path);
                return;
            }

            try (BufferedInputStream buffered = new BufferedInputStream(stream);
                 AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(buffered)) {
                playAudioStream(audioInputStream, volumePercent);
            }
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException exception) {
            Strange.LOGGER.warn("Failed to play sound resource {}", path, exception);
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
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException exception) {
            Strange.LOGGER.warn("Failed to play custom sound {}", resolved, exception);
        }
    }

    private static void playAudioStream(AudioInputStream audioInputStream, float volumePercent)
            throws IOException, LineUnavailableException {
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
        Path hitSoundRoot = Strange.root.toPath().resolve(DEFAULT_CUSTOM_SOUND_DIRECTORY).toAbsolutePath().normalize();

        Path direct = Path.of(normalized);
        if (direct.isAbsolute()) {
            Path absolute = direct.normalize();
            if (Files.isRegularFile(absolute)) {
                clearCustomSoundLookupFailure(normalized);
                return absolute;
            }
        }

        Path relative = resolveUnderHitSoundRoot(hitSoundRoot, normalized);
        if (relative != null && Files.isRegularFile(relative)) {
            clearCustomSoundLookupFailure(normalized);
            return relative;
        }

        if (!normalized.toLowerCase().endsWith(".wav")) {
            Path withExtension = resolveUnderHitSoundRoot(hitSoundRoot, normalized + ".wav");
            if (withExtension != null && Files.isRegularFile(withExtension)) {
                clearCustomSoundLookupFailure(normalized);
                return withExtension;
            }
        }

        logMissingCustomSoundOnce(normalized, hitSoundRoot);
        return null;
    }

    private static Path resolveUnderHitSoundRoot(Path hitSoundRoot, String fileName) {
        Path resolved = hitSoundRoot.resolve(fileName).normalize();
        return resolved.startsWith(hitSoundRoot) ? resolved : null;
    }

    private static void logMissingCustomSoundOnce(String fileName, Path hitSoundRoot) {
        String key = fileName.toLowerCase();
        if (LOGGED_CUSTOM_SOUND_LOOKUP_FAILURES.add(key)) {
            Strange.LOGGER.warn("Custom sound {} was not found under {} or as an absolute path", fileName, hitSoundRoot);
        }
    }

    private static void clearCustomSoundLookupFailure(String fileName) {
        LOGGED_CUSTOM_SOUND_LOOKUP_FAILURES.remove(fileName.toLowerCase());
    }
}
