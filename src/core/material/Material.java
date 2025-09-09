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

    public Material(String name, double lossDbAt24, double lossDbAt5, double dbPerCm) {
        this.name = name;
        this.lossDbAt24 = lossDbAt24;
        this.lossDbAt5 = lossDbAt5;
        this.dbPerCm = dbPerCm;
    }

    public String getName() { return name; }

    /** Pérdida base por cruce para una frecuencia (MHz) y grosor en cm. */
    public double lossDb(double freqMHz, double thicknessCm) {
        double base = (freqMHz < 3000.0) ? lossDbAt24 : lossDbAt5;
        return base + Math.max(0.0, thicknessCm) * Math.max(0.0, dbPerCm);
    }

    @Override public String toString() {
        return "Material{" + name + ", 2.4GHz=" + lossDbAt24 + " dB, 5GHz=" + lossDbAt5 + " dB}";
    }
}
