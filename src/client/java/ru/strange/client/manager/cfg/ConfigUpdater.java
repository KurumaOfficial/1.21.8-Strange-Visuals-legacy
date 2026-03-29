package ru.strange.client.manager.cfg;

import com.google.gson.JsonObject;

public interface ConfigUpdater {

    JsonObject save();

    void load(JsonObject object);

}