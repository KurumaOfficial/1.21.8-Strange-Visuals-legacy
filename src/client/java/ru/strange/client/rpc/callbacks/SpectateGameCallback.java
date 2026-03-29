package ru.strange.client.rpc.callbacks;

import com.sun.jna.Callback;

public interface SpectateGameCallback extends Callback {
    void apply(final String p0);
}
