package ru.strange.client.StarterMenu;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AltAccount {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

    public final String name;
    public final String date;
    public final long createdAt;
    public boolean pinned;

    public AltAccount(String name, String date, long createdAt, boolean pinned) {
        this.name = name;
        this.date = date;
        this.createdAt = createdAt;
        this.pinned = pinned;
    }

    public static AltAccount create(String name) {
        return new AltAccount(name, LocalDateTime.now().format(DATE_FORMAT), System.currentTimeMillis(), false);
    }

    public AltAccount copy() {
        return new AltAccount(name, date, createdAt, pinned);
    }
}
