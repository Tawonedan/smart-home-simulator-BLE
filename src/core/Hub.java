package core;

public class Hub extends Device {
    // Ganancia de recepción en dB
    private double grDb = 0.0;

    // Constructor
    public Hub(String id, String nombre, int x, int y) {
        super(id, nombre, x, y);
    }

    // Getter y Setter de grDb
    public double getGrDb() {
        return grDb;
    }

    public void setGrDb(double grDb) {
        this.grDb = grDb;
    }
}

