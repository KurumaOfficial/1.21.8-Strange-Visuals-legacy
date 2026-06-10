package ru.strange.client.event;
public interface Cancellable {

    boolean isCancelled();
    void cancel();

}
