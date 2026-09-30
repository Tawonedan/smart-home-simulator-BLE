package io.github.phlekies.smarthome.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import io.github.phlekies.smarthome.model.AntennaType;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;
import io.github.phlekies.smarthome.simulation.FadingModel;
import io.github.phlekies.smarthome.simulation.MapMetric;
import io.github.phlekies.smarthome.simulation.PropagationMode;
import io.github.phlekies.smarthome.simulation.SimulationSettings;

/** Reads and writes projects as human-readable JSON files. */
public final class ProjectFiles {

    public static final String FORMAT = "smart-home-simulator";
    public static final int VERSION = 1;
    public static final String EXTENSION = ".shsim.json";

    // Enums are stored by constant name, not by their display label, so relabelling the UI never
    // breaks saved projects.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
            .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    /** Thrown when a file is not a valid project. */
    public static final class InvalidProjectException extends RuntimeException {
        public InvalidProjectException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private ProjectFiles() {
    }

    /** Saves a project as UTF-8 JSON. */
    public static void write(ProjectState state, Path file) throws IOException {
        Files.writeString(file, toJson(state), StandardCharsets.UTF_8);
    }

    /** Loads a project; see {@link #fromJson(String)} for the validation rules. */
    public static ProjectState read(Path file) throws IOException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Serialises a project to indented JSON. */
    public static String toJson(ProjectState state) {
        return MAPPER.writeValueAsString(ProjectFile.of(state));
    }

    /** @throws InvalidProjectException if the text is not a project this version understands */
    public static ProjectState fromJson(String json) {
        ProjectFile file;
        try {
            file = MAPPER.readValue(json, ProjectFile.class);
        } catch (JacksonException ex) {
            throw new InvalidProjectException("Not a valid project file: " + ex.getOriginalMessage(), ex);
        }
        if (!FORMAT.equals(file.format())) {
            throw new InvalidProjectException("Not a Smart Home Simulator project (format: " + file.format() + ")", null);
        }
        if (file.version() > VERSION) {
            throw new InvalidProjectException("The project was saved by a newer version (format version "
                    + file.version() + ")", null);
        }
        try {
            return file.toState();
        } catch (InvalidProjectException ex) {
            throw ex;
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidProjectException("Invalid value in project file: " + ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------------------------------------
    // File layout. Records map one-to-one to the JSON objects.
    // ---------------------------------------------------------------------------------------

    record ProjectFile(String format, int version, String template, EnvironmentJson environment,
                       SettingsJson settings, List<WallJson> walls, List<SensorJson> sensors, HubJson hub) {

        static ProjectFile of(ProjectState state) {
            Environment env = state.environment();
            return new ProjectFile(FORMAT, VERSION,
                    state.template() == null ? null : state.template().name(),
                    EnvironmentJson.of(env),
                    SettingsJson.of(state.settings()),
                    env.getWalls().stream().map(WallJson::of).toList(),
                    state.sensors().stream().map(SensorJson::of).toList(),
                    state.hub() == null ? null : HubJson.of(state.hub()));
        }

        ProjectState toState() {
            Environment env = environment.toEnvironment();
            env.setWalls(walls.stream().map(WallJson::toWall).toList());
            FloorPlanTemplate floorPlan = (template == null) ? null : Arrays.stream(FloorPlanTemplate.values())
                    .filter(t -> t.name().equals(template)).findFirst().orElse(null);
            return new ProjectState(floorPlan, env, settings.toSettings(),
                    sensors.stream().map(SensorJson::toSensor).toList(),
                    hub == null ? null : hub.toHub());
        }
    }

    record EnvironmentJson(double frequencyMHz, double bandwidthHz, double noiseFigureDb,
                           double extraLossDbPerMeter, double systemGainDb) {

        static EnvironmentJson of(Environment env) {
            return new EnvironmentJson(env.getFreqMHz(), env.getBandwidthHz(), env.getNoiseFigureDb(),
                    env.getAlphaDbPerMeter(), env.getSystemGainDb());
        }

        Environment toEnvironment() {
            Environment env = new Environment();
            env.setFreqMHz(frequencyMHz);
            env.setBandwidthHz(bandwidthHz);
            env.setNoiseFigureDb(noiseFigureDb);
            env.setAlphaDbPerMeter(extraLossDbPerMeter);
            env.setSystemGainDb(systemGainDb);
            return env;
        }
    }

    record SettingsJson(PropagationMode propagationMode, MapMetric heatmapMetric, FadingModel fading,
                        boolean diffraction, boolean scattering, boolean parallelComputation,
                        double pathLossExponent, double ricianKFactorDb, double cullingThresholdDbm,
                        double receiverSensitivityDbm, int maxReflectionPaths, int maxDiffractionPaths,
                        int maxScatteringPaths) {

        static SettingsJson of(SimulationSettings s) {
            return new SettingsJson(s.getPropagationMode(), s.getMapMetric(), s.getFadingModel(),
                    s.isDiffractionEnabled(), s.isScatteringEnabled(), s.isParallelComputation(),
                    s.getLogDistanceExponent(), s.getRicianKFactorDb(), s.getCullingThresholdDbm(),
                    s.getReceiverSensitivityDbm(), s.getMaxReflectionPaths(), s.getMaxDiffractionPaths(),
                    s.getMaxScatteringPaths());
        }

        SimulationSettings toSettings() {
            SimulationSettings s = new SimulationSettings();
            s.setPropagationMode(propagationMode);
            s.setMapMetric(heatmapMetric);
            s.setFadingModel(fading);
            s.setDiffractionEnabled(diffraction);
            s.setScatteringEnabled(scattering);
            s.setParallelComputation(parallelComputation);
            s.setLogDistanceExponent(pathLossExponent);
            s.setRicianKFactorDb(ricianKFactorDb);
            s.setCullingThresholdDbm(cullingThresholdDbm);
            s.setReceiverSensitivityDbm(receiverSensitivityDbm);
            s.setMaxReflectionPaths(maxReflectionPaths);
            s.setMaxDiffractionPaths(maxDiffractionPaths);
            s.setMaxScatteringPaths(maxScatteringPaths);
            return s;
        }
    }

    record WallJson(double x1, double y1, double x2, double y2, String material, double thicknessCm) {

        static WallJson of(Wall wall) {
            return new WallJson(wall.getX1(), wall.getY1(), wall.getX2(), wall.getY2(),
                    wall.getMaterial().getName(), wall.getThicknessCm());
        }

        Wall toWall() {
            return new Wall(x1, y1, x2, y2, Materials.byName(material).orElseThrow(
                    () -> new InvalidProjectException("Unknown wall material: " + material, null)), thicknessCm);
        }
    }

    record SensorJson(String id, String name, int x, int y, double txPowerDbm, double txGainDb,
                      AntennaType antenna, double orientationDeg, double beamwidthDeg, double patternSharpness,
                      double sideLobeAttenuationDb, double polarizationDeg) {

        static SensorJson of(Sensor s) {
            return new SensorJson(s.getId(), s.getName(), s.getX(), s.getY(), s.getTxPowerDbm(), s.getTxGainDb(),
                    s.getAntennaType(), s.getOrientationDeg(), s.getBeamwidthDeg(), s.getPatternSharpness(),
                    s.getSideLobeAttenuationDb(), s.getPolarizationDeg());
        }

        Sensor toSensor() {
            Sensor sensor = new Sensor(id, name, x, y);
            sensor.setTxPowerDbm(txPowerDbm);
            sensor.setTxGainDb(txGainDb);
            sensor.setAntennaType(antenna);
            sensor.setOrientationDeg(orientationDeg);
            sensor.setBeamwidthDeg(beamwidthDeg);
            sensor.setPatternSharpness(patternSharpness);
            sensor.setSideLobeAttenuationDb(sideLobeAttenuationDb);
            sensor.setPolarizationDeg(polarizationDeg);
            return sensor;
        }
    }

    record HubJson(String id, String name, int x, int y, double receiverGainDb, double polarizationDeg) {

        static HubJson of(Hub hub) {
            return new HubJson(hub.getId(), hub.getName(), hub.getX(), hub.getY(), hub.getReceiverGainDb(),
                    hub.getPolarizationDeg());
        }

        Hub toHub() {
            Hub hub = new Hub(id, name, x, y);
            hub.setReceiverGainDb(receiverGainDb);
            hub.setPolarizationDeg(polarizationDeg);
            return hub;
        }
    }
}
