package ru.strange.client.manager.friend;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class FriendManager {
    public static MinecraftClient mc = MinecraftClient.getInstance();
    public static final List<Friend> friends = new ArrayList<>();
    public static final File file = new File(new File(Strange.root, "configs"), "friend.cfg");
    private static boolean initialized = false;

    public FriendManager() {
        init();
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        boolean ready = false;
        try {
            ensureStorageDirectory();
            recoverTemporaryStorageIfNeeded();
            if (!file.exists()) {
                Files.createFile(file.toPath());
            }
            ready = readFriends();
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to initialize friend storage at {}", file.getAbsolutePath(), e);
        } finally {
            initialized = ready;
        }
    }

    public void add(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty() || isFriend(normalizedName)) {
            return;
        }
        friends.add(new Friend(normalizedName));
        updateFile();
    }

    public Friend getFriend(String friend) {
        return friends.stream().filter(isFriend -> isFriend.getName().equalsIgnoreCase(friend)).findFirst().orElse(null);
    }

    public boolean isFriend(String friend) {
        return friend != null && friends.stream().anyMatch(isFriend -> isFriend.getName().equalsIgnoreCase(friend));
    }

    public void remove(String name) {
        friends.removeIf(friend -> friend.getName().equalsIgnoreCase(name));
        updateFile();
    }

    public void clearFriend() {
        friends.clear();
        updateFile();
    }

    public static List<Friend> getFriends() {
        return List.copyOf(friends);
    }

    public static boolean getNearFriends(String name) {
        return mc.world != null && name != null
                && mc.world.getPlayers().stream().anyMatch(player -> player.getName().getString().equalsIgnoreCase(name));
    }

    public void updateFile() {
        init();
        try {
            StringBuilder builder = new StringBuilder();
            friends.forEach(friend -> builder.append(friend.getName()).append("\n"));
            Path filePath = file.toPath();
            Path tempPath = tempFilePath();
            ensureStorageDirectory();
            Files.writeString(tempPath, builder.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to save friends to {}", file.getAbsolutePath(), e);
        }
    }

    public static synchronized boolean readFriends() {
        Path filePath = file.toPath();
        Path tempPath = tempFilePath();
        if (!Files.isRegularFile(filePath) && !Files.isRegularFile(tempPath)) {
            friends.clear();
            return true;
        }

        List<Friend> loadedFriends = tryReadFriends(filePath, false);
        if (loadedFriends == null) {
            loadedFriends = tryReadFriends(tempPath, true);
            if (loadedFriends == null) {
                return false;
            }
            promoteRecoveredTempFile(tempPath, filePath);
        }

        friends.clear();
        friends.addAll(loadedFriends);
        return true;
    }

    private static List<Friend> tryReadFriends(Path path, boolean temporaryFile) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        List<Friend> loadedFriends = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalizedName = line.trim();
                String normalizedKey = normalizedName.toLowerCase(Locale.ROOT);
                if (!normalizedName.isBlank() && seen.add(normalizedKey)) {
                    loadedFriends.add(new Friend(normalizedName));
                }
            }
            return loadedFriends;
        } catch (IOException e) {
            String sourceType = temporaryFile ? "temporary friends storage" : "friends storage";
            Strange.LOGGER.warn("Failed to load {} from {}", sourceType, path.toAbsolutePath(), e);
            return null;
        }
    }

    private static void ensureStorageDirectory() throws IOException {
        Path parent = file.toPath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void recoverTemporaryStorageIfNeeded() {
        Path filePath = file.toPath();
        Path tempPath = tempFilePath();
        if (!file.exists() && Files.isRegularFile(tempPath)) {
            promoteRecoveredTempFile(tempPath, filePath);
        }
    }

    private static void promoteRecoveredTempFile(Path tempPath, Path filePath) {
        try {
            try {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            Strange.LOGGER.warn("Recovered friend storage from temporary file {}", tempPath.toAbsolutePath());
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to promote temporary friend storage {}", tempPath.toAbsolutePath(), exception);
        }
    }

    private static Path tempFilePath() {
        return file.toPath().resolveSibling(file.getName() + ".tmp");
    }
}
