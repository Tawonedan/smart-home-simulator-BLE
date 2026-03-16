package core.material;

/** Material de pared con pérdidas por cruce dependientes de frecuencia. */
public class Material {

    private final String name;
    /** Pérdida por cruce a 2.4 GHz (dB) para un espesor típico (p. ej. 10 cm). */
    private final double lossDbAt24;
    /** Pérdida por cruce a 5 GHz (dB) para un espesor típico (p. ej. 10 cm). */
    private final double lossDbAt5;

    /** Opcional: factor por grosor (lineal simple dB por cm). */
    private final double dbPerCm; // 0 si no lo usas
    /** Sesgo del material al reflejar: menor valor => reflejo mÃ¡s intenso. */
    private final double reflectionBiasDb;
    /** Rugosidad equivalente: controla cuÃ¡nta energÃ­a se dispersa. */
    private final double roughnessDb;

    public Material(String name, double lossDbAt24, double lossDbAt5, double dbPerCm) {
        this(name, lossDbAt24, lossDbAt5, dbPerCm, 5.0, 10.0);
    }

    public Material(String name, double lossDbAt24, double lossDbAt5, double dbPerCm,
                    double reflectionBiasDb, double roughnessDb) {
        this.name = name;
        this.lossDbAt24 = lossDbAt24;
        this.lossDbAt5 = lossDbAt5;
        this.dbPerCm = dbPerCm;
        this.reflectionBiasDb = Math.max(1.0, reflectionBiasDb);
        this.roughnessDb = Math.max(0.0, roughnessDb);
    }

    public String getName() { return name; }

    /** Pérdida base por cruce para una frecuencia (MHz) y grosor en cm. */
    public double lossDb(double freqMHz, double thicknessCm) {
        double base = (freqMHz < 3000.0) ? lossDbAt24 : lossDbAt5;
        return base + Math.max(0.0, thicknessCm) * Math.max(0.0, dbPerCm);
    }

    public double reflectionLossDb(double freqMHz, double incidenceAngleDeg) {
        double freqFactor = (freqMHz < 3000.0) ? 1.0 : 1.15;
        double grazingGain = 6.0 * Math.sin(Math.toRadians(Math.max(0.0, Math.min(89.0, incidenceAngleDeg))));
        double roughnessPenalty = 0.25 * roughnessDb;
        return Math.max(1.5, reflectionBiasDb * freqFactor - grazingGain + roughnessPenalty);
    }

    public double scatteringLossDb(double freqMHz, double incidenceAngleDeg) {
        double freqPenalty = (freqMHz < 3000.0) ? 1.0 : 3.0;
        double anglePenalty = 4.0 * Math.cos(Math.toRadians(Math.max(0.0, Math.min(89.0, incidenceAngleDeg))));
        return Math.max(6.0, roughnessDb + freqPenalty + anglePenalty);
    }

    public double transmissionCoefficient(double freqMHz, double thicknessCm) {
        return Math.pow(10.0, -lossDb(freqMHz, thicknessCm) / 20.0);
    }

    public double reflectionCoefficient(double freqMHz, double incidenceAngleDeg) {
        return Math.pow(10.0, -reflectionLossDb(freqMHz, incidenceAngleDeg) / 20.0);
    }

    @Override public String toString() {
        return "Material{" + name + ", 2.4GHz=" + lossDbAt24 + " dB, 5GHz=" + lossDbAt5 + " dB}";
    }
}
