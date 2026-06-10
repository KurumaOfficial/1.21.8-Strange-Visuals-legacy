package ru.strange.client.manager.promo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ru.strange.client.Strange;
import ru.strange.client.utils.io.AtomicFileIO;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PromoCodeManager {

    public static final int TYPE_IP = 0;
    public static final int TYPE_SINGLE = 1;

    private static final File STORAGE_FILE = new File(Strange.root, "promocodes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static PromoStore store = new PromoStore();

    private PromoCodeManager() {}

    public static void ensureLoaded() {
        if (STORAGE_FILE.exists()) {
            try {
                String json = Files.readString(STORAGE_FILE.toPath(), StandardCharsets.UTF_8);
                PromoStore parsed = GSON.fromJson(json, PromoStore.class);
                if (parsed != null) {
                    store = parsed;
                }
            } catch (IOException e) {
                Strange.LOGGER.warn("Failed to load promocodes.json", e);
            }
        }
        if (store.codes == null) store.codes = new ArrayList<>();
        if (store.activations == null) store.activations = new ArrayList<>();
    }

    public static void save() {
        try {
            Path parent = STORAGE_FILE.toPath().getParent();
            if (parent != null) Files.createDirectories(parent);
            AtomicFileIO.writeUtf8StringAtomically(STORAGE_FILE.toPath(), GSON.toJson(store));
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to save promocodes.json", e);
        }
    }

    public static List<PromoCode> getCodes() {
        ensureLoaded();
        return Collections.unmodifiableList(store.codes);
    }

    public static List<PromoActivation> getActivations() {
        ensureLoaded();
        return Collections.unmodifiableList(store.activations);
    }

    public static void addCode(PromoCode code) {
        ensureLoaded();
        store.codes.removeIf(c -> c.code.equalsIgnoreCase(code.code));
        store.codes.add(code);
        save();
    }

    public static void removeCode(String code) {
        ensureLoaded();
        store.codes.removeIf(c -> c.code.equalsIgnoreCase(code));
        store.activations.removeIf(a -> a.code.equalsIgnoreCase(code));
        save();
    }

    public static PromoResult validate(String code, String clientIp) {
        ensureLoaded();
        if (code == null || code.isBlank()) {
            return PromoResult.denied("Код не может быть пустым");
        }

        PromoCode promoTemp = null;
         for (PromoCode c : store.codes) {
             if (c.code.equalsIgnoreCase(code.trim())) {
                 promoTemp = c;
                 break;
             }
         }
         if (promoTemp == null) {
             return PromoResult.denied("Промокод не найден");
         }

         final PromoCode promo = promoTemp;
         String normalizedIp = clientIp == null ? "" : clientIp.trim();

         if (promo.type == TYPE_IP) {
             final String promoCode = promo.code;
            long ipCount = store.activations.stream()
                    .filter(a -> a.code.equalsIgnoreCase(promoCode)
                            && a.ip.equals(normalizedIp))
                    .count();
            if (ipCount >= promo.maxActivations) {
                return PromoResult.denied("Промокод уже активирован на этом IP");
            }
        } else if (promo.type == TYPE_SINGLE) {
            final String promoCode = promo.code;
            boolean used = store.activations.stream()
                    .anyMatch(a -> a.code.equalsIgnoreCase(promoCode));
            if (used) {
                return PromoResult.denied("Этот промокод уже был использован");
            }
        }

        return PromoResult.accepted(promo);
    }

    public static PromoResult apply(String code, String clientIp) {
        PromoResult result = validate(code, clientIp);
        if (!result.accepted) {
            return result;
        }

        PromoActivation activation = new PromoActivation();
        activation.code = result.promoCode.code;
        activation.ip = clientIp == null ? "" : clientIp.trim();
        activation.timestamp = System.currentTimeMillis();
        store.activations.add(activation);
        save();
        return result;
    }

    public static boolean isApplied(String code, String clientIp) {
        ensureLoaded();
        String normCode = code == null ? "" : code.trim().toLowerCase(java.util.Locale.ROOT);
        String normIp = clientIp == null ? "" : clientIp.trim();
        return store.activations.stream().anyMatch(a ->
                a.code.equalsIgnoreCase(normCode) && a.ip.equals(normIp));
    }

    public static void seedBuiltInCodes() {
        ensureLoaded();
        boolean has = store.codes.stream().anyMatch(c -> c.code.equalsIgnoreCase("BoxingGames"));
        if (!has) {
            store.codes.add(new PromoCode("BoxingGames", TYPE_IP, 1, "Открывает секретные мини-игры (Тетрис + Пакман)"));
            save();
            Strange.LOGGER.info("Seeded BoxingGames promo code");
        }
    }

    public static boolean isGamesUnlocked() {
        ensureLoaded();
        return store.activations.stream().anyMatch(a -> a.code.equalsIgnoreCase("BoxingGames"));
    }

    // ---- Data classes ----

    public static class PromoCode {
        public String code;
        public int type;
        public int maxActivations = 1;
        public String description = "";

        public PromoCode() {}

        public PromoCode(String code, int type, int maxActivations, String description) {
            this.code = code;
            this.type = type;
            this.maxActivations = maxActivations;
            this.description = description;
        }
    }

    public static class PromoActivation {
        public String code;
        public String ip;
        public long timestamp;

        public PromoActivation() {}
    }

    public static class PromoResult {
        public final boolean accepted;
        public final String message;
        public final PromoCode promoCode;

        private PromoResult(boolean accepted, String message, PromoCode promoCode) {
            this.accepted = accepted;
            this.message = message;
            this.promoCode = promoCode;
        }

        public static PromoResult accepted(PromoCode code) {
            return new PromoResult(true, "Промокод активирован!", code);
        }

        public static PromoResult denied(String message) {
            return new PromoResult(false, message, null);
        }
    }

    private static class PromoStore {
        List<PromoCode> codes = new ArrayList<>();
        List<PromoActivation> activations = new ArrayList<>();
    }
}