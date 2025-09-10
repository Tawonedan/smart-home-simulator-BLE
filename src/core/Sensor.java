package core;

import java.util.ArrayList;
import java.util.List;

public class Sensor extends Device {
    // ==== Medida y potencia ====
    private double value;     // Lo que mide el sensor: temperatura, humedad, etc.
    private double TxDbm;     // Potencia de transmisión en dBm
    private double txGainDb = 0.0;

    // ==== Orientación y antena ====
    private double orientationDeg = 0.0; // orientación de la antena direccional, en grados
    private double beamwidthDeg   = 360.0; // ancho de haz en grados (por defecto omni)

    // ==== Antenas ====
    public enum AntennaType { ISOTROPIC, DIRECTIVE, OMNI, DIRECTIONAL }
    public enum Quadrant { Q1, Q2, Q3, Q4 }

    private AntennaType antennaType;
    private Quadrant directiveQuadrant;

    // ==== Constructores ====
    public Sensor(String id, String nombre, int x, int y, double value) {
        super(id, nombre, x, y);
        this.value = value;
        this.TxDbm = 20.0;
        this.txGainDb = 0.0;

        // Por defecto: OMNI (compatible con ISOTROPIC)
        this.antennaType = AntennaType.OMNI;
        this.directiveQuadrant = Quadrant.Q1;
    }

    // ==== Medida ====
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    // ==== Potencia Tx / Ganancia ====
    public double getTxDbm() { return TxDbm; }
    public void setTxDbm(double TxDbm) { this.TxDbm = TxDbm; }

    public double getTxGainDb() { return txGainDb; }
    public void setTxGainDb(double txGainDb) { this.txGainDb = txGainDb; }

    // ==== Antena ====
    public AntennaType getAntennaType() { return antennaType; }
    public void setAntennaType(AntennaType antennaType) {
        this.antennaType = (antennaType == null) ? AntennaType.OMNI : antennaType;
    }

    public Quadrant getDirectiveQuadrant() { return directiveQuadrant; }
    public void setDirectiveQuadrant(Quadrant directiveQuadrant) {
        this.directiveQuadrant = (directiveQuadrant == null) ? Quadrant.Q1 : directiveQuadrant;
    }

    public double getOrientationDeg() { return orientationDeg; }
    public void setOrientationDeg(double orientationDeg) {
        this.orientationDeg = orientationDeg % 360.0;
        if (this.orientationDeg < 0) this.orientationDeg += 360.0;
    }

    public double getBeamwidthDeg() { return beamwidthDeg; }
    public void setBeamwidthDeg(double beamwidthDeg) {
        this.beamwidthDeg = Math.max(1.0, Math.min(360.0, beamwidthDeg));
    }

    // ==== Helpers de compatibilidad ====
    public boolean isOmni() {
        return antennaType == AntennaType.OMNI || antennaType == AntennaType.ISOTROPIC;
    }

    public boolean isDirectional() {
        return antennaType == AntennaType.DIRECTIONAL || antennaType == AntennaType.DIRECTIVE;
    }

    public double[] quadrantBoundsDeg() {
        return switch (directiveQuadrant) {
            case Q1 -> new double[]{  0.0,  90.0};
            case Q2 -> new double[]{ 90.0, 180.0};
            case Q3 -> new double[]{180.0, 270.0};
            case Q4 -> new double[]{270.0, 360.0};
        };
    }

    // ==== Generación de ángulos ====
    public List<Double> emissionAnglesDeg(double stepDeg) {
        List<Double> list = new ArrayList<>();
        if (stepDeg <= 0) stepDeg = 10.0;

        if (isOmni()) {
            // Emisión completa en 360°
            for (double a = 0.0; a < 360.0; a += stepDeg) list.add(a);
        } else {
            if (beamwidthDeg >= 360.0) {
                for (double a = 0.0; a < 360.0; a += stepDeg) list.add(a);
            } else {
                // Emisión centrada en orientationDeg ± beamwidthDeg/2
                double start = orientationDeg - beamwidthDeg / 2.0;
                double end   = orientationDeg + beamwidthDeg / 2.0;

                // Normaliza entre 0° y 360°
                while (start < 0) start += 360.0;
                while (end >= 360) end -= 360.0;

                if (start < end) {
                    for (double a = start; a < end; a += stepDeg) list.add(a);
                } else {
                    // Caso donde el haz cruza 0°
                    for (double a = start; a < 360; a += stepDeg) list.add(a);
                    for (double a = 0; a < end; a += stepDeg) list.add(a);
                }
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
                ", Tx=" + TxDbm + " dBm" +
                ", gain=" + txGainDb + " dB" +
                ", antenna=" + antennaType +
                ", beam=" + beamwidthDeg + "°" +
                ", orient=" + orientationDeg + "°" +
                ", quadrant=" + directiveQuadrant +
                '}';
    }
}

