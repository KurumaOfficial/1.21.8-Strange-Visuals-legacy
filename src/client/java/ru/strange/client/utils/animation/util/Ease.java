package ru.strange.client.utils.animation.util;

public enum Ease {
    LINEAR(Easings.LINEAR),
    QUAD_OUT(Easings.QUAD_OUT),
    CUBIC_OUT(Easings.CUBIC_OUT),
    QUART_OUT(Easings.QUART_OUT),
    QUINT_OUT(Easings.QUINT_OUT),
    SINE_OUT(Easings.SINE_OUT),
    CIRC_OUT(Easings.CIRC_OUT),
    ELASTIC_OUT(Easings.ELASTIC_OUT),
    EXPO_OUT(Easings.EXPO_OUT),
    BACK_OUT(Easings.BACK_OUT),
    BOUNCE_OUT(Easings.BOUNCE_OUT);

    private final Easing easing;

    Ease(Easing easing) {
        this.easing = easing;
    }

    public Easing getEasing() {
        return easing;
    }

    @Override
    public String toString() {
        String name = name().toLowerCase();
        String[] words = name.split("_");
        StringBuilder formattedName = new StringBuilder();

        for (String word : words) {
            formattedName.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return formattedName.toString().trim();
    }

}