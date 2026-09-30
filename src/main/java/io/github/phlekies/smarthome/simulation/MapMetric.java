package io.github.phlekies.smarthome.simulation;

/** Quantity displayed on the coverage heatmap. */
public enum MapMetric {
    POWER_DBM("Received power (dBm)"),
    SINR_DB("SINR (dB)"),
    BER("Bit error rate"),
    CAPACITY_MBPS("Capacity (Mbps)");

    private final String label;

    MapMetric(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
