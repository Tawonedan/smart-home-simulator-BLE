package io.github.phlekies.smarthome.model;

public class Scanner extends Hub {

    public static final double DEFAULT_RX_SENSITIVITY_DBM = -100.0;
    public static final double DEFAULT_RECEIVER_GAIN_DB = 1.0;
    public static final double DEFAULT_RSSI_THRESHOLD_DBM = -105.0;

    private double rxSensitivityDbm = DEFAULT_RX_SENSITIVITY_DBM;
    private double rssiThresholdDbm = DEFAULT_RSSI_THRESHOLD_DBM;

    public Scanner(String id, String name, int x, int y) {
        super(id, name, x, y, DEFAULT_RECEIVER_GAIN_DB);
    }

    @Override
    public Scanner copy() {
        Scanner copy = new Scanner(getId(), getName(), getX(), getY());
        copy.setReceiverGainDb(getReceiverGainDb());
        copy.setPolarizationDeg(getPolarizationDeg());
        copy.rxSensitivityDbm = rxSensitivityDbm;
        copy.rssiThresholdDbm = rssiThresholdDbm;
        return copy;
    }

    public double getRxSensitivityDbm() {
        return rxSensitivityDbm;
    }

    public void setRxSensitivityDbm(double rxSensitivityDbm) {
        this.rxSensitivityDbm = rxSensitivityDbm;
    }

    public double getRssiThresholdDbm() {
        return rssiThresholdDbm;
    }

    public void setRssiThresholdDbm(double rssiThresholdDbm) {
        this.rssiThresholdDbm = rssiThresholdDbm;
    }
}
