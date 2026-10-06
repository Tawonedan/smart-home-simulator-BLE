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

    private static final double ASPECT_RATIO = 16.0 / 9.0;
    private boolean adjustingSize;

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
        legend.showMetric(model.settings().getMapMetric(), model.isBleMode());
        legend.visibleProperty().bind(state.heatmapVisible);
        model.addListener(change -> legend.showMetric(model.settings().getMapMetric(), model.isBleMode()));
        state.coverage.addListener((obs, old, stats) -> legend.showCoverage(stats));
        StackPane plan = new StackPane(planView);
        plan.getStyleClass().add("plan-stack");
        viewport = new PlanViewport(plan, legend);

        ProjectController projects = new ProjectController(model, state, stage, getHostServices(),
                () -> Snapshots.planWithOverlay(plan, legend));
        projects.openExample("Smart apartment", DemoScenario.smartApartment());

        BorderPane root = new BorderPane();
        root.setTop(new VBox(AppMenuBar.build(model, state, projects, simulation, editor, viewport, stage),
                EditorToolBar.build(model, state, simulation, viewport)));
        root.setCenter(viewport);
        root.setRight(ControlPanel.build(model, state, editor, simulation, projects));
        root.setBottom(statusBar(model, state, heatmaps, simulation));

        Scene scene = new Scene(root);
        scene.getStylesheets().add(SimulatorApp.class.getResource("app.css").toExternalForm());
        stage.setScene(scene);
        for (int size : new int[] { 16, 32, 48, 64, 128, 256 }) {
            stage.getIcons().add(new Image(SimulatorApp.class.getResourceAsStream("icon-" + size + ".png")));
        }
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double minWidth = Math.min(960.0, screen.getWidth());
        double minHeight = minWidth / ASPECT_RATIO;
        double targetWidth = Math.min(1280.0, screen.getWidth() * 0.88);
        double targetHeight = targetWidth / ASPECT_RATIO;
        if (targetHeight > screen.getHeight() * 0.88) {
            targetHeight = screen.getHeight() * 0.88;
            targetWidth = targetHeight * ASPECT_RATIO;
        }

        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        stage.setWidth(targetWidth);
        stage.setHeight(targetHeight);
        stage.centerOnScreen();
        stage.show();
        setupAspectRatioLock(stage);
        javafx.application.Platform.runLater(viewport::fit);

        state.status.set("Smart apartment BLE demo loaded. Drag the devices, or press Optimise hub.");
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

    private static HBox statusBar(SimulatorModel model, UiState state, HeatmapService heatmaps, SimulationController simulation) {
        Label status = new Label();
        status.textProperty().bind(state.status);
        Label pointer = new Label();
        pointer.textProperty().bind(state.pointer);
        pointer.getStyleClass().add("status-pointer");

        Label modeBadge = new Label();
        modeBadge.getStyleClass().add("status-badge");
        Runnable updateBadge = () -> {
            if (model.isBleMode()) {
                modeBadge.setText("BLE Mode (2.4 GHz | 1 MHz)");
                modeBadge.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-padding: 2 10 2 10; -fx-background-radius: 12; -fx-font-weight: bold; -fx-font-size: 11px;");
            } else {
                modeBadge.setText(String.format("WiFi (%.0f MHz | %.0f MHz)", model.environment().getFreqMHz(), model.environment().getBandwidthHz() / 1e6));
                modeBadge.setStyle("-fx-background-color: #475569; -fx-text-fill: white; -fx-padding: 2 10 2 10; -fx-background-radius: 12; -fx-font-size: 11px;");
            }
        };
        model.addListener(change -> {
            if (change == SimulatorModel.Change.RADIO) {
                updateBadge.run();
            }
        });
        updateBadge.run();

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
        HBox bar = new HBox(12, modeBadge, status, spacer, optimising, progressLabel, progress, pointer);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void setupAspectRatioLock(Stage stage) {
        stage.widthProperty().addListener((obs, oldVal, newVal) -> enforceAspectRatio(stage, true));
        stage.heightProperty().addListener((obs, oldVal, newVal) -> enforceAspectRatio(stage, false));
    }

    private void enforceAspectRatio(Stage stage, boolean fromWidth) {
        if (adjustingSize || stage.isMaximized() || stage.isFullScreen() || stage.isIconified()) {
            return;
        }
        double w = stage.getWidth();
        double h = stage.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        double currentRatio = w / h;
        if (Math.abs(currentRatio - ASPECT_RATIO) < 0.005) {
            return;
        }
        adjustingSize = true;
        try {
            if (fromWidth) {
                stage.setHeight(w / ASPECT_RATIO);
            } else {
                stage.setWidth(h * ASPECT_RATIO);
            }
        } finally {
            adjustingSize = false;
        }
    }
}
