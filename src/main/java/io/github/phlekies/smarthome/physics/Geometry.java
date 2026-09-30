package io.github.phlekies.smarthome.physics;

import java.awt.geom.Line2D;

/** 2D segment geometry used for wall crossings, specular reflections and hit testing. */
public final class Geometry {

    private static final double EPSILON = 1e-12;

    private Geometry() {
    }

    /** True if segments AB and CD touch or cross (collinear overlaps included). */
    public static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                            double cx, double cy, double dx, double dy) {
        return Line2D.linesIntersect(ax, ay, bx, by, cx, cy, dx, dy);
    }

    /**
     * Proper intersection point of segments AB and CD.
     *
     * @return {@code {x, y, t}} where {@code t ∈ [0, 1]} is the position along AB,
     *         or {@code null} if the segments are parallel or do not meet
     */
    public static double[] segmentIntersection(double ax, double ay, double bx, double by,
                                               double cx, double cy, double dx, double dy) {
        double den = (ax - bx) * (cy - dy) - (ay - by) * (cx - dx);
        if (Math.abs(den) < EPSILON) {
            return null;
        }
        double t = ((ax - cx) * (cy - dy) - (ay - cy) * (cx - dx)) / den;
        double u = ((ax - cx) * (ay - by) - (ay - cy) * (ax - bx)) / den;
        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) {
            return null;
        }
        return new double[] { ax + t * (bx - ax), ay + t * (by - ay), t };
    }

    /** Euclidean distance from point P to segment AB. */
    public static double distancePointToSegment(double px, double py,
                                                double ax, double ay, double bx, double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double wx = px - ax;
        double wy = py - ay;
        double c1 = vx * wx + vy * wy;
        if (c1 <= 0.0) return Math.hypot(px - ax, py - ay);
        double c2 = vx * vx + vy * vy;
        if (c2 <= c1) return Math.hypot(px - bx, py - by);
        double t = c1 / c2;
        return Math.hypot(px - (ax + t * vx), py - (ay + t * vy));
    }

    /** Mirror image of point P across the infinite line through A and B. */
    public static double[] mirrorPoint(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 <= EPSILON) {
            return new double[] { px, py };
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / len2;
        double projX = ax + t * dx;
        double projY = ay + t * dy;
        return new double[] { 2.0 * projX - px, 2.0 * projY - py };
    }

    /**
     * Angle between the incoming direction (from → hit) and the normal of segment AB, in degrees.
     * 0° is normal incidence, 90° is grazing incidence.
     */
    public static double incidenceAngleDeg(double fromX, double fromY, double hitX, double hitY,
                                           double ax, double ay, double bx, double by) {
        double wx = bx - ax;
        double wy = by - ay;
        double wallLength = Math.hypot(wx, wy);
        if (wallLength <= EPSILON) {
            return 0.0;
        }
        double nx = -wy / wallLength;
        double ny = wx / wallLength;
        double dx = hitX - fromX;
        double dy = hitY - fromY;
        double dl = Math.hypot(dx, dy);
        if (dl <= EPSILON) {
            return 0.0;
        }
        double dot = Math.abs((dx / dl) * nx + (dy / dl) * ny);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }
}
