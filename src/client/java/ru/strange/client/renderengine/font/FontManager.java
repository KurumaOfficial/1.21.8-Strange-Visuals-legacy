package ru.strange.client.renderengine.font;

import me.x150.renderer.fontng.FTLibrary;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.strange.client.Strange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FontManager implements AutoCloseable {
    private static final String DEFAULT_FAMILY_NAME = "default";
    private static final String BUNDLED_DEFAULT_FONT = "strange:fonts/medium.ttf";
    private static FontManager instance;
    private final FTLibrary library;

    private final Map<String, FontFamily> families = new HashMap<>();
    private final Path tempDir;
    private boolean closed;

    public static FontManager getInstance() {
        if (instance == null) {

            instance = new FontManager();
        }
        return instance;
    }

    private FontManager() {
        this.library = new FTLibrary();

        try {
            this.tempDir = Files.createTempDirectory("renderer_fonts");
            this.tempDir.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory for fonts", e);
        }

        loadDefaultFonts();
    }

    private void loadDefaultFonts() {
        try {
            loadFontFromResources(DEFAULT_FAMILY_NAME, BUNDLED_DEFAULT_FONT);
        } catch (RuntimeException exception) {
            Strange.LOGGER.warn("Failed to load bundled default font, falling back to system font", exception);
            try {
                loadSystemFont(DEFAULT_FAMILY_NAME);
            } catch (RuntimeException fallbackException) {
                Strange.LOGGER.error("Failed to load fallback system font family {}", DEFAULT_FAMILY_NAME, fallbackException);
            }
        }
    }

    public FontFamily loadFontFromResources(@NotNull String familyName, @NotNull String resourcePath) {
        Identifier id = Identifier.of(resourcePath);
        String path = String.format("/assets/%s/%s", id.getNamespace(), id.getPath());

        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }

            Path tempFile = tempDir.resolve(familyName + resourceExtension(resourcePath));
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();

            return loadFontFromFile(familyName, tempFile.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load font from resources: " + resourcePath, e);
        }
    }

    public FontFamily loadFontFromFile(@NotNull String familyName, @NotNull String filePath) {
        return loadFontFromFile(familyName, filePath, 0);
    }

    public FontFamily loadFontFromFile(@NotNull String familyName, @NotNull String filePath, int faceIndex) {
        FontFamily family = new FontFamily(familyName, filePath, faceIndex, library);
        FontFamily previousFamily = families.put(familyName, family);
        if (previousFamily != null && previousFamily != family) {
            previousFamily.close();
        }
        return family;
    }

    public FontFamily loadSystemFont(@NotNull String familyName) {
        String os = System.getProperty("os.name").toLowerCase();
        List<String> candidates;

        if (os.contains("win")) {
            candidates = List.of(
                    "C:/Windows/Fonts/arial.ttf",
                    "C:/Windows/Fonts/segoeui.ttf",
                    "C:/Windows/Fonts/tahoma.ttf"
            );
        } else if (os.contains("mac")) {
            candidates = List.of(
                    "/System/Library/Fonts/Helvetica.ttc",
                    "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                    "/System/Library/Fonts/Supplemental/Arial.ttf"
            );
        } else {
            candidates = List.of(
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                    "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
                    "/usr/share/fonts/TTF/DejaVuSans.ttf"
            );
        }

        String fontPath = findExistingFontPath(candidates);
        if (fontPath == null) {
            throw new IllegalStateException("No system fallback font found for OS " + os);
        }

        return loadFontFromFile(familyName, fontPath);
    }

    @Nullable
    public FontFamily getFamily(@NotNull String familyName) {
        return families.get(familyName);
    }

    @NotNull
    public FontFamily getFamilyOrDefault(@NotNull String familyName) {
        FontFamily family = families.get(familyName);
        if (family == null) {
            family = getDefaultFamily();
        }
        return family;
    }

    @NotNull
    public FontFamily getDefaultFamily() {
        FontFamily family = families.get(DEFAULT_FAMILY_NAME);
        if (family == null) {
            family = families.get("medium");
        }
        if (family == null) {
            family = families.get("minecraft");
        }
        if (family == null) {
            throw new IllegalStateException("No default font family loaded");
        }
        return family;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        families.values().forEach(FontFamily::close);
        families.clear();
        library.close();

        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            Strange.LOGGER.debug("Failed to delete temp font path {}", path, exception);
                        }
                    });
        } catch (IOException exception) {
            Strange.LOGGER.debug("Failed to clean temp font directory {}", tempDir, exception);
        }

        instance = null;
    }

    private static String resourceExtension(String resourcePath) {
        int dotIndex = resourcePath.lastIndexOf('.');
        return dotIndex >= 0 ? resourcePath.substring(dotIndex) : ".ttf";
    }

    private static String findExistingFontPath(List<String> candidates) {
        for (String candidate : candidates) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    public FTLibrary getLibrary() {
        return library;
    }
}
