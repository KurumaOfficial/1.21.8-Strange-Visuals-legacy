package ru.strange.client.utils.other;

import by.bonenaut7.mediatransport4j.api.MediaSession;
import by.bonenaut7.mediatransport4j.api.MediaTransport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaUtil {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "strange-media-session");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, Identifier> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean transportReady;
    private static volatile MediaInfo currentMedia;
    private static volatile String previousHash = "";
    private static volatile String lastTrackKey = "";
    private static volatile long lastTrackChangedAt;
    private static volatile long lastTimingSnapshotAt;
    private static volatile long lastResolvedDurationMs;
    private static final AtomicBoolean CONTROL_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile long lastControlAttemptAt;

    private MediaUtil() {
    }

    public static MediaInfo getCurrentMedia() {
        ensureStarted();
        return currentMedia;
    }

    public static TrackInfo currentTrack() {
        MediaInfo media = getCurrentMedia();
        if (media == null) {
            return TrackInfo.stopped();
        }

        String subtitle = media.artist == null || media.artist.isBlank() ? "media session" : media.artist;
        return new TrackInfo(media.title, subtitle, media.playing, media.progress);
    }

    public static boolean togglePlayPause() {
        return scheduleControl(session -> {
            if (!invokeControlMethod(session, "togglePlay", "togglePlayPause", "playPause")) {
                session.togglePlay();
            }
        });
    }

    public static boolean skipNext() {
        return scheduleControl(session -> invokeControlMethod(session,
                "skipNext",
                "next",
                "nextTrack",
                "nextItem",
                "forward"
        ));
    }

    public static boolean skipPrevious() {
        return scheduleControl(session -> invokeControlMethod(session,
                "skipPrevious",
                "previous",
                "previousTrack",
                "prev",
                "back"
        ));
    }

    private static boolean scheduleControl(SessionControlAction action) {
        ensureStarted();
        if (!transportReady) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastControlAttemptAt < 120L) {
            return false;
        }

        if (!CONTROL_IN_FLIGHT.compareAndSet(false, true)) {
            return false;
        }

        lastControlAttemptAt = now;
        SCHEDULER.execute(() -> {
            try {
                MediaSession session = firstSession();
                if (session != null) {
                    action.apply(session);
                }
                pollSessions();
            } catch (Throwable e) {
                Strange.LOGGER.debug("Media control action failed", e);
            } finally {
                CONTROL_IN_FLIGHT.set(false);
            }
        });
        return true;
    }

    private static boolean invokeControlMethod(MediaSession session, String... methodNames) {
        if (session == null || methodNames == null) {
            return false;
        }

        for (String methodName : methodNames) {
            if (methodName == null || methodName.isBlank()) {
                continue;
            }

            try {
                Method method;
                try {
                    method = session.getClass().getMethod(methodName);
                } catch (NoSuchMethodException missingPublic) {
                    method = session.getClass().getDeclaredMethod(methodName);
                    method.setAccessible(true);
                }

                Object result = method.invoke(session);
                if (!(result instanceof Boolean bool) || bool) {
                    return true;
                }
            } catch (Throwable e) {
                Strange.LOGGER.debug("Failed to invoke media control method: {}", methodName, e);
            }
        }
        return false;
    }

    private static void ensureStarted() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        transportReady = initTransport();
        if (!transportReady) {
            return;
        }

        SCHEDULER.scheduleAtFixedRate(MediaUtil::pollSessions, 0L, 220L, TimeUnit.MILLISECONDS);
    }

    private static boolean initTransport() {
        try {
            return MediaTransport.init();
        } catch (Throwable throwable) {
            Strange.LOGGER.warn("OS media transport is unavailable", throwable);
            return false;
        }
    }

    private static void pollSessions() {
        if (!transportReady) {
            currentMedia = null;
            return;
        }

        MediaInfo previousMedia = currentMedia;
        try {
            MediaSession session = firstSession();
            if (session == null) {
                clearTextures();
                currentMedia = null;
                return;
            }

            String title = safeText(readTitle(session));
            String artist = safeText(readArtist(session));
            String sourceApp = safeText(readSourceApp(session));

            String trackKey = (title == null ? "" : title) + "|" + (artist == null ? "" : artist);
            long now = System.currentTimeMillis();
            boolean sameTrack = trackKey.equals(lastTrackKey);
            if (!sameTrack) {
                lastTrackKey = trackKey;
                lastTrackChangedAt = now;
            }

            Identifier textureId = resolveTexture(session);
            if (textureId == null && currentMedia != null && currentMedia.textureId() != null) {
                textureId = currentMedia.textureId();
            }

            boolean playing = isPlaying(session);
            Timing timing = resolveTiming(
                    sameTrack,
                    now,
                    readPosition(session),
                    readDuration(session),
                    playing
            );

            float progress = resolveProgress(timing.positionMs(), timing.durationMs());
            if (progress < 0.0f) {
                long elapsed = Math.max(0L, now - lastTrackChangedAt);
                if (timing.durationMs() > 0L) {
                    progress = Math.max(0.0f, Math.min(1.0f, elapsed / (float) timing.durationMs()));
                } else {
                    progress = Math.max(0.0f, Math.min(1.0f, (elapsed % 240000L) / 240000.0f));
                }
            }

            currentMedia = new MediaInfo(
                    (title == null || title.isBlank())
                            ? (sourceApp == null || sourceApp.isBlank() ? "Unknown Track" : sourceApp)
                            : title,
                    artist == null ? "" : artist,
                    textureId,
                    progress,
                    playing,
                    sourceApp,
                    timing.positionMs(),
                    timing.durationMs()
            );
        } catch (Throwable throwable) {
            currentMedia = previousMedia;
        }
    }

    private static Timing resolveTiming(boolean sameTrack,
                                        long now,
                                        long rawPosition,
                                        long rawDuration,
                                        boolean playing) {
        long positionMs = normalizeMediaTime(rawPosition);
        long durationMs = normalizeMediaTime(rawDuration);

        MediaInfo previous = currentMedia;
        if (sameTrack && previous != null) {
            if (durationMs <= 0L && previous.durationMs() > 0L) {
                durationMs = previous.durationMs();
            }

            long previousPosition = Math.max(0L, previous.positionMs());
            if (positionMs <= 0L) {
                positionMs = previousPosition;
            }

            long delta = Math.max(0L, now - lastTimingSnapshotAt);
            if (playing && previous.playing() && delta > 0L) {
                long predicted = previousPosition + delta;
                if (positionMs < predicted) {
                    positionMs = predicted;
                }
            }
        }

        if (durationMs <= 0L && sameTrack && lastResolvedDurationMs > 0L) {
            durationMs = lastResolvedDurationMs;
        }

        if (!sameTrack && positionMs <= 0L) {
            positionMs = 0L;
        }

        if (durationMs > 0L) {
            positionMs = Math.max(0L, Math.min(durationMs, positionMs));
        } else {
            positionMs = Math.max(0L, positionMs);
        }

        lastTimingSnapshotAt = now;
        lastResolvedDurationMs = durationMs;

        return new Timing(positionMs, durationMs);
    }

    private static long normalizeMediaTime(long value) {
        if (value <= 0L) {
            return 0L;
        }

        long abs = Math.abs(value);

        // Typical track time in seconds (e.g. 180) from some providers.
        if (abs <= 86_400L) {
            return abs * 1000L;
        }

        // Nanoseconds range -> milliseconds.
        if (abs > 86_400_000_000L) {
            return abs / 1_000_000L;
        }

        // Microseconds range -> milliseconds.
        if (abs > 86_400_000L) {
            return abs / 1_000L;
        }

        // Already milliseconds.
        return abs;
    }

    private static MediaSession firstSession() {
        try {
            List<MediaSession> sessions = MediaTransport.getMediaSessions();
            if (sessions == null || sessions.isEmpty()) {
                return null;
            }

            for (MediaSession session : sessions) {
                if (session != null && isPlaying(session) && hasThumbnail(session)) {
                    return session;
                }
            }

            for (MediaSession session : sessions) {
                if (session != null && hasThumbnail(session)
                        && (!safeText(readTitle(session)).isBlank() || !safeText(readArtist(session)).isBlank() || !safeText(readSourceApp(session)).isBlank())) {
                    return session;
                }
            }

            for (MediaSession session : sessions) {
                if (session != null && isPlaying(session)) {
                    return session;
                }
            }

            for (MediaSession session : sessions) {
                if (session != null && hasThumbnail(session)) {
                    return session;
                }
            }

            for (MediaSession session : sessions) {
                if (session != null && (!safeText(readTitle(session)).isBlank()
                        || !safeText(readArtist(session)).isBlank()
                        || !safeText(readSourceApp(session)).isBlank())) {
                    return session;
                }
            }

            return sessions.get(0);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Identifier resolveTexture(MediaSession session) {
        if (session == null) {
            return null;
        }

        try {
            if (!hasThumbnail(session)) {
                return null;
            }
            ByteBuffer thumbnail = readThumbnail(session);
            if (thumbnail == null) {
                return null;
            }

            String hash = hashBuffer(thumbnail);
            if (hash.isEmpty()) {
                return null;
            }

            if (!hash.equals(previousHash)) {
                Identifier uploaded = uploadTexture(hash, thumbnail);
                if (uploaded != null) {
                    TEXTURE_CACHE.put(hash, uploaded);
                }
                previousHash = hash;
            }

            return TEXTURE_CACHE.get(hash);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readTitle(MediaSession session) {
        try {
            return session == null ? null : session.getTitle();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readArtist(MediaSession session) {
        try {
            return session == null ? null : session.getArtist();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readSourceApp(MediaSession session) {
        try {
            return session == null ? null : session.getSourceApp();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isPlaying(MediaSession session) {
        try {
            return session != null && session.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasThumbnail(MediaSession session) {
        try {
            return session != null && session.hasThumbnail();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ByteBuffer readThumbnail(MediaSession session) {
        try {
            return session == null ? null : session.getThumbnail();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long readPosition(MediaSession session) {
        try {
            return session == null ? 0L : session.getPosition();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static long readDuration(MediaSession session) {
        try {
            return session == null ? 0L : session.getDuration();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static float resolveProgress(long position, long duration) {
        if (duration <= 0L || position < 0L) {
            return -1.0f;
        }

        double total = duration;
        if (total <= 0.0) {
            return -1.0f;
        }

        double current = position;
        return (float) Math.max(0.0, Math.min(1.0, current / total));
    }

    private static Identifier uploadTexture(String hash, ByteBuffer buffer) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }

        try {
            Identifier textureId = Identifier.of("strange", "media/cover_" + hash);
            NativeImageBackedTexture texture = decodeTexture(buffer, textureId.toString());
            if (texture == null) {
                return null;
            }

            client.execute(() -> {
                client.getTextureManager().destroyTexture(textureId);
                client.getTextureManager().registerTexture(textureId, texture);
            });
            return textureId;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static NativeImageBackedTexture decodeTexture(ByteBuffer buffer, String textureName) {
        try {
            byte[] bytes = toByteArray(buffer);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            return toNativeTexture(image, textureName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NativeImageBackedTexture toNativeTexture(BufferedImage image, String textureName) {
        int width = image.getWidth();
        int height = image.getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                nativeImage.setColorArgb(x, y, image.getRGB(x, y));
            }
        }
        return new NativeImageBackedTexture(() -> textureName, nativeImage);
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private static String hashBuffer(ByteBuffer buffer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(toByteArray(buffer));
            return toHex(digest.digest());
        } catch (Throwable throwable) {
            return "";
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private static void clearTextures() {
        if (TEXTURE_CACHE.isEmpty()) {
            previousHash = "";
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                for (Identifier textureId : TEXTURE_CACHE.values()) {
                    client.getTextureManager().destroyTexture(textureId);
                }
            });
        }

        TEXTURE_CACHE.clear();
        previousHash = "";
    }

    private static String safeText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.replace('\u00A0', ' ').replace('\uFEFF', ' ');
        StringBuilder builder = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                return;
            }
            if (codePoint == 0xFE0F || codePoint == 0x200D) {
                return;
            }

            if (isLikelyHudGlyph(codePoint)) {
                builder.appendCodePoint(codePoint);
            } else if (Character.isWhitespace(codePoint)) {
                builder.append(' ');
            }
        });

        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private static boolean isLikelyHudGlyph(int codePoint) {
        if (codePoint == 0x2116) {
            return true;
        }
        return (codePoint >= 0x20 && codePoint <= 0x7E)
                || (codePoint >= 0x0400 && codePoint <= 0x052F);
    }

    public record MediaInfo(String title, String artist, Identifier textureId, float progress, boolean playing, String sourceApp, long positionMs, long durationMs) {
    }

    private record Timing(long positionMs, long durationMs) {
    }

    @FunctionalInterface
    private interface SessionControlAction {
        void apply(MediaSession session) throws Throwable;
    }

    public record TrackInfo(String title, String subtitle, boolean playing, float progress) {
        public static TrackInfo stopped() {
            return new TrackInfo("No music", "idle", false, 0.0f);
        }
    }
}
