package io.github.phlekies.smarthome.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.phlekies.smarthome.physics.RadioMath;

/** A transmitting IoT sensor with a configurable antenna. */
public class Sensor extends Device {

    public static final double DEFAULT_TX_POWER_DBM = 20.0;

    private double txPowerDbm = DEFAULT_TX_POWER_DBM;
    private double txGainDb = 0.0;
    private AntennaType antennaType = AntennaType.OMNIDIRECTIONAL;
    private double orientationDeg = 0.0;
    private double beamwidthDeg = 360.0;
    private double patternSharpness = 1.8;
    private double sideLobeAttenuationDb = 18.0;
    private double polarizationDeg = 0.0;

    public Sensor(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    /** Deep copy, used to hand an immutable snapshot to background computations. */
    public Sensor copy() {
        Sensor copy = new Sensor(getId(), getName(), getX(), getY());
        copy.txPowerDbm = txPowerDbm;
        copy.txGainDb = txGainDb;
        copy.antennaType = antennaType;
        copy.orientationDeg = orientationDeg;
        copy.beamwidthDeg = beamwidthDeg;
        copy.patternSharpness = patternSharpness;
        copy.sideLobeAttenuationDb = sideLobeAttenuationDb;
        copy.polarizationDeg = polarizationDeg;
        return copy;
    }

    public double getTxPowerDbm() {
        return txPowerDbm;
    }

    public void setTxPowerDbm(double txPowerDbm) {
        this.txPowerDbm = txPowerDbm;
    }

    public double getTxGainDb() {
        return txGainDb;
    }

    public void setTxGainDb(double txGainDb) {
        this.txGainDb = txGainDb;
    }

    public AntennaType getAntennaType() {
        return antennaType;
    }

    public void setAntennaType(AntennaType antennaType) {
        this.antennaType = (antennaType == null) ? AntennaType.OMNIDIRECTIONAL : antennaType;
    }

    public boolean isOmnidirectional() {
        return antennaType == AntennaType.OMNIDIRECTIONAL;
    }

    public boolean isDirectional() {
        return antennaType == AntennaType.DIRECTIONAL;
    }

    public double getOrientationDeg() {
        return orientationDeg;
    }

    public void setOrientationDeg(double orientationDeg) {
        this.orientationDeg = RadioMath.normalizeAngleDeg(orientationDeg);
    }

    public double getBeamwidthDeg() {
        return beamwidthDeg;
    }

    public void setBeamwidthDeg(double beamwidthDeg) {
        this.beamwidthDeg = Math.max(1.0, Math.min(360.0, beamwidthDeg));
    }

    public double getPatternSharpness() {
        return patternSharpness;
    }

    public void setPatternSharpness(double patternSharpness) {
        this.patternSharpness = Math.max(0.5, patternSharpness);
    }

    public double getSideLobeAttenuationDb() {
        return sideLobeAttenuationDb;
    }

    public void setSideLobeAttenuationDb(double sideLobeAttenuationDb) {
        this.sideLobeAttenuationDb = Math.max(0.0, sideLobeAttenuationDb);
    }

    public double getPolarizationDeg() {
        return polarizationDeg;
    }

    public void setPolarizationDeg(double polarizationDeg) {
        this.polarizationDeg = RadioMath.normalizeAngleDeg(polarizationDeg);
    }

    /**
     * Antenna gain towards an absolute direction, in dB.
     *
     * <p>Directional antennas use a cos^n main lobe inside the beamwidth and a flat side-lobe
     * floor that decays a further 6 dB towards the back. The main lobe never drops below the
     * side-lobe floor, so the pattern is continuous at the beam edge.
     */
    public double gainTowardsDb(double absoluteAngleDeg) {
        if (isOmnidirectional() || beamwidthDeg >= 360.0) {
            return txGainDb;
        }

        double delta = RadioMath.angularDistanceDeg(absoluteAngleDeg, orientationDeg);
        double halfBeam = Math.max(1.0, beamwidthDeg / 2.0);
        double sideLobeFloorDb = txGainDb - sideLobeAttenuationDb;
        if (delta <= halfBeam) {
            double normalized = delta / halfBeam;
            double mainLobe = Math.pow(Math.max(Math.cos(normalized * Math.PI / 2.0), 1e-3), patternSharpness);
            return Math.max(txGainDb + 20.0 * Math.log10(mainLobe), sideLobeFloorDb);
        }

        double offAxis = Math.min(1.0, (delta - halfBeam) / Math.max(1.0, 180.0 - halfBeam));
        return sideLobeFloorDb - 6.0 * offAxis;
    }

    /** Polarization mismatch loss against a receiver polarization, capped at ~26 dB. */
    public double polarizationMismatchLossDb(double receiverPolarizationDeg) {
        double delta = RadioMath.angularDistanceDeg(polarizationDeg, receiverPolarizationDeg);
        double coupling = Math.abs(Math.cos(Math.toRadians(delta)));
        return -20.0 * Math.log10(Math.max(coupling, 0.05));
    }

    /** Directions (degrees) in which rays are launched: the whole circle or just the beam. */
    public List<Double> emissionAnglesDeg(double stepDeg) {
        List<Double> angles = new ArrayList<>();
        if (stepDeg <= 0) stepDeg = 10.0;

        if (isOmnidirectional() || beamwidthDeg >= 360.0) {
            for (double a = 0.0; a < 360.0; a += stepDeg) angles.add(a);
            return angles;
        }

        double start = RadioMath.normalizeAngleDeg(orientationDeg - beamwidthDeg / 2.0);
        double end = RadioMath.normalizeAngleDeg(orientationDeg + beamwidthDeg / 2.0);
        if (start < end) {
            for (double a = start; a < end; a += stepDeg) angles.add(a);
        } else {
            for (double a = start; a < 360.0; a += stepDeg) angles.add(a);
            for (double a = 0.0; a < end; a += stepDeg) angles.add(a);
        }
        return angles;
    }

    public String describe() {
        return String.format(Locale.US,
                "%s at (%d, %d): Tx %.1f dBm, %s antenna, gain %.1f dB, beam %.0f°, orientation %.0f°, polarization %.0f°",
                getName(), getX(), getY(), txPowerDbm, antennaType, txGainDb, beamwidthDeg, orientationDeg,
                polarizationDeg);
    }
}
