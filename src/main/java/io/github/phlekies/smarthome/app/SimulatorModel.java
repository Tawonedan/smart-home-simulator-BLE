package io.github.phlekies.smarthome.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Material;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;
import io.github.phlekies.smarthome.simulation.CellResult;
import io.github.phlekies.smarthome.simulation.PropagationEngine;
import io.github.phlekies.smarthome.simulation.SimulationSettings;

/**
 * Application state, independent of any UI toolkit: the floor plan with undo/redo, the
 * deployed devices and the simulation parameters. Views subscribe to {@link Change} events.
 */
public final class SimulatorModel {

    /** Plan size in metres; the heatmap has one cell per square metre. */
    public static final int PLAN_WIDTH_METERS = 50;
    public static final int PLAN_HEIGHT_METERS = 40;

    private static final int MAX_UNDO_STEPS = 100;
    private static final String HUB_ID = "H1";

    /** What part of the state changed. */
    public enum Change {
        WALLS,
        DEVICES,
        RADIO,
        SETTINGS,
        /** Only how results are displayed changed (e.g. the heatmap metric); no recomputation needed. */
        DISPLAY
    }

    private final Environment environment = new Environment();
    private final SimulationSettings settings = new SimulationSettings();
    private final List<Sensor> sensors = new ArrayList<>();
    private Hub hub;
    private FloorPlanTemplate activeTemplate;
    private final Deque<List<Wall>> undoStack = new ArrayDeque<>();
    private final Deque<List<Wall>> redoStack = new ArrayDeque<>();
    private final List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

    public SimulatorModel() {
        loadTemplate(FloorPlanTemplate.defaultTemplate());
    }

    public void addListener(Consumer<Change> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Change> listener) {
        listeners.remove(listener);
    }

    private void fire(Change change) {
        listeners.forEach(listener -> listener.accept(change));
    }

    // ---------------------------------------------------------------------------------------
    // Floor plan
    // ---------------------------------------------------------------------------------------

    /** The live wall instances of the plan (read-only list). */
    public List<Wall> walls() {
        return environment.getWalls();
    }

    public void addWalls(List<Wall> newWalls) {
        if (newWalls == null || newWalls.isEmpty()) {
            return;
        }
        checkpoint();
        newWalls.forEach(wall -> environment.addWall(wall.copy()));
        fire(Change.WALLS);
    }

    /** Adds the four walls of an axis-aligned room. Returns false for a degenerate rectangle. */
    public boolean addRoom(double x1, double y1, double x2, double y2, Material material, double thicknessCm) {
        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double maxX = Math.max(x1, x2);
        double maxY = Math.max(y1, y2);
        if (maxX - minX < 1e-6 || maxY - minY < 1e-6) {
            return false;
        }
        addWalls(List.of(
                new Wall(minX, minY, maxX, minY, material, thicknessCm),
                new Wall(maxX, minY, maxX, maxY, material, thicknessCm),
                new Wall(maxX, maxY, minX, maxY, material, thicknessCm),
                new Wall(minX, maxY, minX, minY, material, thicknessCm)));
        return true;
    }

    public void removeWall(Wall wall) {
        if (!containsWall(wall)) {
            return;
        }
        checkpoint();
        environment.removeWall(wall);
        fire(Change.WALLS);
    }

    public void updateWall(Wall wall, Material material, double thicknessCm) {
        if (!containsWall(wall)) {
            return;
        }
        checkpoint();
        wall.setMaterial(material);
        wall.setThicknessCm(thicknessCm);
        fire(Change.WALLS);
    }

    public void clearWalls() {
        if (walls().isEmpty()) {
            return;
        }
        checkpoint();
        environment.setWalls(List.of());
        fire(Change.WALLS);
    }

    /** Replaces the plan with a template. The undo history starts again from here. */
    public void loadTemplate(FloorPlanTemplate template) {
        activeTemplate = template;
        environment.setWalls(template.walls());
        undoStack.clear();
        redoStack.clear();
        fire(Change.WALLS);
    }

    /** Adds a template's walls to the current plan, shifted by an offset in metres. */
    public void appendTemplate(FloorPlanTemplate template, double offsetX, double offsetY) {
        addWalls(template.walls().stream().map(wall -> wall.translated(offsetX, offsetY)).toList());
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        redoStack.push(copyOfWalls());
        environment.setWalls(undoStack.pop());
        fire(Change.WALLS);
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        undoStack.push(copyOfWalls());
        environment.setWalls(redoStack.pop());
        fire(Change.WALLS);
        return true;
    }

    private void checkpoint() {
        undoStack.push(copyOfWalls());
        while (undoStack.size() > MAX_UNDO_STEPS) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private List<Wall> copyOfWalls() {
        return walls().stream().map(Wall::copy).toList();
    }

    private boolean containsWall(Wall wall) {
        return wall != null && walls().stream().anyMatch(existing -> existing == wall);
    }

    public Optional<FloorPlanTemplate> activeTemplate() {
        return Optional.ofNullable(activeTemplate);
    }

    /** True while the plan is still exactly the last loaded template. */
    public boolean matchesActiveTemplate() {
        if (activeTemplate == null) {
            return false;
        }
        List<Wall> current = walls();
        List<Wall> template = activeTemplate.walls();
        if (current.size() != template.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).isEquivalentTo(template.get(i))) {
                return false;
            }
        }
        return true;
    }

    public String scenarioName() {
        if (walls().isEmpty()) return "Empty plan";
        if (matchesActiveTemplate()) return activeTemplate.displayName();
        return "Custom plan";
    }

    public String scenarioDescription() {
        if (walls().isEmpty()) return "There are no walls in the current plan.";
        if (matchesActiveTemplate()) return activeTemplate.description();
        return "The plan has been edited since the template was loaded.";
    }

    // ---------------------------------------------------------------------------------------
    // Devices
    // ---------------------------------------------------------------------------------------

    public List<Sensor> sensors() {
        return Collections.unmodifiableList(sensors);
    }

    /** Adds a sensor with the next free id ({@code S1}, {@code S2}...), clamped to the plan. */
    public Sensor addSensor(int x, int y) {
        int number = nextSensorNumber();
        Sensor sensor = new Sensor("S" + number, "Sensor " + number, clampX(x), clampY(y));
        sensors.add(sensor);
        fire(Change.DEVICES);
        return sensor;
    }

    public void removeSensor(Sensor sensor) {
        if (sensors.remove(sensor)) {
            fire(Change.DEVICES);
        }
    }

    public Optional<Hub> hub() {
        return Optional.ofNullable(hub);
    }

    /** Places the hub, or moves it keeping its receiver configuration. */
    public Hub placeHub(int x, int y) {
        if (hub == null) {
            hub = new Hub(HUB_ID, "Hub", clampX(x), clampY(y));
        } else {
            hub.moveTo(clampX(x), clampY(y));
        }
        fire(Change.DEVICES);
        return hub;
    }

    public void removeHub() {
        if (hub != null) {
            hub = null;
            fire(Change.DEVICES);
        }
    }

    /** Must be called after mutating a sensor or the hub directly. */
    public void devicesChanged() {
        fire(Change.DEVICES);
    }

    private int nextSensorNumber() {
        int next = 1;
        for (Sensor sensor : sensors) {
            String id = sensor.getId();
            if (id.matches("S\\d+")) {
                next = Math.max(next, Integer.parseInt(id.substring(1)) + 1);
            }
        }
        return next;
    }

    private static int clampX(int x) {
        return Math.max(0, Math.min(PLAN_WIDTH_METERS, x));
    }

    private static int clampY(int y) {
        return Math.max(0, Math.min(PLAN_HEIGHT_METERS, y));
    }

    // ---------------------------------------------------------------------------------------
    // Radio environment and engine settings
    // ---------------------------------------------------------------------------------------

    public Environment environment() {
        return environment;
    }

    public SimulationSettings settings() {
        return settings;
    }

    /** Must be called after mutating {@link #environment()} radio parameters. */
    public void radioChanged() {
        fire(Change.RADIO);
    }

    /** Must be called after mutating {@link #settings()}. */
    public void settingsChanged() {
        fire(Change.SETTINGS);
    }

    public void displayChanged() {
        fire(Change.DISPLAY);
    }

    // ---------------------------------------------------------------------------------------
    // Snapshots and link queries
    // ---------------------------------------------------------------------------------------

    /** Immutable copy of everything a background computation needs. */
    public Snapshot snapshot() {
        return new Snapshot(environment.copy(), sensors.stream().map(Sensor::copy).toList(), heatmapSettings());
    }

    /** Heatmap cells are isotropic probes aligned with the hub polarization (if any). */
    public SimulationSettings heatmapSettings() {
        SimulationSettings copy = settings.copy();
        copy.setReceiverGainDb(0.0);
        copy.setReceiverPolarizationDeg(hub == null ? 0.0 : hub.getPolarizationDeg());
        return copy;
    }

    /** Settings for a receiver with the hub's antenna gain and polarization. */
    public SimulationSettings receiverSettings() {
        SimulationSettings copy = settings.copy();
        copy.setReceiverGainDb(hub == null ? 0.0 : hub.getReceiverGainDb());
        copy.setReceiverPolarizationDeg(hub == null ? 0.0 : hub.getPolarizationDeg());
        return copy;
    }

    /** Combined link at the hub (strongest sensor as signal, the rest as interference). */
    public Optional<CellResult> hubLink() {
        if (hub == null) {
            return Optional.empty();
        }
        return Optional.of(PropagationEngine.computeCell(environment, sensors, hub.getX(), hub.getY(),
                receiverSettings()));
    }

    /** Link from a single sensor to the hub, ignoring the other sensors. */
    public Optional<CellResult> sensorLink(Sensor sensor) {
        if (hub == null) {
            return Optional.empty();
        }
        return Optional.of(PropagationEngine.computeCell(environment, List.of(sensor), hub.getX(), hub.getY(),
                receiverSettings()));
    }

    /** Link metrics at an arbitrary point, as seen by a receiver with the hub's antenna. */
    public CellResult probe(double x, double y) {
        return PropagationEngine.computeCell(environment, sensors, x, y, receiverSettings());
    }

    public record Snapshot(Environment environment, List<Sensor> sensors, SimulationSettings settings) {
    }
}
