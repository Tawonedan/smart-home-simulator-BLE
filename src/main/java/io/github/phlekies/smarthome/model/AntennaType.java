package io.github.phlekies.smarthome.model;

/** Radiation pattern family of a transmitter antenna. */
public enum AntennaType {
    /** Same gain in every horizontal direction. */
    OMNIDIRECTIONAL("Omnidirectional"),
    /** Main lobe around an orientation, attenuated side and back lobes. */
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
