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
import java.util.Map;

public class FontManager implements AutoCloseable {
    private static final String DEFAULT_FAMILY_NAME = "default";
    private static final String BUNDLED_DEFAULT_FONT = "strange:fonts/medium.ttf";
    private static FontManager instance;
    private final FTLibrary library;

    private final Map<String, FontFamily> families = new HashMap<>();
    private final Path tempDir;

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
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to load bundled default font, falling back to system font", e);
            loadSystemFont(DEFAULT_FAMILY_NAME);
        }
    }

    public FontFamily loadFontFromResources(@NotNull String familyName, @NotNull String resourcePath) {
        Identifier id = Identifier.of(resourcePath);
        String path = String.format("/assets/%s/%s", id.getNamespace(), id.getPath());

        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }

            Path tempFile = tempDir.resolve(familyName + ".ttf");
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
        families.put(familyName, family);
        return family;
    }

    public FontFamily loadSystemFont(@NotNull String familyName) {
        String os = System.getProperty("os.name").toLowerCase();
        String fontPath;

        if (os.contains("win")) {
            fontPath = "C:/Windows/Fonts/arial.ttf";
        } else if (os.contains("mac")) {
            fontPath = "/System/Library/Fonts/Helvetica.ttc";
        } else {
            fontPath = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf";
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
        families.values().forEach(FontFamily::close);
        families.clear();
        library.close();

        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }

        instance = null;
    }

    public FTLibrary getLibrary() {
        return library;
    }
}
