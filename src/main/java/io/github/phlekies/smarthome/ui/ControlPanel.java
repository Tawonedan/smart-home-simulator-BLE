package io.github.phlekies.smarthome.ui;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;

/** Side panel: one tab per concern (devices, walls, radio model, results). */
final class ControlPanel {

    static final double WIDTH_PX = 390;

    private ControlPanel() {
    }

    static TabPane build(SimulatorModel model, UiState state, FloorPlanEditor editor,
                         SimulationController simulation, ProjectController projects) {
        TabPane tabs = new TabPane(
                tab("Devices", new DevicesSection(model, state).build()),
                tab("Plan", new VBox(14,
                        new FloorPlanSection(model, state, editor).build(),
                        new TemplatesSection(model, state).build())),
                tab("Radio", new VBox(14,
                        new SimulationSection(model).build(),
                        new RadioSection(model, state).build())),
                tab("Results", new ResultsSection(state, simulation, projects).build()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("side-panel");
        tabs.setPrefWidth(WIDTH_PX);
        tabs.setMinWidth(WIDTH_PX);
        tabs.setMaxWidth(WIDTH_PX);
        return tabs;
    }

    private static Tab tab(String title, Node content) {
        content.getStyleClass().add("side-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("side-scroll");
        return new Tab(title, scroll);
    }
}
