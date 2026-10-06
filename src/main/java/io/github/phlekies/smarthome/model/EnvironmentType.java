package io.github.phlekies.smarthome.model;

public enum EnvironmentType {
    OPEN("Open space", 2.0),
    RESIDENTIAL("Residential", 2.5),
    OFFICE("Office", 3.0),
    WAREHOUSE("Warehouse", 3.5);

    private final String label;
    private final double defaultPathLossExponent;

    EnvironmentType(String label, double defaultPathLossExponent) {
        this.label = label;
        this.defaultPathLossExponent = defaultPathLossExponent;
    }

    public String getLabel() {
        return label;
    }

    public double getDefaultPathLossExponent() {
        return defaultPathLossExponent;
    }

    @Override
    public String toString() {
        return label;
    }
}
