package ru.strange.client.StarterMenu;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Генератор случайных никнеймов для Minecraft.
 * <p>
 * Поддерживает несколько стратегий:
 * <ul>
 *   <li>Комбинация прилагательное + существительное + цифры</li>
 *   <li>Слог-based генерация (фонетически читаемые ники)</li>
 *   <li>Тематические слова (Cyber, Fantasy, Nature)</li>
 * </ul>
 *
 * <h3>Использование:</h3>
 * <pre>
 * String nick = NickGenerator.generate();
 * String[] batch = NickGenerator.generateBatch(10);
 * </pre>
 */
public final class NickGenerator {

    private NickGenerator() {}

    private static final String[] ADJECTIVES = {
            "Dark", "Ice", "Fire", "Neo", "Sky", "Storm", "Iron", "Shadow",
            "Void", "Star", "Frost", "Solar", "Night", "Swift", "Brave",
            "Red", "Blue", "Neon", "Hex", "Zero", "Wild", "Ace", "Axe"
    };

    private static final String[] NOUNS = {
            "Wolf", "Blade", "Fox", "Viper", "Hawk", "Bear", "Raven", "Tiger",
            "Ninja", "Lynx", "Titan", "Fang", "Ghost", "King", "Sage",
            "Mage", "Punk", "Byte", "Glitch", "Spark", "Flame", "Shard"
    };

    private static final String[] CYBER = {
            "Cyber", "Pixel", "Bit", "Nano", "Node", "Hex", "Data", "Core",
            "Net", "Grid", "Pulse", "Link", "Warp", "Drift", "Flux"
    };

    private static final String[] FANTASY = {
            "Elf", "Orc", "Dragon", "Rune", "Arcane", "Mythic", "Wyrm",
            "Thorn", "Shade", "Ember", "Frost", "Storm", "Crystal"
    };

    private static final String[][] SYLLABLES = {
            {"Ka", "Ri", "Mo", "Ze", "Lu", "Na", "To", "Vi", "Se", "Di"},
            {"ra", "lo", "xi", "ne", "va", "ko", "mi", "zu", "ta", "sa"},
            {"x", "n", "s", "r", "th", "z", "k", ""}
    };

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;

    /**
     * Генерирует один случайный никнейм.
     *
     * @return никнейм длиной от 3 до 16 символов
     */
    public static String generate() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        String nick = switch (rng.nextInt(5)) {
            case 0 -> adjectiveNoun(rng);
            case 1 -> syllabic(rng);
            case 2 -> themed(CYBER, rng);
            case 3 -> themed(FANTASY, rng);
            default -> wordNumber(rng);
        };

        return clamp(nick);
    }

    /**
     * Генерирует пакет уникальных никнеймов.
     *
     * @param count количество никнеймов
     * @return массив уникальных никнеймов
     */
    public static String[] generateBatch(int count) {
        String[] result = new String[count];
        java.util.Set<String> seen = new java.util.HashSet<>();

        for (int i = 0; i < count; i++) {
            String nick;
            int attempts = 0;
            do {
                nick = generate();
                attempts++;
            } while (seen.contains(nick) && attempts < 100);
            seen.add(nick);
            result[i] = nick;
        }

        return result;
    }

    // --- Стратегии генерации ---

    /** Adjective + Noun [+ digits] */
    private static String adjectiveNoun(ThreadLocalRandom rng) {
        String adj = pick(ADJECTIVES, rng);
        String noun = pick(NOUNS, rng);
        String suffix = rng.nextBoolean() ? String.valueOf(rng.nextInt(10, 999)) : "";
        return adj + noun + suffix;
    }

    /** Фонетические слоги */
    private static String syllabic(ThreadLocalRandom rng) {
        int count = 2 + rng.nextInt(2); // 2-3 слога
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(pick(SYLLABLES[0], rng));
            sb.append(pick(SYLLABLES[1], rng));
        }
        sb.append(pick(SYLLABLES[2], rng));
        // Первая буква заглавная
        if (sb.length() > 0) {
            sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        }
        return sb.toString();
    }

    /** Theme word + Noun/Number */
    private static String themed(String[] pool, ThreadLocalRandom rng) {
        String theme = pick(pool, rng);
        if (rng.nextBoolean()) {
            return theme + pick(NOUNS, rng);
        } else {
            return theme + rng.nextInt(10, 9999);
        }
    }

    /** Simple word + number */
    private static String wordNumber(ThreadLocalRandom rng) {
        String word = rng.nextBoolean() ? pick(ADJECTIVES, rng) : pick(NOUNS, rng);
        boolean underscore = rng.nextInt(4) == 0;
        String sep = underscore ? "_" : "";
        return word + sep + rng.nextInt(1, 9999);
    }

    // --- Утилиты ---

    private static String pick(String[] array, ThreadLocalRandom rng) {
        return array[rng.nextInt(array.length)];
    }

    /** Обрезает ник до допустимой длины Minecraft (3-16 символов). */
    private static String clamp(String nick) {
        if (nick.length() > MAX_LENGTH) {
            nick = nick.substring(0, MAX_LENGTH);
        }
        if (nick.length() < MIN_LENGTH) {
            nick = nick + ThreadLocalRandom.current().nextInt(100, 999);
        }
        return nick;
    }
}
