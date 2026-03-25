package ru.strange.client.StarterMenu;

import java.util.Comparator;
import java.util.Locale;

public enum AltSortMode {
    NEWEST("alt.sort.newest"),
    OLDEST("alt.sort.oldest"),
    AZ("alt.sort.az"),
    ZA("alt.sort.za");

    private final String labelKey;

    AltSortMode(String labelKey) {
        this.labelKey = labelKey;
    }

    public String label() {
        return MenuLocalization.tr(labelKey);
    }

    public Comparator<AltAccount> comparator() {
        return switch (this) {
            case OLDEST -> Comparator.comparingLong(account -> account.createdAt);
            case AZ -> Comparator.comparing(account -> account.name.toLowerCase(Locale.ROOT));
            case ZA -> (a, b) -> b.name.compareToIgnoreCase(a.name);
            case NEWEST -> (a, b) -> Long.compare(b.createdAt, a.createdAt);
        };
    }
}
