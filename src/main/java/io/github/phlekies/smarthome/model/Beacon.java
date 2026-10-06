package io.github.phlekies.smarthome.model;

import io.github.phlekies.smarthome.physics.RadioMath;

public class Beacon extends Sensor {

    public static final double DEFAULT_BLE_TX_POWER_DBM = -6.0;
    public static final String DEFAULT_UUID = "00000000-0000-0000-0000-000000000000";
    public static final String DEFAULT_MAC_ADDRESS = "00:00:00:00:00:00";
    public static final double DEFAULT_ADVERTISING_INTERVAL_MS = 100.0;
    public static final double DEFAULT_CALIBRATED_RSSI_AT_ONE_METER_DBM = -59.0;

    private String uuid = DEFAULT_UUID;
    private String macAddress = DEFAULT_MAC_ADDRESS;
    private double advertisingIntervalMs = DEFAULT_ADVERTISING_INTERVAL_MS;
    private double frequencyMHz = RadioMath.BLE_CENTER_FREQ_MHZ;
    private double calibratedRssiAtOneMeterDbm = DEFAULT_CALIBRATED_RSSI_AT_ONE_METER_DBM;

    public Beacon(String id, String name, int x, int y) {
        super(id, name, x, y, DEFAULT_BLE_TX_POWER_DBM);
    }

    @Override
    public Beacon copy() {
        Beacon copy = new Beacon(getId(), getName(), getX(), getY());
        copy.setTxPowerDbm(getTxPowerDbm());
        copy.setTxGainDb(getTxGainDb());
        copy.setAntennaType(getAntennaType());
        copy.setOrientationDeg(getOrientationDeg());
        copy.setBeamwidthDeg(getBeamwidthDeg());
        copy.setPatternSharpness(getPatternSharpness());
        copy.setSideLobeAttenuationDb(getSideLobeAttenuationDb());
        copy.setPolarizationDeg(getPolarizationDeg());
        copy.uuid = uuid;
        copy.macAddress = macAddress;
        copy.advertisingIntervalMs = advertisingIntervalMs;
        copy.frequencyMHz = frequencyMHz;
        copy.calibratedRssiAtOneMeterDbm = calibratedRssiAtOneMeterDbm;
        return copy;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = (uuid == null) ? DEFAULT_UUID : uuid;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = (macAddress == null) ? DEFAULT_MAC_ADDRESS : macAddress;
    }

    public double getAdvertisingIntervalMs() {
        return advertisingIntervalMs;
    }

    public void setAdvertisingIntervalMs(double advertisingIntervalMs) {
        this.advertisingIntervalMs = Math.max(20.0, Math.min(10240.0, advertisingIntervalMs));
    }

    public double getFrequencyMHz() {
        return frequencyMHz;
    }

    public void setFrequencyMHz(double frequencyMHz) {
        if (frequencyMHz <= 0) throw new IllegalArgumentException("frequencyMHz must be > 0");
        this.frequencyMHz = frequencyMHz;
    }

    public double getCalibratedRssiAtOneMeterDbm() {
        return calibratedRssiAtOneMeterDbm;
    }

    public void setCalibratedRssiAtOneMeterDbm(double calibratedRssiAtOneMeterDbm) {
        this.calibratedRssiAtOneMeterDbm = calibratedRssiAtOneMeterDbm;
    }

    @Override
    public String describe() {
        return String.format(java.util.Locale.US,
                "%s [BLE Beacon] at (%d, %d): Tx %.1f dBm, Adv %.0f ms, RSSI@1m %.0f dBm",
                getName(), getX(), getY(), getTxPowerDbm(), advertisingIntervalMs, calibratedRssiAtOneMeterDbm);
    }
}
