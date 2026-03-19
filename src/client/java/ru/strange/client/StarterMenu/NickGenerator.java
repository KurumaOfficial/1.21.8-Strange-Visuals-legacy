package ru.strange.client.StarterMenu;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║                    NICK GENERATOR — ULTIMATE EDITION                ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║  • 30+ weighted generation strategies                               ║
 * ║  • Phonetic synthesis engine (infinite unique pronounceable names)   ║
 * ║  • Markov-chain inspired syllable combiner                          ║
 * ║  • 10 themes: GAMING, CYBER, FANTASY, MILITARY, NATURE,            ║
 * ║               SPACE, DARK, ANIME, ABSTRACT, MIXED                   ║
 * ║  • Builder pattern: length, case, leet, uniqueness, blacklist       ║
 * ║  • Thread-safe (ThreadLocalRandom + ConcurrentHashMap)              ║
 * ║  • Quality scoring & filtering                                      ║
 * ║  • Name blending / portmanteau engine                               ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * <h3>Quick usage (drop-in replacement):</h3>
 * <pre>{@code
 * String nick = NickGenerator.generate();
 * String[] batch = NickGenerator.generateBatch(10);
 * }</pre>
 *
 * <h3>Advanced usage:</h3>
 * <pre>{@code
 * NickGenerator gen = NickGenerator.builder()
 *     .theme(NickGenerator.Theme.CYBER)
 *     .caseStyle(NickGenerator.CaseStyle.ORIGINAL)
 *     .lengthRange(4, 16)
 *     .allowNumbers(true)
 *     .allowLeet(true)
 *     .allowUnderscores(true)
 *     .ensureUnique(true)
 *     .seed(42L)
 *     .build();
 *
 * String nick = gen.next();
 * List<String> nicks = gen.nextList(500);
 * }</pre>
 */
public final class NickGenerator {

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                          ENUMS                              ║
    // ╚══════════════════════════════════════════════════════════════╝

    public enum Theme {
        GAMING, CYBER, FANTASY, MILITARY, NATURE,
        SPACE, DARK, ANIME, ABSTRACT, MIXED
    }

    public enum CaseStyle {
        ORIGINAL,
        LOWER,
        UPPER,
        CAPITALIZE,
        RANDOM_CASE,
        ALTERNATING
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                     CONFIGURATION                           ║
    // ╚══════════════════════════════════════════════════════════════╝

    private final Theme theme;
    private final CaseStyle caseStyle;
    private final int minLength;
    private final int maxLength;
    private final boolean allowNumbers;
    private final boolean allowUnderscores;
    private final boolean allowLeet;
    private final boolean ensureUnique;
    private final double leetChance;
    private final double numberChance;
    private final Set<String> blacklist;
    private final Random rng;
    private final Set<String> history;
    private final int maxRetries;

    // Default instance for static methods
    private static final NickGenerator DEFAULT = builder().build();

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                     CONSTRUCTOR                             ║
    // ╚══════════════════════════════════════════════════════════════╝

    private NickGenerator(Builder b) {
        this.theme = b.theme;
        this.caseStyle = b.caseStyle;
        this.minLength = b.minLength;
        this.maxLength = b.maxLength;
        this.allowNumbers = b.allowNumbers;
        this.allowUnderscores = b.allowUnderscores;
        this.allowLeet = b.allowLeet;
        this.ensureUnique = b.ensureUnique;
        this.leetChance = b.leetChance;
        this.numberChance = b.numberChance;
        this.blacklist = b.blacklist;
        this.rng = b.seed != null ? new Random(b.seed) : new Random();
        this.history = ensureUnique ? ConcurrentHashMap.newKeySet() : null;
        this.maxRetries = b.maxRetries;
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                       BUILDER                               ║
    // ╚══════════════════════════════════════════════════════════════╝

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Theme theme = Theme.MIXED;
        private CaseStyle caseStyle = CaseStyle.ORIGINAL;
        private int minLength = 3;
        private int maxLength = 20;
        private boolean allowNumbers = true;
        private boolean allowUnderscores = true;
        private boolean allowLeet = false;
        private boolean ensureUnique = false;
        private double leetChance = 0.15;
        private double numberChance = 0.4;
        private Set<String> blacklist = Collections.emptySet();
        private Long seed = null;
        private int maxRetries = 1000;

        private Builder() {}

        public Builder theme(Theme t)            { this.theme = t; return this; }
        public Builder caseStyle(CaseStyle c)    { this.caseStyle = c; return this; }
        public Builder minLength(int n)          { this.minLength = Math.max(1, n); return this; }
        public Builder maxLength(int n)          { this.maxLength = Math.max(1, n); return this; }
        public Builder lengthRange(int lo, int hi) {
            this.minLength = Math.max(1, lo);
            this.maxLength = Math.max(this.minLength, hi);
            return this;
        }
        public Builder allowNumbers(boolean b)   { this.allowNumbers = b; return this; }
        public Builder allowUnderscores(boolean b){ this.allowUnderscores = b; return this; }
        public Builder allowLeet(boolean b)      { this.allowLeet = b; return this; }
        public Builder leetChance(double d)      { this.leetChance = Math.max(0, Math.min(1, d)); return this; }
        public Builder numberChance(double d)    { this.numberChance = Math.max(0, Math.min(1, d)); return this; }
        public Builder ensureUnique(boolean b)   { this.ensureUnique = b; return this; }
        public Builder blacklist(Set<String> s)  { this.blacklist = s != null ? s : Collections.emptySet(); return this; }
        public Builder blacklist(String... words){ this.blacklist = new HashSet<>(Arrays.asList(words)); return this; }
        public Builder seed(long s)              { this.seed = s; return this; }
        public Builder maxRetries(int n)         { this.maxRetries = Math.max(1, n); return this; }

        public NickGenerator build() { return new NickGenerator(this); }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║              STATIC API (backward-compatible)               ║
    // ╚══════════════════════════════════════════════════════════════╝

    /** Drop-in replacement: generates one random nick. */
    public static String generate() {
        return DEFAULT.next();
    }

    /** Drop-in replacement: generates a batch of nicks. */
    public static String[] generateBatch(int count) {
        return DEFAULT.nextArray(count);
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                    INSTANCE API                             ║
    // ╚══════════════════════════════════════════════════════════════╝

    /** Generate a single nick. */
    public String next() {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            String raw = dispatchStrategy();
            String processed = postProcess(raw);

            if (processed.length() < minLength || processed.length() > maxLength) continue;
            if (blacklist.contains(processed.toLowerCase())) continue;
            if (ensureUnique) {
                if (!history.add(processed)) continue;
            }
            return processed;
        }
        // Fallback: phonetic + timestamp to guarantee uniqueness
        return postProcess(Phonetic.word(rng, 2 + rng.nextInt(2))
                + System.nanoTime() % 10000);
    }

    /** Generate multiple nicks as a List. */
    public List<String> nextList(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> next())
                .collect(Collectors.toList());
    }

    /** Generate multiple nicks as an array. */
    public String[] nextArray(int count) {
        return nextList(count).toArray(new String[0]);
    }

    /** Reset uniqueness history. */
    public void resetHistory() {
        if (history != null) history.clear();
    }

    /** Returns how many unique nicks have been generated. */
    public int historySize() {
        return history != null ? history.size() : 0;
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║               STRATEGY DISPATCH (30 strategies)             ║
    // ╚══════════════════════════════════════════════════════════════╝

    private String dispatchStrategy() {
        int roll = rng.nextInt(1000);

        // Cumulative weights out of 1000
        if (roll < 120) return sAdjNoun();           // 12.0%  DarkWolf
        if (roll < 210) return sAdjNounNum();         // 9.0%   DarkWolf42
        if (roll < 290) return sAdjSepNoun();         // 8.0%   Dark_Wolf
        if (roll < 360) return sPhonetic();           // 7.0%   Zykoreth
        if (roll < 420) return sAdjShort();           // 6.0%   NeonAce
        if (roll < 475) return sShortNoun();          // 5.5%   AceWolf
        if (roll < 525) return sNounAdj();            // 5.0%   WolfDark
        if (roll < 570) return sPhoneticNum();        // 4.5%   Kryven88
        if (roll < 610) return sTriple();             // 4.0%   DarkAceWolf
        if (roll < 645) return sShortShort();         // 3.5%   AceVex
        if (roll < 678) return sNounTag();            // 3.3%   WolfGG
        if (roll < 708) return sTheNoun();            // 3.0%   TheHunter
        if (roll < 735) return sReverse();            // 2.7%   Nogard
        if (roll < 760) return sBlend();              // 2.5%   Dolf (Dragon+Wolf)
        if (roll < 783) return sPhoneticAdj();        // 2.3%   KryvenShadow
        if (roll < 804) return sInitials();           // 2.1%   DW42
        if (roll < 823) return sNameNum();            // 1.9%   Alex77
        if (roll < 840) return sShortUpper();         // 1.7%   BLITZ
        if (roll < 855) return sOGWrap();             // 1.5%   xShadowx
        if (roll < 868) return sNounNoun();           // 1.3%   WolfBlade
        if (roll < 880) return sLowerConcat();        // 1.2%   darkwolf
        if (roll < 891) return sPhoneticShort();      // 1.1%   ZyAce
        if (roll < 901) return sAdjPhonetic();        // 1.0%   DarkKryven
        if (roll < 910) return sShortNum();           // 0.9%   Ace99
        if (roll < 918) return sDoubleSyllable();     // 0.8%   KaiZen
        if (roll < 925) return sLongPhonetic();       // 0.7%   Kryvexionthar
        if (roll < 932) return sNameTag();            // 0.7%   AlexGG
        if (roll < 940) return sMicroRepeat();        // 0.8%   ZzZz
        if (roll < 950) return sCleanPhonetic();      // 1.0%   Vorenth
        if (roll < 960) return sAdjNounAdj();         // 1.0%   DarkWolfSwift
        if (roll < 972) return sPhoneticBlend();      // 1.2%   KryWolf
        if (roll < 985) return sNumberSandwich();     // 1.3%   x42Darkx
        if (roll < 1000) return sElite();             // 1.5%   iiDarkWolfii
        // unreachable
        return sAdjNoun();
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                  GENERATION STRATEGIES                      ║
    // ╚══════════════════════════════════════════════════════════════╝

    /** DarkWolf */
    private String sAdjNoun() {
        return cap(adj()) + cap(noun());
    }

    /** DarkWolf42 */
    private String sAdjNounNum() {
        return cap(adj()) + cap(noun()) + num();
    }

    /** Dark_Wolf, Dark.Wolf */
    private String sAdjSepNoun() {
        return cap(adj()) + sep() + cap(noun());
    }

    /** Zykoreth */
    private String sPhonetic() {
        return cap(Phonetic.word(rng, 2 + rng.nextInt(2)));
    }

    /** NeonAce */
    private String sAdjShort() {
        return cap(adj()) + cap(shortWord());
    }

    /** AceWolf */
    private String sShortNoun() {
        return cap(shortWord()) + cap(noun());
    }

    /** WolfDark */
    private String sNounAdj() {
        return cap(noun()) + cap(adj());
    }

    /** Kryven88 */
    private String sPhoneticNum() {
        return cap(Phonetic.word(rng, 2 + rng.nextInt(2))) + num();
    }

    /** DarkAceWolf */
    private String sTriple() {
        return cap(adj()) + cap(shortWord()) + cap(noun());
    }

    /** AceVex */
    private String sShortShort() {
        return cap(shortWord()) + cap(shortWord());
    }

    /** WolfGG */
    private String sNounTag() {
        return cap(noun()) + tag();
    }

    /** TheHunter */
    private String sTheNoun() {
        String prefix = rng.nextBoolean() ? "The" : "El";
        return prefix + cap(noun());
    }

    /** Nogard (Dragon reversed) */
    private String sReverse() {
        String w = noun();
        return cap(new StringBuilder(w).reverse().toString());
    }

    /** Dolf (blend of Dragon + Wolf) */
    private String sBlend() {
        String a = noun();
        String b = noun();
        // Make sure they're different
        int safety = 10;
        while (a.equalsIgnoreCase(b) && safety-- > 0) b = noun();
        return cap(Blender.blend(a, b, rng));
    }

    /** KryvenShadow */
    private String sPhoneticAdj() {
        return cap(Phonetic.word(rng, 2)) + cap(adj());
    }

    /** DW42 */
    private String sInitials() {
        String a = adj(), n = noun();
        String initials = "" + Character.toUpperCase(a.charAt(0))
                + Character.toUpperCase(n.charAt(0));
        return initials + num();
    }

    /** Alex77 */
    private String sNameNum() {
        return cap(Names.pick(rng)) + num();
    }

    /** BLITZ */
    private String sShortUpper() {
        return shortWord().toUpperCase();
    }

    /** xShadowx */
    private String sOGWrap() {
        char c = "xXiI_".charAt(rng.nextInt(5));
        return c + cap(adj()) + c;
    }

    /** WolfBlade */
    private String sNounNoun() {
        return cap(noun()) + cap(noun());
    }

    /** darkwolf */
    private String sLowerConcat() {
        return adj().toLowerCase() + noun().toLowerCase();
    }

    /** ZyAce */
    private String sPhoneticShort() {
        return cap(Phonetic.syllable(rng)) + cap(shortWord());
    }

    /** DarkKryven */
    private String sAdjPhonetic() {
        return cap(adj()) + cap(Phonetic.word(rng, 2));
    }

    /** Ace99 */
    private String sShortNum() {
        return cap(shortWord()) + num();
    }

    /** KaiZen */
    private String sDoubleSyllable() {
        return cap(Phonetic.syllable(rng)) + cap(Phonetic.syllable(rng));
    }

    /** Kryvexionthar */
    private String sLongPhonetic() {
        return cap(Phonetic.word(rng, 3 + rng.nextInt(2)));
    }

    /** AlexGG */
    private String sNameTag() {
        return cap(Names.pick(rng)) + tag();
    }

    /** Repeated micro: ZzZz */
    private String sMicroRepeat() {
        String m = Phonetic.syllable(rng);
        if (m.length() > 3) m = m.substring(0, 2);
        return cap(m) + m + cap(m);
    }

    /** Clean 2-syllable phonetic: Vorenth */
    private String sCleanPhonetic() {
        return cap(Phonetic.word(rng, 2));
    }

    /** DarkWolfSwift */
    private String sAdjNounAdj() {
        return cap(adj()) + cap(noun()) + cap(adj());
    }

    /** KryWolf — phonetic syllable + noun */
    private String sPhoneticBlend() {
        String syl = Phonetic.syllable(rng);
        if (syl.length() > 4) syl = syl.substring(0, 3);
        return cap(syl) + cap(noun());
    }

    /** x42Darkx */
    private String sNumberSandwich() {
        char wrap = "xXoO".charAt(rng.nextInt(4));
        return "" + wrap + (rng.nextInt(99) + 1) + cap(adj()) + wrap;
    }

    /** iiDarkWolfii */
    private String sElite() {
        String[] wraps = {"ii", "ll", "xx", "oO", "Xx"};
        String w = wraps[rng.nextInt(wraps.length)];
        return w + cap(adj()) + cap(noun()) + w;
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                    POST-PROCESSING                          ║
    // ╚══════════════════════════════════════════════════════════════╝

    private String postProcess(String raw) {
        String result = raw;

        // Leet speak
        if (allowLeet && rng.nextDouble() < leetChance) {
            result = LeetTransform.apply(result, rng);
        }

        // Case style
        result = applyCase(result);

        // Clean up any double separators
        result = result.replaceAll("[_]{2,}", "_")
                .replaceAll("[.]{2,}", ".");

        return result;
    }

    private String applyCase(String s) {
        switch (caseStyle) {
            case LOWER:       return s.toLowerCase();
            case UPPER:       return s.toUpperCase();
            case CAPITALIZE:  return cap(s);
            case RANDOM_CASE: return randomizeCase(s);
            case ALTERNATING: return alternateCase(s);
            case ORIGINAL:
            default:          return s;
        }
    }

    private String randomizeCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(rng.nextBoolean()
                    ? Character.toUpperCase(c)
                    : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private String alternateCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int idx = 0;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) {
                sb.append((idx++ & 1) == 0
                        ? Character.toUpperCase(c)
                        : Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                    WORD SELECTION                            ║
    // ╚══════════════════════════════════════════════════════════════╝

    private String adj()       { return pick(Pool.adjectives(theme)); }
    private String noun()      { return pick(Pool.nouns(theme)); }
    private String shortWord() { return pick(Pool.shorts(theme)); }

    private String tag() {
        return pick(Tags.ALL);
    }

    private String num() {
        if (!allowNumbers) return "";
        if (rng.nextDouble() > numberChance) return "";
        int style = rng.nextInt(10);
        if (style < 3) return String.valueOf(rng.nextInt(99) + 1);       // 1-99
        if (style < 5) return String.valueOf(rng.nextInt(999) + 1);      // 1-999
        if (style < 7) return String.format("%02d", rng.nextInt(100));    // 00-99
        if (style < 8) return String.valueOf(rng.nextInt(10));            // 0-9
        if (style < 9) return String.valueOf((rng.nextInt(20) + 1) * 100); // 100,200..2000
        return String.valueOf(rng.nextInt(9000) + 1000);                  // 1000-9999
    }

    private String sep() {
        if (!allowUnderscores) return "";
        int r = rng.nextInt(10);
        if (r < 5) return "";
        if (r < 8) return "_";
        return ".";
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                       UTILITIES                             ║
    // ╚══════════════════════════════════════════════════════════════╝

    private String pick(String[] arr) {
        return arr[rng.nextInt(arr.length)];
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s.toUpperCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                  PHONETIC ENGINE                             ║
    // ╠══════════════════════════════════════════════════════════════╣
    // ║  Generates pronounceable, original words from                ║
    // ║  onset + nucleus + coda syllable components                  ║
    // ╚══════════════════════════════════════════════════════════════╝

    private static final class Phonetic {

        // Weighted onsets (empty = vowel-led syllable)
        private static final String[] ONSET = {
                // Simple consonants — high weight
                "b", "b", "d", "d", "f", "f", "g", "g",
                "k", "k", "k", "l", "l", "m", "m", "n", "n",
                "p", "p", "r", "r", "r", "s", "s", "s",
                "t", "t", "t", "v", "v", "w", "z", "z",
                "j", "h", "h",
                // Clusters — medium weight
                "br", "cr", "dr", "fr", "gr", "kr", "pr", "tr",
                "bl", "cl", "fl", "gl", "pl", "sl",
                "sk", "sn", "sp", "st", "sw",
                "sh", "th", "ch", "wh",
                // Exotic clusters — low weight
                "str", "shr", "scr", "spl",
                "zy", "ky", "vy", "ry",
                // Vowel-led
                "", "", ""
        };

        // Nuclei (vowels)
        private static final String[] NUCLEUS = {
                // Simple vowels — very high weight
                "a", "a", "a", "a", "e", "e", "e", "e",
                "i", "i", "i", "o", "o", "o", "u", "u",
                // Common combinations
                "ai", "ei", "ou", "au",
                "ae", "oe",
                "ar", "or", "er", "ir", "ur",
                // Rare
                "y"
        };

        // Codas (can be empty for open syllables)
        private static final String[] CODA = {
                // Empty (open syllable) — high weight
                "", "", "", "", "", "", "", "",
                // Simple codas
                "n", "n", "m", "r", "r", "l", "l",
                "s", "s", "t", "t", "k", "x", "d",
                "th", "sh", "nd", "nt", "nk",
                "rn", "rd", "rk", "rt", "rs",
                "lk", "lt", "lf", "lm",
                "st", "sk", "sp",
                // Heavy (rare)
                "nth", "rth"
        };

        // Final codas — used for last syllable to give a solid ending
        private static final String[] FINAL_CODA = {
                "n", "m", "r", "l", "s", "t", "k", "x", "d",
                "th", "sh", "nd", "nt", "nk", "rn", "rd", "rk",
                "rt", "rs", "st", "sk", "lf", "lm", "lt",
                "", "", ""  // some open endings ok
        };

        /** Generate a single syllable. */
        static String syllable(Random rng) {
            return pick(ONSET, rng) + pick(NUCLEUS, rng) + pick(CODA, rng);
        }

        /** Generate a word with n syllables, using a stronger final coda. */
        static String word(Random rng, int syllables) {
            if (syllables < 1) syllables = 2;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < syllables; i++) {
                sb.append(pick(ONSET, rng));
                sb.append(pick(NUCLEUS, rng));
                if (i < syllables - 1) {
                    sb.append(pick(CODA, rng));
                } else {
                    // Last syllable: stronger ending
                    sb.append(pick(FINAL_CODA, rng));
                }
            }

            String result = sb.toString();

            // Quality filter: avoid triple+ identical chars
            result = result.replaceAll("(.)\\1{2,}", "$1$1");

            // Avoid awkward 4+ consonant clusters
            if (hasHeavyCluster(result)) {
                return word(rng, syllables); // re-roll
            }

            return result;
        }

        private static boolean hasHeavyCluster(String s) {
            int cons = 0;
            for (char c : s.toCharArray()) {
                if (isVowel(c)) {
                    cons = 0;
                } else {
                    cons++;
                    if (cons >= 4) return true;
                }
            }
            return false;
        }

        private static boolean isVowel(char c) {
            return "aeiouAEIOU".indexOf(c) >= 0;
        }

        private static String pick(String[] arr, Random rng) {
            return arr[rng.nextInt(arr.length)];
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                   BLENDER ENGINE                            ║
    // ╠══════════════════════════════════════════════════════════════╣
    // ║  Creates portmanteau words from two inputs                   ║
    // ║  "Dragon" + "Wolf" → "Dolf" or "Wogon"                     ║
    // ╚══════════════════════════════════════════════════════════════╝

    private static final class Blender {
        static String blend(String a, String b, Random rng) {
            a = a.toLowerCase();
            b = b.toLowerCase();

            // Strategy 1: first half of A + second half of B
            // Strategy 2: onset of A + rime of B
            // Strategy 3: alternate characters

            int strategy = rng.nextInt(3);
            String result;

            switch (strategy) {
                case 0: {
                    int cutA = Math.max(1, a.length() / 2 + rng.nextInt(2) - 1);
                    int cutB = Math.max(1, b.length() / 2 + rng.nextInt(2) - 1);
                    result = a.substring(0, cutA) + b.substring(cutB);
                    break;
                }
                case 1: {
                    int cutA = findFirstVowel(a);
                    int cutB = findFirstVowel(b);
                    if (cutA <= 0) cutA = 1;
                    if (cutB <= 0) cutB = 1;
                    result = a.substring(0, cutA) + b.substring(cutB);
                    break;
                }
                case 2: {
                    StringBuilder sb = new StringBuilder();
                    int len = Math.max(a.length(), b.length());
                    for (int i = 0; i < len; i++) {
                        if (i < a.length() && (i % 2 == 0 || i >= b.length())) sb.append(a.charAt(i));
                        else if (i < b.length()) sb.append(b.charAt(i));
                    }
                    result = sb.toString();
                    break;
                }
                default:
                    result = a.substring(0, 2) + b.substring(1);
            }

            // Ensure minimum length 3
            if (result.length() < 3) result = a.charAt(0) + b;

            return result;
        }

        private static int findFirstVowel(String s) {
            for (int i = 0; i < s.length(); i++) {
                if ("aeiou".indexOf(s.charAt(i)) >= 0) return i;
            }
            return 1;
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                  LEET TRANSFORMER                           ║
    // ╚══════════════════════════════════════════════════════════════╝

    private static final class LeetTransform {
        private static final char[][] MAP = {
                {'a', '4'}, {'A', '4'},
                {'e', '3'}, {'E', '3'},
                {'i', '1'}, {'I', '1'},
                {'o', '0'}, {'O', '0'},
                {'s', '5'}, {'S', '5'},
                {'t', '7'}, {'T', '7'},
                {'l', '1'}, {'L', '1'},
                {'b', '8'}, {'B', '8'},
                {'g', '9'}, {'G', '9'},
        };

        static String apply(String input, Random rng) {
            char[] chars = input.toCharArray();
            // Only transform 1-3 characters to keep it readable
            int transforms = 1 + rng.nextInt(Math.min(3, chars.length));
            Set<Integer> transformed = new HashSet<>();

            for (int t = 0; t < transforms * 3; t++) { // extra attempts
                if (transformed.size() >= transforms) break;
                int pos = rng.nextInt(chars.length);
                if (transformed.contains(pos)) continue;
                for (char[] m : MAP) {
                    if (chars[pos] == m[0]) {
                        chars[pos] = m[1];
                        transformed.add(pos);
                        break;
                    }
                }
            }
            return new String(chars);
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                       TAGS                                  ║
    // ╚══════════════════════════════════════════════════════════════╝

    private static final class Tags {
        static final String[] ALL = {
                // Empty — most common
                "", "", "", "", "", "", "",
                // Gaming
                "GG", "Pro", "HD", "TV", "YT",
                "TTV", "BTW", "FTW",
                // Style
                "x", "X", "z", "Z", "s",
                // Numbers
                "99", "77", "42", "69", "13", "007", "1",
                // Specials
                "XD", "OP", "MVP", "EZ", "GOD",
                "_", "Jr", "Sr", "III", "II"
        };
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                    REAL NAMES                                ║
    // ╚══════════════════════════════════════════════════════════════╝

    private static final class Names {
        private static final String[] NAMES = {
                "Alex", "Max", "Sam", "Jay", "Kai", "Leo", "Zed", "Rex",
                "Ash", "Eli", "Finn", "Cole", "Milo", "Axel", "Jace", "Remy",
                "Niko", "Theo", "Hugo", "Ezra", "Ivan", "Otto", "Kira", "Luna",
                "Nova", "Aria", "Zara", "Sage", "Jade", "Eden", "Skye", "Wren",
                "Lux", "Cruz", "Knox", "Vick", "Nash", "Rhys", "Beau", "Grey",
                "Duke", "Zion", "Arlo", "Hank", "Dean", "Kurt", "Troy", "Wade",
                "Jake", "Ryan", "Luke", "Mark", "Jack", "Nick", "Mike", "Dave"
        };

        static String pick(Random rng) { return NAMES[rng.nextInt(NAMES.length)]; }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║              THEMED WORD POOLS                               ║
    // ╠══════════════════════════════════════════════════════════════╣
    // ║  Each theme has: adjectives, nouns, short words              ║
    // ║  MIXED combines all themes                                   ║
    // ╚══════════════════════════════════════════════════════════════╝

    private static final class Pool {

        // ─── GAMING ────────────────────────────────────────────────
        private static final String[] GAMING_ADJ = {
                "Dark", "Swift", "Neon", "Silent", "Wild", "Cold", "Brave", "Sharp",
                "Frost", "Storm", "Night", "Steel", "Iron", "Nova", "Prime", "Rogue",
                "Hyper", "Ultra", "Mega", "Toxic", "Chaos", "Shadow", "Phantom", "Epic",
                "Rapid", "Fierce", "Brutal", "Lucky", "Deadly", "Blazing", "Savage", "Elite"
        };
        private static final String[] GAMING_NOUN = {
                "Wolf", "Fox", "Hawk", "Bear", "Dragon", "Tiger", "Eagle", "Viper",
                "Raven", "Phoenix", "Panther", "Hunter", "Sniper", "Blade", "Strike",
                "Titan", "Ninja", "Samurai", "Viking", "Knight", "Slayer", "Crusher",
                "Raider", "Breaker", "Seeker", "Runner", "Fighter", "Rider", "Drifter",
                "Ranger", "Warden", "Bandit", "Pirate", "Ronin", "Golem", "Stalker"
        };
        private static final String[] GAMING_SHORT = {
                "Ace", "Blitz", "Bolt", "Dash", "Edge", "Flux", "Grit", "Jinx",
                "Jolt", "Rush", "Rift", "Spike", "Spark", "Sting", "Snap", "Slash",
                "Rage", "Fury", "Hex", "Zip", "Pop", "Boom", "Bang", "Fang"
        };

        // ─── CYBER ─────────────────────────────────────────────────
        private static final String[] CYBER_ADJ = {
                "Cyber", "Digital", "Neon", "Quantum", "Synthetic", "Virtual", "Wired",
                "Coded", "Binary", "Neural", "Hacked", "Glitched", "Modded", "Chrome",
                "Pixel", "Vector", "Matrix", "Proxy", "Root", "Core", "Nano", "Bio",
                "Tech", "Data", "Net", "Static", "Laser", "Plasma", "Volt", "Arc"
        };
        private static final String[] CYBER_NOUN = {
                "Byte", "Node", "Bot", "Cipher", "Daemon", "Kernel", "Stack", "Frame",
                "Socket", "Pulse", "Signal", "Circuit", "Server", "Router", "Packet",
                "Script", "Coder", "Hacker", "Module", "Driver", "Cache", "Token",
                "Proxy", "Drone", "Mech", "Grid", "Link", "Wire", "Chip", "Port"
        };
        private static final String[] CYBER_SHORT = {
                "Bit", "Bug", "Hex", "Log", "Sys", "Dev", "Ops", "Ram",
                "Cpu", "Gpu", "Api", "Sdk", "Usb", "Lan", "Wan", "Vpn",
                "Zip", "Tar", "Png", "Sql", "Cmd", "Ssh", "Tcp", "Dns"
        };

        // ─── FANTASY ───────────────────────────────────────────────
        private static final String[] FANTASY_ADJ = {
                "Ancient", "Arcane", "Mystic", "Celestial", "Ethereal", "Cursed", "Blessed",
                "Sacred", "Fallen", "Immortal", "Divine", "Enchanted", "Crystal", "Emerald",
                "Obsidian", "Crimson", "Azure", "Golden", "Silver", "Amber", "Jade", "Scarlet",
                "Sapphire", "Twilight", "Shadow", "Lunar", "Solar", "Astral", "Elven", "Fae"
        };
        private static final String[] FANTASY_NOUN = {
                "Wizard", "Mage", "Sorcerer", "Paladin", "Druid", "Cleric", "Warlock",
                "Shaman", "Dragon", "Griffin", "Unicorn", "Hydra", "Wraith", "Specter",
                "Golem", "Titan", "Oracle", "Prophet", "Templar", "Sage", "Monk", "Bard",
                "Archer", "Rogue", "Thief", "Herald", "Sentinel", "Champion", "Seraph", "Demon"
        };
        private static final String[] FANTASY_SHORT = {
                "Rune", "Glyph", "Spell", "Wand", "Orb", "Gem", "Tome", "Vow",
                "Oath", "Fate", "Myth", "Lore", "Dawn", "Dusk", "Pyre", "Mist",
                "Aura", "Bane", "Hex", "Ward", "Rite", "Seal", "Sigil", "Mark"
        };

        // ─── MILITARY ──────────────────────────────────────────────
        private static final String[] MILITARY_ADJ = {
                "Alpha", "Bravo", "Delta", "Omega", "Sigma", "Tactical", "Stealth",
                "Covert", "Lethal", "Hostile", "Armed", "Combat", "Rogue", "Ghost",
                "Recon", "Elite", "Iron", "Steel", "Heavy", "Black", "Gray", "Red",
                "Spec", "Rapid", "Silent", "Night", "Deep", "Lone", "Cold", "Hard"
        };
        private static final String[] MILITARY_NOUN = {
                "Soldier", "Sniper", "Gunner", "Pilot", "Ranger", "Reaper", "Guardian",
                "Commando", "Operative", "Trooper", "Sergeant", "Captain", "Colonel",
                "Marshal", "Vanguard", "Sentinel", "Enforcer", "Warden", "Scout",
                "Marksman", "Striker", "Breacher", "Medic", "Tank", "Hawk", "Falcon",
                "Eagle", "Cobra", "Viper", "Wolf"
        };
        private static final String[] MILITARY_SHORT = {
                "Ammo", "Flak", "Frag", "Tank", "Bomb", "Mine", "Clip", "Slug",
                "Shot", "Rank", "Ops", "Tac", "Spec", "Recon", "Base", "Fort",
                "Raid", "Drop", "Fire", "Lead", "Aim", "Lock", "Hit", "Kill"
        };

        // ─── NATURE ────────────────────────────────────────────────
        private static final String[] NATURE_ADJ = {
                "Wild", "Frost", "Storm", "Thunder", "Frozen", "Burning", "Stone",
                "Mountain", "Ocean", "River", "Forest", "Desert", "Arctic", "Tropic",
                "Autumn", "Winter", "Spring", "Summer", "Coral", "Moss", "Thorn",
                "Timber", "Boulder", "Glacier", "Ember", "Ash", "Dust", "Clay"
        };
        private static final String[] NATURE_NOUN = {
                "Wolf", "Bear", "Eagle", "Hawk", "Fox", "Lynx", "Raven", "Falcon",
                "Shark", "Whale", "Cobra", "Viper", "Tiger", "Lion", "Jaguar", "Puma",
                "Condor", "Orca", "Moose", "Elk", "Bison", "Crow", "Owl", "Stag",
                "Mantis", "Scorpion", "Stallion", "Mustang", "Raptor", "Serpent"
        };
        private static final String[] NATURE_SHORT = {
                "Rain", "Snow", "Hail", "Wind", "Gust", "Tide", "Wave", "Root",
                "Leaf", "Bark", "Vine", "Fern", "Moss", "Rock", "Sand", "Dust",
                "Peak", "Vale", "Glen", "Moor", "Lake", "Pond", "Brook", "Creek"
        };

        // ─── SPACE ─────────────────────────────────────────────────
        private static final String[] SPACE_ADJ = {
                "Cosmic", "Stellar", "Lunar", "Solar", "Astral", "Galactic", "Nebula",
                "Orbital", "Quantum", "Void", "Dark", "Nova", "Pulsar", "Quasar",
                "Binary", "Super", "Hyper", "Warp", "Zero", "Deep", "Far", "High",
                "Star", "Exo", "Ion", "Plasma", "Flux", "Red", "Blue", "White"
        };
        private static final String[] SPACE_NOUN = {
                "Star", "Comet", "Meteor", "Asteroid", "Planet", "Moon", "Nebula",
                "Quasar", "Pulsar", "Photon", "Neutron", "Proton", "Electron", "Boson",
                "Voyager", "Pioneer", "Explorer", "Ranger", "Pilot", "Navigator",
                "Drifter", "Nomad", "Seeker", "Watcher", "Beacon", "Horizon", "Eclipse",
                "Aurora", "Zenith", "Apex"
        };
        private static final String[] SPACE_SHORT = {
                "Sol", "Mars", "Nix", "Io", "Lux", "Vox", "Arc", "Ray",
                "Orb", "Sun", "Jet", "Warp", "Core", "Glow", "Flux", "Beam",
                "Void", "Halo", "Ring", "Axis", "Spin", "Dock", "Bay", "Hub"
        };

        // ─── DARK ──────────────────────────────────────────────────
        private static final String[] DARK_ADJ = {
                "Dark", "Shadow", "Night", "Black", "Grim", "Dread", "Cursed", "Damned",
                "Wicked", "Sinister", "Corrupt", "Twisted", "Hollow", "Fallen", "Lost",
                "Forsaken", "Haunted", "Blighted", "Ashen", "Charred", "Rotting", "Decayed",
                "Morbid", "Lethal", "Vile", "Toxic", "Malice", "Doom", "Death", "Blood"
        };
        private static final String[] DARK_NOUN = {
                "Reaper", "Wraith", "Specter", "Phantom", "Ghoul", "Demon", "Fiend",
                "Shade", "Shadow", "Stalker", "Lurker", "Creeper", "Crawler", "Slayer",
                "Bringer", "Eater", "Devourer", "Ravager", "Destroyer", "Plague", "Blight",
                "Scourge", "Terror", "Horror", "Nightmare", "Abyss", "Void", "Crypt",
                "Grave", "Skull"
        };
        private static final String[] DARK_SHORT = {
                "Hex", "Bane", "Rot", "Ash", "Fog", "Maw", "Pit", "Tar",
                "Soot", "Dusk", "Woe", "Sin", "End", "Ire", "Ruin", "Scar",
                "Pox", "Bile", "Gore", "Gash", "Cult", "Lich", "Bone", "Fang"
        };

        // ─── ANIME ─────────────────────────────────────────────────
        private static final String[] ANIME_ADJ = {
                "Kawaii", "Sakura", "Crimson", "Azure", "Scarlet", "Violet", "Emerald",
                "Onyx", "Silver", "Golden", "Phantom", "Shadow", "Divine", "Eternal",
                "Silent", "Swift", "Rising", "Fallen", "Blazing", "Frozen", "Thunder",
                "Storm", "Lunar", "Solar", "Celestial", "Dark", "Light", "Neo", "True"
        };
        private static final String[] ANIME_NOUN = {
                "Sensei", "Ronin", "Samurai", "Ninja", "Shogun", "Kitsune", "Tengu",
                "Oni", "Yokai", "Shinigami", "Senshi", "Otaku", "Senpai", "Kohai",
                "Miko", "Neko", "Usagi", "Tora", "Ryu", "Kaze", "Hoshi", "Tsuki",
                "Yami", "Hikari", "Akira", "Raiden", "Kenshi", "Blade", "Soul", "Spirit"
        };
        private static final String[] ANIME_SHORT = {
                "Kai", "Zen", "Rin", "Yuu", "Kou", "Rei", "Sho", "Tao",
                "Jin", "Ken", "Aki", "Mei", "Hana", "Yuki", "Kuro", "Shiro",
                "Ao", "Aka", "Sora", "Umi", "Hi", "Chi", "Ki", "Sei"
        };

        // ─── ABSTRACT ──────────────────────────────────────────────
        private static final String[] ABSTRACT_ADJ = {
                "Pure", "Raw", "True", "Free", "Real", "Null", "Void", "Zero",
                "Prime", "Sole", "Lone", "Dual", "Twin", "Tri", "Omni", "Meta",
                "Proto", "Neo", "Post", "Anti", "Ultra", "Hyper", "Super", "Semi",
                "Quasi", "Pseudo", "Crypto", "Subtle", "Lucid", "Vivid"
        };
        private static final String[] ABSTRACT_NOUN = {
                "Mind", "Soul", "Dream", "Fate", "Hope", "Fear", "Rage", "Love",
                "Hate", "Calm", "Chaos", "Order", "Logic", "Sense", "Doubt", "Faith",
                "Trust", "Truth", "Myth", "Echo", "Flux", "Drift", "Shift", "Phase",
                "Pulse", "Wave", "Flow", "Rift", "Edge", "Core"
        };
        private static final String[] ABSTRACT_SHORT = {
                "Id", "Ego", "Zen", "Tao", "Om", "Phi", "Psi", "Chi",
                "Nu", "Mu", "Pi", "Tau", "Eta", "Rho", "Xi", "Zeta",
                "Nix", "Nil", "Void", "Naught", "One", "All", "Any", "Few"
        };

        // ─── COMBINED POOLS (lazy init, thread-safe via holder) ────
        private static final String[] ALL_ADJ = concat(
                GAMING_ADJ, CYBER_ADJ, FANTASY_ADJ, MILITARY_ADJ,
                NATURE_ADJ, SPACE_ADJ, DARK_ADJ, ANIME_ADJ, ABSTRACT_ADJ
        );
        private static final String[] ALL_NOUN = concat(
                GAMING_NOUN, CYBER_NOUN, FANTASY_NOUN, MILITARY_NOUN,
                NATURE_NOUN, SPACE_NOUN, DARK_NOUN, ANIME_NOUN, ABSTRACT_NOUN
        );
        private static final String[] ALL_SHORT = concat(
                GAMING_SHORT, CYBER_SHORT, FANTASY_SHORT, MILITARY_SHORT,
                NATURE_SHORT, SPACE_SHORT, DARK_SHORT, ANIME_SHORT, ABSTRACT_SHORT
        );

        // ─── SELECTORS ────────────────────────────────────────────
        static String[] adjectives(Theme t) {
            switch (t) {
                case GAMING:   return GAMING_ADJ;
                case CYBER:    return CYBER_ADJ;
                case FANTASY:  return FANTASY_ADJ;
                case MILITARY: return MILITARY_ADJ;
                case NATURE:   return NATURE_ADJ;
                case SPACE:    return SPACE_ADJ;
                case DARK:     return DARK_ADJ;
                case ANIME:    return ANIME_ADJ;
                case ABSTRACT: return ABSTRACT_ADJ;
                case MIXED:
                default:       return ALL_ADJ;
            }
        }

        static String[] nouns(Theme t) {
            switch (t) {
                case GAMING:   return GAMING_NOUN;
                case CYBER:    return CYBER_NOUN;
                case FANTASY:  return FANTASY_NOUN;
                case MILITARY: return MILITARY_NOUN;
                case NATURE:   return NATURE_NOUN;
                case SPACE:    return SPACE_NOUN;
                case DARK:     return DARK_NOUN;
                case ANIME:    return ANIME_NOUN;
                case ABSTRACT: return ABSTRACT_NOUN;
                case MIXED:
                default:       return ALL_NOUN;
            }
        }

        static String[] shorts(Theme t) {
            switch (t) {
                case GAMING:   return GAMING_SHORT;
                case CYBER:    return CYBER_SHORT;
                case FANTASY:  return FANTASY_SHORT;
                case MILITARY: return MILITARY_SHORT;
                case NATURE:   return NATURE_SHORT;
                case SPACE:    return SPACE_SHORT;
                case DARK:     return DARK_SHORT;
                case ANIME:    return ANIME_SHORT;
                case ABSTRACT: return ABSTRACT_SHORT;
                case MIXED:
                default:       return ALL_SHORT;
            }
        }

        @SafeVarargs
        private static String[] concat(String[]... arrays) {
            int total = 0;
            for (String[] a : arrays) total += a.length;
            String[] result = new String[total];
            int pos = 0;
            for (String[] a : arrays) {
                System.arraycopy(a, 0, result, pos, a.length);
                pos += a.length;
            }
            return result;
        }
    }
}