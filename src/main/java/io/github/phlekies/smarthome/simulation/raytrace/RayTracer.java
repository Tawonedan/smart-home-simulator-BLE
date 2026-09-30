package io.github.phlekies.smarthome.simulation.raytrace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.model.material.WallInteraction;
import io.github.phlekies.smarthome.physics.Geometry;
import io.github.phlekies.smarthome.physics.RadioMath;

/**
 * Geometric ray launcher used to visualise how energy bounces through the floor plan.
 *
 * <p>Rays are emitted in a fan from every sensor. At each wall they split into a transmitted
 * ray (penetration loss) and, if the material reflects, a specularly reflected ray. Power
 * decays with free-space loss along the travelled path, and a ray stops when it leaves the
 * plan, exceeds the interaction or length budget, falls below the power floor or reaches the
 * hub. The tracer is iterative, so the ray tree never grows the call stack.
 */
public final class RayTracer {

    /** Nudge applied after an interaction so the child ray does not re-hit the same wall. */
    private static final double EPSILON = 1e-6;
    /** Free-space loss is evaluated from this distance to avoid the singularity at the source. */
    private static final double MIN_LOSS_DISTANCE = 0.1;

    /** Tracing limits. */
    public record Settings(double angularStepDeg, int maxInteractions, double maxPathLengthMeters,
                           double powerFloorDbm, double hubCaptureRadiusMeters, int maxSegments) {

        public static Settings defaults() {
            return new Settings(10.0, 6, 60.0, -100.0, 0.30, 20_000);
        }
    }

    private RayTracer() {
    }

    /**
     * Traces all sensors inside the rectangle {@code [0, widthMeters] × [0, heightMeters]}.
     *
     * @param hub optional receiver; rays passing within the capture radius end there
     */
    public static RayTraceResult trace(Environment env, List<Sensor> sensors, Hub hub,
                                       double widthMeters, double heightMeters, Settings settings) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(sensors, "sensors");
        Objects.requireNonNull(settings, "settings");

        List<RaySegment> segments = new ArrayList<>();
        List<HubHit> hubHits = new ArrayList<>();
        boolean truncated = false;

        for (Sensor sensor : sensors) {
            Deque<Ray> pending = new ArrayDeque<>();
            for (double angleDeg : sensor.emissionAnglesDeg(settings.angularStepDeg())) {
                double rad = Math.toRadians(angleDeg);
                double sourceDbm = sensor.getTxPowerDbm() + sensor.gainTowardsDb(angleDeg);
                pending.add(new Ray(sensor.getX(), sensor.getY(), Math.cos(rad), Math.sin(rad),
                        sourceDbm, 0.0, 0.0, 0, null));
            }

            while (!pending.isEmpty()) {
                if (segments.size() >= settings.maxSegments()) {
                    truncated = true;
                    break;
                }
                traceOne(pending.poll(), sensor, env, hub, widthMeters, heightMeters, settings,
                        pending, segments, hubHits);
            }
        }

        hubHits.sort((a, b) -> Double.compare(a.pathLengthMeters(), b.pathLengthMeters()));
        return new RayTraceResult(segments, hubHits, truncated);
    }

    private static void traceOne(Ray ray, Sensor sensor, Environment env, Hub hub,
                                 double widthMeters, double heightMeters, Settings settings,
                                 Deque<Ray> pending, List<RaySegment> segments, List<HubHit> hubHits) {
        double freqMHz = env.getFreqMHz();
        double remaining = settings.maxPathLengthMeters() - ray.travelledMeters();
        double exit = distanceToExit(ray.x(), ray.y(), ray.dirX(), ray.dirY(), widthMeters, heightMeters);
        double reach = Math.min(remaining, exit);
        if (reach <= EPSILON) {
            return;
        }
        double endX = ray.x() + ray.dirX() * reach;
        double endY = ray.y() + ray.dirY() * reach;

        // Nearest wall along the ray.
        Wall hitWall = null;
        double hitT = Double.POSITIVE_INFINITY;
        for (Wall wall : env.getWalls()) {
            if (wall == ray.lastWall()) continue;
            double[] hit = Geometry.segmentIntersection(ray.x(), ray.y(), endX, endY,
                    wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2());
            if (hit != null && hit[2] > EPSILON && hit[2] < hitT) {
                hitT = hit[2];
                hitWall = wall;
            }
        }
        double length = (hitWall == null) ? reach : hitT * reach;

        // The hub absorbs rays that pass close enough to it before any wall.
        if (hub != null) {
            double along = (hub.getX() - ray.x()) * ray.dirX() + (hub.getY() - ray.y()) * ray.dirY();
            if (along > 0 && along <= length) {
                double closestX = ray.x() + ray.dirX() * along;
                double closestY = ray.y() + ray.dirY() * along;
                if (Math.hypot(hub.getX() - closestX, hub.getY() - closestY) <= settings.hubCaptureRadiusMeters()) {
                    double pathLength = ray.travelledMeters() + along;
                    segments.add(segment(ray, sensor, along, freqMHz));
                    hubHits.add(hubHit(sensor, hub, env, pathLength, ray));
                    return;
                }
            }
        }

        RaySegment segment = segment(ray, sensor, length, freqMHz);
        segments.add(segment);

        if (hitWall == null || ray.interactions() + 1 > settings.maxInteractions()) {
            return;
        }

        double hitX = ray.x() + ray.dirX() * length;
        double hitY = ray.y() + ray.dirY() * length;
        double travelled = ray.travelledMeters() + length;
        double incidenceDeg = Geometry.incidenceAngleDeg(ray.x(), ray.y(), hitX, hitY,
                hitWall.getX1(), hitWall.getY1(), hitWall.getX2(), hitWall.getY2());
        WallInteraction interaction = Materials.interact(hitWall.getMaterial(), freqMHz,
                hitWall.getThicknessCm(), incidenceDeg);

        double transmittedLoss = ray.interactionLossDb() + interaction.transmissionLossDb();
        enqueueIfAudible(pending, settings, freqMHz, new Ray(hitX + ray.dirX() * EPSILON, hitY + ray.dirY() * EPSILON,
                ray.dirX(), ray.dirY(), ray.sourceDbm(), travelled, transmittedLoss, ray.interactions() + 1, hitWall));

        if (interaction.reflects()) {
            double[] reflected = reflect(ray.dirX(), ray.dirY(), hitWall);
            double reflectedLoss = ray.interactionLossDb() + interaction.reflectionLossDb();
            enqueueIfAudible(pending, settings, freqMHz, new Ray(hitX + reflected[0] * EPSILON,
                    hitY + reflected[1] * EPSILON, reflected[0], reflected[1], ray.sourceDbm(), travelled,
                    reflectedLoss, ray.interactions() + 1, hitWall));
        }
    }

    private static void enqueueIfAudible(Deque<Ray> pending, Settings settings, double freqMHz, Ray ray) {
        if (ray.powerAt(ray.travelledMeters(), freqMHz) >= settings.powerFloorDbm()) {
            pending.add(ray);
        }
    }

    private static RaySegment segment(Ray ray, Sensor sensor, double length, double freqMHz) {
        double endDistance = ray.travelledMeters() + length;
        return new RaySegment(sensor.getId(),
                ray.x(), ray.y(), ray.x() + ray.dirX() * length, ray.y() + ray.dirY() * length,
                ray.travelledMeters(), endDistance,
                ray.powerAt(ray.travelledMeters(), freqMHz), ray.powerAt(endDistance, freqMHz),
                ray.interactions());
    }

    private static HubHit hubHit(Sensor sensor, Hub hub, Environment env, double pathLength, Ray ray) {
        double fsplDb = RadioMath.fsplDb(Math.max(pathLength, MIN_LOSS_DISTANCE), env.getFreqMHz());
        double receivedDbm = ray.sourceDbm() + hub.getReceiverGainDb() - fsplDb - ray.interactionLossDb();
        double snrDb = receivedDbm - env.noiseFloorDbm();
        return new HubHit(sensor.getId(), sensor.getName(), pathLength, ray.interactions(), fsplDb,
                ray.interactionLossDb(), receivedDbm, snrDb, RadioMath.bpskBer(snrDb),
                RadioMath.shannonCapacityMbps(snrDb, env.getBandwidthHz()));
    }

    /** Distance along the direction until the point leaves the plan rectangle. */
    private static double distanceToExit(double x, double y, double dx, double dy, double width, double height) {
        double tx = (dx > 0) ? (width - x) / dx : (dx < 0) ? -x / dx : Double.POSITIVE_INFINITY;
        double ty = (dy > 0) ? (height - y) / dy : (dy < 0) ? -y / dy : Double.POSITIVE_INFINITY;
        return Math.max(0.0, Math.min(tx, ty));
    }

    /** Specular reflection of a unit direction about the wall: r = d − 2(d·n)n. */
    static double[] reflect(double dirX, double dirY, Wall wall) {
        double wx = wall.getX2() - wall.getX1();
        double wy = wall.getY2() - wall.getY1();
        double length = Math.hypot(wx, wy);
        double nx = -wy / length;
        double ny = wx / length;
        double dot = dirX * nx + dirY * ny;
        double rx = dirX - 2.0 * dot * nx;
        double ry = dirY - 2.0 * dot * ny;
        double norm = Math.hypot(rx, ry);
        return new double[] { rx / norm, ry / norm };
    }

    /** A ray in flight: origin, direction and the budget spent so far. */
    private record Ray(double x, double y, double dirX, double dirY, double sourceDbm,
                       double travelledMeters, double interactionLossDb, int interactions, Wall lastWall) {

        double powerAt(double distanceMeters, double freqMHz) {
            return sourceDbm - RadioMath.fsplDb(Math.max(distanceMeters, MIN_LOSS_DISTANCE), freqMHz)
                    - interactionLossDb;
        }
    }
}
