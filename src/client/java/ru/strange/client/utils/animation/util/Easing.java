package ru.strange.client.utils.animation.util;

@FunctionalInterface
public interface Easing {
    double ease(double value);
}