package ru.strange.client.utils.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// Привет Горелкинг - система кастомных плащей
public class CapeUtil {
    private static final Map<UUID, Identifier> PLAYER_CAPES = new HashMap<>();
    private static final Map<UUID, NativeImageBackedTexture> CAPE_TEXTURES = new HashMap<>();
    private static final Map<UUID, String> CAPE_PATHS = new HashMap<>();
    
    private static String localPlayerCapePath = null;
    private static Identifier localPlayerCapeId = null;
    private static NativeImageBackedTexture localPlayerCapeTexture = null;
    private static boolean initialized = false;

    private static final Map<UUID, AnimatedCapeData> ANIMATED_CAPES = new HashMap<>();

    private static class AnimatedCapeData {
        final List<BufferedImage> frames;
        final List<Integer> delaysMs;
        int currentFrame;
        long lastFrameTime;

        AnimatedCapeData(List<BufferedImage> frames, List<Integer> delaysMs) {
            this.frames = frames;
            this.delaysMs = delaysMs;
            this.currentFrame = 0;
            this.lastFrameTime = System.currentTimeMillis();
        }
    }

    private static NativeImage toNativeImage(BufferedImage bi) {
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, bi.getWidth(), bi.getHeight(), false);
        for (int y = 0; y < bi.getHeight(); y++) {
            for (int x = 0; x < bi.getWidth(); x++) {
                ni.setColorArgb(x, y, bi.getRGB(x, y));
            }
        }
        return ni;
    }

    private static Identifier createAndRegisterCapeTexture(UUID playerId, NativeImage image) {
        Identifier capeId = Strange.id("dynamic/cape_" + playerId.toString().replace("-", ""));
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> capeId.toString(), image);
        clearCapeTexture(playerId);
        MinecraftClient client = MinecraftClient.getInstance();
        client.getTextureManager().registerTexture(capeId, texture);
        CAPE_TEXTURES.put(playerId, texture);
        PLAYER_CAPES.put(playerId, capeId);
        CAPE_PATHS.put(playerId, "animated:" + playerId);
        return capeId;
    }

    private static Identifier loadGifCapeTexture(UUID playerId, File gifFile) throws IOException {
        javax.imageio.stream.ImageInputStream stream = javax.imageio.ImageIO.createImageInputStream(gifFile);
        java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF reader available");
        }
        javax.imageio.ImageReader reader = readers.next();
        reader.setInput(stream);

        int frameCount = reader.getNumImages(true);
        List<BufferedImage> frames = new ArrayList<>(frameCount);
        List<Integer> delays = new ArrayList<>(frameCount);

        for (int i = 0; i < frameCount; i++) {
            BufferedImage frame = reader.read(i);
            frames.add(frame);

            int delayMs = 50;
            try {
                javax.imageio.metadata.IIOMetadata metadata = reader.getImageMetadata(i);
                javax.imageio.metadata.IIOMetadataNode root = (javax.imageio.metadata.IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                for (int c = 0; c < root.getLength(); c++) {
                    org.w3c.dom.Node child = root.item(c);
                    if (child.getNodeName().equals("GraphicControlExtension")) {
                        String delayStr = child.getAttributes().getNamedItem("delayTime").getNodeValue();
                        if (delayStr != null) {
                            delayMs = Integer.parseInt(delayStr) * 10;
                            if (delayMs < 20) delayMs = 50;
                        }
                    }
                }
            } catch (Exception ignored) {}
            delays.add(delayMs);
        }

        stream.close();

        AnimatedCapeData animData = new AnimatedCapeData(frames, delays);
        ANIMATED_CAPES.put(playerId, animData);

        return createAndRegisterCapeTexture(playerId, toNativeImage(frames.get(0)));
    }

    private static void updateAnimatedCape(UUID playerId) {
        AnimatedCapeData anim = ANIMATED_CAPES.get(playerId);
        if (anim == null) return;

        long now = System.currentTimeMillis();
        int delay = anim.delaysMs.get(anim.currentFrame);
        if (now - anim.lastFrameTime < delay) return;

        anim.currentFrame = (anim.currentFrame + 1) % anim.frames.size();
        anim.lastFrameTime = now;

        Identifier capeId = PLAYER_CAPES.get(playerId);
        if (capeId != null) {
            NativeImage nextFrame = toNativeImage(anim.frames.get(anim.currentFrame));
            clearCapeTexture(playerId);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> capeId.toString(), nextFrame);
            CAPE_TEXTURES.put(playerId, texture);
            PLAYER_CAPES.put(playerId, capeId);
            MinecraftClient.getInstance().getTextureManager().registerTexture(capeId, texture);
        }
    }

    public static void uiPickAndApplyCape() {
        Thread thread = new Thread(CapeUtil::openCapeFileDialog, "CapeManager-FileDialog");
        thread.setDaemon(true);
        thread.start();
    }

    public static void uiResetCape() {
        String message;
        try {
            File activeFile = new File(getCapeDirectory(), "active_cape.txt");
            String fileName = null;
            if (activeFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(activeFile, StandardCharsets.UTF_8))) {
                    fileName = reader.readLine();
                } catch (IOException ignored) {}
            }
            clearActiveCapeSelection();
            if (fileName != null && !fileName.isBlank()) {
                File capeFile = new File(getCapeDirectory(), fileName.trim());
                if (capeFile.exists()) {
                    boolean ok = capeFile.delete();
                    message = ok ? "Cape reset successfully!" : "Failed to delete " + fileName;
                } else {
                    message = "No custom cape to reset.";
                }
            } else {
                message = "No custom cape to reset.";
            }
        } catch (RuntimeException e) {
            Strange.LOGGER.warn("Failed to reset custom cape", e);
            message = "Error resetting cape: " + e.getMessage();
        }

        final String resultMessage = message;
        runOnClientThread(() -> {
            applyLocalCape(null);
            sendPlayerMessage(resultMessage);
        });
    }

    public static void openCapeDirectory() {
        try {
            File capeDir = getCapeDirectory();
            if (!capeDir.exists() && !capeDir.mkdirs()) {
                sendPlayerMessage("§cНе удалось создать папку: " + capeDir.getAbsolutePath());
                return;
            }
            boolean opened = false;
            if (java.awt.Desktop.isDesktopSupported()) {
                try {
                    java.awt.Desktop.getDesktop().open(capeDir);
                    opened = true;
                } catch (Exception ignored) {}
            }
            if (!opened) {
                try {
                    Runtime.getRuntime().exec("explorer.exe \"" + capeDir.getAbsolutePath() + "\"");
                    opened = true;
                } catch (Exception ignored) {}
            }
            if (opened) {
                sendPlayerMessage("§aПапка с плащами открыта");
            } else {
                sendPlayerMessage("§aПапка с плащами: §7" + capeDir.getAbsolutePath());
            }
            sendPlayerMessage("§7Положите PNG/GIF в папку и используйте §e.cape load <имя файла>");
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to open cape directory", e);
            File capeDir = getCapeDirectory();
            sendPlayerMessage("§cНе удалось открыть папку через проводник");
            sendPlayerMessage("§eПапка находится здесь: §7" + capeDir.getAbsolutePath());
        }
    }

    public static List<String> listCapeFiles() {
        File capeDir = getCapeDirectory();
        if (!capeDir.exists()) {
            return List.of();
        }

        File[] files = capeDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".png") || lower.endsWith(".gif");
        });
        if (files == null || files.length == 0) {
            return List.of();
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        List<String> names = new ArrayList<>(files.length);
        for (File file : files) {
            names.add(file.getName());
        }
        return names;
    }

    public static boolean useCapeFromFolder(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        String name = fileName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png") && !lower.endsWith(".gif")) {
            name += ".png";
        }
        String normalized = name;

        File capeFile = new File(getCapeDirectory(), normalized);
        if (!capeFile.exists() || !capeFile.isFile()) {
            sendPlayerMessage("§cПлащ не найден: §7" + normalized);
            return false;
        }

        saveActiveCapeSelection(normalized);
        runOnClientThread(() -> {
            applyLocalCape(capeFile.getAbsolutePath());
            sendPlayerMessage("§aПлащ применён: §7" + normalized);
        });
        return true;
    }

    private static Identifier loadCapeTexture(UUID playerId, String capePath) {
        if (capePath == null || capePath.isEmpty()) {
            return null;
        }

        File capeFile = new File(capePath);
        if (!capeFile.exists()) {
            return null;
        }

        if (capePath.toLowerCase(Locale.ROOT).endsWith(".gif")) {
            try {
                return loadGifCapeTexture(playerId, capeFile);
            } catch (IOException e) {
                Strange.LOGGER.warn("Failed to load animated cape from {}", capePath, e);
                return null;
            }
        }

        NativeImage image = null;
        try (FileInputStream inputStream = new FileInputStream(capeFile)) {
            image = NativeImage.read(inputStream);
            Identifier capeId = Strange.id("dynamic/cape_" + playerId.toString().replace("-", ""));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> capeId.toString(), image);
            
            MinecraftClient client = MinecraftClient.getInstance();
            
            // Очищаем старую текстуру если есть
            clearCapeTexture(playerId);
            
            client.getTextureManager().registerTexture(capeId, texture);
            
            CAPE_TEXTURES.put(playerId, texture);
            PLAYER_CAPES.put(playerId, capeId);
            CAPE_PATHS.put(playerId, capePath);
            
            Strange.LOGGER.info("Loaded cape for player {}", playerId);
            return capeId;
        } catch (IOException | RuntimeException e) {
            // Clean up image if texture creation failed
            if (image != null) {
                try {
                    image.close();
                } catch (Exception closeException) {
                    Strange.LOGGER.debug("Failed to close NativeImage during error cleanup", closeException);
                }
            }
            Strange.LOGGER.warn("Failed to load cape from {} for player {}", capeFile.getAbsolutePath(), playerId, e);
            return null;
        }
    }

    public static String previewCapeOverride = null;

    public static SkinTextures updatedPlayerSkin(SkinTextures originalSkin, PlayerEntity entity) {
        if (entity == null) {
            return originalSkin;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return originalSkin;
        }

        // Инициализация при первом вызове
        if (!initialized) {
            loadSavedCape();
            initialized = true;
        }

        UUID playerId = entity.getUuid();

        // Обновляем анимацию GIF плаща если есть
        updateAnimatedCape(playerId);

        // Если это локальный игрок, применяем его плащ
        if (playerId.equals(client.player.getUuid())) {
            String capePath = previewCapeOverride != null ? previewCapeOverride : localPlayerCapePath;
            if (capePath != null) {
                Identifier capeTexture;
                if (capePath.equals(localPlayerCapePath) && localPlayerCapeId != null) {
                    capeTexture = localPlayerCapeId;
                } else {
                    capeTexture = loadAndCacheCapeTexture(playerId, capePath);
                }
                if (capeTexture != null) {
                    return new SkinTextures(
                            originalSkin.texture(),
                            originalSkin.textureUrl(),
                            capeTexture,
                            capeTexture,
                            originalSkin.model(),
                            originalSkin.secure()
                    );
                }
            }
            return originalSkin;
        }

        // Для других игроков проверяем, есть ли у них кастомный плащ
        Identifier capeTexture = PLAYER_CAPES.get(playerId);
        if (capeTexture != null) {
            return new SkinTextures(
                    originalSkin.texture(),
                    originalSkin.textureUrl(),
                    capeTexture,
                    capeTexture,
                    originalSkin.model(),
                    originalSkin.secure()
            );
        }

        return originalSkin;
    }

    private static Identifier loadLocalCape() {
        if (localPlayerCapePath == null || localPlayerCapePath.isEmpty()) {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }

        UUID playerId = client.player.getUuid();
        Identifier capeId = loadCapeTexture(playerId, localPlayerCapePath);
        
        if (capeId != null) {
            localPlayerCapeId = capeId;
            localPlayerCapeTexture = CAPE_TEXTURES.get(playerId);
        }
        
        return capeId;
    }

    public static Identifier loadAndCacheCapeTexture(UUID playerId, String capePath) {
        Identifier existing = PLAYER_CAPES.get(playerId);
        if (existing != null && capePath.equals(CAPE_PATHS.get(playerId))) {
            return existing;
        }
        return loadCapeTexture(playerId, capePath);
    }

    private static void openCapeFileDialog() {
        try {
            File selectedFile = null;
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                selectedFile = openWindowsFileDialog();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                selectedFile = openLinuxFileDialog();
            } else if (os.contains("mac")) {
                selectedFile = openMacFileDialog();
            } else {
                selectedFile = openWindowsFileDialog();
            }

            if (selectedFile != null && selectedFile.exists()) {
                String name = selectedFile.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".png") || name.endsWith(".gif")) {
                    saveCapeFile(selectedFile);
                }
            }
        } catch (RuntimeException e) {
            Strange.LOGGER.warn("Cape file dialog failed", e);
        }
    }

    private static File openMacFileDialog() {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "osascript",
                    "-e",
                    "choose file of type {\"public.png\",\"com.compuserve.gif\"} with prompt \"Select Cape File\""
            );
            process = pb.start();
            String path = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                path = reader.readLine();
            }

            int exitCode = process.waitFor();
            if (path != null && !path.isEmpty() && exitCode == 0) {
                return new File(path.replaceFirst("^alias .*:", "").trim());
            }
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to open macOS cape dialog", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Strange.LOGGER.debug("macOS cape picker was interrupted", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
        return null;
    }

    private static File openWindowsFileDialog() {
        Process process = null;
        try {
            String powerShellScript =
                    "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                            "$dialog = New-Object System.Windows.Forms.OpenFileDialog; " +
                            "$dialog.Filter = 'Images (*.png;*.gif)|*.png;*.gif|All Files (*.*)|*.*'; " +
                            "$dialog.Title = 'Select Cape File'; " +
                            "$result = $dialog.ShowDialog(); " +
                            "if ($result -eq 'OK') { Write-Output $dialog.FileName }";

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", powerShellScript);
            pb.redirectErrorStream(true);
            process = pb.start();

            String path = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()
                            && !line.contains("System.Windows.Forms")
                            && !line.startsWith("PS ")
                            && !line.contains("Microsoft")
                            && (line.contains(":\\") || line.startsWith("/"))) {
                        path = line;
                        break;
                    }
                }
            }

            int exitCode = process.waitFor();
            if (path != null && !path.isEmpty() && exitCode == 0) {
                return new File(path);
            }
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to open Windows cape dialog", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Strange.LOGGER.debug("Windows cape picker was interrupted", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
        return null;
    }

    private static File openLinuxFileDialog() {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("zenity", "--file-selection",
                    "--title=Select Cape File", "--file-filter=*.png *.gif");
            process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String path = reader.readLine();
                    if (path != null && !path.isEmpty()) {
                        return new File(path.trim());
                    }
                }
            }
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to open Linux cape dialog", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Strange.LOGGER.debug("Linux cape picker was interrupted", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }

        Process kdialogProcess = null;
        try {
            ProcessBuilder kdialogPb = new ProcessBuilder("kdialog", "--getopenfilename", "", "*.png *.gif");
            kdialogProcess = kdialogPb.start();
            int kdialogExitCode = kdialogProcess.waitFor();

            if (kdialogExitCode == 0) {
                try (BufferedReader kdialogReader = new BufferedReader(new InputStreamReader(kdialogProcess.getInputStream()))) {
                    String path = kdialogReader.readLine();
                    if (path != null && !path.isEmpty()) {
                        return new File(path.trim());
                    }
                }
            }
        } catch (IOException e) {
            Strange.LOGGER.debug("kdialog cape picker is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Strange.LOGGER.debug("kdialog cape picker was interrupted", e);
        } finally {
            if (kdialogProcess != null && kdialogProcess.isAlive()) {
                kdialogProcess.destroy();
            }
        }
        
        return null;
    }

    private static void saveCapeFile(File sourceFile) {
        try {
            File capeDir = getCapeDirectory();
            if (!capeDir.exists() && !capeDir.mkdirs()) {
                throw new IllegalStateException("Failed to create cape directory");
            }

            String fileName = sourceFile.getName();
            File destFile = new File(capeDir, fileName);
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            saveActiveCapeSelection(fileName);

            String destPath = destFile.getAbsolutePath();
            runOnClientThread(() -> {
                applyLocalCape(destPath);
                sendPlayerMessage("§aCape saved successfully! All players with this mod will see it!");
            });
        } catch (IOException | IllegalStateException e) {
            Strange.LOGGER.warn("Failed to save cape from {}", sourceFile.getAbsolutePath(), e);
            String message = "Error saving cape: " + e.getMessage();
            runOnClientThread(() -> sendPlayerMessage(message));
        }
    }

    private static File getCapeDirectory() {
        return new File(Strange.root, "capes");
    }

    private static void loadSavedCape() {
        File selected = resolveSavedCapeFile();
        if (selected != null) {
            localPlayerCapePath = selected.getAbsolutePath();
        }
    }

    private static File resolveSavedCapeFile() {
        File capeDir = getCapeDirectory();
        if (!capeDir.exists()) {
            return null;
        }

        File activeFile = new File(capeDir, "active_cape.txt");
        if (activeFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(activeFile, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    File selected = new File(capeDir, line.trim());
                    if (selected.exists() && selected.isFile()) {
                        return selected;
                    }
                }
            } catch (IOException e) {
                Strange.LOGGER.warn("Failed to read active cape selection", e);
            }
        }

        File legacy = new File(capeDir, "custom_cape.png");
        return legacy.exists() ? legacy : null;
    }

    private static void saveActiveCapeSelection(String fileName) {
        File capeDir = getCapeDirectory();
        if (!capeDir.exists() && !capeDir.mkdirs()) {
            throw new IllegalStateException("Failed to create cape directory");
        }

        File activeFile = new File(capeDir, "active_cape.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(activeFile, StandardCharsets.UTF_8, false))) {
            writer.write(fileName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save active cape selection", e);
        }
    }

    private static void clearActiveCapeSelection() {
        File activeFile = new File(getCapeDirectory(), "active_cape.txt");
        if (activeFile.exists()) {
            activeFile.delete();
        }
    }

    private static void sendPlayerMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    private static void applyLocalCape(String path) {
        localPlayerCapePath = (path == null || path.isBlank()) ? null : path;
        clearLocalCape();
        initialized = true;
    }

    private static void clearLocalCape() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            clearCapeTexture(client.player.getUuid());
        }
        localPlayerCapeId = null;
        if (localPlayerCapeTexture != null) {
            localPlayerCapeTexture.close();
            localPlayerCapeTexture = null;
        }
    }

    private static void clearCapeTexture(UUID playerId) {
        Identifier capeId = PLAYER_CAPES.remove(playerId);
        if (capeId != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.getTextureManager().destroyTexture(capeId);
            }
        }
        
        NativeImageBackedTexture texture = CAPE_TEXTURES.remove(playerId);
        if (texture != null) {
            texture.close();
        }
        
        CAPE_PATHS.remove(playerId);
        ANIMATED_CAPES.remove(playerId);
    }

    private static void runOnClientThread(Runnable task) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            task.run();
            return;
        }

        if (client.isOnThread()) {
            task.run();
            return;
        }

        client.execute(task);
    }

    public static String resolveCapePath(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        return new File(getCapeDirectory(), fileName).getAbsolutePath();
    }

    // API для загрузки плащей других игроков (для будущей сетевой синхронизации)
    public static void loadPlayerCape(UUID playerId, String capePath) {
        runOnClientThread(() -> {
            loadCapeTexture(playerId, capePath);
        });
    }

    public static void removePlayerCape(UUID playerId) {
        runOnClientThread(() -> {
            clearCapeTexture(playerId);
        });
    }
}
