package ru.strange.client.rpc.callbacks;


import com.sun.jna.Callback;
import ru.strange.client.rpc.DiscordUser;

public interface JoinRequestCallback extends Callback {
    void apply(final DiscordUser p0);
}
