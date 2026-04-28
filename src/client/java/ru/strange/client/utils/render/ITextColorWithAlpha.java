package ru.strange.client.utils.render;

/**
 * Interface for TextColor that supports alpha channel.
 * Allows retrieving the full color value including alpha component.
 */
public interface ITextColorWithAlpha {
    /**
     * Gets the full color value including alpha channel.
     * @return The full ARGB color value
     */
    int strange$getFullColor();
}
