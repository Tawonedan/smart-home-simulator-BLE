package io.github.phlekies.smarthome.ui;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

import io.github.phlekies.smarthome.app.SimulatorModel;

/** JavaFX entry point: wires the model, the plan view, the editor and the side panel. */
public final class SimulatorApp extends Application {

    private SimulationController controller;

    @Override
    public void start(Stage stage) {
        SimulatorModel model = new SimulatorModel();
        UiState state = new UiState();
        PlanView planView = new PlanView(model, state);
        HeatmapService heatmaps = new HeatmapService();
        CellProbe probe = new CellProbe(model, state, heatmaps, planView);
        FloorPlanEditor editor = new FloorPlanEditor(model, state, planView, probe);
        controller = new SimulationController(model, state, planView, heatmaps, stage);

        StackPane planHolder = new StackPane(planView);
        planHolder.getStyleClass().add("plan-area");
        ScrollPane planScroll = new ScrollPane(planHolder);
        planScroll.setFitToWidth(true);
        planScroll.setFitToHeight(true);
        planScroll.setPrefViewportWidth(PlanCoordinates.VIEW_WIDTH_PX);
        planScroll.setPrefViewportHeight(PlanCoordinates.VIEW_HEIGHT_PX);
        planScroll.getStyleClass().add("plan-area");

        BorderPane root = new BorderPane();
        root.setCenter(planScroll);
        root.setRight(ControlPanel.build(model, state, editor, controller));
        root.setBottom(statusBar(state, heatmaps));

        Scene scene = new Scene(root);
        scene.getStylesheets().add(SimulatorApp.class.getResource("app.css").toExternalForm());
        stage.setTitle("Smart Home Simulator");
        stage.setScene(scene);
        stage.sizeToScene();
        fitToScreen(stage);
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    private static HBox statusBar(UiState state, HeatmapService heatmaps) {
        Label status = new Label();
        status.textProperty().bind(state.status);

        ProgressBar progress = new ProgressBar();
        progress.progressProperty().bind(heatmaps.progressProperty());
        progress.setPrefWidth(160);
        Label progressLabel = new Label();
        progressLabel.textProperty().bind(Bindings.createStringBinding(
                () -> String.format("Computing coverage... %.0f%%", Math.max(0, heatmaps.progressProperty().get()) * 100),
                heatmaps.progressProperty()));
        for (var node : new javafx.scene.Node[] { progress, progressLabel }) {
            node.visibleProperty().bind(heatmaps.runningProperty());
            node.managedProperty().bind(heatmaps.runningProperty());
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(status, spacer, progressLabel, progress);
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
