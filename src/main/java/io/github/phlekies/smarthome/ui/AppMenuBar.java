package io.github.phlekies.smarthome.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

import io.github.phlekies.smarthome.app.DemoScenario;
import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;

/** Application menu with keyboard shortcuts. */
final class AppMenuBar {

    private AppMenuBar() {
    }

    static MenuBar build(SimulatorModel model, UiState state, ProjectController projects,
                         SimulationController simulation, FloorPlanEditor editor, Stage stage) {
        Menu examples = new Menu("Open example");
        examples.getItems().addAll(
                item("Smart apartment", null, () -> projects.openExample("Smart apartment", DemoScenario.smartApartment())),
                item("Office floor", null, () -> projects.openExample("Office floor", DemoScenario.officeFloor())),
                item("Warehouse", null, () -> projects.openExample("Warehouse", DemoScenario.warehouse())));

        Menu file = new Menu("File", null,
                item("New project", shortcut(KeyCode.N), projects::newProject),
                item("Open...", shortcut(KeyCode.O), projects::open),
                examples,
                new SeparatorMenuItem(),
                item("Save", shortcut(KeyCode.S), projects::save),
                item("Save as...", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                        projects::saveAs),
                new SeparatorMenuItem(),
                item("Export plan image...", shortcut(KeyCode.E), projects::exportImage),
                item("Export coverage report...", shortcut(KeyCode.R), projects::exportReport),
                new SeparatorMenuItem(),
                item("Exit", null, () -> {
                    if (projects.confirmDiscard()) {
                        Platform.exit();
                    }
                }));

        Menu edit = new Menu("Edit", null,
                item("Undo", shortcut(KeyCode.Z), () -> {
                    if (model.undo()) state.status.set("Last plan edit undone.");
                }),
                item("Redo", shortcut(KeyCode.Y), () -> {
                    if (model.redo()) state.status.set("Plan edit redone.");
                }),
                new SeparatorMenuItem(),
                item("Delete selection", new KeyCodeCombination(KeyCode.DELETE), () -> deleteSelection(model, state)),
                item("Cancel drawing", new KeyCodeCombination(KeyCode.ESCAPE), () -> {
                    editor.cancelDraft();
                    state.tool.set(EditorTool.SELECT);
                }));

        CheckMenuItem heatmap = new CheckMenuItem("Coverage heatmap");
        heatmap.setAccelerator(shortcut(KeyCode.H));
        heatmap.selectedProperty().bindBidirectional(state.heatmapVisible);
        CheckMenuItem links = new CheckMenuItem("Sensor-hub links");
        links.setAccelerator(shortcut(KeyCode.L));
        links.selectedProperty().bindBidirectional(state.linksVisible);
        CheckMenuItem dark = new CheckMenuItem("Dark theme");
        dark.setAccelerator(shortcut(KeyCode.T));
        dark.selectedProperty().bindBidirectional(state.darkTheme);
        Menu view = new Menu("View", null, heatmap, links, new SeparatorMenuItem(), dark);

        Menu simulationMenu = new Menu("Simulation", null,
                item("Optimise hub position", shortcut(KeyCode.P), simulation::optimiseHub),
                new SeparatorMenuItem(),
                item("Launch rays", null, simulation::launchRays),
                item("Launch waves", null, simulation::launchWaves),
                item("Stop animations", null, simulation::stopAnimations),
                new SeparatorMenuItem(),
                item("Simulation summary...", null, simulation::openSummary));

        Menu help = new Menu("Help", null, item("About Smart Home Simulator", null, () -> about(stage)));

        return new MenuBar(file, edit, view, simulationMenu, help);
    }

    private static void deleteSelection(SimulatorModel model, UiState state) {
        Wall wall = state.selectedWall.get();
        Device device = state.selectedDevice.get();
        if (wall != null) {
            model.removeWall(wall);
            state.status.set("Wall deleted. Use Undo to bring it back.");
        } else if (device instanceof Sensor sensor) {
            model.removeSensor(sensor);
            state.status.set(sensor.getName() + " removed.");
        } else if (device instanceof Hub) {
            model.removeHub();
            state.status.set("Hub removed.");
        }
    }

    private static void about(Stage owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, """
                Indoor Wi-Fi propagation simulator for smart-home and IoT deployments.

                Multipath engine (reflection, diffraction, scattering, fading), coverage heatmaps,
                ray tracing and automatic hub placement.

                Java 21 · JavaFX 21 · MIT License
                https://github.com/Phlekies/Smart_Home_Simulator_2""", ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle("About");
        alert.setHeaderText("Smart Home Simulator");
        alert.showAndWait();
    }

    private static MenuItem item(String text, KeyCombination accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setAccelerator(accelerator);
        item.setOnAction(e -> action.run());
        return item;
    }

    private static KeyCombination shortcut(KeyCode key) {
        return new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN);
    }
}
