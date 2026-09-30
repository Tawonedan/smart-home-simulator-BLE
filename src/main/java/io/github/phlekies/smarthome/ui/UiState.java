package io.github.phlekies.smarthome.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Material;
import io.github.phlekies.smarthome.model.material.Materials;

/** View state shared by the plan, the editor and the side panel (selection, active tool...). */
final class UiState {

    final ObjectProperty<Device> selectedDevice = new SimpleObjectProperty<>();
    final ObjectProperty<Wall> selectedWall = new SimpleObjectProperty<>();
    final ObjectProperty<EditorTool> tool = new SimpleObjectProperty<>(EditorTool.SELECT);
    /** Feedback for the last user action, shown in the status bar. */
    final StringProperty status = new SimpleStringProperty(EditorTool.SELECT.help());
    final BooleanProperty heatmapVisible = new SimpleBooleanProperty(false);

    /** Material and thickness used when drawing new walls. */
    final ObjectProperty<Material> drawMaterial = new SimpleObjectProperty<>(Materials.DRYWALL);
    final DoubleProperty drawThicknessCm = new SimpleDoubleProperty(8.0);
    final BooleanProperty snapToGrid = new SimpleBooleanProperty(true);
}
