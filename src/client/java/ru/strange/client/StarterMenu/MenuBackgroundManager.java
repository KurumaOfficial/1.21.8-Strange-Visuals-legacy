package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.utils.render.RenderUtil;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MenuBackgroundManager {
    private static final boolean USE_RESOURCE_BACKGROUND_ONLY = true;
    private static final File BACKGROUND_DIRECTORY = new File(Strange.root, "backgrounds");
    private static final File README_FILE = new File(BACKGROUND_DIRECTORY, "README.txt");
    private static final File SAMPLE_FILE = new File(BACKGROUND_DIRECTORY, "sample-background.png");
    private static final List<String> DEFAULT_BACKGROUND_RESOURCES = List.of(
            "Night2.jpg",
            "Night3.png",
            "Night4.jpg"
    );
    private static final Pattern PANORAMA_FACE_PATTERN = Pattern.compile("(?i)^(.*)_([0-5])$");
    private static final int[] VANILLA_FACE_TO_LAYER = {1, 3, 5, 4, 0, 2};
    private static final Identifier FALLBACK_TEXTURE = Identifier.of("strange", "menu/fallback_panorama");
    private static final float EQUIRECTANGULAR_ASPECT_RATIO = 2.0f;
    private static final float EQUIRECTANGULAR_ASPECT_TOLERANCE = 0.08f;
    private static final int GENERATED_SIDE_BLUR_RADIUS = 3;
    private static final int GENERATED_CAP_BLUR_RADIUS = 10;
    private static final int GENERATED_CAP_BLUR_PASSES = 2;
    private static final float GLOBAL_BACKGROUND_BLUR_RADIUS = 17.0f;

    private static final Map<String, BackgroundEntry> REGISTERED_BACKGROUNDS = new HashMap<>();
    private static final Map<String, Long> FAILED_BACKGROUNDS = new HashMap<>();
    private static final Set<String> LOGGED_INCOMPLETE_PANORAMA_GROUPS = new HashSet<>();
    private static final Set<String> LOGGED_DUPLICATE_PANORAMA_FACES = new HashSet<>();
    private static boolean loggedBlurFailure;

    private static boolean initialized;
    private static List<BackgroundEntry> backgrounds = List.of();
    private static BackgroundEntry activeBackground;
    private static BackgroundEntry fallbackBackground;
    private static long scannedBackgroundFingerprint = Long.MIN_VALUE;

    private MenuBackgroundManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        ensureFallbackBackgroundLoaded();
        if (USE_RESOURCE_BACKGROUND_ONLY) {
            resetToFallbackOnly();
            return;
        }
        if (ensureBackgroundDirectory()) {
            ensureBundledBackgrounds();
            ensureSampleBackground();
        }
        scanBackgroundsIfNeeded(true);
    }

    public static synchronized void refresh() {
        if (!initialized) {
            initialize();
            return;
        }

        ensureFallbackBackgroundLoaded();
        if (USE_RESOURCE_BACKGROUND_ONLY) {
            resetToFallbackOnly();
            return;
        }
        if (ensureBackgroundDirectory()) {
            ensureBundledBackgrounds();
            ensureSampleBackground();
        }
        scanBackgroundsIfNeeded(false);
    }

    public static synchronized void renderPanoramaBackground(DrawContext context, int width, int height, float deltaTicks) {
        initialize();
        ensureFallbackBackgroundLoaded();

        if (width <= 0 || height <= 0) {
            return;
        }

        BackgroundEntry entry = activeBackground();
        if (entry == null) {
            drawFallbackFill(context, 0, 0, width, height);
            return;
        }

        try {
            entry.rotatingRenderer().render(context, width, height, true);
        } catch (RuntimeException exception) {
            handleBackgroundRenderFailure(entry, exception);
            drawFallbackFill(context, 0, 0, width, height);
        }
    }

    public static synchronized void renderDarkening(DrawContext context, int x, int y, int width, int height, boolean inWorld) {
        initialize();

        if (width <= 0 || height <= 0) {
            return;
        }

        int topAlpha = inWorld ? 86 : 72;
        int bottomAlpha = inWorld ? 164 : 142;
        context.fillGradient(x, y, x + width, y + height, rgba(4, 7, 11, topAlpha), rgba(1, 2, 4, bottomAlpha));
        drawVignette(context, x, y, width, height);
    }

    public static synchronized void renderBlur(DrawContext context, int x, int y, int width, int height) {
        initialize();

        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            RenderUtil.Blur.draw(context, x, y, width, height, GLOBAL_BACKGROUND_BLUR_RADIUS, new Color(255, 255, 255, 255));
            loggedBlurFailure = false;
        } catch (RuntimeException exception) {
            if (!loggedBlurFailure) {
                loggedBlurFailure = true;
                Strange.LOGGER.warn("Failed to render starter-menu blur pass", exception);
            }
        }
    }

    public static synchronized void renderMenuTextureBackground(DrawContext context, int x, int y, int width, int height) {
        renderStandaloneBackground(context, x, y, width, height, false);
    }

    public static synchronized void renderStandaloneBackground(DrawContext context, int x, int y, int width, int height, boolean inWorld) {
        if (!isFullScreenBackgroundRegion(x, y, width, height)) {
            drawFallbackFill(context, x, y, width, height);
            return;
        }
        renderPanoramaBackground(context, width, height, 0.0f);
    }

    public static boolean shouldUseCustomBackground(Screen screen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || screen == null) {
            return false;
        }

        if (client.world == null) {
            return true;
        }

        return screen instanceof DownloadingTerrainScreen
                || screen instanceof LevelLoadingScreen
                || screen instanceof MessageScreen
                || screen instanceof ProgressScreen;
    }

    public static boolean shouldApplyBlur(Screen screen) {
        return blurPasses(screen) > 0;
    }

    public static int blurPasses(Screen screen) {
        return shouldUseCustomBackground(screen) ? 1 : 0;
    }

    public static boolean shouldReplaceBackgroundTexture(Identifier textureId, int x, int y, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (textureId == null
                || client == null
                || !shouldUseCustomBackground(client.currentScreen)
                || !"minecraft".equals(textureId.getNamespace())
                || !isFullScreenBackgroundRegion(x, y, width, height)) {
            return false;
        }

        String path = textureId.getPath();
        return path.startsWith("textures/gui/") && path.contains("background");
    }

    private static void scanBackgroundsIfNeeded(boolean force) {
        long fingerprint = computeBackgroundFingerprint();
        if (!force && fingerprint == scannedBackgroundFingerprint) {
            return;
        }

        scanBackgrounds();
        scannedBackgroundFingerprint = fingerprint;
    }

    private static long computeBackgroundFingerprint() {
        List<File> files = listBackgroundFiles();
        if (files.isEmpty()) {
            return 0L;
        }

        long fingerprint = 1125899906842597L;
        for (File file : files) {
            fingerprint = 31L * fingerprint + file.getAbsolutePath().hashCode();
            fingerprint = 31L * fingerprint + file.length();
            fingerprint = 31L * fingerprint + file.lastModified();
        }
        return fingerprint;
    }

    private static void scanBackgrounds() {
        List<File> files = listBackgroundFiles();
        if (files.isEmpty()) {
            releaseStaleBackgrounds(Set.of());
            backgrounds = List.of();
            activeBackground = null;
            return;
        }

        String previousActiveKey = activeBackground == null ? null : activeBackground.key();
        List<BackgroundCandidate> candidates = buildCandidates(files);
        List<BackgroundEntry> loaded = new ArrayList<>();
        Set<String> liveKeys = new HashSet<>();

        for (BackgroundCandidate candidate : candidates) {
            liveKeys.add(candidate.key());

            BackgroundEntry entry = REGISTERED_BACKGROUNDS.get(candidate.key());
            Long failedVersion = FAILED_BACKGROUNDS.get(candidate.key());
            if (failedVersion != null && failedVersion == candidate.lastModified()) {
                if (entry != null) {
                    loaded.add(entry);
                }
                continue;
            }

            if (entry == null || entry.lastModified() != candidate.lastModified()) {
                entry = registerBackground(candidate);
            }

            if (entry != null) {
                FAILED_BACKGROUNDS.remove(candidate.key());
                loaded.add(entry);
            }
        }

        releaseStaleBackgrounds(liveKeys);

        backgrounds = List.copyOf(loaded);
        if (backgrounds.isEmpty()) {
            activeBackground = null;
            return;
        }

        if (previousActiveKey != null) {
            for (BackgroundEntry background : backgrounds) {
                if (previousActiveKey.equals(background.key())) {
                    activeBackground = background;
                    return;
                }
            }
        }

        activeBackground = backgrounds.get(ThreadLocalRandom.current().nextInt(backgrounds.size()));
    }

    private static List<BackgroundCandidate> buildCandidates(List<File> sortedFiles) {
        Map<String, PanoramaGroup> groups = new LinkedHashMap<>();
        Set<String> reservedFacePaths = new HashSet<>();

        for (File file : sortedFiles) {
            String baseName = stripExtension(file.getName());
            Matcher matcher = PANORAMA_FACE_PATTERN.matcher(baseName);
            if (!matcher.matches() || matcher.group(1).isBlank()) {
                continue;
            }

            String groupName = matcher.group(1);
            int faceIndex = Integer.parseInt(matcher.group(2));
            PanoramaGroup group = groups.computeIfAbsent(groupName, ignored -> new PanoramaGroup(groupName));
            group.put(faceIndex, file);
        }

        List<BackgroundCandidate> candidates = new ArrayList<>();
        for (PanoramaGroup group : groups.values()) {
            reservedFacePaths.addAll(group.matchedPathSet());
            if (!group.isComplete()) {
                logIncompletePanoramaGroupOnce(group);
                continue;
            }

            LOGGED_INCOMPLETE_PANORAMA_GROUPS.remove(group.name());
            candidates.add(new BackgroundCandidate(
                    "group:" + new File(BACKGROUND_DIRECTORY, group.name()).getAbsolutePath(),
                    group.lastModified(),
                    group.faceFiles(),
                    null
            ));
        }

        for (File file : sortedFiles) {
            if (reservedFacePaths.contains(file.getAbsolutePath())) {
                continue;
            }

            candidates.add(new BackgroundCandidate(file.getAbsolutePath(), file.lastModified(), null, file));
        }

        return candidates;
    }

    private static void releaseStaleBackgrounds(Set<String> liveKeys) {
        Iterator<Map.Entry<String, BackgroundEntry>> iterator = REGISTERED_BACKGROUNDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BackgroundEntry> entry = iterator.next();
            if (!liveKeys.contains(entry.getKey())) {
                releaseBackground(entry.getValue());
                iterator.remove();
            }
        }

        FAILED_BACKGROUNDS.entrySet().removeIf(entry -> !liveKeys.contains(entry.getKey()));
    }

    private static void resetToFallbackOnly() {
        releaseStaleBackgrounds(Set.of());
        backgrounds = List.of();
        activeBackground = null;
    }

    private static BackgroundEntry registerBackground(BackgroundCandidate candidate) {
        String key = candidate.key();
        BackgroundEntry previousEntry = REGISTERED_BACKGROUNDS.get(key);

        try {
            BackgroundEntry entry = createBackgroundEntry(candidate);
            if (previousEntry != null) {
                releaseBackground(previousEntry);
            }
            REGISTERED_BACKGROUNDS.put(key, entry);
            FAILED_BACKGROUNDS.remove(key);
            return entry;
        } catch (BackgroundTextureAccessException exception) {
            Strange.LOGGER.debug("Deferring custom menu background {} because texture manager is not ready", key, exception);
            return previousEntry;
        } catch (IOException | RuntimeException exception) {
            FAILED_BACKGROUNDS.put(key, candidate.lastModified());
            Strange.LOGGER.warn("Failed to load custom menu background {}", key, exception);
            return previousEntry;
        }
    }

    private static BackgroundEntry createBackgroundEntry(BackgroundCandidate candidate) throws IOException {
        NativeImage[] faces = candidate.faceFiles() != null
                ? loadPanoramaFaces(candidate.faceFiles())
                : createCubemapFaces(loadBackgroundImage(candidate.singleFile()));
        boolean facesTransferred = false;

        Identifier textureId = Identifier.of(
                "strange",
                "menu/panorama_" + Integer.toUnsignedString(candidate.key().hashCode(), 16)
                        + "_" + Long.toUnsignedString(candidate.lastModified(), 36)
        );

        boolean registered = false;
        try {
            registerCubemapTexture(textureId, faces);
            registered = true;
            facesTransferred = true;

            CubeMapRenderer cubeMapRenderer = new CubeMapRenderer(textureId);
            RotatingCubeMapRenderer rotatingRenderer = new RotatingCubeMapRenderer(cubeMapRenderer);
            return new BackgroundEntry(candidate.key(), textureId, cubeMapRenderer, rotatingRenderer, candidate.lastModified());
        } catch (RuntimeException exception) {
            if (registered) {
                destroyTextureIfAvailable(textureId);
            }
            throw exception;
        } finally {
            if (!facesTransferred) {
                closeFaces(faces);
            }
        }
    }

    private static void releaseBackground(BackgroundEntry entry) {
        if (entry == null) {
            return;
        }
        entry.cubeMapRenderer().close();
        destroyTextureIfAvailable(entry.textureId());
    }

    private static void ensureFallbackBackgroundLoaded() {
        if (fallbackBackground == null) {
            loadFallbackBackground();
        }
    }

    private static void loadFallbackBackground() {
        if (fallbackBackground != null) {
            return;
        }

        try (InputStream stream = MenuBackgroundManager.class.getResourceAsStream("/assets/back.png")) {
            if (stream == null) {
                Strange.LOGGER.warn("Fallback menu background /assets/back.png was not found");
                return;
            }

            NativeImage[] faces = createCubemapFaces(NativeImage.read(stream));
            boolean facesTransferred = false;
            try {
                registerCubemapTexture(FALLBACK_TEXTURE, faces);
                facesTransferred = true;
            } finally {
                if (!facesTransferred) {
                    closeFaces(faces);
                }
            }

            CubeMapRenderer cubeMapRenderer = new CubeMapRenderer(FALLBACK_TEXTURE);
            RotatingCubeMapRenderer rotatingRenderer = new RotatingCubeMapRenderer(cubeMapRenderer);
            fallbackBackground = new BackgroundEntry("fallback", FALLBACK_TEXTURE, cubeMapRenderer, rotatingRenderer, -1L);
        } catch (BackgroundTextureAccessException exception) {
            Strange.LOGGER.debug("Deferring fallback menu background load because texture manager is not ready", exception);
        } catch (IOException | RuntimeException exception) {
            Strange.LOGGER.warn("Failed to load fallback menu background", exception);
        }
    }

    private static void registerCubemapTexture(Identifier id, NativeImage[] faces) {
        TextureManager textureManager = requireTextureManager();
        textureManager.destroyTexture(id);
        textureManager.registerTexture(id, new DynamicCubemapTexture(id, faces));
    }

    private static void handleBackgroundRenderFailure(BackgroundEntry entry, RuntimeException exception) {
        if (entry == null) {
            return;
        }

        if (entry == fallbackBackground) {
            Strange.LOGGER.warn("Failed to render fallback menu background", exception);
            releaseBackground(fallbackBackground);
            fallbackBackground = null;
            return;
        }

        Strange.LOGGER.warn("Failed to render menu background {}", entry.key(), exception);
        invalidateBackgroundEntry(entry, false);
    }

    private static void invalidateBackgroundEntry(BackgroundEntry entry, boolean markFailed) {
        if (entry == null) {
            return;
        }

        REGISTERED_BACKGROUNDS.entrySet().removeIf(mapEntry -> mapEntry.getValue() == entry);
        if (markFailed) {
            FAILED_BACKGROUNDS.put(entry.key(), entry.lastModified());
        } else {
            FAILED_BACKGROUNDS.remove(entry.key());
        }

        releaseBackground(entry);
        backgrounds = backgrounds.stream()
                .filter(background -> background != entry)
                .toList();
        if (activeBackground == entry) {
            activeBackground = backgrounds.isEmpty()
                    ? null
                    : backgrounds.get(ThreadLocalRandom.current().nextInt(backgrounds.size()));
        }
        scannedBackgroundFingerprint = Long.MIN_VALUE;
    }

    private static NativeImage[] loadPanoramaFaces(List<File> faceFiles) throws IOException {
        NativeImage[] sourceFaces = new NativeImage[6];
        NativeImage[] layers = new NativeImage[6];
        boolean success = false;
        try {
            for (int index = 0; index < 6; index++) {
                sourceFaces[index] = loadBackgroundImage(faceFiles.get(index));
            }

            int faceSize = determinePanoramaFaceSize(sourceFaces);
            for (int layer = 0; layer < VANILLA_FACE_TO_LAYER.length; layer++) {
                layers[layer] = createSquareFace(sourceFaces[VANILLA_FACE_TO_LAYER[layer]], faceSize);
            }
            success = true;
            return layers;
        } finally {
            for (NativeImage face : sourceFaces) {
                if (face != null) {
                    face.close();
                }
            }
            if (!success) {
                closeFaces(layers);
            }
        }
    }

    private static NativeImage[] createCubemapFaces(NativeImage source) {
        NativeImage[] faces = new NativeImage[6];
        boolean success = false;
        try {
            int faceSize = determineGeneratedFaceSize(source);
            faces = isEquirectangularPanorama(source)
                    ? createEquirectangularCubemapFaces(source, faceSize)
                    : createPseudoCubemapFaces(source, faceSize);
            success = true;
            return faces;
        } finally {
            if (!success) {
                closeFaces(faces);
            }
            source.close();
        }
    }

    private static boolean isEquirectangularPanorama(NativeImage source) {
        if (source.getHeight() <= 0) {
            return false;
        }

        float aspectRatio = source.getWidth() / (float) source.getHeight();
        return Math.abs(aspectRatio - EQUIRECTANGULAR_ASPECT_RATIO) <= EQUIRECTANGULAR_ASPECT_TOLERANCE;
    }

    private static NativeImage[] createEquirectangularCubemapFaces(NativeImage source, int faceSize) {
        NativeImage[] faces = new NativeImage[6];
        boolean success = false;

        try {
            for (CubemapFace face : CubemapFace.values()) {
                faces[face.layer()] = sampleCubemapFace(source, face, faceSize);
            }
            success = true;
            return faces;
        } finally {
            if (!success) {
                closeFaces(faces);
            }
        }
    }

    private static NativeImage[] createPseudoCubemapFaces(NativeImage source, int faceSize) {
        NativeImage[] faces = new NativeImage[6];
        boolean success = false;

        try {
            faces[0] = softenFace(createSideFace(source, faceSize, 0.75f), GENERATED_SIDE_BLUR_RADIUS, 1);
            faces[1] = softenFace(createSideFace(source, faceSize, 0.25f), GENERATED_SIDE_BLUR_RADIUS, 1);
            faces[2] = softenFace(createCapFace(source, faceSize, 0.16f), GENERATED_CAP_BLUR_RADIUS, GENERATED_CAP_BLUR_PASSES);
            faces[3] = softenFace(createCapFace(source, faceSize, 0.84f), GENERATED_CAP_BLUR_RADIUS, GENERATED_CAP_BLUR_PASSES);
            faces[4] = softenFace(createSideFace(source, faceSize, 0.50f), GENERATED_SIDE_BLUR_RADIUS, 1);
            faces[5] = softenFace(createSideFace(source, faceSize, 1.00f), GENERATED_SIDE_BLUR_RADIUS, 1);
            success = true;
            return faces;
        } finally {
            if (!success) {
                closeFaces(faces);
            }
        }
    }

    private static NativeImage createSideFace(NativeImage source, int faceSize, float centerXFactor) {
        return createRegionFace(source, faceSize, centerXFactor, 0.54f, 0.72f, 1.00f);
    }

    private static NativeImage createCapFace(NativeImage source, int faceSize, float centerYFactor) {
        NativeImage face = new NativeImage(NativeImage.Format.RGBA, faceSize, faceSize, false);
        float centerY = source.getHeight() * centerYFactor;
        float sampleHeight = Math.max(1.0f, source.getHeight() * 0.14f);

        for (int y = 0; y < faceSize; y++) {
            float sampleY = centerY + ((y + 0.5f) / faceSize - 0.5f) * sampleHeight;
            for (int x = 0; x < faceSize; x++) {
                float sampleX = (x + 0.5f) * source.getWidth() / faceSize;
                face.setColorArgb(x, y, bilinearSample(source, sampleX, sampleY, true));
            }
        }

        return face;
    }

    private static NativeImage createRegionFace(NativeImage source, int faceSize, float centerXFactor, float centerYFactor,
                                                float widthFactor, float heightFactor) {
        NativeImage face = new NativeImage(NativeImage.Format.RGBA, faceSize, faceSize, false);
        float centerX = source.getWidth() * centerXFactor;
        float centerY = source.getHeight() * centerYFactor;
        float sampleWidth = Math.max(1.0f, source.getWidth() * widthFactor);
        float sampleHeight = Math.max(1.0f, source.getHeight() * heightFactor);

        for (int y = 0; y < faceSize; y++) {
            float sampleY = centerY + ((y + 0.5f) / faceSize - 0.5f) * sampleHeight;
            for (int x = 0; x < faceSize; x++) {
                float sampleX = centerX + ((x + 0.5f) / faceSize - 0.5f) * sampleWidth;
                float mirroredX = mirrorCoordinate(sampleX, source.getWidth());
                face.setColorArgb(x, y, bilinearSample(source, mirroredX, sampleY, false));
            }
        }

        return face;
    }

    private static NativeImage sampleCubemapFace(NativeImage source, CubemapFace cubemapFace, int faceSize) {
        NativeImage face = new NativeImage(NativeImage.Format.RGBA, faceSize, faceSize, false);

        for (int y = 0; y < faceSize; y++) {
            float v = 1.0f - 2.0f * ((y + 0.5f) / faceSize);
            for (int x = 0; x < faceSize; x++) {
                float u = 2.0f * ((x + 0.5f) / faceSize) - 1.0f;
                float[] direction = cubemapFace.direction(u, v);
                float length = (float) Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
                float dx = direction[0] / length;
                float dy = direction[1] / length;
                float dz = direction[2] / length;

                float longitude = (float) Math.atan2(dz, dx);
                float latitude = (float) Math.asin(clamp(dy, -1.0f, 1.0f));

                float sampleX = (longitude / (float) (Math.PI * 2.0) + 0.5f) * source.getWidth();
                float sampleY = (0.5f - latitude / (float) Math.PI) * source.getHeight();
                face.setColorArgb(x, y, bilinearSample(source, sampleX, sampleY, true));
            }
        }

        return face;
    }

    private static NativeImage createSquareFace(NativeImage source, int faceSize) {
        NativeImage square = new NativeImage(NativeImage.Format.RGBA, faceSize, faceSize, false);
        float scale = Math.max(faceSize / (float) source.getWidth(), faceSize / (float) source.getHeight());
        float sampleWidth = faceSize / scale;
        float sampleHeight = faceSize / scale;
        float offsetX = (source.getWidth() - sampleWidth) * 0.5f;
        float offsetY = (source.getHeight() - sampleHeight) * 0.5f;

        for (int y = 0; y < faceSize; y++) {
            float sourceY = offsetY + (y + 0.5f) * (sampleHeight / faceSize);
            for (int x = 0; x < faceSize; x++) {
                float sourceX = offsetX + (x + 0.5f) * (sampleWidth / faceSize);
                square.setColorArgb(x, y, bilinearSample(source, sourceX, sourceY, false));
            }
        }

        return square;
    }

    private static int determinePanoramaFaceSize(NativeImage[] faces) {
        int size = Integer.MAX_VALUE;
        for (NativeImage face : faces) {
            size = Math.min(size, Math.min(face.getWidth(), face.getHeight()));
        }

        size = Math.max(256, size);
        return Math.min(1024, size);
    }

    private static int determineGeneratedFaceSize(NativeImage panorama) {
        int size = Math.min(panorama.getWidth(), panorama.getHeight());
        size = Math.max(256, size);
        return Math.min(1024, size);
    }

    private static NativeImage softenFace(NativeImage source, int radius, int passes) {
        if (radius <= 0 || passes <= 0) {
            return source;
        }

        NativeImage current = source;
        for (int pass = 0; pass < passes; pass++) {
            NativeImage blurred = boxBlur(current, radius);
            current.close();
            current = blurred;
        }
        return current;
    }

    private static NativeImage boxBlur(NativeImage source, int radius) {
        if (radius <= 0) {
            return copyImage(source);
        }

        int width = source.getWidth();
        int height = source.getHeight();
        NativeImage horizontal = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        NativeImage blurred = new NativeImage(NativeImage.Format.RGBA, width, height, false);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                horizontal.setColorArgb(x, y, averageHorizontal(source, y, x, radius));
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                blurred.setColorArgb(x, y, averageVertical(horizontal, y, x, radius));
            }
        }

        horizontal.close();
        return blurred;
    }

    private static NativeImage copyImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getFormat(), source.getWidth(), source.getHeight(), false);
        source.copyRect(copy, 0, 0, 0, 0, source.getWidth(), source.getHeight(), false, false);
        return copy;
    }

    private static int averageHorizontal(NativeImage source, int y, int x, int radius) {
        int alpha = 0;
        int red = 0;
        int green = 0;
        int blue = 0;
        int samples = 0;

        for (int offset = -radius; offset <= radius; offset++) {
            int sampleX = Math.max(0, Math.min(source.getWidth() - 1, x + offset));
            int color = source.getColorArgb(sampleX, y);
            alpha += (color >>> 24) & 0xFF;
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            samples++;
        }

        return ((alpha / samples) << 24)
                | ((red / samples) << 16)
                | ((green / samples) << 8)
                | (blue / samples);
    }

    private static int averageVertical(NativeImage source, int y, int x, int radius) {
        int alpha = 0;
        int red = 0;
        int green = 0;
        int blue = 0;
        int samples = 0;

        for (int offset = -radius; offset <= radius; offset++) {
            int sampleY = Math.max(0, Math.min(source.getHeight() - 1, y + offset));
            int color = source.getColorArgb(x, sampleY);
            alpha += (color >>> 24) & 0xFF;
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            samples++;
        }

        return ((alpha / samples) << 24)
                | ((red / samples) << 16)
                | ((green / samples) << 8)
                | (blue / samples);
    }

    private static int bilinearSample(NativeImage image, float x, float y, boolean wrapX) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return 0xFFFFFFFF;
        }

        float sampleX = wrapX ? wrapCoordinate(x, width) : clamp(x, 0.0f, width - 1.0f);
        float sampleY = clamp(y, 0.0f, height - 1.0f);

        int x0 = (int) Math.floor(sampleX);
        int y0 = (int) Math.floor(sampleY);
        int x1 = wrapX ? (x0 + 1) % width : Math.min(width - 1, x0 + 1);
        int y1 = Math.min(height - 1, y0 + 1);

        float tx = sampleX - x0;
        float ty = sampleY - y0;

        int c00 = image.getColorArgb(x0, y0);
        int c10 = image.getColorArgb(x1, y0);
        int c01 = image.getColorArgb(x0, y1);
        int c11 = image.getColorArgb(x1, y1);

        int top = lerpColor(c00, c10, tx);
        int bottom = lerpColor(c01, c11, tx);
        return lerpColor(top, bottom, ty);
    }

    private static int lerpColor(int first, int second, float amount) {
        float t = clamp(amount, 0.0f, 1.0f);

        int a1 = (first >>> 24) & 0xFF;
        int r1 = (first >>> 16) & 0xFF;
        int g1 = (first >>> 8) & 0xFF;
        int b1 = first & 0xFF;

        int a2 = (second >>> 24) & 0xFF;
        int r2 = (second >>> 16) & 0xFF;
        int g2 = (second >>> 8) & 0xFF;
        int b2 = second & 0xFF;

        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float wrapCoordinate(float value, int size) {
        float wrapped = value % size;
        return wrapped < 0.0f ? wrapped + size : wrapped;
    }

    private static float mirrorCoordinate(float value, int size) {
        if (size <= 1) {
            return 0.0f;
        }

        float period = (size - 1) * 2.0f;
        float wrapped = value % period;
        if (wrapped < 0.0f) {
            wrapped += period;
        }
        return wrapped <= size - 1 ? wrapped : period - wrapped;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static NativeImage loadBackgroundImage(File file) throws IOException {
        String lowerCaseName = file.getName().toLowerCase();
        try (InputStream stream = Files.newInputStream(file.toPath())) {
            if (lowerCaseName.endsWith(".png")) {
                return NativeImage.read(stream);
            }

            BufferedImage bufferedImage = ImageIO.read(stream);
            if (bufferedImage == null) {
                throw new IOException("Unsupported image format: " + file.getName());
            }
            return toNativeImage(bufferedImage);
        }
    }

    private static NativeImage toNativeImage(BufferedImage bufferedImage) {
        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, bufferedImage.getWidth(), bufferedImage.getHeight(), false);
        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                nativeImage.setColorArgb(x, y, bufferedImage.getRGB(x, y));
            }
        }
        return nativeImage;
    }

    private static void closeFaces(NativeImage[] faces) {
        if (faces == null) {
            return;
        }

        for (NativeImage face : faces) {
            if (face != null) {
                face.close();
            }
        }
    }

    private static BackgroundEntry activeBackground() {
        if (activeBackground != null) {
            return activeBackground;
        }
        return fallbackBackground;
    }

    private static boolean isFullScreenBackgroundRegion(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return Math.abs(x) <= 1 && Math.abs(y) <= 1;
        }

        int screenWidth = Math.max(1, client.getWindow().getScaledWidth());
        int screenHeight = Math.max(1, client.getWindow().getScaledHeight());
        return Math.abs(x) <= 1
                && Math.abs(y) <= 1
                && width >= screenWidth - 1
                && height >= screenHeight - 1;
    }

    private static void drawVignette(DrawContext context, int x, int y, int width, int height) {
        int vertical = Math.min(96, Math.max(18, height / 8));
        int horizontal = Math.min(128, Math.max(24, width / 8));

        for (int i = 0; i < vertical; i++) {
            int alpha = Math.max(0, vertical - i) * 2;
            context.fill(x, y + i, x + width, y + i + 1, rgba(0, 0, 0, alpha));
            context.fill(x, y + height - i - 1, x + width, y + height - i, rgba(0, 0, 0, alpha));
        }

        for (int i = 0; i < horizontal; i++) {
            int alpha = Math.max(0, horizontal - i);
            context.fill(x + i, y, x + i + 1, y + height, rgba(0, 0, 0, alpha));
            context.fill(x + width - i - 1, y, x + width - i, y + height, rgba(0, 0, 0, alpha));
        }
    }

    private static void drawFallbackFill(DrawContext context, int x, int y, int width, int height) {
        context.fillGradient(x, y, x + width, y + height, 0xFF05080E, 0xFF0D131B);
    }

    private static boolean ensureBackgroundDirectory() {
        if (BACKGROUND_DIRECTORY.exists()) {
            if (!BACKGROUND_DIRECTORY.isDirectory()) {
                Strange.LOGGER.warn("Menu background path {} exists but is not a directory", BACKGROUND_DIRECTORY.getAbsolutePath());
                return false;
            }
        } else if (!BACKGROUND_DIRECTORY.mkdirs()) {
            Strange.LOGGER.warn("Failed to create background directory {}", BACKGROUND_DIRECTORY.getAbsolutePath());
            return false;
        }

        if (!README_FILE.isFile()) {
            String note = """
                    Drop your .png, .jpg or .jpeg files into this folder.
                    Strange Visuals uses them as rotating panorama backgrounds.
                    Single images are converted into a seamless cubemap automatically.
                    Full panorama packs are also supported when files are named pack_0..pack_5.
                    A sample image is created automatically if the folder is empty.
                    """;
            try {
                Files.writeString(README_FILE.toPath(), note, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                Strange.LOGGER.warn("Failed to create menu background readme {}", README_FILE.getAbsolutePath(), exception);
            }
        }
        return true;
    }

    private static List<File> listBackgroundFiles() {
        if (!BACKGROUND_DIRECTORY.isDirectory()) {
            return List.of();
        }

        File[] files = BACKGROUND_DIRECTORY.listFiles(file -> file.isFile() && isSupportedImage(file.getName()));
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        boolean hasNonSampleBackground = sortedFiles.stream().anyMatch(file -> !file.equals(SAMPLE_FILE));
        if (hasNonSampleBackground) {
            sortedFiles.removeIf(file -> file.equals(SAMPLE_FILE));
        }
        sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return sortedFiles;
    }

    private static void ensureSampleBackground() {
        if (hasCustomBackgrounds() || SAMPLE_FILE.isFile() || hasBundledBackgrounds()) {
            return;
        }

        try (InputStream stream = MenuBackgroundManager.class.getResourceAsStream("/assets/back.png")) {
            if (stream == null) {
                return;
            }
            Files.copy(stream, SAMPLE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to create sample menu background {}", SAMPLE_FILE.getAbsolutePath(), exception);
        }
    }

    private static boolean hasCustomBackgrounds() {
        File[] files = BACKGROUND_DIRECTORY.listFiles(file ->
                file.isFile()
                        && isSupportedImage(file.getName())
                        && !file.equals(SAMPLE_FILE)
                        && !isBundledBackgroundFile(file));
        return files != null && files.length > 0;
    }

    private static boolean hasBundledBackgrounds() {
        return bundledBackgroundCount() > 0;
    }

    private static boolean hasCompleteBundledBackgroundSet() {
        return bundledBackgroundCount() >= DEFAULT_BACKGROUND_RESOURCES.size();
    }

    private static int bundledBackgroundCount() {
        int count = 0;
        for (String resourceName : DEFAULT_BACKGROUND_RESOURCES) {
            if (new File(BACKGROUND_DIRECTORY, resourceName).isFile()) {
                count++;
            }
        }
        return count;
    }

    private static void ensureBundledBackgrounds() {
        if (hasCustomBackgrounds() || hasCompleteBundledBackgroundSet()) {
            return;
        }

        for (String resourceName : DEFAULT_BACKGROUND_RESOURCES) {
            File target = new File(BACKGROUND_DIRECTORY, resourceName);
            if (target.isFile()) {
                continue;
            }

            String resourcePath = "/assets/strange/backgrounds/" + resourceName;
            try (InputStream stream = MenuBackgroundManager.class.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    Strange.LOGGER.warn("Bundled menu background {} was not found", resourcePath);
                    continue;
                }
                Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                Strange.LOGGER.warn("Failed to seed bundled menu background {}", target.getAbsolutePath(), exception);
            }
        }
    }

    private static boolean isSupportedImage(String fileName) {
        String lowerCase = fileName.toLowerCase();
        return lowerCase.endsWith(".png") || lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg");
    }

    private static boolean isBundledBackgroundFile(File file) {
        String fileName = file.getName().toLowerCase(Locale.ROOT);
        for (String resourceName : DEFAULT_BACKGROUND_RESOURCES) {
            if (resourceName.toLowerCase(Locale.ROOT).equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private static void logIncompletePanoramaGroupOnce(PanoramaGroup group) {
        if (group == null || !LOGGED_INCOMPLETE_PANORAMA_GROUPS.add(group.name())) {
            return;
        }

        Strange.LOGGER.warn("Skipping incomplete panorama background pack {} because faces {} are missing",
                group.name(),
                group.missingFaceIndices());
    }

    private static void logDuplicatePanoramaFaceOnce(String groupName, int index, File previousFile, File newFile) {
        String logKey = groupName + ":" + index;
        if (!LOGGED_DUPLICATE_PANORAMA_FACES.add(logKey)) {
            return;
        }

        Strange.LOGGER.warn("Duplicate panorama face {}_{} detected: {} and {}. Using the newer file.",
                groupName,
                index,
                previousFile == null ? "<none>" : previousFile.getName(),
                newFile == null ? "<none>" : newFile.getName());
    }

    private static TextureManager textureManagerOrNull() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.getTextureManager();
    }

    private static TextureManager requireTextureManager() {
        TextureManager textureManager = textureManagerOrNull();
        if (textureManager == null) {
            throw new BackgroundTextureAccessException("Minecraft texture manager is not available yet");
        }
        return textureManager;
    }

    private static void destroyTextureIfAvailable(Identifier textureId) {
        TextureManager textureManager = textureManagerOrNull();
        if (textureManager != null && textureId != null) {
            textureManager.destroyTexture(textureId);
        }
    }

    private static int rgba(int r, int g, int b, int a) {
        return ((Math.max(0, Math.min(255, a)) & 0xFF) << 24)
                | ((Math.max(0, Math.min(255, r)) & 0xFF) << 16)
                | ((Math.max(0, Math.min(255, g)) & 0xFF) << 8)
                | (Math.max(0, Math.min(255, b)) & 0xFF);
    }

    private record BackgroundCandidate(String key, long lastModified, List<File> faceFiles, File singleFile) {
    }

    private record BackgroundEntry(String key, Identifier textureId, CubeMapRenderer cubeMapRenderer,
                                   RotatingCubeMapRenderer rotatingRenderer, long lastModified) {
    }

    private static final class BackgroundTextureAccessException extends IllegalStateException {
        private BackgroundTextureAccessException(String message) {
            super(message);
        }
    }

    private enum CubemapFace {
        POSITIVE_X(0) {
            @Override
            float[] direction(float u, float v) {
                return new float[]{1.0f, v, -u};
            }
        },
        NEGATIVE_X(1) {
            @Override
            float[] direction(float u, float v) {
                return new float[]{-1.0f, v, u};
            }
        },
        POSITIVE_Y(2) {
            @Override
            float[] direction(float u, float v) {
                return new float[]{u, 1.0f, -v};
            }
        },
        NEGATIVE_Y(3) {
            @Override
            float[] direction(float u, float v) {
                return new float[]{u, -1.0f, v};
            }
        },
        POSITIVE_Z(4) {
            @Override
            float[] direction(float u, float v) {
                return new float[]{u, v, 1.0f};
            }
        },
        NEGATIVE_Z(5) {
            @Override
            float[] direction(float u, float v) {
                return new float[]{-u, v, -1.0f};
            }
        };

        private final int layer;

        CubemapFace(int layer) {
            this.layer = layer;
        }

        int layer() {
            return layer;
        }

        abstract float[] direction(float u, float v);
    }

    private static final class PanoramaGroup {
        private final String name;
        private final File[] faces = new File[6];
        private final Set<String> matchedPaths = new HashSet<>();

        private PanoramaGroup(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }

        private void put(int index, File file) {
            if (index >= 0 && index < faces.length) {
                matchedPaths.add(file.getAbsolutePath());
                File previous = faces[index];
                if (previous != null && !previous.equals(file)) {
                    logDuplicatePanoramaFaceOnce(name, index, previous, file);
                    if (file.lastModified() < previous.lastModified()) {
                        return;
                    }
                }
                faces[index] = file;
            }
        }

        private boolean isComplete() {
            for (File face : faces) {
                if (face == null) {
                    return false;
                }
            }
            return true;
        }

        private List<File> faceFiles() {
            return List.of(faces.clone());
        }

        private Set<String> facePathSet() {
            Set<String> paths = new HashSet<>();
            for (File face : faces) {
                if (face != null) {
                    paths.add(face.getAbsolutePath());
                }
            }
            return paths;
        }

        private Set<String> matchedPathSet() {
            return new HashSet<>(matchedPaths);
        }

        private String missingFaceIndices() {
            StringBuilder missing = new StringBuilder();
            for (int index = 0; index < faces.length; index++) {
                if (faces[index] != null) {
                    continue;
                }

                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(index);
            }
            return missing.toString();
        }

        private long lastModified() {
            long value = 0L;
            for (File face : faces) {
                value += face == null ? 0L : face.lastModified();
            }
            return value;
        }
    }
}
