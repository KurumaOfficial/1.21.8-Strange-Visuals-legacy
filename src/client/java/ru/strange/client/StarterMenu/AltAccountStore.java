package ru.strange.client.StarterMenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.strange.client.Strange;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AltAccountStore {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File storageFile;

    public AltAccountStore(File storageFile) {
        this.storageFile = storageFile;
    }

    public LoadedAccounts load() {
        LoadedAccounts fromPrimary = tryLoad(storageFile.toPath());
        if (fromPrimary != null) {
            return fromPrimary;
        }

        Path tempPath = tempFilePath();
        LoadedAccounts fromTemp = tryLoad(tempPath);
        if (fromTemp != null) {
            Strange.LOGGER.warn("Recovered alt storage from temporary file {}", tempPath.toAbsolutePath());
            promoteRecoveredTempFile(tempPath, storageFile.toPath());
            return fromTemp;
        }

        return emptyAccounts();
    }

    private LoadedAccounts tryLoad(Path sourcePath) {
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return null;
        }

        List<AltAccount> active = new ArrayList<>();
        List<AltAccount> deleted = new ArrayList<>();
        String selectedActiveName = null;

        try (Reader reader = Files.newBufferedReader(sourcePath, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonArray()) {
                readAccounts(parsed.getAsJsonArray(), active, deleted, sourcePath);
                return new LoadedAccounts(active, deleted, null, null);
            }

            if (!parsed.isJsonObject()) {
                Strange.LOGGER.warn("Invalid alt storage format in {}", sourcePath.toAbsolutePath());
                return null;
            }

            JsonObject root = parsed.getAsJsonObject();
            if (root.has("selectedActiveName") && root.get("selectedActiveName").isJsonPrimitive()) {
                selectedActiveName = root.get("selectedActiveName").getAsString();
            }
            AltSortMode sortMode = readSortMode(root);

            JsonElement accountsElement = root.get("accounts");
            if (accountsElement != null && accountsElement.isJsonArray()) {
                readAccounts(accountsElement.getAsJsonArray(), active, deleted, sourcePath);
            } else {
                Strange.LOGGER.warn("Missing accounts array in {}", sourcePath.toAbsolutePath());
            }
            return new LoadedAccounts(active, deleted, sanitizeSelectedName(active, selectedActiveName), sortMode);
        } catch (IOException | RuntimeException exception) {
            Strange.LOGGER.warn("Failed to load accounts from {}", sourcePath.toAbsolutePath(), exception);
            return null;
        }
    }

    public boolean save(List<AltAccount> active, List<AltAccount> deleted, String selectedActiveName, AltSortMode sortMode) {
        try {
            Path storagePath = storageFile.toPath();
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String sanitizedSelectedName = sanitizeSelectedName(active, selectedActiveName);
            JsonArray array = new JsonArray();
            appendAccounts(array, active, false);
            appendAccounts(array, deleted, true);
            JsonObject root = new JsonObject();
            root.addProperty("version", 3);
            if (sanitizedSelectedName != null) {
                root.addProperty("selectedActiveName", sanitizedSelectedName);
            }
            if (sortMode != null) {
                root.addProperty("sortMode", sortMode.name());
            }
            root.add("accounts", array);

            Path tempFile = storagePath.resolveSibling(storageFile.getName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                PRETTY_GSON.toJson(root, writer);
            }

            try {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to save accounts to {}", storageFile.getAbsolutePath(), e);
            return false;
        }
    }

    private static void readAccounts(JsonArray array, List<AltAccount> active, List<AltAccount> deleted, Path sourcePath) {
        Set<String> activeNames = new HashSet<>();
        Set<String> deletedNames = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                Strange.LOGGER.warn("Skipping invalid alt account entry {} in {}", i, sourcePath.toAbsolutePath());
                continue;
            }

            try {
                JsonObject object = element.getAsJsonObject();
                AltAccount account = readAccount(object);
                if (account == null) {
                    Strange.LOGGER.warn("Skipping incomplete alt account entry {} in {}", i, sourcePath.toAbsolutePath());
                    continue;
                }

                boolean isDeleted = readBooleanProperty(object, "deleted");
                String normalizedName = normalizeAccountName(account.name);
                if (isDeleted) {
                    if (activeNames.contains(normalizedName) || !deletedNames.add(normalizedName)) {
                        Strange.LOGGER.warn("Skipping duplicate deleted alt account {} in {}", account.name, sourcePath.toAbsolutePath());
                        continue;
                    }
                    deleted.add(account);
                } else {
                    if (!activeNames.add(normalizedName)) {
                        Strange.LOGGER.warn("Skipping duplicate active alt account {} in {}", account.name, sourcePath.toAbsolutePath());
                        continue;
                    }

                    if (deletedNames.remove(normalizedName)) {
                        removeAccountByName(deleted, account.name);
                        Strange.LOGGER.warn("Preferring active alt account {} over deleted duplicate in {}", account.name, sourcePath.toAbsolutePath());
                    }
                    active.add(account);
                }
            } catch (RuntimeException exception) {
                Strange.LOGGER.warn("Skipping malformed alt account entry {} in {}", i, sourcePath.toAbsolutePath(), exception);
            }
        }
    }

    private static AltAccount readAccount(JsonObject object) {
        if (!object.has("name") || !object.get("name").isJsonPrimitive()) {
            return null;
        }

        String name = sanitizeStoredName(object.get("name").getAsString());
        if (name == null) {
            return null;
        }

        String date = object.has("date") && object.get("date").isJsonPrimitive() && object.get("date").getAsJsonPrimitive().isString()
                ? object.get("date").getAsString().trim()
                : "";
        long createdAt = readCreatedAt(object);
        boolean pinned = readBooleanProperty(object, "pinned");
        return new AltAccount(name, date, createdAt, pinned);
    }

    private static String sanitizeSelectedName(List<AltAccount> active, String selectedActiveName) {
        if (selectedActiveName == null || selectedActiveName.isBlank()) {
            return null;
        }

        for (AltAccount account : active) {
            if (account.name.equalsIgnoreCase(selectedActiveName)) {
                return account.name;
            }
        }
        return null;
    }

    private static AltSortMode readSortMode(JsonObject root) {
        if (root == null || !root.has("sortMode") || !root.get("sortMode").isJsonPrimitive()) {
            return null;
        }

        try {
            return AltSortMode.valueOf(root.get("sortMode").getAsString());
        } catch (IllegalArgumentException exception) {
            Strange.LOGGER.warn("Skipping unknown alt sort mode {}", root.get("sortMode").getAsString(), exception);
            return null;
        }
    }

    private static void appendAccounts(JsonArray array, List<AltAccount> accounts, boolean deleted) {
        for (AltAccount account : accounts) {
            String storedName = sanitizeStoredName(account.name);
            if (storedName == null) {
                Strange.LOGGER.warn("Skipping invalid in-memory alt account during save to {}", deleted ? "deleted" : "active");
                continue;
            }

            JsonObject object = new JsonObject();
            object.addProperty("name", storedName);
            object.addProperty("date", account.date);
            object.addProperty("createdAt", account.createdAt);
            object.addProperty("pinned", account.pinned);
            object.addProperty("deleted", deleted);
            array.add(object);
        }
    }

    private static boolean readBooleanProperty(JsonObject object, String propertyName) {
        return object != null
                && propertyName != null
                && object.has(propertyName)
                && object.get(propertyName).isJsonPrimitive()
                && object.get(propertyName).getAsJsonPrimitive().isBoolean()
                && object.get(propertyName).getAsBoolean();
    }

    private static long readCreatedAt(JsonObject object) {
        long fallback = System.currentTimeMillis();
        if (object == null || !object.has("createdAt") || !object.get("createdAt").isJsonPrimitive()) {
            return fallback;
        }

        var primitive = object.get("createdAt").getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return fallback;
        }

        long createdAt = primitive.getAsLong();
        return createdAt > 0L ? createdAt : fallback;
    }

    private static String sanitizeStoredName(String name) {
        if (name == null) {
            return null;
        }

        String trimmed = name.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String normalizeAccountName(String name) {
        String sanitized = sanitizeStoredName(name);
        return sanitized == null ? "" : sanitized.toLowerCase(Locale.ROOT);
    }

    private static void removeAccountByName(List<AltAccount> accounts, String name) {
        String normalizedName = normalizeAccountName(name);
        for (int index = 0; index < accounts.size(); index++) {
            if (normalizeAccountName(accounts.get(index).name).equals(normalizedName)) {
                accounts.remove(index);
                return;
            }
        }
    }

    private Path tempFilePath() {
        return storageFile.toPath().resolveSibling(storageFile.getName() + ".tmp");
    }

    private void promoteRecoveredTempFile(Path tempPath, Path storagePath) {
        try {
            try {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to promote recovered alt temp file {}", tempPath.toAbsolutePath(), exception);
        }
    }

    private static LoadedAccounts emptyAccounts() {
        return new LoadedAccounts(new ArrayList<>(), new ArrayList<>(), null, null);
    }

    public record LoadedAccounts(List<AltAccount> active, List<AltAccount> deleted, String selectedActiveName, AltSortMode sortMode) {}
}
