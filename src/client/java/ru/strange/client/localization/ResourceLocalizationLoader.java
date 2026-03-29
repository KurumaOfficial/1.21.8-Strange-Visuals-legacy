package ru.strange.client.localization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import ru.strange.client.Strange;

import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ResourceLocalizationLoader {
    private ResourceLocalizationLoader() {
    }

    public static Map<String, String> load(String resourcePath) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (resourcePath == null || resourcePath.isBlank()) {
            return entries;
        }

        try (InputStream stream = ResourceLocalizationLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                Strange.LOGGER.warn("Localization resource not found: {}", resourcePath);
                return entries;
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    Strange.LOGGER.warn("Localization resource {} is not a JSON object", resourcePath);
                    return entries;
                }

                JsonObject object = parsed.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
                        Strange.LOGGER.warn("Skipping invalid localization entry {} in {}", entry.getKey(), resourcePath);
                        continue;
                    }
                    entries.put(entry.getKey(), primitive.getAsString());
                }
            }
        } catch (IOException | RuntimeException exception) {
            Strange.LOGGER.warn("Failed to load localization resource {}", resourcePath, exception);
        }

        return Map.copyOf(entries);
    }
}
