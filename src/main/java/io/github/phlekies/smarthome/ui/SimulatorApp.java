package io.github.phlekies.smarthome.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import io.github.phlekies.smarthome.app.DemoScenario;
import io.github.phlekies.smarthome.app.SimulatorModel;

/** JavaFX entry point: wires the model, the plan view, the editor, the toolbar and the side panel. */
public final class SimulatorApp extends Application {

    // Package-private so the UI tests can drive and inspect a running application.
    SimulatorModel model;
    UiState state;
    SimulationController simulation;
    PlanViewport viewport;
    Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        state = new UiState();
        state.darkTheme.addListener((obs, old, dark) -> applyTheme(dark));
        applyTheme(state.darkTheme.get());

        model = new SimulatorModel();

        PlanView planView = new PlanView(model, state);
        HeatmapService heatmaps = new HeatmapService();
        CellProbe probe = new CellProbe(model, state, heatmaps, planView);
        FloorPlanEditor editor = new FloorPlanEditor(model, state, planView, probe);
        simulation = new SimulationController(model, state, planView, heatmaps, stage);

        HeatmapLegend legend = new HeatmapLegend();
        legend.showMetric(model.settings().getMapMetric());
        legend.visibleProperty().bind(state.heatmapVisible);
        model.addListener(change -> legend.showMetric(model.settings().getMapMetric()));
        state.coverage.addListener((obs, old, stats) -> legend.showCoverage(stats));
        StackPane plan = new StackPane(planView);
        plan.getStyleClass().add("plan-stack");
        viewport = new PlanViewport(plan, legend);
        viewport.setPreferredViewport(PlanCoordinates.VIEW_WIDTH_PX + 24, PlanCoordinates.VIEW_HEIGHT_PX + 24);

        ProjectController projects = new ProjectController(model, state, stage, getHostServices(),
                () -> Snapshots.planWithOverlay(plan, legend));
        projects.openExample("Smart apartment", DemoScenario.smartApartment());

        BorderPane root = new BorderPane();
        root.setTop(new VBox(AppMenuBar.build(model, state, projects, simulation, editor, viewport, stage),
                EditorToolBar.build(model, state, simulation, viewport)));
        root.setCenter(viewport);
        root.setRight(ControlPanel.build(model, state, editor, simulation, projects));
        root.setBottom(statusBar(state, heatmaps, simulation));

        Scene scene = new Scene(root);
        scene.getStylesheets().add(SimulatorApp.class.getResource("app.css").toExternalForm());
        stage.setScene(scene);
        for (int size : new int[] { 16, 32, 48, 64, 128, 256 }) {
            stage.getIcons().add(new Image(SimulatorApp.class.getResourceAsStream("icon-" + size + ".png")));
        }
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.sizeToScene();
        fitToScreen(stage);
        stage.show();

        state.status.set("Smart apartment demo loaded. Drag the devices, or press Optimise hub.");
        state.heatmapVisible.set(true);
    }

    @Override
    public void stop() {
        if (simulation != null) {
            simulation.shutdown();
        }
    }

    private static void applyTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    private static HBox statusBar(UiState state, HeatmapService heatmaps, SimulationController simulation) {
        Label status = new Label();
        status.textProperty().bind(state.status);
        Label pointer = new Label();
        pointer.textProperty().bind(state.pointer);
        pointer.getStyleClass().add("status-pointer");

        ProgressBar progress = new ProgressBar();
        progress.progressProperty().bind(heatmaps.progressProperty());
        progress.setPrefWidth(140);
        Label progressLabel = new Label();
        progressLabel.textProperty().bind(Bindings.createStringBinding(
                () -> String.format("Computing coverage %.0f%%", Math.max(0, heatmaps.progressProperty().get()) * 100),
                heatmaps.progressProperty()));
        for (var node : new javafx.scene.Node[] { progress, progressLabel }) {
            node.visibleProperty().bind(heatmaps.runningProperty());
            node.managedProperty().bind(heatmaps.runningProperty());
        }
        Label optimising = new Label("Optimising hub position...");
        optimising.visibleProperty().bind(simulation.optimisingProperty());
        optimising.managedProperty().bind(simulation.optimisingProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, status, spacer, optimising, progressLabel, progress, pointer);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    /** Never open a window larger than the visible area of the primary screen. */
    private static void fitToScreen(Stage stage) {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        if (stage.getWidth() > screen.getWidth()) stage.setWidth(screen.getWidth());
        if (stage.getHeight() > screen.getHeight()) stage.setHeight(screen.getHeight());
        stage.centerOnScreen();
    }
}
