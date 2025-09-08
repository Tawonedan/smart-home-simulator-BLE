package core;

/** Utilidades de propagación en espacio libre (FSPL) basadas en Friis. */
public final class Propagation {

    private Propagation() {}

    /**
     * FSPL (dB) en función de distancia y frecuencia.
     * Fórmula clásica (unidades): d en km, f en MHz
     *   Lp(dB) = 20*log10(d_km) + 20*log10(f_MHz) + 32.44
     */
    public static double fsplLossDb(double distanceMeters, double freqMHz) {
        if (distanceMeters <= 0) return 0.0; // evita -Inf en log10(0)
        double dKm = distanceMeters / 1000.0;
        return 20.0 * Math.log10(dKm) + 20.0 * Math.log10(freqMHz) + 32.44;
    }

    /**
     * Potencia recibida (dBm) aplicando Friis: Pr = Pt + Gt + Gr - Lp
     * Usa Gt=0 si no modelas aún la ganancia de transmisión del sensor.
     */
    public static double receivedPowerDbm(double ptDbm, double gtDb, double grDb,
                                          double fsplLossDb) {
        return ptDbm + gtDb + grDb - fsplLossDb;
    }

    /** Distancia euclídea en metros entre dos puntos (x,y) en la rejilla. */
    public static double euclideanMeters(int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
