package core;

public class Sensor extends Device {
    private double value; // Lo que mide el sensor: temperatura, humedad, etc.
    private double TxDbm;

    // ==== Antenas ====
    public enum AntennaType { ISOTROPIC, DIRECTIVE }
    public enum Quadrant { Q1, Q2, Q3, Q4 } // Cuadrantes alrededor del sensor

    private AntennaType antennaType;
    private Quadrant directiveQuadrant;

    public Sensor(String id, String nombre, int x, int y, double value) {
        super(id, nombre, x, y);
        this.value = value;
        this.TxDbm = 30;

        // Por defecto: antena isotrópica
        this.antennaType = AntennaType.ISOTROPIC;
        this.directiveQuadrant = Quadrant.Q1;
    }

    // ==== Medida ====
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    // ==== Potencia Tx ====
    public double getTxDbm() { return TxDbm; }
    public void setTxDbm(double TxDbm) { this.TxDbm = TxDbm; }

    // ==== Antena ====
    public AntennaType getAntennaType() { return antennaType; }
    public void setAntennaType(AntennaType antennaType) { this.antennaType = antennaType; }

    public Quadrant getDirectiveQuadrant() { return directiveQuadrant; }
    public void setDirectiveQuadrant(Quadrant directiveQuadrant) { this.directiveQuadrant = directiveQuadrant; }
}
