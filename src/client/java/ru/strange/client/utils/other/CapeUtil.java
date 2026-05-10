package ru.strange.client.utils.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
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

    public static void uiPickAndApplyCape() {
        Thread thread = new Thread(CapeUtil::openCapeFileDialog, "CapeManager-FileDialog");
        thread.setDaemon(true);
        thread.start();
    }

    public static void uiResetCape() {
        String message;
        try {
            File capeFile = new File(getCapeDirectory(), "custom_cape.png");
            if (capeFile.exists()) {
                boolean ok = capeFile.delete();
                message = ok ? "Cape reset successfully!" : "Failed to delete custom_cape.png";
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

    private static Identifier loadCapeTexture(UUID playerId, String capePath) {
        if (capePath == null || capePath.isEmpty()) {
            return null;
        }

        File capeFile = new File(capePath);
        if (!capeFile.exists()) {
            return null;
        }

        try (FileInputStream inputStream = new FileInputStream(capeFile)) {
            NativeImage image = NativeImage.read(inputStream);
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
            Strange.LOGGER.warn("Failed to load cape from {} for player {}", capeFile.getAbsolutePath(), playerId, e);
            return null;
        }
    }

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
        
        // Если это локальный игрок, применяем его плащ
        if (playerId.equals(client.player.getUuid())) {
            if (localPlayerCapePath != null) {
                Identifier capeTexture = localPlayerCapeId != null ? localPlayerCapeId : loadLocalCape();
                if (capeTexture != null) {
                    return new SkinTextures(
                            originalSkin.texture(),
                            originalSkin.textureUrl(),
                            capeTexture,
                            capeTexture, // Используем плащ и для элитр
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

            if (selectedFile != null && selectedFile.exists() && selectedFile.getName().toLowerCase().endsWith(".png")) {
                saveCapeFile(selectedFile);
            }
        } catch (RuntimeException e) {
            Strange.LOGGER.warn("Cape file dialog failed", e);
        }
    }

    private static File openMacFileDialog() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "osascript",
                    "-e",
                    "choose file of type {\"public.png\"} with prompt \"Select Cape File\""
            );
            Process process = pb.start();
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
        }
        return null;
    }

    private static File openWindowsFileDialog() {
        try {
            String powerShellScript =
                    "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                            "$dialog = New-Object System.Windows.Forms.OpenFileDialog; " +
                            "$dialog.Filter = 'PNG Images (*.png)|*.png|All Files (*.*)|*.*'; " +
                            "$dialog.Title = 'Select Cape File'; " +
                            "$result = $dialog.ShowDialog(); " +
                            "if ($result -eq 'OK') { Write-Output $dialog.FileName }";

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", powerShellScript);
            pb.redirectErrorStream(true);
            Process process = pb.start();

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
        }
        return null;
    }

    private static File openLinuxFileDialog() {
        try {
            ProcessBuilder pb = new ProcessBuilder("zenity", "--file-selection",
                    "--title=Select Cape File", "--file-filter=*.png");
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String path = reader.readLine();
                    if (path != null && !path.isEmpty()) {
                        return new File(path.trim());
                    }
                }
            }

            try {
                ProcessBuilder kdialogPb = new ProcessBuilder("kdialog", "--getopenfilename", "", "*.png");
                Process kdialogProcess = kdialogPb.start();
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
            }
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to open Linux cape dialog", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Strange.LOGGER.debug("Linux cape picker was interrupted", e);
        }
        return null;
    }

    private static void saveCapeFile(File sourceFile) {
        try {
            File capeDir = getCapeDirectory();
            if (!capeDir.exists() && !capeDir.mkdirs()) {
                throw new IllegalStateException("Failed to create cape directory");
            }

            File destFile = new File(capeDir, "custom_cape.png");
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

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
        File capeFile = new File(getCapeDirectory(), "custom_cape.png");
        if (capeFile.exists()) {
            localPlayerCapePath = capeFile.getAbsolutePath();
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
