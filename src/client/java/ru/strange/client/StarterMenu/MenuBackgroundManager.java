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

    private static boolean initialized;
    private static List<BackgroundEntry> backgrounds = List.of();
    private static BackgroundEntry activeBackground;
    private static BackgroundEntry fallbackBackground;

    private MenuBackgroundManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        loadFallbackBackground();
        if (USE_RESOURCE_BACKGROUND_ONLY) {
            resetToFallbackOnly();
            return;
        }

        ensureBackgroundDirectory();
        ensureBundledBackgrounds();
        ensureSampleBackground();
        scanBackgrounds();
    }

    public static synchronized void refresh() {
        if (!initialized) {
            initialize();
            return;
        }

        if (USE_RESOURCE_BACKGROUND_ONLY) {
            resetToFallbackOnly();
            return;
        }

        scanBackgrounds();
    }

    public static synchronized void renderPanoramaBackground(DrawContext context, int width, int height, float deltaTicks) {
        initialize();

        if (width <= 0 || height <= 0) {
            return;
        }

        BackgroundEntry entry = activeBackground();
        if (entry == null) {
            drawFallbackFill(context, 0, 0, width, height);
            return;
        }

        entry.rotatingRenderer().render(context, width, height, true);
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

        RenderUtil.Blur.draw(context, x, y, width, height, GLOBAL_BACKGROUND_BLUR_RADIUS, new Color(255, 255, 255, 255));
    }

    public static synchronized void renderMenuTextureBackground(DrawContext context, int x, int y, int width, int height) {
        renderStandaloneBackground(context, x, y, width, height, false);
    }

    public static synchronized void renderStandaloneBackground(DrawContext context, int x, int y, int width, int height, boolean inWorld) {
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

    public static boolean shouldReplaceBackgroundTexture(Identifier textureId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (textureId == null || client == null || !shouldUseCustomBackground(client.currentScreen) || !"minecraft".equals(textureId.getNamespace())) {
            return false;
        }

        String path = textureId.getPath();
        return path.startsWith("textures/gui/") && path.contains("background");
    }

    private static void scanBackgrounds() {
        File[] files = BACKGROUND_DIRECTORY.listFiles(file -> file.isFile() && isSupportedImage(file.getName()));
        if (files == null || files.length == 0) {
            releaseStaleBackgrounds(Set.of());
            backgrounds = List.of();
            activeBackground = null;
            return;
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        String previousActiveKey = activeBackground == null ? null : activeBackground.key();
        List<BackgroundCandidate> candidates = buildCandidates(sortedFiles);
        List<BackgroundEntry> loaded = new ArrayList<>();
        Set<String> liveKeys = new HashSet<>();

        for (BackgroundCandidate candidate : candidates) {
            liveKeys.add(candidate.key());

            BackgroundEntry entry = REGISTERED_BACKGROUNDS.get(candidate.key());
            Long failedVersion = FAILED_BACKGROUNDS.get(candidate.key());
            if (entry == null && failedVersion != null && failedVersion == candidate.lastModified()) {
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
        Set<String> groupedFacePaths = new HashSet<>();

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
            if (!group.isComplete()) {
                continue;
            }

            groupedFacePaths.addAll(group.facePathSet());
            candidates.add(new BackgroundCandidate(
                    "group:" + new File(BACKGROUND_DIRECTORY, group.name()).getAbsolutePath(),
                    group.lastModified(),
                    group.faceFiles(),
                    null
            ));
        }

        for (File file : sortedFiles) {
            if (groupedFacePaths.contains(file.getAbsolutePath())) {
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
        BackgroundEntry previousEntry = REGISTERED_BACKGROUNDS.remove(key);
        if (previousEntry != null) {
            releaseBackground(previousEntry);
        }

        try {
            NativeImage[] faces = candidate.faceFiles() != null
                    ? loadPanoramaFaces(candidate.faceFiles())
                    : createCubemapFaces(loadBackgroundImage(candidate.singleFile()));

            Identifier textureId = Identifier.of("strange", "menu/panorama_" + Integer.toUnsignedString(key.hashCode(), 16));
            registerCubemapTexture(textureId, faces);

            CubeMapRenderer cubeMapRenderer = new CubeMapRenderer(textureId);
            RotatingCubeMapRenderer rotatingRenderer = new RotatingCubeMapRenderer(cubeMapRenderer);
            BackgroundEntry entry = new BackgroundEntry(key, textureId, cubeMapRenderer, rotatingRenderer, candidate.lastModified());

            REGISTERED_BACKGROUNDS.put(key, entry);
            return entry;
        } catch (IOException exception) {
            FAILED_BACKGROUNDS.put(key, candidate.lastModified());
            Strange.LOGGER.warn("Failed to load custom menu background {}", key, exception);
            return null;
        }
    }

    private static void releaseBackground(BackgroundEntry entry) {
        entry.cubeMapRenderer().close();
        textureManager().destroyTexture(entry.textureId());
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
            registerCubemapTexture(FALLBACK_TEXTURE, faces);

            CubeMapRenderer cubeMapRenderer = new CubeMapRenderer(FALLBACK_TEXTURE);
            RotatingCubeMapRenderer rotatingRenderer = new RotatingCubeMapRenderer(cubeMapRenderer);
            fallbackBackground = new BackgroundEntry("fallback", FALLBACK_TEXTURE, cubeMapRenderer, rotatingRenderer, -1L);
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to load fallback menu background", exception);
        }
    }

    private static void registerCubemapTexture(Identifier id, NativeImage[] faces) {
        TextureManager textureManager = textureManager();
        textureManager.destroyTexture(id);
        textureManager.registerTexture(id, new DynamicCubemapTexture(id, faces));
    }

    private static NativeImage[] loadPanoramaFaces(List<File> faceFiles) throws IOException {
        NativeImage[] sourceFaces = new NativeImage[6];
        try {
            for (int index = 0; index < 6; index++) {
                sourceFaces[index] = loadBackgroundImage(faceFiles.get(index));
            }

            int faceSize = determinePanoramaFaceSize(sourceFaces);
            NativeImage[] layers = new NativeImage[6];
            for (int layer = 0; layer < VANILLA_FACE_TO_LAYER.length; layer++) {
                layers[layer] = createSquareFace(sourceFaces[VANILLA_FACE_TO_LAYER[layer]], faceSize);
            }
            return layers;
        } finally {
            for (NativeImage face : sourceFaces) {
                if (face != null) {
                    face.close();
                }
            }
        }
    }

    private static NativeImage[] createCubemapFaces(NativeImage source) {
        try {
            int faceSize = determineGeneratedFaceSize(source);
            NativeImage[] faces = new NativeImage[6];
            faces[0] = createSideFace(source, faceSize, 0.75f);
            faces[1] = createSideFace(source, faceSize, 0.25f);
            faces[2] = createRegionFace(source, faceSize, 0.50f, 0.18f, 0.68f, 0.34f);
            faces[3] = createRegionFace(source, faceSize, 0.50f, 0.82f, 0.74f, 0.38f);
            faces[4] = createSideFace(source, faceSize, 0.50f);
            faces[5] = createSideFace(source, faceSize, 1.00f);
            return faces;
        } finally {
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

        try {
            for (CubemapFace face : CubemapFace.values()) {
                faces[face.layer()] = sampleCubemapFace(source, face, faceSize);
            }
            return faces;
        } catch (Throwable throwable) {
            closeFaces(faces);
            throw throwable;
        }
    }

    private static NativeImage[] createPseudoCubemapFaces(NativeImage source, int faceSize) {
        NativeImage[] faces = new NativeImage[6];

        try {
            faces[0] = softenFace(createSideFace(source, faceSize, 0.75f), GENERATED_SIDE_BLUR_RADIUS, 1);
            faces[1] = softenFace(createSideFace(source, faceSize, 0.25f), GENERATED_SIDE_BLUR_RADIUS, 1);
            faces[2] = softenFace(createCapFace(source, faceSize, 0.16f), GENERATED_CAP_BLUR_RADIUS, GENERATED_CAP_BLUR_PASSES);
            faces[3] = softenFace(createCapFace(source, faceSize, 0.84f), GENERATED_CAP_BLUR_RADIUS, GENERATED_CAP_BLUR_PASSES);
            faces[4] = softenFace(createSideFace(source, faceSize, 0.50f), GENERATED_SIDE_BLUR_RADIUS, 1);
            faces[5] = softenFace(createSideFace(source, faceSize, 1.00f), GENERATED_SIDE_BLUR_RADIUS, 1);
            return faces;
        } catch (Throwable throwable) {
            closeFaces(faces);
            throw throwable;
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

    private static void ensureBackgroundDirectory() {
        if (!BACKGROUND_DIRECTORY.exists() && !BACKGROUND_DIRECTORY.mkdirs()) {
            Strange.LOGGER.warn("Failed to create background directory {}", BACKGROUND_DIRECTORY.getAbsolutePath());
            return;
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
    }

    private static void ensureSampleBackground() {
        if (hasUserBackgrounds() || SAMPLE_FILE.isFile() || hasBundledBackgrounds()) {
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

    private static boolean hasUserBackgrounds() {
        File[] files = BACKGROUND_DIRECTORY.listFiles(file -> file.isFile() && isSupportedImage(file.getName()) && !file.equals(SAMPLE_FILE));
        return files != null && files.length > 0;
    }

    private static boolean hasBundledBackgrounds() {
        for (String resourceName : DEFAULT_BACKGROUND_RESOURCES) {
            if (new File(BACKGROUND_DIRECTORY, resourceName).isFile()) {
                return true;
            }
        }
        return false;
    }

    private static void ensureBundledBackgrounds() {
        if (hasUserBackgrounds() || hasBundledBackgrounds()) {
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

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private static TextureManager textureManager() {
        return MinecraftClient.getInstance().getTextureManager();
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

        private PanoramaGroup(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }

        private void put(int index, File file) {
            if (index >= 0 && index < faces.length) {
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

        private long lastModified() {
            long value = 0L;
            for (File face : faces) {
                value += face == null ? 0L : face.lastModified();
            }
            return value;
        }
    }
}
