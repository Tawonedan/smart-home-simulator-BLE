package core.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import core.Propagation;
import core.Sensor;
import core.Wall;
import core.material.MaterialsDB;
import core.material.WallInteraction;
import env.Environment;

public final class IndoorWaveEngine {

    private static final double MIN_DISTANCE = 1e-3;
    private static final double MIN_POWER_MW = 1e-15;

    private IndoorWaveEngine() {
    }

    public static HeatmapResult computeHeatmap(Environment env, List<Sensor> sensors,
                                               int width, int height, SimulationSettings settings) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(sensors, "sensors");

        SimulationSettings snapshot = (settings == null) ? new SimulationSettings() : settings.copy();
        CellResult[][] cells = new CellResult[width][height];
        List<Sensor> sensorSnapshot = List.copyOf(sensors);

        IntStream indices = IntStream.range(0, width * height);
        if (snapshot.isParallelComputation()) {
            indices = indices.parallel();
        }

        indices.forEach(index -> {
            int x = index % width;
            int y = index / width;
            cells[x][y] = computeCell(env, sensorSnapshot, x + 0.5, y + 0.5, snapshot);
        });

        return new HeatmapResult(width, height, cells, snapshot);
    }

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
            allContributions.addAll(aggregate.contributions);
            totalField = totalField.add(aggregate.field);
            totalIncoherentMw += aggregate.powerMw;
        }

        allContributions.sort(Comparator.comparingDouble(PathContribution::powerDbm).reversed());

        double signalMw = 0.0;
        String dominantSensorId = null;
        for (SensorAggregate aggregate : sensorAggregates) {
            if (aggregate.powerMw > signalMw) {
                signalMw = aggregate.powerMw;
                dominantSensorId = aggregate.sensorId;
            }
        }

        double totalPowerMw = (snapshot.getPropagationMode() == PropagationMode.WAVES)
                ? Math.max(totalField.magnitudeSquared(), MIN_POWER_MW)
                : Math.max(totalIncoherentMw, MIN_POWER_MW);
        double interferenceMw = Math.max(0.0, totalIncoherentMw - signalMw);
        double noiseDbm = env.noiseFloorDbm();
        double noiseMw = Propagation.dbmToMilliwatt(noiseDbm);
        double snrDb = Propagation.linearRatioToDb(signalMw / Math.max(noiseMw, MIN_POWER_MW));
        double sinrDb = Propagation.linearRatioToDb(signalMw / Math.max(noiseMw + interferenceMw, MIN_POWER_MW));
        double signalDbm = Propagation.milliwattToDbm(Math.max(signalMw, MIN_POWER_MW));

        return new CellResult(
                rxX,
                rxY,
                Propagation.milliwattToDbm(totalPowerMw),
                signalDbm,
                Propagation.milliwattToDbm(Math.max(interferenceMw, MIN_POWER_MW)),
                noiseDbm,
                snrDb,
                sinrDb,
                signalDbm - snapshot.getReceiverSensitivityDbm(),
                Propagation.bpskBer(sinrDb),
                Propagation.shannonCapacityMbps(sinrDb, env.getBandwidthHz()),
                totalField.phaseRad(),
                allContributions.size(),
                dominantSensorId,
                allContributions
        );
    }

    private static CellResult emptyCell(double rxX, double rxY, double noiseDbm, SimulationSettings settings) {
        return new CellResult(
                rxX,
                rxY,
                -150.0,
                -150.0,
                -150.0,
                noiseDbm,
                -150.0,
                -150.0,
                -150.0 - settings.getReceiverSensitivityDbm(),
                0.5,
                0.0,
                0.0,
                0,
                "—",
                List.of()
        );
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
            field = field.add(contribution.fieldVector());
        }

        if (settings.getPropagationMode() == PropagationMode.WAVES) {
            powerMw = Math.max(field.magnitudeSquared(), MIN_POWER_MW);
        }

        return new SensorAggregate(sensor.getId(), field, Math.max(powerMw, MIN_POWER_MW), contributions);
    }

    private static PathContribution buildDirectPath(Environment env, Sensor sensor,
                                                    double rxX, double rxY,
                                                    SimulationSettings settings) {
        double distance = safeDistance(sensor.getX(), sensor.getY(), rxX, rxY);
        double angle = angleDeg(sensor.getX(), sensor.getY(), rxX, rxY);
        double txGain = sensor.gainTowards(angle);
        double wallLoss = wallLossAlongPath(env.getWalls(), env.getFreqMHz(),
                sensor.getX(), sensor.getY(), rxX, rxY, null);
        double polLoss = sensor.polarizationMismatchLossDb(settings.getReceiverPolarizationDeg());

        double powerDbm = receivedPowerDbm(
                env,
                sensor,
                distance,
                txGain,
                settings.getReceiverGainDb(),
                wallLoss,
                0.0,
                0.0,
                0.0,
                polLoss,
                fadingPowerDeltaDb(sensor, rxX, rxY, PathType.DIRECT, 0, settings),
                settings
        );

        return buildContribution(sensor, PathType.DIRECT, distance, txGain, wallLoss,
                0.0, 0.0, 0.0, polLoss,
                powerDbm,
                Propagation.phaseRad(distance, env.wavelengthMeters())
                        + fadingPhaseShiftRad(sensor, rxX, rxY, PathType.DIRECT, 0, settings),
                0,
                settings);
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

            WallInteraction interaction = MaterialsDB.interact(
                    wall.getMaterial(), env.getFreqMHz(), wall.getThicknessCm(), hit.incidenceAngleDeg());
            if (!interaction.hasReflection()) continue;

            double txGain = sensor.gainTowards(angleDeg(sensor.getX(), sensor.getY(), hit.x(), hit.y()));
            double wallLoss = wallLossAlongPath(walls, env.getFreqMHz(),
                    sensor.getX(), sensor.getY(), hit.x(), hit.y(), wall)
                    + wallLossAlongPath(walls, env.getFreqMHz(), hit.x(), hit.y(), rxX, rxY, wall);
            double polLoss = sensor.polarizationMismatchLossDb(settings.getReceiverPolarizationDeg());
            double powerDbm = receivedPowerDbm(
                    env,
                    sensor,
                    hit.totalDistance(),
                    txGain,
                    settings.getReceiverGainDb(),
                    wallLoss,
                    interaction.getReflectLossDb(),
                    0.0,
                    0.0,
                    polLoss,
                    fadingPowerDeltaDb(sensor, rxX, rxY, PathType.REFLECTION, i + 1, settings),
                    settings
            );

            PathContribution contribution = buildContribution(sensor, PathType.REFLECTION, hit.totalDistance(), txGain,
                    wallLoss, interaction.getReflectLossDb(), 0.0, 0.0, polLoss,
                    powerDbm,
                    Propagation.phaseRad(hit.totalDistance(), env.wavelengthMeters())
                            + Math.PI / 2.0
                            + fadingPhaseShiftRad(sensor, rxX, rxY, PathType.REFLECTION, i + 1, settings),
                    1,
                    settings);
            if (contribution != null) {
                candidates.add(contribution);
            }
        }

        candidates.sort(Comparator.comparingDouble(PathContribution::powerDbm).reversed());
        return trim(candidates, settings.getMaxReflectionPaths());
    }

    private static List<PathContribution> buildDiffractionPaths(Environment env, Sensor sensor,
                                                                double rxX, double rxY,
                                                                SimulationSettings settings) {
        if (settings.getMaxDiffractionPaths() <= 0) {
            return List.of();
        }

        double directDistance = safeDistance(sensor.getX(), sensor.getY(), rxX, rxY);
        int crossings = countWallCrossings(env.getWalls(), sensor.getX(), sensor.getY(), rxX, rxY, null);
        double adaptiveStep = settings.adaptiveStepMeters(directDistance);
        List<PathContribution> candidates = new ArrayList<>();

        int edgeIndex = 0;
        for (EdgePoint edge : uniqueEdges(env.getWalls())) {
            double edgeDistanceToDirect = distancePointToSegment(edge.x(), edge.y(),
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

            double diffractionLoss = Propagation.knifeEdgeLossDb(
                    Math.sqrt(Math.max(2.0 * excessDistance / env.wavelengthMeters(), 0.0))) + 3.0;
            double txGain = sensor.gainTowards(angleDeg(sensor.getX(), sensor.getY(), edge.x(), edge.y()));
            double wallLoss = wallLossAlongPath(env.getWalls(), env.getFreqMHz(),
                    sensor.getX(), sensor.getY(), edge.x(), edge.y(), null)
                    + wallLossAlongPath(env.getWalls(), env.getFreqMHz(), edge.x(), edge.y(), rxX, rxY, null);
            double polLoss = sensor.polarizationMismatchLossDb(settings.getReceiverPolarizationDeg());
            double totalDistance = d1 + d2;
            double powerDbm = receivedPowerDbm(
                    env,
                    sensor,
                    totalDistance,
                    txGain,
                    settings.getReceiverGainDb(),
                    wallLoss,
                    0.0,
                    diffractionLoss,
                    0.0,
                    polLoss,
                    fadingPowerDeltaDb(sensor, rxX, rxY, PathType.DIFFRACTION, edgeIndex + 1, settings),
                    settings
            );

            PathContribution contribution = buildContribution(sensor, PathType.DIFFRACTION, totalDistance, txGain,
                    wallLoss, 0.0, diffractionLoss, 0.0, polLoss,
                    powerDbm,
                    Propagation.phaseRad(totalDistance, env.wavelengthMeters())
                            + Math.PI / 4.0
                            + fadingPhaseShiftRad(sensor, rxX, rxY, PathType.DIFFRACTION, edgeIndex + 1, settings),
                    1,
                    settings);
            if (contribution != null) {
                candidates.add(contribution);
            }
            edgeIndex++;
        }

        candidates.sort(Comparator.comparingDouble(PathContribution::powerDbm).reversed());
        return trim(candidates, settings.getMaxDiffractionPaths());
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
        int directCrossings = countWallCrossings(walls, sensor.getX(), sensor.getY(), rxX, rxY, null);

        for (int i = 0; i < walls.size(); i++) {
            Wall wall = walls.get(i);
            double mx = (wall.getX1() + wall.getX2()) / 2.0;
            double my = (wall.getY1() + wall.getY2()) / 2.0;
            double midpointDistance = distancePointToSegment(mx, my, sensor.getX(), sensor.getY(), rxX, rxY);
            if (midpointDistance > 4.0 * adaptiveStep && directCrossings == 0) {
                continue;
            }

            double d1 = safeDistance(sensor.getX(), sensor.getY(), mx, my);
            double d2 = safeDistance(mx, my, rxX, rxY);
            double totalDistance = d1 + d2;
            double incidenceAngle = incidenceAngleDeg(sensor.getX(), sensor.getY(), mx, my, wall);
            WallInteraction interaction = MaterialsDB.interact(
                    wall.getMaterial(), env.getFreqMHz(), wall.getThicknessCm(), incidenceAngle);
            double txGain = sensor.gainTowards(angleDeg(sensor.getX(), sensor.getY(), mx, my));
            double wallLoss = wallLossAlongPath(walls, env.getFreqMHz(),
                    sensor.getX(), sensor.getY(), mx, my, null)
                    + wallLossAlongPath(walls, env.getFreqMHz(), mx, my, rxX, rxY, null);
            double polLoss = sensor.polarizationMismatchLossDb(settings.getReceiverPolarizationDeg());
            double powerDbm = receivedPowerDbm(
                    env,
                    sensor,
                    totalDistance,
                    txGain,
                    settings.getReceiverGainDb(),
                    wallLoss,
                    0.0,
                    0.0,
                    interaction.getScatterLossDb() + 3.0,
                    polLoss,
                    fadingPowerDeltaDb(sensor, rxX, rxY, PathType.SCATTER, i + 1, settings),
                    settings
            );

            PathContribution contribution = buildContribution(sensor, PathType.SCATTER, totalDistance, txGain,
                    wallLoss, 0.0, 0.0, interaction.getScatterLossDb() + 3.0, polLoss,
                    powerDbm,
                    Propagation.phaseRad(totalDistance, env.wavelengthMeters())
                            + 2.0 * Math.PI * uniform(seedFor(sensor, rxX, rxY, PathType.SCATTER, i + 1)),
                    1,
                    settings);
            if (contribution != null) {
                candidates.add(contribution);
            }
        }

        candidates.sort(Comparator.comparingDouble(PathContribution::powerDbm).reversed());
        return trim(candidates, settings.getMaxScatteringPaths());
    }

    private static double receivedPowerDbm(Environment env, Sensor sensor, double distanceMeters,
                                           double txGainDb, double receiverGainDb,
                                           double wallLossDb, double materialLossDb,
                                           double diffractionLossDb, double scatteringLossDb,
                                           double polarizationLossDb, double fadingDeltaDb,
                                           SimulationSettings settings) {
        double pathLossDb = Propagation.logDistanceLossDb(distanceMeters, env.getFreqMHz(),
                settings.getLogDistanceExponent());
        double mediumLossDb = env.getAlphaDbPerMeter() * distanceMeters;
        return sensor.getTxDbm() + txGainDb + receiverGainDb + env.getSystemGainDb()
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

        return new PathContribution(
                sensor.getId(),
                type,
                distanceMeters,
                txGainDb,
                wallLossDb,
                materialLossDb,
                diffractionLossDb,
                scatteringLossDb,
                polarizationLossDb,
                powerDbm,
                phaseRad,
                bounceCount
        );
    }

    private static List<PathContribution> trim(List<PathContribution> candidates, int maxItems) {
        if (candidates.isEmpty() || maxItems <= 0) {
            return List.of();
        }
        return new ArrayList<>(candidates.subList(0, Math.min(maxItems, candidates.size())));
    }

    private static double safeDistance(double x1, double y1, double x2, double y2) {
        return Math.max(MIN_DISTANCE, Math.hypot(x2 - x1, y2 - y1));
    }

    private static double angleDeg(double x1, double y1, double x2, double y2) {
        return Propagation.normalizeAngleDeg(Math.toDegrees(Math.atan2(y2 - y1, x2 - x1)));
    }

    private static double wallLossAlongPath(List<Wall> walls, double freqMHz,
                                            double x1, double y1, double x2, double y2, Wall excluded) {
        double total = 0.0;
        for (Wall wall : walls) {
            if (wall == excluded) continue;
            if (Propagation.segmentsIntersect(x1, y1, x2, y2, wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2())) {
                total += wall.lossDb(freqMHz);
            }
        }
        return total;
    }

    private static int countWallCrossings(List<Wall> walls, double x1, double y1, double x2, double y2, Wall excluded) {
        int count = 0;
        for (Wall wall : walls) {
            if (wall == excluded) continue;
            if (Propagation.segmentsIntersect(x1, y1, x2, y2, wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2())) {
                count++;
            }
        }
        return count;
    }

    private static SpecularHit findSpecularHit(double txX, double txY, double rxX, double rxY, Wall wall) {
        double[] mirrored = mirrorPoint(txX, txY, wall);
        double[] intersection = segmentIntersection(
                mirrored[0], mirrored[1], rxX, rxY,
                wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2()
        );
        if (intersection == null) {
            return null;
        }

        double hitX = intersection[0];
        double hitY = intersection[1];
        double incidenceAngle = incidenceAngleDeg(txX, txY, hitX, hitY, wall);
        return new SpecularHit(hitX, hitY,
                safeDistance(txX, txY, hitX, hitY),
                safeDistance(hitX, hitY, rxX, rxY),
                incidenceAngle);
    }

    private static double[] mirrorPoint(double px, double py, Wall wall) {
        double ax = wall.getX1();
        double ay = wall.getY1();
        double bx = wall.getX2();
        double by = wall.getY2();
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 <= 1e-12) {
            return new double[] { px, py };
        }

        double t = ((px - ax) * dx + (py - ay) * dy) / len2;
        double projX = ax + t * dx;
        double projY = ay + t * dy;
        return new double[] { 2.0 * projX - px, 2.0 * projY - py };
    }

    private static double incidenceAngleDeg(double fromX, double fromY, double hitX, double hitY, Wall wall) {
        double wx = wall.getX2() - wall.getX1();
        double wy = wall.getY2() - wall.getY1();
        double wallLength = Math.hypot(wx, wy);
        if (wallLength <= 1e-12) {
            return 0.0;
        }

        double nx = -wy / wallLength;
        double ny = wx / wallLength;
        double dx = hitX - fromX;
        double dy = hitY - fromY;
        double dl = Math.hypot(dx, dy);
        if (dl <= 1e-12) {
            return 0.0;
        }

        double dot = Math.abs((dx / dl) * nx + (dy / dl) * ny);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    private static double[] segmentIntersection(double x1, double y1, double x2, double y2,
                                                double x3, double y3, double x4, double y4) {
        double den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(den) < 1e-12) {
            return null;
        }

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den;
        double u = ((x1 - x3) * (y1 - y2) - (y1 - y3) * (x1 - x2)) / den;
        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) {
            return null;
        }

        return new double[] {
                x1 + t * (x2 - x1),
                y1 + t * (y2 - y1)
        };
    }

    private static List<EdgePoint> uniqueEdges(List<Wall> walls) {
        Set<String> seen = new HashSet<>();
        List<EdgePoint> edges = new ArrayList<>();
        for (Wall wall : walls) {
            addEdgeIfAbsent(edges, seen, wall.getX1(), wall.getY1());
            addEdgeIfAbsent(edges, seen, wall.getX2(), wall.getY2());
        }
        return edges;
    }

    private static void addEdgeIfAbsent(List<EdgePoint> edges, Set<String> seen, double x, double y) {
        String key = x + ":" + y;
        if (seen.add(key)) {
            edges.add(new EdgePoint(x, y));
        }
    }

    private static double distancePointToSegment(double px, double py,
                                                 double x1, double y1, double x2, double y2) {
        double vx = x2 - x1;
        double vy = y2 - y1;
        double wx = px - x1;
        double wy = py - y1;
        double c1 = vx * wx + vy * wy;
        if (c1 <= 0.0) return Math.hypot(px - x1, py - y1);
        double c2 = vx * vx + vy * vy;
        if (c2 <= c1) return Math.hypot(px - x2, py - y2);
        double t = c1 / c2;
        double projX = x1 + t * vx;
        double projY = y1 + t * vy;
        return Math.hypot(px - projX, py - projY);
    }

    private static double fadingPowerDeltaDb(Sensor sensor, double rxX, double rxY,
                                             PathType type, int discriminator,
                                             SimulationSettings settings) {
        return switch (settings.getFadingModel()) {
            case NONE -> 0.0;
            case RAYLEIGH -> Propagation.linearRatioToDb(rayleighPower(seedFor(sensor, rxX, rxY, type, discriminator)));
            case RICIAN -> Propagation.linearRatioToDb(
                    ricianPower(seedFor(sensor, rxX, rxY, type, discriminator), settings.getRicianKFactorDb()));
        };
    }

    private static double fadingPhaseShiftRad(Sensor sensor, double rxX, double rxY,
                                              PathType type, int discriminator,
                                              SimulationSettings settings) {
        if (settings.getFadingModel() == FadingModel.NONE) {
            return 0.0;
        }
        return (uniform(seedFor(sensor, rxX, rxY, type, discriminator) ^ 0x9E3779B97F4A7C15L) - 0.5) * Math.PI;
    }

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

    private static double rayleighPower(long seed) {
        return Math.max(-Math.log(Math.max(uniform(seed), 1e-12)), 1e-6);
    }

    private static double ricianPower(long seed, double kFactorDb) {
        double k = Math.max(0.0, Propagation.dbToLinearRatio(kFactorDb));
        double sigma = Math.sqrt(1.0 / (2.0 * (k + 1.0)));
        double s = Math.sqrt(k / (k + 1.0));
        double g1 = gaussian(seed);
        double g2 = gaussian(seed ^ 0x9E3779B97F4A7C15L);
        double i = s + sigma * g1;
        double q = sigma * g2;
        return Math.max(i * i + q * q, 1e-6);
    }

    private static double gaussian(long seed) {
        double u1 = Math.max(uniform(seed), 1e-12);
        double u2 = uniform(seed ^ 0xC2B2AE3D27D4EB4FL);
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    private static double uniform(long seed) {
        long mixed = mix64(seed);
        long bits = (mixed >>> 11) & ((1L << 53) - 1);
        return bits / (double) (1L << 53);
    }

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
