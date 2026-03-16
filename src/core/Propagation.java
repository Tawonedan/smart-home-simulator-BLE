package core;

import java.awt.geom.Line2D;
import java.util.List;

public final class Propagation {

    private static final double MIN_LINEAR = 1e-15;

    private Propagation(){}

    public static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                            double cx, double cy, double dx, double dy){
        return new Line2D.Double(ax,ay,bx,by).intersectsLine(cx,cy,dx,dy);
    }

    /** Suma de pérdidas por paredes cruzadas entre A(x1,y1) y B(x2,y2). */
    public static double wallLossAlongLine(List<Wall> walls, double freqMHz,
                                           double x1, double y1, double x2, double y2){
        if (walls == null || walls.isEmpty()) return 0.0;
        double total = 0.0;
        for (Wall w : walls){
            if (segmentsIntersect(x1,y1,x2,y2, w.getX1(),w.getY1(),w.getX2(),w.getY2())){
                total += w.lossDb(freqMHz);
            }
        }
        return total;
    }

    /** FSPL en dB (distancia en metros, frecuencia en MHz). */
    public static double fsplLossDb(double distanceMeters, double freqMHz){
        if (distanceMeters <= 0) return 0.0;
        // 32.45 + 20log10(fMHz) + 20log10(d_km)
        double d_km = distanceMeters / 1000.0;
        return 32.45 + 20.0 * Math.log10(freqMHz) + 20.0 * Math.log10(d_km);
    }

    /**
     * Modelo indoor log-distance anclado a 1 m.
     * Para n=2 reproduce aproximadamente la FSPL clÃ¡sica en espacio libre.
     */
    public static double logDistanceLossDb(double distanceMeters, double freqMHz, double pathLossExponent){
        if (distanceMeters <= 0) return 0.0;
        if (distanceMeters <= 1.0) return fsplLossDb(distanceMeters, freqMHz);
        double n = Math.max(1.0, pathLossExponent);
        return fsplLossDb(1.0, freqMHz) + 10.0 * n * Math.log10(distanceMeters);
    }

    public static double dbmToMilliwatt(double dbm){ return Math.pow(10.0, dbm/10.0); }
    public static double milliwattToDbm(double mw){ return 10.0 * Math.log10(Math.max(mw, MIN_LINEAR)); }
    public static double dbToLinearRatio(double db){ return Math.pow(10.0, db / 10.0); }
    public static double dbToAmplitudeRatio(double db){ return Math.pow(10.0, db / 20.0); }
    public static double linearRatioToDb(double ratio){ return 10.0 * Math.log10(Math.max(ratio, MIN_LINEAR)); }
    public static double amplitudeRatioToDb(double ratio){ return 20.0 * Math.log10(Math.max(ratio, MIN_LINEAR)); }
    public static double snrDb(double prxDbm, double noiseDbm){ return prxDbm - noiseDbm; }

    public static double phaseRad(double distanceMeters, double wavelengthMeters){
        if (distanceMeters <= 0 || wavelengthMeters <= 0) return 0.0;
        return 2.0 * Math.PI * distanceMeters / wavelengthMeters;
    }

    public static double shannonCapacityMbps(double snrDb, double bandwidthHz){
        double snrLinear = dbToLinearRatio(snrDb);
        return bandwidthHz * (Math.log(1.0 + snrLinear) / Math.log(2.0)) / 1e6;
    }

    /** BER aproximado para BPSK sobre AWGN usando Q(sqrt(2*Eb/N0)). */
    public static double bpskBer(double snrDb){
        double snrLinear = dbToLinearRatio(snrDb);
        return 0.5 * erfcApprox(Math.sqrt(snrLinear));
    }

    /** PÃ©rdida adicional de difracciÃ³n por filo de cuchillo (ITU-R simplificado). */
    public static double knifeEdgeLossDb(double v){
        if (v <= -0.78) return 0.0;
        double term = Math.sqrt((v - 0.1) * (v - 0.1) + 1.0) + v - 0.1;
        return 6.9 + 20.0 * Math.log10(Math.max(term, MIN_LINEAR));
    }

    public static double angularDistanceDeg(double aDeg, double bDeg){
        double diff = Math.abs(normalizeAngleDeg(aDeg) - normalizeAngleDeg(bDeg));
        return (diff > 180.0) ? 360.0 - diff : diff;
    }

    public static double normalizeAngleDeg(double angleDeg){
        double normalized = angleDeg % 360.0;
        return (normalized < 0) ? normalized + 360.0 : normalized;
    }

    private static double erfcApprox(double x){
        // AproximaciÃ³n de Abramowitz & Stegun 7.1.26.
        double sign = (x < 0) ? -1.0 : 1.0;
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * ax);
        double poly = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
                + 0.254829592) * t;
        double erf = 1.0 - poly * Math.exp(-ax * ax);
        double adjustedErf = sign * erf;
        return 1.0 - adjustedErf;
    }
}
