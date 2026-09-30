package io.github.phlekies.smarthome.ui;

import java.util.List;

import atlantafx.base.theme.Styles;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.simulation.MapMetric;

/** Tool palette, undo/redo, heatmap controls and simulation actions above the plan. */
final class EditorToolBar {

    private EditorToolBar() {
    }

    static ToolBar build(SimulatorModel model, UiState state, SimulationController simulation,
                         PlanViewport viewport) {
        ToggleGroup tools = new ToggleGroup();
        HBox toolButtons = new HBox();
        EditorTool[] values = EditorTool.values();
        for (int i = 0; i < values.length; i++) {
            EditorTool tool = values[i];
            ToggleButton button = new ToggleButton(null, Icons.of(iconFor(tool)));
            button.setUserData(tool);
            button.setToggleGroup(tools);
            button.setTooltip(new Tooltip(tool + ": " + tool.help()));
            button.getStyleClass().add(i == 0 ? Styles.LEFT_PILL : i == values.length - 1 ? Styles.RIGHT_PILL : Styles.CENTER_PILL);
            button.setSelected(state.tool.get() == tool);
            toolButtons.getChildren().add(button);
        }
        tools.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                old.setSelected(true); // a tool is always active
            } else {
                state.tool.set((EditorTool) selected.getUserData());
            }
        });
        state.tool.addListener((obs, old, tool) -> tools.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == tool).findFirst().ifPresent(toggle -> toggle.setSelected(true)));

        Button undo = iconButton(Icons.UNDO, "Undo (Ctrl+Z)", () -> {
            if (model.undo()) state.status.set("Last plan edit undone.");
        });
        Button redo = iconButton(Icons.REDO, "Redo (Ctrl+Y)", () -> {
            if (model.redo()) state.status.set("Plan edit redone.");
        });
        Runnable refreshHistory = () -> {
            undo.setDisable(!model.canUndo());
            redo.setDisable(!model.canRedo());
        };
        model.addListener(change -> refreshHistory.run());
        refreshHistory.run();

        ToggleButton heatmap = new ToggleButton("Heatmap", Icons.of(Icons.HEATMAP));
        heatmap.selectedProperty().bindBidirectional(state.heatmapVisible);
        heatmap.setTooltip(new Tooltip("Show the coverage heatmap (Ctrl+H)"));

        ComboBox<MapMetric> metric = Ui.comboBox(List.of(MapMetric.values()), model.settings().getMapMetric());
        metric.setMaxWidth(Region.USE_PREF_SIZE);
        metric.setTooltip(new Tooltip("Quantity shown on the heatmap"));
        metric.setOnAction(e -> {
            model.settings().setMapMetric(metric.getValue());
            model.displayChanged();
        });
        model.addListener(change -> {
            if (metric.getValue() != model.settings().getMapMetric()) {
                metric.setValue(model.settings().getMapMetric());
            }
        });

        Button rays = textButton("Rays", Icons.RAYS, "Animate ray tracing from every sensor", simulation::launchRays);
        Button waves = textButton("Waves", Icons.WAVES, "Animate wavefronts reaching the hub", simulation::launchWaves);
        Button optimise = textButton("Optimise hub", Icons.OPTIMISE,
                "Move the hub to the position that maximises the weakest sensor link", simulation::optimiseHub);
        optimise.getStyleClass().add("toolbar-accent");
        optimise.disableProperty().bind(simulation.optimisingProperty());

        ToggleButton theme = new ToggleButton();
        theme.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        theme.selectedProperty().bindBidirectional(state.darkTheme);
        theme.graphicProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                () -> Icons.of(state.darkTheme.get() ? Icons.LIGHT : Icons.DARK), state.darkTheme));
        theme.setTooltip(new Tooltip("Switch between the dark and the light theme"));

        Button zoomOut = iconButton(Icons.ZOOM_OUT, "Zoom out (Ctrl+-)", viewport::zoomOut);
        Button zoomIn = iconButton(Icons.ZOOM_IN, "Zoom in (Ctrl++, or Ctrl + mouse wheel)", viewport::zoomIn);
        Button fit = iconButton(Icons.FIT, "Fit the plan to the window (Ctrl+0)", viewport::fit);
        Label zoomLabel = new Label();
        zoomLabel.getStyleClass().add("zoom-label");
        zoomLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> Math.round(viewport.zoomProperty().get() * 100) + "%", viewport.zoomProperty()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label metricLabel = new Label("Metric");

        ToolBar bar = new ToolBar(toolButtons, new Separator(), undo, redo, new Separator(), heatmap, metricLabel,
                metric, new Separator(), rays, waves, optimise, spacer, zoomOut, zoomLabel, zoomIn, fit,
                new Separator(), theme);
        bar.getStyleClass().add("editor-toolbar");
        return bar;
    }

    private static Button iconButton(org.kordamp.ikonli.Ikon icon, String tooltip, Runnable action) {
        Button button = new Button(null, Icons.of(icon));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Button textButton(String text, org.kordamp.ikonli.Ikon icon, String tooltip, Runnable action) {
        Button button = new Button(text, Icons.of(icon));
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private static org.kordamp.ikonli.Ikon iconFor(EditorTool tool) {
        return switch (tool) {
            case SELECT -> Icons.SELECT;
            case WALL -> Icons.WALL;
            case ROOM -> Icons.ROOM;
            case SENSOR -> Icons.SENSOR;
            case HUB -> Icons.HUB;
            case DELETE -> Icons.ERASE;
        };
    }
}
