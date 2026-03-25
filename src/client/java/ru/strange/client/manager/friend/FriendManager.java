package ru.strange.client.manager.friend;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                file.createNewFile();
            } else {
                readFriends();
            }
            ready = true;
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
        return friends;
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
            Files.writeString(file.toPath(), builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to save friends to {}", file.getAbsolutePath(), e);
        }
    }

    public static void readFriends() {
        friends.clear();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalizedName = line.trim();
                if (!normalizedName.isBlank() && friends.stream().noneMatch(friend -> friend.getName().equalsIgnoreCase(normalizedName))) {
                    friends.add(new Friend(normalizedName));
                }
            }
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to load friends from {}", file.getAbsolutePath(), e);
        }
    }
}
