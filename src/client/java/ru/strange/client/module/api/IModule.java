package ru.strange.client.module.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value = RetentionPolicy.RUNTIME)
public @interface IModule {
    String name();
    String description();
    Category category();
    int bind();
}