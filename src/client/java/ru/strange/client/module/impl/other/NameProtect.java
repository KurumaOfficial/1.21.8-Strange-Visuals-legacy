package ru.strange.client.module.impl.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.client.network.PlayerListEntry;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.StringSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@IModule(
        name = "Нейм Протект",
        description = "Скрывает ваш ник в чате и интерфейсе",
        category = Category.Other,
        bind = -1
)
public class NameProtect extends Module {
    private static final String NAME_BOUNDARY = "[\\p{IsAlphabetic}\\p{IsDigit}_]";
    private static final String DECORATED_GAP_PATTERN = "(?:\\u00A7.|&.|[\\u200B-\\u200F\\uFEFF])*";
    private static final ThreadLocal<Integer> ALIAS_COLLECTION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private static volatile NameProtect instance;

    // --- Caching for protected names and compiled patterns ---
    private static volatile Set<String> cachedProtectedNames;
    private static volatile Map<String, Pattern[]> cachedPatterns;
    private static volatile long cacheTimestamp = 0L;
    private static final long CACHE_TTL_MS = 500L;

    public final StringSetting fakeName = new StringSetting("Замена ника", "");
    public final BooleanSetting hideSkin = new BooleanSetting("Скрывать в Tab", true);

    public NameProtect() {
        instance = this;
        addSettings(fakeName, hideSkin);
    }

    public static NameProtect getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.enable;
    }

    public static boolean isCollectingAliases() {
        return ALIAS_COLLECTION_DEPTH.get() > 0;
    }

    public String getFakeName() {
        String name = fakeName.input;
        if (name == null || name.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSession() != null) {
                String username = mc.getSession().getUsername();
                if (username != null && !username.isBlank()) return username;
            }
            return "Player";
        }
        return name;
    }

    public static Set<String> getProtectedNames() {
        long now = System.currentTimeMillis();
        Set<String> cached = cachedProtectedNames;
        if (cached != null && now - cacheTimestamp < CACHE_TTL_MS) {
            return cached;
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (instance == null || instance.mc == null) {
            cachedProtectedNames = names;
            cacheTimestamp = now;
            return names;
        }

        withAliasCollection(() -> {
            if (instance.mc.getSession() != null) {
                addProtectedName(names, instance.mc.getSession().getUsername());
            }

            if (instance.mc.player != null) {
                addProtectedName(names, instance.mc.player.getGameProfile().getName());
                addProtectedName(names, instance.mc.player.getNameForScoreboard());
                addProtectedName(names, instance.mc.player.getName().getString());
                addProtectedName(names, instance.mc.player.getDisplayName().getString());

                if (instance.mc.getNetworkHandler() != null) {
                    PlayerListEntry ownEntry = instance.mc.getNetworkHandler().getPlayerListEntry(instance.mc.player.getUuid());
                    if (ownEntry != null) {
                        addProtectedName(names, ownEntry.getProfile().getName());
                        Text tabDisplayName = ownEntry.getDisplayName();
                        if (tabDisplayName != null) {
                            addProtectedName(names, tabDisplayName.getString());
                        }
                    }
                }

                collectScoreboardAliases(names);
            }

            return null;
        });

        cachedProtectedNames = names;
        cacheTimestamp = now;
        rebuildPatternCache(names);
        return names;
    }

    private static void rebuildPatternCache(Set<String> names) {
        Map<String, Pattern[]> patterns = new HashMap<>();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            patterns.put(name, new Pattern[]{
                buildVisibleNamePattern(name),
                buildDecoratedNamePattern(name)
            });
        }
        cachedPatterns = patterns;
    }

    private static Pattern[] getCachedPatterns(String name) {
        Map<String, Pattern[]> patterns = cachedPatterns;
        if (patterns != null) {
            Pattern[] cached = patterns.get(name);
            if (cached != null) return cached;
        }
        return new Pattern[]{buildVisibleNamePattern(name), buildDecoratedNamePattern(name)};
    }

    public static String process(String text) {
        if (!isActive() || isCollectingAliases() || text == null || text.isEmpty()) return text;
        Set<String> protectedNames = getProtectedNames();
        if (protectedNames.isEmpty()) return text;
        String replacement = getReplacementName();

        String result = text;
        for (String realName : protectedNames) {
            result = replaceProtectedName(result, realName, replacement);
        }
        return result;
    }

    public static Text process(Text text) {
        if (!isActive() || isCollectingAliases() || text == null) return text;

        StyledTextData data = extractStyledText(text);
        if (data.text().isEmpty()) {
            return text;
        }

        MutableText rebuilt = rebuildStyledText(data.text(), data.styles());
        return rebuilt == null ? text : rebuilt;
    }

    public static StringVisitable process(StringVisitable text) {
        if (!isActive() || isCollectingAliases() || text == null) return text;
        if (text instanceof Text regularText) {
            return process(regularText);
        }

        StyledTextData data = extractStyledText(text);
        if (data.text().isEmpty()) {
            return text;
        }

        MutableText rebuilt = rebuildStyledText(data.text(), data.styles());
        return rebuilt == null ? text : rebuilt;
    }

    public static OrderedText process(OrderedText text) {
        if (!isActive() || isCollectingAliases() || text == null) return text;

        StyledTextData data = extractStyledText(text);
        if (data.text().isEmpty()) {
            return text;
        }

        MutableText rebuilt = rebuildStyledText(data.text(), data.styles());
        return rebuilt == null ? text : rebuilt.asOrderedText();
    }

    private static String getReplacementName() {
        String replacement = instance == null ? null : instance.fakeName.input;
        return (replacement == null || replacement.isEmpty()) ? "Protect" : replacement;
    }

    private static void addProtectedName(Set<String> names, String candidate) {
        if (candidate == null) {
            return;
        }

        String trimmed = candidate.trim();
        if (!trimmed.isEmpty()) {
            names.add(trimmed);
        }
    }

    private static <T> T withAliasCollection(java.util.function.Supplier<T> action) {
        ALIAS_COLLECTION_DEPTH.set(ALIAS_COLLECTION_DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            ALIAS_COLLECTION_DEPTH.set(Math.max(0, ALIAS_COLLECTION_DEPTH.get() - 1));
        }
    }

    private static void collectScoreboardAliases(Set<String> names) {
        if (instance == null || instance.mc == null || instance.mc.world == null) {
            return;
        }

        Object scoreboard = instance.mc.world.getScoreboard();
        if (scoreboard == null) {
            return;
        }

        ArrayList<String> bases = new ArrayList<>(names);
        for (String base : bases) {
            Text decorated = decorateScoreboardName(scoreboard, base);
            if (decorated != null) {
                addProtectedName(names, decorated.getString());
            }
        }
    }

    private static Text decorateScoreboardName(Object scoreboard, String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return null;
        }

        Object team = lookupScoreboardTeam(scoreboard, baseName);
        if (team == null) {
            return null;
        }

        return invokeTeamDecorateName(team, Text.literal(baseName));
    }

    private static Object lookupScoreboardTeam(Object scoreboard, String scoreHolder) {
        Object team = invokeStringMethod(scoreboard, "getScoreHolderTeam", scoreHolder);
        if (team != null) {
            return team;
        }
        return invokeStringMethod(scoreboard, "getPlayerTeam", scoreHolder);
    }

    private static Object invokeStringMethod(Object target, String methodName, String argument) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class);
            return method.invoke(target, argument);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Text invokeTeamDecorateName(Object team, Text baseName) {
        for (String ownerName : List.of("net.minecraft.scoreboard.Team", "net.minecraft.scoreboard.AbstractTeam")) {
            try {
                Class<?> owner = Class.forName(ownerName);
                for (Method method : owner.getMethods()) {
                    if (!Modifier.isStatic(method.getModifiers())
                            || !"decorateName".equals(method.getName())
                            || method.getParameterCount() != 2
                            || !Text.class.isAssignableFrom(method.getReturnType())) {
                        continue;
                    }

                    Class<?>[] parameters = method.getParameterTypes();
                    if (!parameters[0].isInstance(team) || !parameters[1].isAssignableFrom(Text.class)) {
                        continue;
                    }

                    Object result = method.invoke(null, team, baseName);
                    if (result instanceof Text decorated) {
                        return decorated;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private static String replaceProtectedName(String input, String realName, String replacement) {
        if (input == null || input.isEmpty() || realName == null || realName.isBlank() || realName.equals(replacement)) {
            return input;
        }

        Pattern[] patterns = getCachedPatterns(realName);

        String result = patterns[0].matcher(input).replaceAll(Matcher.quoteReplacement(replacement));
        return patterns[1].matcher(result).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        int hLen = haystack.length();
        int nLen = needle.length();
        if (nLen > hLen) return false;
        for (int i = 0; i <= hLen - nLen; i++) {
            if (haystack.regionMatches(true, i, needle, 0, nLen)) {
                return true;
            }
        }
        return false;
    }

    private static Pattern buildVisibleNamePattern(String name) {
        return Pattern.compile(
                "(?iu)(?<!" + NAME_BOUNDARY + ")" + Pattern.quote(name) + "(?!" + NAME_BOUNDARY + ")"
        );
    }

    private static Pattern buildDecoratedNamePattern(String name) {
        StringBuilder pattern = new StringBuilder("(?iu)(?<!").append(NAME_BOUNDARY).append(")");
        for (int index = 0; index < name.length(); index++) {
            pattern.append(Pattern.quote(String.valueOf(name.charAt(index))));
            if (index + 1 < name.length()) {
                pattern.append(DECORATED_GAP_PATTERN);
            }
        }
        pattern.append("(?!").append(NAME_BOUNDARY).append(")");
        return Pattern.compile(pattern.toString());
    }

    private static StyledTextData extractStyledText(Text text) {
        StringBuilder builder = new StringBuilder();
        ArrayList<Style> styles = new ArrayList<>();
        text.visit((Style style, String part) -> {
            if (part != null && !part.isEmpty()) {
                builder.append(part);
                for (int index = 0; index < part.length(); index++) {
                    styles.add(style);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new StyledTextData(builder.toString(), styles);
    }

    private static StyledTextData extractStyledText(StringVisitable text) {
        StringBuilder builder = new StringBuilder();
        ArrayList<Style> styles = new ArrayList<>();
        text.visit((Style style, String part) -> {
            if (part != null && !part.isEmpty()) {
                builder.append(part);
                for (int index = 0; index < part.length(); index++) {
                    styles.add(style);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new StyledTextData(builder.toString(), styles);
    }

    private static StyledTextData extractStyledText(OrderedText text) {
        StringBuilder builder = new StringBuilder();
        ArrayList<Style> styles = new ArrayList<>();
        text.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            int length = Character.charCount(codePoint);
            for (int offset = 0; offset < length; offset++) {
                styles.add(style);
            }
            return true;
        });
        return new StyledTextData(builder.toString(), styles);
    }

    private static MutableText rebuildStyledText(String original, List<Style> originalStyles) {
        ArrayList<Style> styles = new ArrayList<>(originalStyles);
        Set<String> protectedNames = getProtectedNames();
        if (protectedNames.isEmpty()) {
            return null;
        }

        String replacement = getReplacementName();
        String result = original;
        boolean changed = false;

        for (String realName : protectedNames) {
            if (realName == null || realName.isBlank() || realName.equals(replacement)) {
                continue;
            }

            Matcher matcher = getCachedPatterns(realName)[0].matcher(result);
            ArrayList<MatchRange> matches = new ArrayList<>();
            while (matcher.find()) {
                matches.add(new MatchRange(matcher.start(), matcher.end()));
            }

            for (int index = matches.size() - 1; index >= 0; index--) {
                MatchRange match = matches.get(index);
                int start = match.start();
                int end = match.end();
                if (start < 0 || end <= start || start >= styles.size()) {
                    continue;
                }

                Style replacementStyle = styles.get(start);
                result = result.substring(0, start) + replacement + result.substring(end);
                styles.subList(start, Math.min(end, styles.size())).clear();
                for (int offset = 0; offset < replacement.length(); offset++) {
                    styles.add(start + offset, replacementStyle);
                }
                changed = true;
            }
        }

        if (!changed || result.equals(original)) {
            return null;
        }

        MutableText rebuilt = Text.empty();
        int start = 0;
        while (start < result.length()) {
            Style style = start < styles.size() ? styles.get(start) : Style.EMPTY;
            int end = start + 1;
            while (end < result.length()) {
                Style nextStyle = end < styles.size() ? styles.get(end) : Style.EMPTY;
                if (!Objects.equals(style, nextStyle)) {
                    break;
                }
                end++;
            }

            rebuilt.append(Text.literal(result.substring(start, end)).setStyle(style));
            start = end;
        }

        return rebuilt;
    }

    private record StyledTextData(String text, ArrayList<Style> styles) {
    }

    private record MatchRange(int start, int end) {
    }
}
