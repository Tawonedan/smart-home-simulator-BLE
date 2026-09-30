package io.github.phlekies.smarthome.ui;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;

/** Scrollable side panel that stacks all the control sections. */
final class ControlPanel {

    static final double WIDTH_PX = 370;

    private ControlPanel() {
    }

    static ScrollPane build(SimulatorModel model, UiState state, FloorPlanEditor editor,
                            SimulationController controller) {
        Label title = new Label("Smart Home Simulator");
        title.getStyleClass().add("panel-title");
        Label subtitle = new Label("Design a floor plan, place IoT sensors and a hub, and analyse Wi-Fi coverage.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("panel-subtitle");

        VBox actions = Ui.section("Visualise",
                Ui.primaryButton("Launch rays", e -> controller.launchRays()),
                Ui.secondaryButton("Launch waves", e -> controller.launchWaves()),
                Ui.secondaryButton("Open simulation summary", e -> controller.openSummary()));

        VBox content = new VBox(title, subtitle,
                new DevicesSection(model, state).build(),
                new SimulationSection(model, controller).build(),
                actions,
                new FloorPlanSection(model, state, editor).build(),
                new TemplatesSection(model, state).build(),
                new RadioSection(model, state).build());
        content.getStyleClass().add("side-content");

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("side-panel");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(WIDTH_PX);
        scroll.setMinWidth(WIDTH_PX);
        // Without a preferred height the panel asks for its full content height (~2800 px).
        scroll.setPrefHeight(PlanCoordinates.VIEW_HEIGHT_PX);
        return scroll;
    }
}
