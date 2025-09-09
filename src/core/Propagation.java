package core;

import java.awt.geom.Line2D;
import java.util.List;

public final class Propagation {

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

    public static double dbmToMilliwatt(double dbm){ return Math.pow(10.0, dbm/10.0); }
    public static double milliwattToDbm(double mw){ return 10.0 * Math.log10(Math.max(mw,1e-15)); }
    public static double snrDb(double prxDbm, double noiseDbm){ return prxDbm - noiseDbm; }
}
