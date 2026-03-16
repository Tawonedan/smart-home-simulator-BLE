package core.sim;

public enum MapMetric {
    POWER_DBM("Potencia (dBm)"),
    SNR_DB("SNR / SINR (dB)"),
    BER("BER"),
    CAPACITY_MBPS("Capacidad (Mbps)");

    private final String label;

    MapMetric(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
