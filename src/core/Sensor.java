package core;

import java.util.ArrayList;
import java.util.List;

public class Sensor extends Device {
    // ==== Medida y potencia ====
    private double value;     // Lo que mide el sensor: temperatura, humedad, etc.
    private double TxDbm;     // Potencia de transmisión en dBm
    private double txGainDb = 0.0;
    private double orientationDeg = 0.0; // orientación de la antena direccional, en grados


    // ==== Antenas ====
    // Mantengo los valores antiguos (ISOTROPIC/DIRECTIVE) y añado los nuevos (OMNI/DIRECTIONAL).
    // Así no rompe llamadas existentes y puedes ir migrando a OMNI/DIRECTIONAL.
    public enum AntennaType { ISOTROPIC, DIRECTIVE, OMNI, DIRECTIONAL }

    /** Cuadrantes: Q1(+X,+Y), Q2(-X,+Y), Q3(-X,-Y), Q4(+X,-Y) */
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
    

    // ==== Helpers de compatibilidad / conveniencia ====

    /** Devuelve true si el sensor está en modo omnidireccional (OMNI o ISOTROPIC). */
    public boolean isOmni() {
        return antennaType == AntennaType.OMNI || antennaType == AntennaType.ISOTROPIC;
    }

    /** Devuelve true si el sensor está en modo direccional por cuadrante (DIRECTIONAL o DIRECTIVE). */
    public boolean isDirectional() {
        return antennaType == AntennaType.DIRECTIONAL || antennaType == AntennaType.DIRECTIVE;
    }

    /**
     * Rango angular del cuadrante en grados [start, end), usando convención trigonométrica:
     * 0° hacia +X, 90° hacia +Y, antihorario.
     */
    public double[] quadrantBoundsDeg() {
        return switch (directiveQuadrant) {
            case Q1 -> new double[]{  0.0,  90.0}; // +X,+Y
            case Q2 -> new double[]{ 90.0, 180.0}; // -X,+Y
            case Q3 -> new double[]{180.0, 270.0}; // -X,-Y
            case Q4 -> new double[]{270.0, 360.0}; // +X,-Y
        };
    }

    /**
     * Devuelve una lista de ángulos (en grados) para trazar rayos según el tipo de antena.
     * @param stepDeg paso angular, por ejemplo 10.0 para un rayo cada 10°
     */
    public List<Double> emissionAnglesDeg(double stepDeg) {
        List<Double> list = new ArrayList<>();
        if (stepDeg <= 0) stepDeg = 10.0;

        if (isOmni()) {
            for (double a = 0.0; a < 360.0; a += stepDeg) list.add(a);
        } else {
            double[] r = quadrantBoundsDeg();
            for (double a = r[0]; a < r[1]; a += stepDeg) list.add(a);
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
                ", quadrant=" + directiveQuadrant +
                '}';
    }
}
