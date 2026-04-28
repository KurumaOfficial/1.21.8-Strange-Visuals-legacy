package ru.strange.client.event.impl;

import net.minecraft.entity.Entity;
import ru.strange.client.event.Event;

public final class EventTotemPop extends Event {

    private final Entity entity;

    public EventTotemPop(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }
}
