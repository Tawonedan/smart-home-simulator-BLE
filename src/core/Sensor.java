package core;

import java.util.ArrayList;
import java.util.List;

public class Sensor extends Device {
    private double value;
    private double txDbm;
    private double txGainDb = 0.0;

    private double orientationDeg = 0.0;
    private double beamwidthDeg = 360.0;
    private double patternSharpness = 1.8;
    private double sideLobeAttenuationDb = 18.0;
    private double polarizationDeg = 0.0;

    public enum AntennaType { ISOTROPIC, DIRECTIVE, OMNI, DIRECTIONAL }

    private AntennaType antennaType;

    public Sensor(String id, String nombre, int x, int y, double value) {
        super(id, nombre, x, y);
        this.value = value;
        this.txDbm = 20.0;
        this.txGainDb = 0.0;
        this.antennaType = AntennaType.OMNI;
    }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public double getTxDbm() { return txDbm; }
    public void setTxDbm(double txDbm) { this.txDbm = txDbm; }

    public double getTxGainDb() { return txGainDb; }
    public void setTxGainDb(double txGainDb) { this.txGainDb = txGainDb; }

    public AntennaType getAntennaType() { return antennaType; }
    public void setAntennaType(AntennaType antennaType) {
        this.antennaType = (antennaType == null) ? AntennaType.OMNI : antennaType;
    }

    public double getOrientationDeg() { return orientationDeg; }
    public void setOrientationDeg(double orientationDeg) {
        this.orientationDeg = Propagation.normalizeAngleDeg(orientationDeg);
    }

    public double getBeamwidthDeg() { return beamwidthDeg; }
    public void setBeamwidthDeg(double beamwidthDeg) {
        this.beamwidthDeg = Math.max(1.0, Math.min(360.0, beamwidthDeg));
    }

    public double getPatternSharpness() { return patternSharpness; }
    public void setPatternSharpness(double patternSharpness) {
        this.patternSharpness = Math.max(0.5, patternSharpness);
    }

    public double getSideLobeAttenuationDb() { return sideLobeAttenuationDb; }
    public void setSideLobeAttenuationDb(double sideLobeAttenuationDb) {
        this.sideLobeAttenuationDb = Math.max(0.0, sideLobeAttenuationDb);
    }

    public double getPolarizationDeg() { return polarizationDeg; }
    public void setPolarizationDeg(double polarizationDeg) {
        this.polarizationDeg = Propagation.normalizeAngleDeg(polarizationDeg);
    }

    public boolean isOmni() {
        return antennaType == AntennaType.OMNI || antennaType == AntennaType.ISOTROPIC;
    }

    public boolean isDirectional() {
        return antennaType == AntennaType.DIRECTIONAL || antennaType == AntennaType.DIRECTIVE;
    }

    public double gainTowards(double absoluteAngleDeg) {
        if (isOmni() || beamwidthDeg >= 360.0) {
            return txGainDb;
        }

        double delta = Propagation.angularDistanceDeg(absoluteAngleDeg, orientationDeg);
        double halfBeam = Math.max(1.0, beamwidthDeg / 2.0);
        if (delta <= halfBeam) {
            double normalized = delta / halfBeam;
            double mainLobe = Math.pow(Math.max(Math.cos(normalized * Math.PI / 2.0), 1e-3), patternSharpness);
            return txGainDb + 20.0 * Math.log10(mainLobe);
        }

        double offAxis = Math.min(1.0, (delta - halfBeam) / Math.max(1.0, 180.0 - halfBeam));
        return txGainDb - sideLobeAttenuationDb - 6.0 * offAxis;
    }

    public double polarizationMismatchLossDb(double receiverPolarizationDeg) {
        double delta = Propagation.angularDistanceDeg(polarizationDeg, receiverPolarizationDeg);
        double coupling = Math.abs(Math.cos(Math.toRadians(delta)));
        return -20.0 * Math.log10(Math.max(coupling, 0.05));
    }

    public List<Double> emissionAnglesDeg(double stepDeg) {
        List<Double> list = new ArrayList<>();
        if (stepDeg <= 0) stepDeg = 10.0;

        if (isOmni()) {
            for (double a = 0.0; a < 360.0; a += stepDeg) list.add(a);
        } else if (beamwidthDeg >= 360.0) {
            for (double a = 0.0; a < 360.0; a += stepDeg) list.add(a);
        } else {
            double start = Propagation.normalizeAngleDeg(orientationDeg - beamwidthDeg / 2.0);
            double end = Propagation.normalizeAngleDeg(orientationDeg + beamwidthDeg / 2.0);

            if (start < end) {
                for (double a = start; a < end; a += stepDeg) list.add(a);
            } else {
                for (double a = start; a < 360.0; a += stepDeg) list.add(a);
                for (double a = 0.0; a < end; a += stepDeg) list.add(a);
            }
        }
        return list;
    }

    @Override
    public String toString() {
        return "Sensor{" +
                "id=" + getId() +
                ", nombre='" + getNombre() + '\'' +
                ", pos=(" + getX() + "," + getY() + ")" +
                ", Tx=" + txDbm + " dBm" +
                ", gain=" + txGainDb + " dB" +
                ", antenna=" + antennaType +
                ", beam=" + beamwidthDeg + "Â°" +
                ", orient=" + orientationDeg + "Â°" +
                ", pattern=" + patternSharpness +
                ", pol=" + polarizationDeg + "Â°" +
                '}';
    }
}
