package io.github.phlekies.smarthome.model;

/** Radiation pattern family of a transmitter antenna. */
public enum AntennaType {
    OMNIDIRECTIONAL("Omnidirectional"),
    DIRECTIONAL("Directional");

    private final String label;

    AntennaType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
