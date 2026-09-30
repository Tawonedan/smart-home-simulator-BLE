package io.github.phlekies.smarthome.ui;

import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import io.github.phlekies.smarthome.app.LinkSummary;
import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Material;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.simulation.CoverageStats;

/** View state shared by the plan, the editor, the toolbar and the side panel. */
final class UiState {

    final ObjectProperty<Device> selectedDevice = new SimpleObjectProperty<>();
    final ObjectProperty<Wall> selectedWall = new SimpleObjectProperty<>();
    final ObjectProperty<EditorTool> tool = new SimpleObjectProperty<>(EditorTool.SELECT);
    /** Feedback for the last user action, shown in the status bar. */
    final StringProperty status = new SimpleStringProperty(EditorTool.SELECT.help());
    /** Pointer position over the plan, shown in the status bar. */
    final StringProperty pointer = new SimpleStringProperty("");

    final BooleanProperty heatmapVisible = new SimpleBooleanProperty(false);
    final BooleanProperty linksVisible = new SimpleBooleanProperty(true);
    final BooleanProperty darkTheme = new SimpleBooleanProperty(true);

    /** Coverage of the heatmap on screen ({@link CoverageStats#EMPTY} when there is none). */
    final ObjectProperty<CoverageStats> coverage = new SimpleObjectProperty<>(CoverageStats.EMPTY);
    /** Link of every sensor to the hub (empty without a hub). */
    final ObjectProperty<List<LinkSummary>> links = new SimpleObjectProperty<>(List.of());
    /** Human-readable outcome of the last hub placement optimisation. */
    final StringProperty optimisation = new SimpleStringProperty("");

    /** Material and thickness used when drawing new walls. */
    final ObjectProperty<Material> drawMaterial = new SimpleObjectProperty<>(Materials.DRYWALL);
    final DoubleProperty drawThicknessCm = new SimpleDoubleProperty(8.0);
    final BooleanProperty snapToGrid = new SimpleBooleanProperty(true);
}
