package core;

public class Hub extends Device {
    private double grDb = 0.0;
    private double polarizationDeg = 0.0;

    public Hub(String id, String nombre, int x, int y) {
        super(id, nombre, x, y);
    }

    public double getGrDb() {
        return grDb;
    }

    public void setGrDb(double grDb) {
        this.grDb = grDb;
    }

    public double getPolarizationDeg() {
        return polarizationDeg;
    }

    public void setPolarizationDeg(double polarizationDeg) {
        this.polarizationDeg = Propagation.normalizeAngleDeg(polarizationDeg);
    }
}
