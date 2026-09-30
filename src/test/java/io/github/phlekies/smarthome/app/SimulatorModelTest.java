package io.github.phlekies.smarthome.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.phlekies.smarthome.app.SimulatorModel.Change;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;

class SimulatorModelTest {

    private SimulatorModel model;
    private final List<Change> changes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        model = new SimulatorModel();
        model.addListener(changes::add);
    }

    @Nested
    @DisplayName("Floor plan editing")
    class FloorPlanEditing {

        @Test
        void startsWithTheDefaultTemplateAndNoHistory() {
            assertEquals(FloorPlanTemplate.defaultTemplate().walls().size(), model.walls().size());
            assertTrue(model.matchesActiveTemplate());
            assertFalse(model.canUndo());
        }

        @Test
        void undoAndRedoAnAddedWall() {
            int before = model.walls().size();
            model.addWalls(List.of(new Wall(0, 0, 5, 0, Materials.WOOD, 5)));
            assertEquals(before + 1, model.walls().size());

            assertTrue(model.undo());
            assertEquals(before, model.walls().size());
            assertTrue(model.redo());
            assertEquals(before + 1, model.walls().size());
        }

        @Test
        void undoRestoresADeletedWall() {
            // Regression: undoing a deletion used to try to delete the wall again.
            Wall victim = model.walls().get(3);
            Wall snapshot = victim.copy();
            model.removeWall(victim);
            assertTrue(model.walls().stream().noneMatch(w -> w.isEquivalentTo(snapshot)));

            model.undo();
            assertTrue(model.walls().stream().anyMatch(w -> w.isEquivalentTo(snapshot)));
            assertTrue(model.matchesActiveTemplate());
        }

        @Test
        void undoRestoresMaterialAndThickness() {
            Wall wall = model.walls().getFirst();
            model.updateWall(wall, Materials.GLASS, 4);
            assertSame(Materials.GLASS, model.walls().getFirst().getMaterial());

            model.undo();
            assertSame(Materials.BRICK, model.walls().getFirst().getMaterial());
            assertEquals(12.0, model.walls().getFirst().getThicknessCm());
        }

        @Test
        void clearingThePlanCanBeUndone() {
            int before = model.walls().size();
            model.clearWalls();
            assertTrue(model.walls().isEmpty());
            assertEquals("Empty plan", model.scenarioName());
            model.undo();
            assertEquals(before, model.walls().size());
        }

        @Test
        void aNewEditClearsTheRedoHistory() {
            model.addWalls(List.of(new Wall(0, 0, 5, 0, Materials.WOOD, 5)));
            model.undo();
            assertTrue(model.canRedo());
            model.addWalls(List.of(new Wall(0, 0, 0, 5, Materials.WOOD, 5)));
            assertFalse(model.canRedo());
        }

        @Test
        void loadingATemplateResetsTheHistory() {
            model.addWalls(List.of(new Wall(0, 0, 5, 0, Materials.WOOD, 5)));
            model.loadTemplate(FloorPlanTemplate.HOTEL_SUITE);
            assertFalse(model.canUndo());
            assertEquals("Hotel suite", model.scenarioName());
        }

        @Test
        void editingThePlanMakesItCustom() {
            model.addRoom(40, 30, 45, 35, Materials.DRYWALL, 8);
            assertFalse(model.matchesActiveTemplate());
            assertEquals("Custom plan", model.scenarioName());
        }

        @Test
        void roomsNeedAnArea() {
            int before = model.walls().size();
            assertFalse(model.addRoom(5, 5, 5, 10, Materials.DRYWALL, 8));
            assertEquals(before, model.walls().size());
            assertTrue(model.addRoom(10, 10, 4, 4, Materials.DRYWALL, 8));
            assertEquals(before + 4, model.walls().size());
        }

        @Test
        void appendedTemplatesAreShifted() {
            model.clearWalls();
            model.appendTemplate(FloorPlanTemplate.COMPACT_STUDIO, 10, 5);
            Wall original = FloorPlanTemplate.COMPACT_STUDIO.walls().getFirst();
            Wall shifted = model.walls().getFirst();
            assertEquals(original.getX1() + 10, shifted.getX1(), 1e-9);
            assertEquals(original.getY1() + 5, shifted.getY1(), 1e-9);
        }

        @Test
        void wallEditsAreBroadcast() {
            changes.clear();
            model.addWalls(List.of(new Wall(0, 0, 5, 0, Materials.WOOD, 5)));
            assertEquals(List.of(Change.WALLS), changes);
        }
    }

    @Nested
    @DisplayName("Devices")
    class Devices {

        @Test
        void sensorsGetSequentialIds() {
            Sensor first = model.addSensor(1, 1);
            Sensor second = model.addSensor(2, 2);
            assertEquals("S1", first.getId());
            assertEquals("S2", second.getId());

            model.removeSensor(first);
            assertEquals("S3", model.addSensor(3, 3).getId());
        }

        @Test
        void devicesAreClampedToThePlan() {
            Sensor sensor = model.addSensor(-5, 99);
            assertEquals(0, sensor.getX());
            assertEquals(SimulatorModel.PLAN_HEIGHT_METERS, sensor.getY());
        }

        @Test
        void movingTheHubKeepsItsConfiguration() {
            Hub hub = model.placeHub(10, 10);
            hub.setReceiverGainDb(5.0);
            Hub moved = model.placeHub(20, 15);
            assertSame(hub, moved);
            assertEquals(20, moved.getX());
            assertEquals(5.0, moved.getReceiverGainDb());
        }

        @Test
        void deviceChangesAreBroadcast() {
            changes.clear();
            Sensor sensor = model.addSensor(1, 1);
            model.placeHub(5, 5);
            model.removeSensor(sensor);
            model.removeHub();
            assertEquals(List.of(Change.DEVICES, Change.DEVICES, Change.DEVICES, Change.DEVICES), changes);
        }

        @Test
        void linksNeedAHub() {
            Sensor sensor = model.addSensor(5, 5);
            assertTrue(model.hubLink().isEmpty());
            assertTrue(model.sensorLink(sensor).isEmpty());

            model.placeHub(20, 10);
            assertTrue(model.hubLink().isPresent());
            assertEquals("S1", model.sensorLink(sensor).orElseThrow().dominantSensorId());
        }
    }

    @Nested
    @DisplayName("Moving devices")
    class MovingDevices {

        @Test
        void movingADeviceIsBroadcastOnlyWhenItChangesPosition() {
            Sensor sensor = model.addSensor(5, 5);
            changes.clear();
            assertFalse(model.moveDevice(sensor, 5, 5));
            assertTrue(model.moveDevice(sensor, 7, 9));
            assertEquals(List.of(Change.DEVICES), changes);
            assertEquals(7, sensor.getX());
        }

        @Test
        void movedDevicesStayInsideThePlan() {
            Hub hub = model.placeHub(5, 5);
            model.moveDevice(hub, 500, -3);
            assertEquals(SimulatorModel.PLAN_WIDTH_METERS, hub.getX());
            assertEquals(0, hub.getY());
        }

        @Test
        void linkSummariesFollowTheHub() {
            Sensor sensor = model.addSensor(5, 5);
            assertTrue(model.linkSummaries().isEmpty());
            Hub hub = model.placeHub(6, 5);
            double near = model.linkSummaries().getFirst().receivedPowerDbm();
            model.moveDevice(hub, 30, 25);
            double far = model.linkSummaries().getFirst().receivedPowerDbm();
            assertTrue(far < near, "moving the hub away must weaken the link");
            assertEquals(sensor, model.linkSummaries().getFirst().sensor());
        }
    }

    @Nested
    @DisplayName("Whole-project state")
    class WholeProject {

        @Test
        void loadingAProjectReplacesEverythingAndNotifiesEveryView() {
            model.addWalls(List.of(new Wall(0, 0, 5, 0, Materials.WOOD, 5)));
            changes.clear();

            model.load(DemoScenario.warehouse());

            assertEquals(5, model.sensors().size());
            assertEquals(6, model.hub().orElseThrow().getX());
            assertEquals(FloorPlanTemplate.WAREHOUSE_WITH_AISLES.walls().size(), model.walls().size());
            assertEquals("Warehouse with aisles", model.scenarioName());
            assertFalse(model.canUndo());
            assertTrue(changes.containsAll(List.of(Change.WALLS, Change.DEVICES, Change.RADIO, Change.SETTINGS)));
        }

        @Test
        void stateIsADeepCopy() {
            model.load(DemoScenario.smartApartment());
            ProjectState saved = model.state();
            model.sensors().getFirst().moveTo(1, 1);
            model.clearWalls();

            assertEquals(7, saved.sensors().getFirst().getX());
            assertFalse(saved.environment().getWalls().isEmpty());
        }

        @Test
        void aCustomPlanIsSavedWithoutTemplate() {
            model.addRoom(40, 30, 45, 35, Materials.DRYWALL, 8);
            assertEquals(null, model.state().template());
        }

        @Test
        void theFootprintBoundsTheWalls() {
            model.load(DemoScenario.smartApartment());
            var footprint = model.footprint();
            assertEquals(1.3, footprint.minX(), 1e-9);
            assertEquals(41.6, footprint.maxX(), 1e-9);
            model.clearWalls();
            assertEquals(SimulatorModel.PLAN_WIDTH_METERS, model.footprint().maxX());
        }
    }

    @Nested
    @DisplayName("Snapshots")
    class Snapshots {

        @Test
        void snapshotsAreIsolatedFromLaterEdits() {
            Sensor sensor = model.addSensor(5, 5);
            SimulatorModel.Snapshot snapshot = model.snapshot();

            sensor.moveTo(30, 30);
            model.clearWalls();
            model.environment().setFreqMHz(5200);

            assertEquals(5, snapshot.sensors().getFirst().getX());
            assertNotSame(sensor, snapshot.sensors().getFirst());
            assertFalse(snapshot.environment().getWalls().isEmpty());
            assertEquals(2400.0, snapshot.environment().getFreqMHz());
        }

        @Test
        void heatmapProbesUseTheHubPolarizationButNoAntennaGain() {
            Hub hub = model.placeHub(10, 10);
            hub.setPolarizationDeg(45);
            hub.setReceiverGainDb(6);

            assertEquals(45.0, model.heatmapSettings().getReceiverPolarizationDeg());
            assertEquals(0.0, model.heatmapSettings().getReceiverGainDb());
            assertEquals(6.0, model.receiverSettings().getReceiverGainDb());
        }
    }
}
