package ru.strange.client.StarterMenu;

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
import java.util.List;

public final class AltAccountStore {
    private final File storageFile;

    public AltAccountStore(File storageFile) {
        this.storageFile = storageFile;
    }

    public LoadedAccounts load() {
        List<AltAccount> active = new ArrayList<>();
        List<AltAccount> deleted = new ArrayList<>();
        String selectedActiveName = null;

        if (!storageFile.exists()) {
            return new LoadedAccounts(active, deleted, null);
        }

        try (Reader reader = Files.newBufferedReader(storageFile.toPath(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonArray()) {
                readAccounts(parsed.getAsJsonArray(), active, deleted);
                return new LoadedAccounts(active, deleted, null);
            }

            if (!parsed.isJsonObject()) {
                Strange.LOGGER.warn("Invalid alt storage format in {}", storageFile.getAbsolutePath());
                return new LoadedAccounts(active, deleted, null);
            }

            JsonObject root = parsed.getAsJsonObject();
            if (root.has("selectedActiveName") && root.get("selectedActiveName").isJsonPrimitive()) {
                selectedActiveName = root.get("selectedActiveName").getAsString();
            }

            JsonElement accountsElement = root.get("accounts");
            if (accountsElement != null && accountsElement.isJsonArray()) {
                readAccounts(accountsElement.getAsJsonArray(), active, deleted);
            } else {
                Strange.LOGGER.warn("Missing accounts array in {}", storageFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to load accounts from {}", storageFile.getAbsolutePath(), e);
        }

        return new LoadedAccounts(active, deleted, sanitizeSelectedName(active, selectedActiveName));
    }

    public boolean save(List<AltAccount> active, List<AltAccount> deleted, String selectedActiveName) {
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
            root.addProperty("version", 2);
            if (sanitizedSelectedName != null) {
                root.addProperty("selectedActiveName", sanitizedSelectedName);
            }
            root.add("accounts", array);

            Path tempFile = storagePath.resolveSibling(storageFile.getName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
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

    private static void readAccounts(JsonArray array, List<AltAccount> active, List<AltAccount> deleted) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            if (!object.has("name")) {
                continue;
            }

            String name = object.get("name").getAsString();
            String date = object.has("date") ? object.get("date").getAsString() : "";
            long createdAt = object.has("createdAt") ? object.get("createdAt").getAsLong() : System.currentTimeMillis();
            boolean pinned = object.has("pinned") && object.get("pinned").getAsBoolean();
            boolean isDeleted = object.has("deleted") && object.get("deleted").getAsBoolean();

            AltAccount account = new AltAccount(name, date, createdAt, pinned);
            if (isDeleted) {
                deleted.add(account);
            } else {
                active.add(account);
            }
        }
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

    private static void appendAccounts(JsonArray array, List<AltAccount> accounts, boolean deleted) {
        for (AltAccount account : accounts) {
            JsonObject object = new JsonObject();
            object.addProperty("name", account.name);
            object.addProperty("date", account.date);
            object.addProperty("createdAt", account.createdAt);
            object.addProperty("pinned", account.pinned);
            object.addProperty("deleted", deleted);
            array.add(object);
        }
    }

    public record LoadedAccounts(List<AltAccount> active, List<AltAccount> deleted, String selectedActiveName) {}
}
