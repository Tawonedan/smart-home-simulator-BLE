package io.github.phlekies.smarthome.simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.model.material.WallInteraction;
import io.github.phlekies.smarthome.physics.ComplexNumber;
import io.github.phlekies.smarthome.physics.Geometry;
import io.github.phlekies.smarthome.physics.RadioMath;

/**
 * Indoor multipath propagation engine.
 *
 * <p>For every receiver point and sensor it builds a small set of deterministic paths:
 * <ul>
 *   <li><b>direct</b> line of sight, attenuated by every wall it crosses;</li>
 *   <li><b>specular reflections</b> found with the image method (one bounce per wall);</li>
 *   <li><b>diffraction</b> around wall end points, using the ITU knife-edge model;</li>
 *   <li><b>diffuse scattering</b> from wall mid-points.</li>
 * </ul>
 * Paths are combined incoherently ({@link PropagationMode#RAYS}) or as phasors
 * ({@link PropagationMode#WAVES}). Small-scale fading is pseudo-random but seeded by
 * position, so results are reproducible and identical in sequential and parallel runs.
 */
public final class PropagationEngine {

    private static final double MIN_DISTANCE = 1e-3;
    private static final double MIN_POWER_MW = 1e-15;
    private static final double EMPTY_CELL_DBM = -150.0;

    private PropagationEngine() {
    }

    public static HeatmapResult computeHeatmap(Environment env, List<Sensor> sensors,
                                               int width, int height, SimulationSettings settings) {
        return computeHeatmap(env, sensors, width, height, settings, ComputationMonitor.NONE);
    }

    /**
     * Computes one {@link CellResult} per square metre, sampled at the cell centres.
     *
     * @throws CancellationException if the monitor reports cancellation
     */
    public static HeatmapResult computeHeatmap(Environment env, List<Sensor> sensors,
                                               int width, int height, SimulationSettings settings,
                                               ComputationMonitor monitor) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(sensors, "sensors");
        Objects.requireNonNull(monitor, "monitor");

        SimulationSettings snapshot = (settings == null) ? new SimulationSettings() : settings.copy();
        CellResult[][] cells = new CellResult[width][height];
        List<Sensor> sensorSnapshot = List.copyOf(sensors);
        long total = (long) width * height;
        AtomicLong done = new AtomicLong();

        IntStream indices = IntStream.range(0, width * height);
        if (snapshot.isParallelComputation()) {
            indices = indices.parallel();
        }

        indices.forEach(index -> {
            if (monitor.isCancelled()) {
                throw new CancellationException("Heatmap computation cancelled");
            }
            int x = index % width;
            int y = index / width;
            cells[x][y] = computeCell(env, sensorSnapshot, x + 0.5, y + 0.5, snapshot);
            long finished = done.incrementAndGet();
            if (finished % width == 0 || finished == total) {
                monitor.progress(finished, total);
            }
        });

        return new HeatmapResult(width, height, cells, snapshot);
    }

    /** Link metrics at a single receiver point {@code (rxX, rxY)} in metres. */
    public static CellResult computeCell(Environment env, List<Sensor> sensors,
                                         double rxX, double rxY, SimulationSettings settings) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(sensors, "sensors");
        SimulationSettings snapshot = (settings == null) ? new SimulationSettings() : settings.copy();

        if (sensors.isEmpty()) {
            return emptyCell(rxX, rxY, env.noiseFloorDbm(), snapshot);
        }

        List<PathContribution> allContributions = new ArrayList<>();
        List<SensorAggregate> sensorAggregates = new ArrayList<>();
        ComplexNumber totalField = ComplexNumber.ZERO;
        double totalIncoherentMw = 0.0;

        for (Sensor sensor : sensors) {
            SensorAggregate aggregate = computeSensorAggregate(env, sensor, rxX, rxY, snapshot);
            sensorAggregates.add(aggregate);
            allContributions.addAll(aggregate.contributions());
            totalField = totalField.add(aggregate.field());
            totalIncoherentMw += aggregate.powerMw();
        }

        allContributions.sort(Comparator.comparingDouble(PathContribution::powerDbm).reversed());

        double signalMw = 0.0;
        String dominantSensorId = null;
        for (SensorAggregate aggregate : sensorAggregates) {
            if (aggregate.powerMw() > signalMw) {
                signalMw = aggregate.powerMw();
                dominantSensorId = aggregate.sensorId();
            }
        }

        double totalPowerMw = (snapshot.getPropagationMode() == PropagationMode.WAVES)
                ? Math.max(totalField.magnitudeSquared(), MIN_POWER_MW)
                : Math.max(totalIncoherentMw, MIN_POWER_MW);
        double interferenceMw = Math.max(0.0, totalIncoherentMw - signalMw);
        double noiseDbm = env.noiseFloorDbm();
        double noiseMw = RadioMath.dbmToMilliwatt(noiseDbm);
        double snrDb = RadioMath.linearToDb(signalMw / Math.max(noiseMw, MIN_POWER_MW));
        double sinrDb = RadioMath.linearToDb(signalMw / Math.max(noiseMw + interferenceMw, MIN_POWER_MW));
        double signalDbm = RadioMath.milliwattToDbm(Math.max(signalMw, MIN_POWER_MW));

        return new CellResult(
                rxX,
                rxY,
                RadioMath.milliwattToDbm(totalPowerMw),
                signalDbm,
                RadioMath.milliwattToDbm(Math.max(interferenceMw, MIN_POWER_MW)),
                noiseDbm,
                snrDb,
                sinrDb,
                signalDbm - snapshot.getReceiverSensitivityDbm(),
                RadioMath.bpskBer(sinrDb),
                RadioMath.shannonCapacityMbps(sinrDb, env.getBandwidthHz()),
                totalField.phaseRad(),
                allContributions.size(),
                dominantSensorId,
                allContributions);
    }

    private static CellResult emptyCell(double rxX, double rxY, double noiseDbm, SimulationSettings settings) {
        return new CellResult(rxX, rxY, EMPTY_CELL_DBM, EMPTY_CELL_DBM, EMPTY_CELL_DBM, noiseDbm,
                EMPTY_CELL_DBM, EMPTY_CELL_DBM, EMPTY_CELL_DBM - settings.getReceiverSensitivityDbm(),
                0.5, 0.0, 0.0, 0, CellResult.NO_SENSOR, List.of());
    }

    private static SensorAggregate computeSensorAggregate(Environment env, Sensor sensor,
                                                          double rxX, double rxY,
                                                          SimulationSettings settings) {
        List<PathContribution> contributions = new ArrayList<>();

        PathContribution direct = buildDirectPath(env, sensor, rxX, rxY, settings);
        if (direct != null) {
            contributions.add(direct);
        }
        contributions.addAll(buildReflectionPaths(env, sensor, rxX, rxY, settings));
        if (settings.isDiffractionEnabled()) {
            contributions.addAll(buildDiffractionPaths(env, sensor, rxX, rxY, settings));
        }
        if (settings.isScatteringEnabled()) {
            contributions.addAll(buildScatteringPaths(env, sensor, rxX, rxY, settings));
        }

        ComplexNumber field = ComplexNumber.ZERO;
        double powerMw = 0.0;
        for (PathContribution contribution : contributions) {
            powerMw += contribution.powerMilliwatt();
            field = field.add(contribution.fieldPhasor());
        }
        if (settings.getPropagationMode() == PropagationMode.WAVES) {
            powerMw = Math.max(field.magnitudeSquared(), MIN_POWER_MW);
        }

        return new SensorAggregate(sensor.getId(), field, Math.max(powerMw, MIN_POWER_MW), contributions);
    }

    private static PathContribution buildDirectPath(Environment env, Sensor sensor,
                                                    double rxX, double rxY, SimulationSettings settings) {
        double distance = safeDistance(sensor.getX(), sensor.getY(), rxX, rxY);
        double txGain = sensor.gainTowardsDb(angleDeg(sensor.getX(), sensor.getY(), rxX, rxY));
        double wallLoss = wallLossAlongPath(env.getWalls(), env.getFreqMHz(),
                sensor.getX(), sensor.getY(), rxX, rxY, null);
        double polLoss = sensor.polarizationMismatchLossDb(settings.getReceiverPolarizationDeg());

        double powerDbm = receivedPowerDbm(env, sensor, distance, txGain, settings.getReceiverGainDb(),
                wallLoss, 0.0, 0.0, 0.0, polLoss,
                fadingPowerDeltaDb(sensor, rxX, rxY, PathType.DIRECT, 0, settings), settings);

        return buildContribution(sensor, PathType.DIRECT, distance, txGain, wallLoss, 0.0, 0.0, 0.0, polLoss,
                powerDbm,
                RadioMath.phaseRad(distance, env.wavelengthMeters())
                        + fadingPhaseShiftRad(sensor, rxX, rxY, PathType.DIRECT, 0, settings),
                0, settings);
    }

    private static List<PathContribution> buildReflectionPaths(Environment env, Sensor sensor,
                                                               double rxX, double rxY,
                                                               SimulationSettings settings) {
        if (settings.getMaxReflectionPaths() <= 0) {
            return List.of();
        }

        List<PathContribution> candidates = new ArrayList<>();
        List<Wall> walls = env.getWalls();

        for (int i = 0; i < walls.size(); i++) {
            Wall wall = walls.get(i);
            SpecularHit hit = findSpecularHit(sensor.getX(), sensor.getY(), rxX, rxY, wall);
            if (hit == null) continue;

            WallInteraction interaction = Materials.interact(
                    wall.getMaterial(), env.getFreqMHz(), wall.getThicknessCm(), hit.incidenceAngleDeg());
            if (!interaction.reflects()) continue;

            double txGain = sensor.gainTowardsDb(angleDeg(sensor.getX(), sensor.getY(), hit.x(), hit.y()));
            double wallLoss = wallLossAlongPath(walls, env.getFreqMHz(),
                    sensor.getX(), sensor.getY(), hit.x(), hit.y(), wall)
                    + wallLossAlongPath(walls, env.getFreqMHz(), hit.x(), hit.y(), rxX, rxY, wall);
            double polLoss = sensor.polarizationMismatchLossDb(settings.getReceiverPolarizationDeg());
            double powerDbm = receivedPowerDbm(env, sensor, hit.totalDistance(), txGain,
                    settings.getReceiverGainDb(), wallLoss, interaction.reflectionLossDb(), 0.0, 0.0, polLoss,
                    fadingPowerDeltaDb(sensor, rxX, rxY, PathType.REFLECTION, i + 1, settings), settings);

            // A reflection adds a quarter-cycle phase shift to the carrier.
            PathContribution contribution = buildContribution(sensor, PathType.REFLECTION, hit.totalDistance(),
                    txGain, wallLoss, interaction.reflectionLossDb(), 0.0, 0.0, polLoss,
                    powerDbm,
                    RadioMath.phaseRad(hit.totalDistance(), env.wavelengthMeters())
                            + Math.PI / 2.0
                            + fadingPhaseShiftRad(sensor, rxX, rxY, PathType.REFLECTION, i + 1, settings),
                    1, settings);
            if (contribution != null) {
                candidates.add(contribution);
            }
        }

        return strongest(candidates, settings.getMaxReflectionPaths());
    }

    private static List<PathContribution> buildDiffractionPaths(Environment env, Sensor sensor,
                                                                double rxX, double rxY,
                                                                SimulationSettings settings) {
        if (settings.getMaxDiffractionPaths() <= 0) {
            return List.of();
        }

        double directDistance = safeDistance(sensor.getX(), sensor.getY(), rxX, rxY);
        int crossings = countWallCrossings(env.getWalls(), sensor.getX(), sensor.getY(), rxX, rxY);
        double adaptiveStep = settings.adaptiveStepMeters(directDistance);
        List<PathContribution> candidates = new ArrayList<>();

        int edgeIndex = 0;
        for (EdgePoint edge : uniqueEdges(env.getWalls())) {
            // With line of sight, only edges close to the direct ray matter.
            double edgeDistanceToDirect = Geometry.distancePointToSegment(edge.x(), edge.y(),
                    sensor.getX(), sensor.getY(), rxX, rxY);
            if (crossings == 0 && edgeDistanceToDirect > 2.5 * adaptiveStep) {
                continue;
            }

            double d1 = safeDistance(sensor.getX(), sensor.getY(), edge.x(), edge.y());
            double d2 = safeDistance(edge.x(), edge.y(), rxX, rxY);
            double excessDistance = d1 + d2 - directDistance;
            if (excessDistance < 0.05 || excessDistance > 8.0) {
                continue;
            }

            double fresnelParameter = Math.sqrt(Math.max(2.0 * excessDistance / env.wavelengthMeters(), 0.0));
            double diffractionLoss = RadioMath.knifeEdgeLossDb(fresnelParameter) + 3.0;
            double txGain = sensor.gainTowardsDb(angleDeg(sensor.getX(), sensor.getY(), edge.x(), edge.y()));
            double wallLoss = wallLossAlongPath(env.getWalls(), env.getFreqMHz(),
                    sensor.getX(), sensor.getY(), edge.x(), edge.y(), null)
                    + wallLossAlongPath(env.getWalls(), env.getFreqMHz(), edge.x(), edge.y(), rxX, rxY, null);
            double polLoss = sensor.polarizationMismatchLossDb(settings.getReceiverPolarizationDeg());
            double totalDistance = d1 + d2;
            double powerDbm = receivedPowerDbm(env, sensor, totalDistance, txGain, settings.getReceiverGainDb(),
                    wallLoss, 0.0, diffractionLoss, 0.0, polLoss,
                    fadingPowerDeltaDb(sensor, rxX, rxY, PathType.DIFFRACTION, edgeIndex + 1, settings), settings);

            PathContribution contribution = buildContribution(sensor, PathType.DIFFRACTION, totalDistance, txGain,
                    wallLoss, 0.0, diffractionLoss, 0.0, polLoss,
                    powerDbm,
                    RadioMath.phaseRad(totalDistance, env.wavelengthMeters())
                            + Math.PI / 4.0
                            + fadingPhaseShiftRad(sensor, rxX, rxY, PathType.DIFFRACTION, edgeIndex + 1, settings),
                    1, settings);
            if (contribution != null) {
                candidates.add(contribution);
            }
            edgeIndex++;
        }

        return strongest(candidates, settings.getMaxDiffractionPaths());
    }

    private static List<PathContribution> buildScatteringPaths(Environment env, Sensor sensor,
                                                               double rxX, double rxY,
                                                               SimulationSettings settings) {
        if (settings.getMaxScatteringPaths() <= 0) {
            return List.of();
        }

        List<PathContribution> candidates = new ArrayList<>();
        List<Wall> walls = env.getWalls();
        double directDistance = safeDistance(sensor.getX(), sensor.getY(), rxX, rxY);
        double adaptiveStep = settings.adaptiveStepMeters(directDistance);
        int directCrossings = countWallCrossings(walls, sensor.getX(), sensor.getY(), rxX, rxY);

        for (int i = 0; i < walls.size(); i++) {
            Wall wall = walls.get(i);
            double mx = (wall.getX1() + wall.getX2()) / 2.0;
            double my = (wall.getY1() + wall.getY2()) / 2.0;
            double midpointDistance = Geometry.distancePointToSegment(mx, my, sensor.getX(), sensor.getY(), rxX, rxY);
            if (midpointDistance > 4.0 * adaptiveStep && directCrossings == 0) {
                continue;
            }

            double d1 = safeDistance(sensor.getX(), sensor.getY(), mx, my);
            double d2 = safeDistance(mx, my, rxX, rxY);
            double totalDistance = d1 + d2;
            double incidenceAngle = Geometry.incidenceAngleDeg(sensor.getX(), sensor.getY(), mx, my,
                    wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2());
            WallInteraction interaction = Materials.interact(
                    wall.getMaterial(), env.getFreqMHz(), wall.getThicknessCm(), incidenceAngle);
            double scatteringLoss = interaction.scatteringLossDb() + 3.0;
            double txGain = sensor.gainTowardsDb(angleDeg(sensor.getX(), sensor.getY(), mx, my));
            double wallLoss = wallLossAlongPath(walls, env.getFreqMHz(), sensor.getX(), sensor.getY(), mx, my, null)
                    + wallLossAlongPath(walls, env.getFreqMHz(), mx, my, rxX, rxY, null);
            double polLoss = sensor.polarizationMismatchLossDb(settings.getReceiverPolarizationDeg());
            double powerDbm = receivedPowerDbm(env, sensor, totalDistance, txGain, settings.getReceiverGainDb(),
                    wallLoss, 0.0, 0.0, scatteringLoss, polLoss,
                    fadingPowerDeltaDb(sensor, rxX, rxY, PathType.SCATTERING, i + 1, settings), settings);

            // Diffuse scattering has no coherent phase relation with the other paths.
            PathContribution contribution = buildContribution(sensor, PathType.SCATTERING, totalDistance, txGain,
                    wallLoss, 0.0, 0.0, scatteringLoss, polLoss,
                    powerDbm,
                    RadioMath.phaseRad(totalDistance, env.wavelengthMeters())
                            + 2.0 * Math.PI * uniform(seedFor(sensor, rxX, rxY, PathType.SCATTERING, i + 1)),
                    1, settings);
            if (contribution != null) {
                candidates.add(contribution);
            }
        }

        return strongest(candidates, settings.getMaxScatteringPaths());
    }

    /** Link budget: Pr = Pt + Gt + Gr + Gsys − PL(d) − α·d − losses + fading. */
    private static double receivedPowerDbm(Environment env, Sensor sensor, double distanceMeters,
                                           double txGainDb, double receiverGainDb,
                                           double wallLossDb, double materialLossDb,
                                           double diffractionLossDb, double scatteringLossDb,
                                           double polarizationLossDb, double fadingDeltaDb,
                                           SimulationSettings settings) {
        double pathLossDb = RadioMath.logDistanceLossDb(distanceMeters, env.getFreqMHz(),
                settings.getLogDistanceExponent());
        double mediumLossDb = env.getAlphaDbPerMeter() * distanceMeters;
        return sensor.getTxPowerDbm() + txGainDb + receiverGainDb + env.getSystemGainDb()
                - pathLossDb - mediumLossDb - wallLossDb - materialLossDb
                - diffractionLossDb - scatteringLossDb - polarizationLossDb + fadingDeltaDb;
    }

    private static PathContribution buildContribution(Sensor sensor, PathType type, double distanceMeters,
                                                      double txGainDb, double wallLossDb,
                                                      double materialLossDb, double diffractionLossDb,
                                                      double scatteringLossDb, double polarizationLossDb,
                                                      double powerDbm, double phaseRad,
                                                      int bounceCount, SimulationSettings settings) {
        if (Double.isNaN(powerDbm) || powerDbm < settings.getCullingThresholdDbm()) {
            return null;
        }
        return new PathContribution(sensor.getId(), type, distanceMeters, txGainDb, wallLossDb, materialLossDb,
                diffractionLossDb, scatteringLossDb, polarizationLossDb, powerDbm, phaseRad, bounceCount);
    }

    private static List<PathContribution> strongest(List<PathContribution> candidates, int maxItems) {
        if (candidates.isEmpty() || maxItems <= 0) {
            return List.of();
        }
        candidates.sort(Comparator.comparingDouble(PathContribution::powerDbm).reversed());
        return new ArrayList<>(candidates.subList(0, Math.min(maxItems, candidates.size())));
    }

    private static double safeDistance(double x1, double y1, double x2, double y2) {
        return Math.max(MIN_DISTANCE, Math.hypot(x2 - x1, y2 - y1));
    }

    private static double angleDeg(double x1, double y1, double x2, double y2) {
        return RadioMath.normalizeAngleDeg(Math.toDegrees(Math.atan2(y2 - y1, x2 - x1)));
    }

    /** Sum of the penetration losses of every wall crossed by the segment (optionally skipping one). */
    static double wallLossAlongPath(List<Wall> walls, double freqMHz,
                                    double x1, double y1, double x2, double y2, Wall excluded) {
        double total = 0.0;
        for (Wall wall : walls) {
            if (wall == excluded) continue;
            if (Geometry.segmentsIntersect(x1, y1, x2, y2, wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2())) {
                total += wall.transmissionLossDb(freqMHz);
            }
        }
        return total;
    }

    private static int countWallCrossings(List<Wall> walls, double x1, double y1, double x2, double y2) {
        int count = 0;
        for (Wall wall : walls) {
            if (Geometry.segmentsIntersect(x1, y1, x2, y2, wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2())) {
                count++;
            }
        }
        return count;
    }

    /** Image method: mirror the transmitter across the wall and intersect the line to the receiver. */
    private static SpecularHit findSpecularHit(double txX, double txY, double rxX, double rxY, Wall wall) {
        double[] mirrored = Geometry.mirrorPoint(txX, txY, wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2());
        double[] intersection = Geometry.segmentIntersection(mirrored[0], mirrored[1], rxX, rxY,
                wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2());
        if (intersection == null) {
            return null;
        }

        double hitX = intersection[0];
        double hitY = intersection[1];
        double incidenceAngle = Geometry.incidenceAngleDeg(txX, txY, hitX, hitY,
                wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2());
        return new SpecularHit(hitX, hitY, safeDistance(txX, txY, hitX, hitY), safeDistance(hitX, hitY, rxX, rxY),
                incidenceAngle);
    }

    private static List<EdgePoint> uniqueEdges(List<Wall> walls) {
        Set<EdgePoint> seen = new HashSet<>();
        List<EdgePoint> edges = new ArrayList<>();
        for (Wall wall : walls) {
            EdgePoint start = new EdgePoint(wall.getX1(), wall.getY1());
            EdgePoint end = new EdgePoint(wall.getX2(), wall.getY2());
            if (seen.add(start)) edges.add(start);
            if (seen.add(end)) edges.add(end);
        }
        return edges;
    }

    // ---------------------------------------------------------------------------------------
    // Deterministic small-scale fading: every (sensor, point, path) gets its own fixed random
    // draw, so maps are stable between runs and independent of thread scheduling.
    // ---------------------------------------------------------------------------------------

    private static double fadingPowerDeltaDb(Sensor sensor, double rxX, double rxY,
                                             PathType type, int discriminator, SimulationSettings settings) {
        return switch (settings.getFadingModel()) {
            case NONE -> 0.0;
            case RAYLEIGH -> RadioMath.linearToDb(rayleighPower(seedFor(sensor, rxX, rxY, type, discriminator)));
            case RICIAN -> RadioMath.linearToDb(
                    ricianPower(seedFor(sensor, rxX, rxY, type, discriminator), settings.getRicianKFactorDb()));
        };
    }

    private static double fadingPhaseShiftRad(Sensor sensor, double rxX, double rxY,
                                              PathType type, int discriminator, SimulationSettings settings) {
        if (settings.getFadingModel() == FadingModel.NONE) {
            return 0.0;
        }
        return (uniform(seedFor(sensor, rxX, rxY, type, discriminator) ^ 0x9E3779B97F4A7C15L) - 0.5) * Math.PI;
    }

    /** FNV-1a style hash of the path identity, finalised with a 64-bit mixer. */
    private static long seedFor(Sensor sensor, double rxX, double rxY, PathType type, int discriminator) {
        long seed = 1469598103934665603L;
        seed ^= sensor.getId().hashCode();
        seed *= 1099511628211L;
        seed ^= Double.doubleToLongBits(rxX);
        seed *= 1099511628211L;
        seed ^= Double.doubleToLongBits(rxY);
        seed *= 1099511628211L;
        seed ^= type.ordinal();
        seed *= 1099511628211L;
        seed ^= discriminator;
        return mix64(seed);
    }

    /** Exponentially distributed power with unit mean (Rayleigh amplitude). */
    private static double rayleighPower(long seed) {
        return Math.max(-Math.log(Math.max(uniform(seed), 1e-12)), 1e-6);
    }

    /** Power of a Rician channel with unit mean and K-factor given in dB. */
    private static double ricianPower(long seed, double kFactorDb) {
        double k = Math.max(0.0, RadioMath.dbToLinear(kFactorDb));
        double sigma = Math.sqrt(1.0 / (2.0 * (k + 1.0)));
        double lineOfSight = Math.sqrt(k / (k + 1.0));
        double inPhase = lineOfSight + sigma * gaussian(seed);
        double quadrature = sigma * gaussian(seed ^ 0x9E3779B97F4A7C15L);
        return Math.max(inPhase * inPhase + quadrature * quadrature, 1e-6);
    }

    /** Standard normal sample (Box-Muller). */
    private static double gaussian(long seed) {
        double u1 = Math.max(uniform(seed), 1e-12);
        double u2 = uniform(seed ^ 0xC2B2AE3D27D4EB4FL);
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    /** Uniform sample in [0, 1) derived from a seed. */
    private static double uniform(long seed) {
        long mixed = mix64(seed);
        long bits = (mixed >>> 11) & ((1L << 53) - 1);
        return bits / (double) (1L << 53);
    }

    /** MurmurHash3 64-bit finaliser. */
    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private record SensorAggregate(String sensorId, ComplexNumber field, double powerMw,
                                   List<PathContribution> contributions) {
    }

    private record SpecularHit(double x, double y, double txToHit, double hitToRx, double incidenceAngleDeg) {
        double totalDistance() {
            return txToHit + hitToRx;
        }
    }

    private record EdgePoint(double x, double y) {
    }
}
