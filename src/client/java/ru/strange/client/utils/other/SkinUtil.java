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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SkinUtil {
    private static final Identifier CUSTOM_SKIN_TEXTURE_ID = Strange.id("dynamic/custom_skin");
    private static String customSkinPath = null;
    private static Identifier customSkinIdentifier = null;
    private static NativeImageBackedTexture customSkinTexture = null;
    private static boolean lastSetSkinValue = false;

    public static void uiPickAndApplySkin() {
        Thread thread = new Thread(SkinUtil::openSkinFileDialog, "SkinManager-FileDialog");
        thread.setDaemon(true);
        thread.start();
    }

    public static void uiResetSkin() {
        customSkinPath = null;
        clearRegisteredSkinTexture();
        lastSetSkinValue = true;

        try {
            File skinFile = new File(getSkinDirectory(), "custom_skin.png");
            if (skinFile.exists()) {
                boolean ok = skinFile.delete();
                sendPlayerMessage(ok ? "Skin reset successfully!" : "Failed to delete custom_skin.png");
            } else {
                sendPlayerMessage("No custom skin to reset.");
            }
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to reset custom skin", e);
            sendPlayerMessage("Error resetting skin: " + e.getMessage());
        }
    }

    private static Identifier loadCustomSkin() {
        if (customSkinPath == null || customSkinPath.isEmpty()) {
            return null;
        }

        File skinFile = new File(customSkinPath);
        if (!skinFile.exists()) {
            return null;
        }

        try (FileInputStream inputStream = new FileInputStream(skinFile)) {
            NativeImage image = NativeImage.read(inputStream);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> CUSTOM_SKIN_TEXTURE_ID.toString(), image);
            MinecraftClient client = MinecraftClient.getInstance();
            clearRegisteredSkinTexture();
            client.getTextureManager().registerTexture(CUSTOM_SKIN_TEXTURE_ID, texture);

            customSkinTexture = texture;
            customSkinIdentifier = CUSTOM_SKIN_TEXTURE_ID;
            return CUSTOM_SKIN_TEXTURE_ID;
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to load custom skin from {}", skinFile.getAbsolutePath(), e);
            return null;
        }
    }

    public static SkinTextures updatedPlayerSkin(SkinTextures originalSkin, PlayerEntity entity) {
        if ((customSkinPath == null || customSkinPath.isEmpty()) && !lastSetSkinValue) {
            loadSavedSkin();
            lastSetSkinValue = true;
        }

        if (customSkinPath == null || customSkinPath.isEmpty()) {
            return originalSkin;
        }

        if (entity == null || entity != MinecraftClient.getInstance().player) {
            return originalSkin;
        }

        Identifier customTexture = customSkinIdentifier == null ? loadCustomSkin() : customSkinIdentifier;
        if (customTexture == null) {
            return originalSkin;
        }

        return new SkinTextures(
                customTexture,
                originalSkin.textureUrl(),
                originalSkin.capeTexture(),
                originalSkin.elytraTexture(),
                originalSkin.model(),
                originalSkin.secure()
        );
    }

    private static void openSkinFileDialog() {
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
                saveSkinFile(selectedFile);
            }
        } catch (Exception e) {
            Strange.LOGGER.warn("Skin file dialog failed", e);
        }
    }

    private static File openMacFileDialog() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "osascript",
                    "-e",
                    "choose file of type {\"public.png\"} with prompt \"Select Skin File\""
            );
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String path = reader.readLine();
                if (path != null && !path.isEmpty()) {
                    return new File(path.replaceFirst("^alias .*:", "").trim());
                }
            }
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to open macOS skin dialog", e);
        }
        return null;
    }

    private static File openWindowsFileDialog() {
        try {
            String powerShellScript =
                    "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                            "$dialog = New-Object System.Windows.Forms.OpenFileDialog; " +
                            "$dialog.Filter = 'PNG Images (*.png)|*.png|All Files (*.*)|*.*'; " +
                            "$dialog.Title = 'Select Skin File'; " +
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
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to open Windows skin dialog", e);
        }
        return null;
    }

    private static File openLinuxFileDialog() {
        try {
            ProcessBuilder pb = new ProcessBuilder("zenity", "--file-selection",
                    "--title=Select Skin File", "--file-filter=*.png");
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
            } catch (Exception e) {
                Strange.LOGGER.debug("kdialog skin picker is unavailable", e);
            }
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to open Linux skin dialog", e);
        }
        return null;
    }

    private static void saveSkinFile(File sourceFile) {
        try {
            File skinDir = getSkinDirectory();
            if (!skinDir.exists() && !skinDir.mkdirs()) {
                throw new IllegalStateException("Failed to create skin directory");
            }

            File destFile = new File(skinDir, "custom_skin.png");
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            setCustomSkinPath(destFile.getAbsolutePath());
            sendPlayerMessage("Skin saved successfully!");
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to save skin from {}", sourceFile.getAbsolutePath(), e);
            sendPlayerMessage("Error saving skin: " + e.getMessage());
        }
    }

    private static File getSkinDirectory() {
        return new File(Strange.root, "skins");
    }

    private static void loadSavedSkin() {
        File skinFile = new File(getSkinDirectory(), "custom_skin.png");
        if (skinFile.exists()) {
            setCustomSkinPath(skinFile.getAbsolutePath());
        }
    }

    private static void sendPlayerMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    public static void setCustomSkinPath(String path) {
        customSkinPath = (path == null || path.isBlank()) ? null : path;
        clearRegisteredSkinTexture();
        lastSetSkinValue = true;
    }

    private static void clearRegisteredSkinTexture() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.getTextureManager().destroyTexture(CUSTOM_SKIN_TEXTURE_ID);
        }
        if (customSkinTexture != null) {
            customSkinTexture.close();
            customSkinTexture = null;
        }
        customSkinIdentifier = null;
    }
}
