package io.github.phlekies.smarthome.simulation;

/** Small-scale fading applied to each path, generated deterministically per position. */
public enum FadingModel {
    NONE("No fading"),
    RAYLEIGH("Rayleigh"),
    RICIAN("Rician");

    private final String label;

    FadingModel(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
