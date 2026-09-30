package io.github.phlekies.smarthome.simulation;

/** How the contributions of the different paths are combined at the receiver. */
public enum PropagationMode {
    /** Incoherent sum of path powers (no phase interference). */
    RAYS("Rays (incoherent)"),
    /** Coherent phasor sum of path fields (captures constructive/destructive interference). */
    WAVES("Waves (coherent)");

    private final String label;

    PropagationMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
