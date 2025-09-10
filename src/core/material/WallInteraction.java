package core.material;

public class WallInteraction {
    private final double transmitLossDb; // pérdida al atravesar
    private final double reflectLossDb;  // pérdida en rebote
    private final boolean hasReflection;

    public WallInteraction(double transmitLossDb, double reflectLossDb, boolean hasReflection) {
        this.transmitLossDb = transmitLossDb;
        this.reflectLossDb = reflectLossDb;
        this.hasReflection = hasReflection;
    }

    public double getTransmitLossDb() { return transmitLossDb; }
    public double getReflectLossDb() { return reflectLossDb; }
    public boolean hasReflection() { return hasReflection; }

    @Override
    public String toString() {
        return String.format("TxLoss=%.2f dB, ReflLoss=%.2f dB, Reflection=%b",
                transmitLossDb, reflectLossDb, hasReflection);
    }
}
