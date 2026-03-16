package core.material;

public class WallInteraction {
    private final double transmitLossDb;
    private final double reflectLossDb;
    private final boolean hasReflection;
    private final double scatterLossDb;
    private final double transmissionCoeff;
    private final double reflectionCoeff;

    public WallInteraction(double transmitLossDb, double reflectLossDb, boolean hasReflection) {
        this(transmitLossDb, reflectLossDb, hasReflection, 12.0,
                Math.pow(10.0, -transmitLossDb / 20.0),
                Math.pow(10.0, -reflectLossDb / 20.0));
    }

    public WallInteraction(double transmitLossDb, double reflectLossDb, boolean hasReflection,
                           double scatterLossDb, double transmissionCoeff, double reflectionCoeff) {
        this.transmitLossDb = transmitLossDb;
        this.reflectLossDb = reflectLossDb;
        this.hasReflection = hasReflection;
        this.scatterLossDb = scatterLossDb;
        this.transmissionCoeff = transmissionCoeff;
        this.reflectionCoeff = reflectionCoeff;
    }

    public double getTransmitLossDb() { return transmitLossDb; }
    public double getReflectLossDb() { return reflectLossDb; }
    public boolean hasReflection() { return hasReflection; }
    public double getScatterLossDb() { return scatterLossDb; }
    public double getTransmissionCoeff() { return transmissionCoeff; }
    public double getReflectionCoeff() { return reflectionCoeff; }

    @Override
    public String toString() {
        return String.format("TxLoss=%.2f dB, ReflLoss=%.2f dB, Scatter=%.2f dB, Reflection=%b",
                transmitLossDb, reflectLossDb, scatterLossDb, hasReflection);
    }
}
