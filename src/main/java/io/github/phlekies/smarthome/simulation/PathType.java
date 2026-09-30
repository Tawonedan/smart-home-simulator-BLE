package io.github.phlekies.smarthome.simulation;

/** Propagation mechanism of a single path between a sensor and a receiver point. */
public enum PathType {
    DIRECT("Direct"),
    REFLECTION("Reflection"),
    DIFFRACTION("Diffraction"),
    SCATTERING("Scattering");

    private final String label;

    PathType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
